import React, { createContext, useCallback, useContext, useEffect, useRef } from 'react';
import shortcuts from './shortcuts.json';
import { usePlatform } from '../Platform';
import {
    createBackHandledEnvelope,
    getRouteFocusSelectors,
    normalizeDeepLinkToHash,
    setBoundedRouteSnapshot,
    shouldHandleDirectionalKey,
} from './tvHostEvents';
import {
    DEFAULT_FOCUSABLE_SELECTORS as PROFILE_DEFAULT_FOCUSABLE_SELECTORS,
    OVERLAY_SELECTORS as PROFILE_OVERLAY_SELECTORS,
    getRouteNavigationProfile,
    getZoneSelectors,
    getZoneTransferTargets,
    resolveElementZone,
    type Direction as NavigationDirection,
    type TvZone,
} from './tvNavigationProfile';
import {
    createFocusSnapshot,
    restoreFocusSnapshot,
    type TvFocusSnapshot,
} from './tvFocusRestore';

const SHORTCUTS = shortcuts.map(({ shortcuts }) => shortcuts).flat();

const HOST_EVENT_NAME = 'stremio:host-event';
const TV_FOCUS_RING_STYLE_ID = 'stremio-tv-focus-ring';
const TV_NAV_V2_FLAG_KEY = 'tv_nav_v2';
const TV_NAV_V2_QUERY_KEY = 'tv_nav_v2';
const OVERLAY_CLOSE_SETTLE_MS = 60;
const HOST_KEY_DEDUP_MS = 150;
const DIAGNOSTIC_LIMIT = 240;
const ROUTE_FOCUS_SNAPSHOT_LIMIT = 100;

type Direction = NavigationDirection;

export type ShortcutName = string;
export type ShortcutListener = (combo: number) => void;

interface ShortcutsContext {
    grouped: ShortcutGroup[],
    on: (name: ShortcutName, listener: ShortcutListener) => void,
    off: (name: ShortcutName, listener: ShortcutListener) => void,
}

type HostEventEnvelope = {
    type?: string,
    payload?: Record<string, unknown>,
};

type TvDiagnosticCounterKey =
    | 'deadEndMoves'
    | 'focusRecoveryCount'
    | 'zoneTransfers'
    | 'overlayEscapes'
    | 'restoreFocusSuccess';

type TvDiagnosticsCounters = Record<TvDiagnosticCounterKey, number>;

type TvDiagnosticEntry = {
    timestampMs: number,
    event: string,
    routeHash: string,
    zone?: TvZone,
    direction?: Direction,
    detail?: Record<string, unknown>,
};

type TvNavigationContextPayload = {
    routeHash?: string,
    zone?: TvZone,
    focusKey?: string,
    scrollY?: number,
    timestampMs?: number,
    sessionId?: string,
};

declare global {
    interface Window {
        navigate?: (direction: Direction) => void,
        stremioHost?: {
            sendCommand?: (commandJson: string) => void,
        },
        __stremioTvDiagnostics?: TvDiagnosticEntry[],
        __stremioTvDiagnosticsCounters?: TvDiagnosticsCounters,
        __stremioTvNavigationContext?: TvNavigationContextPayload,
    }
}

const ShortcutsContext = createContext<ShortcutsContext>({} as ShortcutsContext);

type Props = {
    children: JSX.Element,
    onShortcut: (name: ShortcutName) => void,
};

const ARROW_KEY_TO_DIRECTION: Record<string, Direction> = {
    ArrowUp: 'up',
    ArrowDown: 'down',
    ArrowLeft: 'left',
    ArrowRight: 'right',
};

const KEY_TO_DIRECTION: Partial<Record<string, Direction>> = {
    ArrowUp: 'up',
    ArrowDown: 'down',
    ArrowLeft: 'left',
    ArrowRight: 'right',
};

const DEFAULT_DIAGNOSTIC_COUNTERS: TvDiagnosticsCounters = {
    deadEndMoves: 0,
    focusRecoveryCount: 0,
    zoneTransfers: 0,
    overlayEscapes: 0,
    restoreFocusSuccess: 0,
};

const DEFAULT_FOCUSABLE_SELECTORS = [
    ...PROFILE_DEFAULT_FOCUSABLE_SELECTORS,
    '[class*="nav-tab-button-container"]',
    '[class*="control-bar-button"]',
];

const OVERLAY_SELECTORS = Array.from(new Set([
    ...PROFILE_OVERLAY_SELECTORS,
    '[role="dialog"]',
    '[aria-modal="true"]',
]));

const navigationSessionId = `tv-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
let lastHostKeyProcessedAt = 0;
let lastFocusSnapshot: TvFocusSnapshot | null = null;
const routeFocusSnapshots = new Map<string, TvFocusSnapshot>();

const isRecord = (value: unknown): value is Record<string, unknown> => {
    return typeof value === 'object' && value !== null;
};

const isEditableTarget = (target: EventTarget | null) => {
    if (!(target instanceof HTMLElement)) {
        return false;
    }

    const tagName = target.tagName.toUpperCase();
    return tagName === 'INPUT' || tagName === 'TEXTAREA' || tagName === 'SELECT' || target.isContentEditable;
};

const getCurrentRouteHash = () => {
    return window.location.hash || '#/';
};

const hasRealFocusedElement = () => {
    const activeElement = document.activeElement;
    return activeElement instanceof HTMLElement && activeElement !== document.body && activeElement !== document.documentElement;
};

const isPlayerRoute = () => {
    return getCurrentRouteHash().startsWith('#/player');
};

const isVisibleElement = (element: HTMLElement) => {
    if (!element.isConnected || element.hidden || element.getAttribute('aria-hidden') === 'true') {
        return false;
    }

    const style = window.getComputedStyle(element);
    if (style.visibility === 'hidden' || style.display === 'none' || style.opacity === '0') {
        return false;
    }

    const rect = element.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
};

const ensureFocusableElement = (element: HTMLElement) => {
    const naturallyFocusable = element.matches('a[href],button,input,textarea,select,[tabindex]');
    if (!naturallyFocusable && element.tabIndex < 0) {
        element.setAttribute('tabindex', '0');
    }
    return element;
};

const collectFocusableCandidates = (root: ParentNode, selectors: string[]) => {
    const seen = new Set<HTMLElement>();
    const candidates: HTMLElement[] = [];

    for (const selector of selectors) {
        const matches = Array.from(root.querySelectorAll<HTMLElement>(selector));
        for (const element of matches) {
            if (
                seen.has(element) ||
                !isVisibleElement(element) ||
                element.getAttribute('tabindex') === '-1' ||
                element.matches('[class*="see-all-container"]')
            ) {
                continue;
            }
            seen.add(element);
            candidates.push(ensureFocusableElement(element));
        }
    }

    return candidates;
};

const focusElement = (element: HTMLElement) => {
    try {
        element.focus({ preventScroll: true });
    } catch (_) {
        element.focus();
    }
    try {
        element.scrollIntoView({ behavior: 'auto', block: 'nearest', inline: 'nearest' });
    } catch (_) {
        element.scrollIntoView(false);
    }
};

const focusFirstElement = (root: ParentNode, selectors: string[]) => {
    const candidates = collectFocusableCandidates(root, selectors);
    const target = candidates[0];
    if (!target) {
        return false;
    }
    focusElement(target);
    return true;
};

const getElementDebugLabel = (element: Element | null) => {
    if (!(element instanceof HTMLElement)) {
        return 'null';
    }

    const tag = element.tagName.toLowerCase();
    const id = element.id ? `#${element.id}` : '';
    const className = typeof element.className === 'string'
        ? element.className.trim().split(/\s+/).filter(Boolean).slice(0, 3).join('.')
        : '';
    const classes = className ? `.${className}` : '';
    const text = (element.getAttribute('aria-label') || element.textContent || '')
        .trim()
        .replace(/\s+/g, ' ')
        .slice(0, 42);
    return `${tag}${id}${classes} "${text}"`;
};

const ensureDiagnosticsCounters = () => {
    if (!window.__stremioTvDiagnosticsCounters) {
        window.__stremioTvDiagnosticsCounters = { ...DEFAULT_DIAGNOSTIC_COUNTERS };
    }
    return window.__stremioTvDiagnosticsCounters;
};

const incrementDiagnosticCounter = (counter: TvDiagnosticCounterKey) => {
    const counters = ensureDiagnosticsCounters();
    counters[counter] = (counters[counter] || 0) + 1;
};

const recordTvDiagnostic = (
    event: string,
    detail: Record<string, unknown> = {},
    zone?: TvZone,
    direction?: Direction
) => {
    const entry: TvDiagnosticEntry = {
        timestampMs: Date.now(),
        event,
        routeHash: getCurrentRouteHash(),
        zone,
        direction,
        detail,
    };

    window.__stremioTvDiagnostics = window.__stremioTvDiagnostics || [];
    window.__stremioTvDiagnostics.push(entry);
    if (window.__stremioTvDiagnostics.length > DIAGNOSTIC_LIMIT) {
        window.__stremioTvDiagnostics = window.__stremioTvDiagnostics.slice(-DIAGNOSTIC_LIMIT);
    }

    if (console && typeof console.info === 'function') {
        console.info('[TVNav]', event, detail);
    }
};

const getRouteEntrySelectors = (hash: string) => {
    const profile = getRouteNavigationProfile(hash);
    const legacySelectors = getRouteFocusSelectors(hash);
    return Array.from(new Set([
        ...profile.entrySelectors,
        ...legacySelectors,
        ...DEFAULT_FOCUSABLE_SELECTORS,
    ]));
};

const applyActiveZone = (zone: TvZone) => {
    document.body.setAttribute('data-tv-active-zone', zone);
};

const publishNavigationContext = (snapshot: TvFocusSnapshot | null) => {
    if (!snapshot) {
        return;
    }

    window.__stremioTvNavigationContext = {
        routeHash: snapshot.routeHash,
        zone: snapshot.zone,
        focusKey: snapshot.focusKey,
        scrollY: snapshot.scrollY,
        timestampMs: snapshot.timestampMs,
        sessionId: snapshot.sessionId,
    };
};

const captureCurrentFocusSnapshot = (source: string) => {
    const activeElement = document.activeElement;
    if (!(activeElement instanceof HTMLElement) || activeElement === document.body || activeElement === document.documentElement) {
        return null;
    }

    const routeHash = getCurrentRouteHash();
    const profile = getRouteNavigationProfile(routeHash);
    const zone = resolveElementZone(activeElement, profile);
    const snapshot = createFocusSnapshot({
        element: activeElement,
        routeHash,
        zone,
        scrollY: Math.max(0, window.scrollY || 0),
        sessionId: navigationSessionId,
    });
    if (!snapshot) {
        return null;
    }

    lastFocusSnapshot = snapshot;
    setBoundedRouteSnapshot(routeFocusSnapshots, routeHash, snapshot, ROUTE_FOCUS_SNAPSHOT_LIMIT);
    applyActiveZone(zone);
    publishNavigationContext(snapshot);
    recordTvDiagnostic('focus.snapshot', { source, element: getElementDebugLabel(activeElement) }, zone);
    return snapshot;
};

const coerceNavigationContext = (input: unknown): TvNavigationContextPayload | null => {
    if (!isRecord(input)) {
        return null;
    }

    const zone = typeof input.zone === 'string' ? input.zone.toLowerCase() : '';
    const normalizedZone: TvZone = (
        zone === 'sidebar' ||
        zone === 'topbar' ||
        zone === 'content' ||
        zone === 'overlay' ||
        zone === 'unknown'
    ) ? zone : 'unknown';

    return {
        routeHash: typeof input.routeHash === 'string' ? input.routeHash : undefined,
        zone: normalizedZone,
        focusKey: typeof input.focusKey === 'string' ? input.focusKey : undefined,
        scrollY: typeof input.scrollY === 'number' && Number.isFinite(input.scrollY) ? input.scrollY : undefined,
        timestampMs: typeof input.timestampMs === 'number' && Number.isFinite(input.timestampMs) ? input.timestampMs : undefined,
        sessionId: typeof input.sessionId === 'string' ? input.sessionId : undefined,
    };
};

const buildRestoreSnapshot = (hint?: TvNavigationContextPayload | null) => {
    const currentRouteHash = getCurrentRouteHash();
    const routeHint = hint?.routeHash || currentRouteHash;
    const storedForHint = routeFocusSnapshots.get(routeHint);
    const storedForCurrent = routeFocusSnapshots.get(currentRouteHash);
    const stored = storedForCurrent || storedForHint || lastFocusSnapshot;

    if (!hint && !stored) {
        return null;
    }

    if (!hint && stored) {
        return stored;
    }

    const merged: TvFocusSnapshot = {
        routeHash: hint?.routeHash || stored?.routeHash || currentRouteHash,
        zone: hint?.zone || stored?.zone || 'unknown',
        focusKey: hint?.focusKey || stored?.focusKey,
        containerKey: stored?.containerKey,
        indexInContainer: stored?.indexInContainer,
        scrollY: hint?.scrollY ?? stored?.scrollY ?? Math.max(0, window.scrollY || 0),
        timestampMs: hint?.timestampMs ?? Date.now(),
        sessionId: hint?.sessionId || stored?.sessionId || navigationSessionId,
    };

    return merged;
};

const focusRouteEntryPoint = (reason: string) => {
    const selectors = getRouteEntrySelectors(getCurrentRouteHash());
    const focused = focusFirstElement(document, selectors);
    if (focused) {
        incrementDiagnosticCounter('focusRecoveryCount');
        recordTvDiagnostic('focus.route_entry', { reason });
        captureCurrentFocusSnapshot('route_entry');
    }
    return focused;
};

const attemptFocusRestore = (reason: string, hint?: TvNavigationContextPayload | null) => {
    const routeHash = getCurrentRouteHash();
    const snapshot = buildRestoreSnapshot(hint);
    if (snapshot && typeof snapshot.scrollY === 'number' && Number.isFinite(snapshot.scrollY)) {
        window.scrollTo(0, Math.max(0, snapshot.scrollY));
    }

    const routeEntryCandidates = collectFocusableCandidates(document, getRouteEntrySelectors(routeHash));
    const result = restoreFocusSnapshot({
        snapshot,
        routeHash,
        routeEntryCandidates,
        isVisibleElement,
        focusElement,
    });

    if (result.restored) {
        incrementDiagnosticCounter('restoreFocusSuccess');
        recordTvDiagnostic('focus.restore', { reason, restoreReason: result.reason }, hint?.zone);
        captureCurrentFocusSnapshot('restore');
        return true;
    }

    const fallback = focusRouteEntryPoint(`restore_failed:${reason}`);
    recordTvDiagnostic('focus.restore_failed', { reason, fallback });
    return fallback;
};

const getHashQueryParamValue = (key: string) => {
    const hash = window.location.hash || '';
    const queryIndex = hash.indexOf('?');
    if (queryIndex < 0) {
        return null;
    }
    const query = hash.slice(queryIndex + 1);
    return new URLSearchParams(query).get(key);
};

const parseFlagValue = (value: string | null) => {
    if (value == null) {
        return null;
    }
    const normalized = value.trim().toLowerCase();
    if (normalized === '') {
        return true;
    }
    if (['1', 'true', 'on', 'yes', 'enabled'].includes(normalized)) {
        return true;
    }
    if (['0', 'false', 'off', 'no', 'disabled'].includes(normalized)) {
        return false;
    }
    return null;
};

const isTvNavV2Enabled = () => {
    const searchFlag = parseFlagValue(new URLSearchParams(window.location.search).get(TV_NAV_V2_QUERY_KEY));
    if (searchFlag != null) {
        return searchFlag;
    }

    const hashFlag = parseFlagValue(getHashQueryParamValue(TV_NAV_V2_QUERY_KEY));
    if (hashFlag != null) {
        return hashFlag;
    }

    try {
        const storageFlag = parseFlagValue(window.localStorage.getItem(TV_NAV_V2_FLAG_KEY));
        if (storageFlag != null) {
            return storageFlag;
        }
    } catch (_) {
        // Ignore storage errors in restricted webviews.
    }

    return false;
};

const resolveCurrentCandidate = (
    activeElement: Element | null,
    candidates: HTMLElement[],
    selectors: string[]
) => {
    if (!(activeElement instanceof HTMLElement)) {
        return null;
    }

    if (candidates.includes(activeElement)) {
        return activeElement;
    }

    for (const selector of selectors) {
        const closest = activeElement.closest<HTMLElement>(selector);
        if (closest && candidates.includes(closest)) {
            return closest;
        }
    }

    const fallbackFocusable = activeElement.closest<HTMLElement>('a[href],button,input,textarea,select,[tabindex]');
    if (fallbackFocusable && candidates.includes(fallbackFocusable)) {
        return fallbackFocusable;
    }

    return null;
};

const scoreDirectionalCandidate = (currentRect: DOMRect, candidateRect: DOMRect, direction: Direction) => {
    const currentCenterX = currentRect.left + (currentRect.width / 2);
    const currentCenterY = currentRect.top + (currentRect.height / 2);
    const candidateCenterX = candidateRect.left + (candidateRect.width / 2);
    const candidateCenterY = candidateRect.top + (candidateRect.height / 2);
    const dx = candidateCenterX - currentCenterX;
    const dy = candidateCenterY - currentCenterY;

    if (direction === 'right' && dx <= 0) return null;
    if (direction === 'left' && dx >= 0) return null;
    if (direction === 'down' && dy <= 0) return null;
    if (direction === 'up' && dy >= 0) return null;

    const horizontal = direction === 'left' || direction === 'right';
    const primaryDist = horizontal
        ? (direction === 'right'
            ? Math.max(0, candidateRect.left - currentRect.right)
            : Math.max(0, currentRect.left - candidateRect.right))
        : (direction === 'down'
            ? Math.max(0, candidateRect.top - currentRect.bottom)
            : Math.max(0, currentRect.top - candidateRect.bottom));

    const overlap = horizontal
        ? Math.max(0, Math.min(currentRect.bottom, candidateRect.bottom) - Math.max(currentRect.top, candidateRect.top))
        : Math.max(0, Math.min(currentRect.right, candidateRect.right) - Math.max(currentRect.left, candidateRect.left));

    const orthogonalDist = horizontal
        ? Math.abs(candidateCenterY - currentCenterY)
        : Math.abs(candidateCenterX - currentCenterX);

    const primaryCenterDist = horizontal ? Math.abs(dx) : Math.abs(dy);
    if (orthogonalDist > (primaryCenterDist * 3.5) + 80) {
        return null;
    }

    const rowPreference = horizontal
        ? (overlap > 0 ? -Math.min(220, overlap * 2.5) : orthogonalDist * 2.2)
        : (overlap > 0 ? -Math.min(120, overlap * 1.2) : orthogonalDist * 1.4);

    return (primaryDist * 12) + (orthogonalDist * 3.5) + rowPreference + Math.hypot(dx, dy);
};

const selectDirectionalCandidate = (
    current: HTMLElement,
    candidates: HTMLElement[],
    direction: Direction
) => {
    const currentRect = current.getBoundingClientRect();
    const scoredCandidates = candidates
        .filter((candidate) => candidate !== current)
        .map((candidate) => ({
            candidate,
            score: scoreDirectionalCandidate(currentRect, candidate.getBoundingClientRect(), direction),
        }))
        .filter((entry): entry is { candidate: HTMLElement, score: number } => entry.score !== null)
        .sort((a, b) => a.score - b.score);

    return scoredCandidates[0]?.candidate || null;
};

type MoveAttemptResult = {
    moved: boolean,
    reason: 'moved' | 'no_candidates' | 'no_current' | 'dead_end',
    next?: HTMLElement | null,
};

const moveFocusByDirection = (
    root: ParentNode,
    direction: Direction,
    selectors: string[],
    currentOverride?: HTMLElement | null
): MoveAttemptResult => {
    const allCandidates = collectFocusableCandidates(root, selectors);
    if (allCandidates.length === 0) {
        return { moved: false, reason: 'no_candidates' };
    }

    const current = currentOverride || resolveCurrentCandidate(document.activeElement, allCandidates, selectors);
    if (!current) {
        focusElement(allCandidates[0]);
        return { moved: true, reason: 'no_current', next: allCandidates[0] };
    }

    const next = selectDirectionalCandidate(current, allCandidates, direction);
    if (!next) {
        return { moved: false, reason: 'dead_end' };
    }

    focusElement(next);
    return { moved: true, reason: 'moved', next };
};

const getTopOverlayElement = () => {
    const selector = OVERLAY_SELECTORS.join(', ');
    const overlays = Array.from(document.querySelectorAll<HTMLElement>(selector))
        .filter((element) => isVisibleElement(element));

    const activeElement = document.activeElement;
    if (activeElement instanceof HTMLElement) {
        const activeOverlay = activeElement.closest<HTMLElement>(selector);
        if (activeOverlay && isVisibleElement(activeOverlay) && !overlays.includes(activeOverlay)) {
            overlays.push(activeOverlay);
        }
    }

    return overlays.length > 0 ? overlays[overlays.length - 1] : null;
};

const isBoundaryMove = (zone: TvZone, direction: Direction, transferTargets: TvZone[]) => {
    if (zone === 'sidebar' && direction === 'left') {
        return true;
    }
    if (zone === 'topbar' && direction === 'up') {
        return true;
    }
    if ((zone === 'content' || zone === 'unknown') && transferTargets.length === 0) {
        return true;
    }
    return false;
};

const navigateTvDirectionLegacy = (direction: Direction) => {
    const activeOverlay = getTopOverlayElement();
    if (activeOverlay) {
        const result = moveFocusByDirection(activeOverlay, direction, DEFAULT_FOCUSABLE_SELECTORS);
        if (!result.moved && result.reason === 'dead_end') {
            incrementDiagnosticCounter('deadEndMoves');
        }
        return result.moved;
    }

    const routeSelectors = getRouteFocusSelectors(getCurrentRouteHash());
    const allSelectors = Array.from(new Set([...routeSelectors, ...DEFAULT_FOCUSABLE_SELECTORS]));

    if (typeof window.navigate === 'function') {
        const before = document.activeElement;
        window.navigate(direction);
        if (document.activeElement !== before) {
            captureCurrentFocusSnapshot('navigate_legacy_native');
            return true;
        }
    }

    const result = moveFocusByDirection(document, direction, allSelectors);
    if (result.moved) {
        captureCurrentFocusSnapshot('navigate_legacy_custom');
        return true;
    }
    if (result.reason === 'dead_end') {
        incrementDiagnosticCounter('deadEndMoves');
    }
    return false;
};

const navigateTvDirectionV2 = (direction: Direction) => {
    const startedAt = performance.now();
    const routeHash = getCurrentRouteHash();
    const profile = getRouteNavigationProfile(routeHash);
    const activeOverlay = getTopOverlayElement();
    const activeElement = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const currentZone = activeOverlay ? 'overlay' : resolveElementZone(activeElement, profile);
    const nativeNavigateAllowed = !activeOverlay && !(currentZone === 'sidebar' && (direction === 'up' || direction === 'down'));
    const before = document.activeElement;

    if (nativeNavigateAllowed && typeof window.navigate === 'function') {
        window.navigate(direction);
        if (document.activeElement !== before) {
            const nextActive = document.activeElement instanceof HTMLElement ? document.activeElement : null;
            const nextZone = resolveElementZone(nextActive, profile);
            if (currentZone !== nextZone) {
                incrementDiagnosticCounter('zoneTransfers');
            }
            applyActiveZone(nextZone);
            captureCurrentFocusSnapshot('navigate_v2_native');
            recordTvDiagnostic(
                'navigate.v2.native',
                { latencyMs: Math.round(performance.now() - startedAt), from: currentZone, to: nextZone },
                nextZone,
                direction
            );
            return true;
        }
    }

    if (activeOverlay) {
        const overlayResult = moveFocusByDirection(
            activeOverlay,
            direction,
            Array.from(new Set([...profile.zones.overlay.selectors, ...DEFAULT_FOCUSABLE_SELECTORS])),
            activeElement
        );
        if (overlayResult.moved) {
            applyActiveZone('overlay');
            captureCurrentFocusSnapshot('navigate_v2_overlay');
            recordTvDiagnostic(
                'navigate.v2.overlay',
                { latencyMs: Math.round(performance.now() - startedAt), moveReason: overlayResult.reason },
                'overlay',
                direction
            );
            return true;
        }
        if (overlayResult.reason === 'dead_end') {
            incrementDiagnosticCounter('deadEndMoves');
        }
        recordTvDiagnostic('navigate.v2.overlay_blocked', { moveReason: overlayResult.reason }, 'overlay', direction);
        return false;
    }

    const baseZone: TvZone = currentZone === 'unknown' ? 'content' : currentZone;
    const zoneSelectors = Array.from(new Set([
        ...getZoneSelectors(profile, baseZone),
        ...DEFAULT_FOCUSABLE_SELECTORS,
    ]));
    const zoneResult = moveFocusByDirection(document, direction, zoneSelectors, activeElement);

    if (zoneResult.moved) {
        const nextZone = resolveElementZone(
            zoneResult.next || (document.activeElement instanceof HTMLElement ? document.activeElement : null),
            profile
        );
        if (nextZone !== baseZone) {
            incrementDiagnosticCounter('zoneTransfers');
        }
        applyActiveZone(nextZone);
        captureCurrentFocusSnapshot('navigate_v2_zone');
        recordTvDiagnostic(
            'navigate.v2.zone',
            {
                latencyMs: Math.round(performance.now() - startedAt),
                moveReason: zoneResult.reason,
                from: baseZone,
                to: nextZone,
            },
            nextZone,
            direction
        );
        return true;
    }

    const transferTargets = getZoneTransferTargets(profile.routeKey, baseZone, direction)
        .filter((zone) => zone !== baseZone);
    for (const targetZone of transferTargets) {
        const targetSelectors = Array.from(new Set([
            ...getZoneSelectors(profile, targetZone),
            ...DEFAULT_FOCUSABLE_SELECTORS,
        ]));
        const targetCandidates = collectFocusableCandidates(document, targetSelectors);
        if (targetCandidates.length === 0) {
            continue;
        }

        let target = null;
        if (activeElement) {
            target = selectDirectionalCandidate(activeElement, targetCandidates, direction);
        }
        if (!target) {
            target = targetCandidates[0];
        }
        if (!target) {
            continue;
        }

        focusElement(target);
        incrementDiagnosticCounter('zoneTransfers');
        applyActiveZone(targetZone);
        captureCurrentFocusSnapshot('navigate_v2_transfer');
        recordTvDiagnostic(
            'navigate.v2.zone_transfer',
            {
                latencyMs: Math.round(performance.now() - startedAt),
                from: baseZone,
                to: targetZone,
            },
            targetZone,
            direction
        );
        return true;
    }

    const boundary = isBoundaryMove(baseZone, direction, transferTargets);
    if (!boundary) {
        incrementDiagnosticCounter('deadEndMoves');
    }
    recordTvDiagnostic(
        'navigate.v2.blocked',
        {
            latencyMs: Math.round(performance.now() - startedAt),
            moveReason: zoneResult.reason,
            zone: baseZone,
            boundary,
        },
        baseZone,
        direction
    );
    return false;
};

const navigateTvDirection = (direction: Direction) => {
    return isTvNavV2Enabled()
        ? navigateTvDirectionV2(direction)
        : navigateTvDirectionLegacy(direction);
};

const activateFocusedElement = () => {
    const activeElement = document.activeElement;
    if (!(activeElement instanceof HTMLElement)) {
        return false;
    }
    if (activeElement === document.body || activeElement === document.documentElement) {
        return false;
    }

    if (typeof activeElement.click === 'function') {
        activeElement.click();
        return true;
    }
    return false;
};

const sendBackHandled = (requestId: string, handled: boolean, reason: string) => {
    const envelope = createBackHandledEnvelope(requestId, handled, reason);
    if (!envelope || !window.stremioHost || typeof window.stremioHost.sendCommand !== 'function') {
        return;
    }
    window.stremioHost.sendCommand(JSON.stringify(envelope));
};

const dispatchEscapeKey = () => {
    const eventInit = {
        key: 'Escape',
        code: 'Escape',
        bubbles: true,
        cancelable: true,
    };
    document.dispatchEvent(new KeyboardEvent('keydown', eventInit));
    window.dispatchEvent(new KeyboardEvent('keydown', eventInit));
};

const isOverlayClosed = (overlay: HTMLElement | null) => {
    if (!overlay) {
        return true;
    }
    if (!overlay.isConnected || !isVisibleElement(overlay)) {
        return true;
    }

    const topOverlay = getTopOverlayElement();
    return topOverlay == null || topOverlay !== overlay;
};

const attemptOverlayClose = () => {
    const overlay = getTopOverlayElement();
    if (!overlay) {
        return { attempted: false as const };
    }

    if (!overlay.contains(document.activeElement)) {
        focusFirstElement(overlay, DEFAULT_FOCUSABLE_SELECTORS);
    }

    const closeButton = overlay.querySelector<HTMLElement>(
        [
            '[class*="close-button"]',
            '[class*="back-button"]',
            '[class*="dismiss"]',
            '[aria-label="Close"]',
            '[title="Close"]',
            '[data-testid*="close"]',
        ].join(', ')
    );
    if (closeButton && isVisibleElement(closeButton)) {
        closeButton.click();
        incrementDiagnosticCounter('overlayEscapes');
        return {
            attempted: true as const,
            overlay,
            reason: 'overlay_close_button',
        };
    }

    dispatchEscapeKey();
    incrementDiagnosticCounter('overlayEscapes');
    return {
        attempted: true as const,
        overlay,
        reason: 'overlay_escape',
    };
};

const handleBackPressedEvent = (payload: Record<string, unknown>) => {
    const requestId = typeof payload.requestId === 'string' ? payload.requestId.trim() : '';
    if (!requestId) {
        return;
    }

    if (document.fullscreenElement && typeof document.exitFullscreen === 'function') {
        document.exitFullscreen().catch(() => undefined);
        sendBackHandled(requestId, true, 'fullscreen_exit');
        recordTvDiagnostic('back.handled', { requestId, handled: true, reason: 'fullscreen_exit' });
        return;
    }

    const overlayAttempt = attemptOverlayClose();
    if (overlayAttempt.attempted) {
        window.setTimeout(() => {
            const closed = isOverlayClosed(overlayAttempt.overlay);
            const reason = closed ? overlayAttempt.reason : 'overlay_still_open';
            sendBackHandled(requestId, closed, reason);
            recordTvDiagnostic('back.handled', { requestId, handled: closed, reason });
        }, OVERLAY_CLOSE_SETTLE_MS);
        return;
    }

    sendBackHandled(requestId, false, 'unhandled');
    recordTvDiagnostic('back.handled', { requestId, handled: false, reason: 'unhandled' });
};

const handleDeepLinkEvent = (payload: Record<string, unknown>) => {
    const rawUrl = typeof payload.url === 'string' ? payload.url : '';
    const targetHash = normalizeDeepLinkToHash(rawUrl);
    if (!targetHash) {
        recordTvDiagnostic('deeplink.ignored', { url: rawUrl });
        return;
    }

    if (window.location.hash !== targetHash) {
        window.location.hash = targetHash;
    }

    window.setTimeout(() => {
        if (!hasRealFocusedElement()) {
            focusRouteEntryPoint('deeplink');
        } else {
            captureCurrentFocusSnapshot('deeplink_focus_ready');
        }
    }, 80);
    recordTvDiagnostic('deeplink.routed', { targetHash });
};

const handleTvKeyHostEvent = (payload: Record<string, unknown>) => {
    const key = typeof payload.key === 'string' ? payload.key : '';
    if (!key) {
        return;
    }
    lastHostKeyProcessedAt = Date.now();

    const direction = KEY_TO_DIRECTION[key];
    if (direction) {
        if (!isPlayerRoute()) {
            if (!hasRealFocusedElement()) {
                focusRouteEntryPoint('tv_key_recovery');
            }
            const moved = navigateTvDirection(direction);
            recordTvDiagnostic('tv.key', { key, moved }, undefined, direction);
        }
        return;
    }

    if (key === 'Enter') {
        if (!hasRealFocusedElement()) {
            focusRouteEntryPoint('tv_key_enter_recovery');
        }
        const activated = activateFocusedElement();
        recordTvDiagnostic('tv.key', { key, activated });
    }
};

const preventScrollGestures = (event: Event) => {
    event.preventDefault();
};

const ensureTvFocusRingStyles = () => {
    if (document.getElementById(TV_FOCUS_RING_STYLE_ID)) {
        return;
    }

    const style = document.createElement('style');
    style.id = TV_FOCUS_RING_STYLE_ID;
    style.textContent = `
      :root {
        --tv-focus-ring-color: #8fd7ff;
        --tv-focus-ring-shadow: rgba(18, 126, 173, 0.55);
        --tv-focus-ring-soft-shadow: rgba(10, 30, 48, 0.65);
        --tv-zone-sidebar-accent: rgba(122, 201, 255, 0.2);
        --tv-zone-topbar-accent: rgba(103, 226, 194, 0.2);
        --tv-zone-content-accent: rgba(255, 183, 94, 0.22);
      }
      *, *:before, *:after {
        scroll-behavior: auto !important;
      }
      ::-webkit-scrollbar {
        display: none !important;
        width: 0 !important;
        height: 0 !important;
      }
      html, body {
        scrollbar-width: none !important;
        overflow: hidden !important;
      }
      :focus-visible {
        outline: 3px solid var(--tv-focus-ring-color) !important;
        outline-offset: 2px !important;
        box-shadow: 0 0 0 3px var(--tv-focus-ring-shadow), 0 10px 24px -8px var(--tv-focus-ring-soft-shadow) !important;
        transform: translateZ(0) scale(1.015);
        transition: box-shadow 120ms ease, transform 120ms ease, background-color 120ms ease;
      }
      [class*="nav-tab-button-container"]:focus-visible {
        background-color: var(--tv-zone-sidebar-accent) !important;
      }
      [class*="button-container"]:focus-visible,
      [class*="menu-option-container"]:focus-visible,
      [class*="control-bar-button"]:focus-visible {
        background-color: rgba(255, 255, 255, 0.11) !important;
      }
      [class*="meta-item-container"]:focus-visible,
      [class*="stream-container"]:focus-visible {
        box-shadow: 0 0 0 2px var(--tv-focus-ring-color), 0 12px 30px -12px rgba(9, 19, 30, 0.85) !important;
      }
      body[data-tv-active-zone="sidebar"] [class*="nav-tab-button-container"]:focus-visible {
        background-color: var(--tv-zone-sidebar-accent) !important;
      }
      body[data-tv-active-zone="topbar"] [class*="top"] [class*="button-container"]:focus-visible,
      body[data-tv-active-zone="topbar"] [class*="search-bar-container"] [class*="button-container"]:focus-visible,
      body[data-tv-active-zone="topbar"] [class*="search-bar-container"] input:focus-visible {
        background-color: var(--tv-zone-topbar-accent) !important;
      }
      body[data-tv-active-zone="content"] [class*="meta-item-container"]:focus-visible,
      body[data-tv-active-zone="content"] [class*="stream-container"]:focus-visible {
        box-shadow: 0 0 0 2px #ffd39e, 0 14px 36px -16px rgba(4, 10, 15, 0.85) !important;
      }
      body.stremio-tv-playback-opening [class*="stream-container"]:focus-visible,
      body.stremio-tv-playback-opening [class*="button-container"]:focus-visible {
        box-shadow: 0 0 0 2px #b7ffe8, 0 0 28px rgba(99, 244, 196, 0.45) !important;
      }
    `;
    document.head.appendChild(style);
};

const ShortcutsProvider = ({ children, onShortcut }: Props) => {
    const platform = usePlatform();
    const listeners = useRef<Map<ShortcutName, Set<ShortcutListener>>>(new Map());
    const platformName = (platform.name || '').toLowerCase();
    const userAgent = window.navigator.userAgent.toLowerCase();
    const tvUaHints = ['android tv', 'google tv', 'aft', 'bravia', 'smarttv', 'tv;'];
    const isTvUa = tvUaHints.some((hint) => userAgent.includes(hint));
    const hasHostBridge = Boolean(window.stremioHost && typeof window.stremioHost.sendCommand === 'function');
    const isTvRuntime = (platformName.includes('android') && !platform.isMobile) || isTvUa || hasHostBridge;
    const hasTvNavigator = typeof window.navigate === 'function';
    const shouldHandleArrowNavigation = isTvRuntime || hasTvNavigator;

    const onKeyDown = useCallback((event: KeyboardEvent) => {
        const direction = ARROW_KEY_TO_DIRECTION[event.key];
        const editableTarget = isEditableTarget(event.target);

        if (shouldHandleDirectionalKey({
            hasDirection: Boolean(direction),
            isEditableTarget: editableTarget,
            shouldHandleArrowNavigation,
            routeHash: getCurrentRouteHash(),
        })) {
            event.preventDefault();

            if (Date.now() - lastHostKeyProcessedAt < HOST_KEY_DEDUP_MS) {
                return;
            }

            if (!hasRealFocusedElement()) {
                focusRouteEntryPoint('keydown_recovery');
            }

            if (direction) {
                const moved = navigateTvDirection(direction);
                recordTvDiagnostic('keydown.navigate', { moved, navV2: isTvNavV2Enabled() }, undefined, direction);
            }
        }

        const { ctrlKey, shiftKey, code, key } = event;
        SHORTCUTS.forEach(({ name, combos }) => combos.forEach((keys) => {
            const modifers = (keys.includes('Ctrl') ? ctrlKey : true)
                && (keys.includes('Shift') ? shiftKey : true);

            if (modifers && (keys.includes(code) || keys.includes(key.toUpperCase()))) {
                const combo = combos.indexOf(keys);
                listeners.current.get(name)?.forEach((listener) => listener(combo));

                onShortcut(name as ShortcutName);
            }
        }));
    }, [onShortcut, shouldHandleArrowNavigation]);

    const on = (name: ShortcutName, listener: ShortcutListener) => {
        !listeners.current.has(name) && listeners.current.set(name, new Set());
        listeners.current.get(name)!.add(listener);
    };

    const off = (name: ShortcutName, listener: ShortcutListener) => {
        listeners.current.get(name)?.delete(listener);
    };

    useEffect(() => {
        const listenerOptions: AddEventListenerOptions = { capture: true };
        document.addEventListener('keydown', onKeyDown, listenerOptions);
        return () => document.removeEventListener('keydown', onKeyDown, listenerOptions);
    }, [onKeyDown]);

    useEffect(() => {
        if (!isTvRuntime) {
            return;
        }

        ensureTvFocusRingStyles();
    }, [isTvRuntime]);

    useEffect(() => {
        if (!isTvRuntime) {
            return;
        }

        const wheelOptions: AddEventListenerOptions = { capture: true, passive: false };
        const touchOptions: AddEventListenerOptions = { capture: true, passive: false };

        document.addEventListener('wheel', preventScrollGestures, wheelOptions);
        document.addEventListener('touchmove', preventScrollGestures, touchOptions);

        return () => {
            document.removeEventListener('wheel', preventScrollGestures, wheelOptions);
            document.removeEventListener('touchmove', preventScrollGestures, touchOptions);
        };
    }, [isTvRuntime]);

    useEffect(() => {
        if (!shouldHandleArrowNavigation) {
            return;
        }

        const recoverRouteFocus = () => {
            if (!hasRealFocusedElement()) {
                if (isTvNavV2Enabled()) {
                    attemptFocusRestore('hashchange');
                } else {
                    focusRouteEntryPoint('hashchange_legacy');
                }
                return;
            }

            captureCurrentFocusSnapshot('hashchange_existing_focus');
        };

        const timer = window.setTimeout(recoverRouteFocus, 100);
        window.addEventListener('hashchange', recoverRouteFocus);
        return () => {
            window.clearTimeout(timer);
            window.removeEventListener('hashchange', recoverRouteFocus);
        };
    }, [shouldHandleArrowNavigation]);

    useEffect(() => {
        if (!shouldHandleArrowNavigation) {
            return;
        }

        const onFocusIn = () => {
            captureCurrentFocusSnapshot('focusin');
        };

        document.addEventListener('focusin', onFocusIn, true);
        return () => document.removeEventListener('focusin', onFocusIn, true);
    }, [shouldHandleArrowNavigation]);

    useEffect(() => {
        if (!isTvRuntime) {
            return;
        }

        const onHostEvent = (event: Event) => {
            const customEvent = event as CustomEvent<unknown>;
            if (!isRecord(customEvent.detail)) {
                return;
            }

            const detail = customEvent.detail as HostEventEnvelope;
            const payload = isRecord(detail.payload) ? detail.payload : {};
            switch (detail.type) {
                case 'back.pressed':
                    handleBackPressedEvent(payload);
                    break;
                case 'deepLink.received':
                    handleDeepLinkEvent(payload);
                    break;
                case 'lifecycle.changed': {
                    const state = String(payload.state ?? 'unknown');
                    recordTvDiagnostic('lifecycle.changed', { state });
                    if (state === 'resumed' && isTvNavV2Enabled()) {
                        window.setTimeout(() => {
                            attemptFocusRestore('lifecycle_resumed');
                        }, 80);
                    }
                    break;
                }
                case 'network.changed':
                    recordTvDiagnostic('network.changed', {
                        connected: String(payload.connected ?? 'unknown'),
                        transport: String(payload.transport ?? 'unknown'),
                    });
                    break;
                case 'playback.result': {
                    const status = String(payload.status ?? 'unknown');
                    const contextHint = coerceNavigationContext(payload.navigationContext);
                    recordTvDiagnostic('playback.result', { status, exitReason: payload.exitReason ?? 'unknown' });
                    if (isTvNavV2Enabled() && ['paused', 'completed', 'failed'].includes(status)) {
                        window.setTimeout(() => {
                            attemptFocusRestore(`playback_${status}`, contextHint);
                        }, 90);
                    }
                    break;
                }
                case 'tv.key':
                    handleTvKeyHostEvent(payload);
                    break;
            }
        };

        window.addEventListener(HOST_EVENT_NAME, onHostEvent as EventListener);
        return () => window.removeEventListener(HOST_EVENT_NAME, onHostEvent as EventListener);
    }, [isTvRuntime]);

    useEffect(() => {
        if (!isTvRuntime) {
            return;
        }

        recordTvDiagnostic('tv.runtime.ready', { navV2Enabled: isTvNavV2Enabled() });
        ensureDiagnosticsCounters();
        if (!hasRealFocusedElement()) {
            window.setTimeout(() => {
                if (!hasRealFocusedElement()) {
                    if (isTvNavV2Enabled()) {
                        attemptFocusRestore('initial_mount');
                    } else {
                        focusRouteEntryPoint('initial_mount_legacy');
                    }
                }
            }, 120);
        } else {
            captureCurrentFocusSnapshot('initial_mount_existing_focus');
        }
    }, [isTvRuntime]);

    return (
        <ShortcutsContext.Provider value={{ grouped: shortcuts, on, off }}>
            {children}
        </ShortcutsContext.Provider>
    );
};

const useShortcuts = () => {
    return useContext(ShortcutsContext);
};

export {
    ShortcutsProvider,
    useShortcuts,
};
