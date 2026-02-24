import React, { createContext, useCallback, useContext, useEffect, useRef } from 'react';
import shortcuts from './shortcuts.json';
import { usePlatform } from '../Platform';
import {
    createBackHandledEnvelope,
    getRouteFocusSelectors,
    normalizeDeepLinkToHash,
} from './tvHostEvents';

const SHORTCUTS = shortcuts.map(({ shortcuts }) => shortcuts).flat();

const HOST_EVENT_NAME = 'stremio:host-event';
const TV_FOCUS_RING_STYLE_ID = 'stremio-tv-focus-ring';

export type ShortcutName = string;
export type ShortcutListener = (combo: number) => void;

interface ShortcutsContext {
    grouped: ShortcutGroup[],
    on: (name: ShortcutName, listener: ShortcutListener) => void,
    off: (name: ShortcutName, listener: ShortcutListener) => void,
}

type Direction = 'up' | 'down' | 'left' | 'right';

type HostEventEnvelope = {
    type?: string,
    payload?: Record<string, unknown>,
};

declare global {
    interface Window {
        navigate?: (direction: Direction) => void,
        stremioHost?: {
            sendCommand?: (commandJson: string) => void,
        },
        __stremioTvDiagnostics?: string[],
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

const DEFAULT_FOCUSABLE_SELECTORS = [
    'a[href]',
    'button',
    '[role="button"]',
    '[tabindex]:not([tabindex="-1"])',
    '[class*="button-container"]',
    '[class*="nav-tab-button-container"]',
    '[class*="meta-item-container"]',
    '[class*="stream-container"]',
    '[class*="control-bar-button"]',
    'input',
    'textarea',
    'select',
];

const OVERLAY_SELECTORS = [
    '[role="dialog"]',
    '[aria-modal="true"]',
];

const SIDEBAR_NAV_SELECTOR = '[class*="nav-tab-button-container"]';
const SIDEBAR_NAV_LIKE_SELECTOR = '[class*="nav-tab-button"]';

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

const isVisibleElement = (element: HTMLElement) => {
    if (!element.isConnected || element.hidden || element.getAttribute('aria-hidden') === 'true') {
        return false;
    }

    const style = window.getComputedStyle(element);
    if (style.visibility === 'hidden' || style.display === 'none') {
        return false;
    }

    const rect = element.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
};

const focusFirstElement = (root: ParentNode, selectors: string[]) => {
    for (const selector of selectors) {
        const candidates = Array.from(root.querySelectorAll<HTMLElement>(selector));
        const target = candidates.find((candidate) => isVisibleElement(candidate));
        if (target) {
            target.focus();
            return true;
        }
    }
    return false;
};

const focusRouteEntryPoint = () => {
    const routeSelectors = getRouteFocusSelectors(window.location.hash);
    return (
        focusFirstElement(document, routeSelectors) ||
        focusFirstElement(document, DEFAULT_FOCUSABLE_SELECTORS)
    );
};

const focusElement = (element: HTMLElement) => {
    try {
        element.focus({ preventScroll: true });
    } catch (_) {
        element.focus();
    }
    try {
        element.scrollIntoView({ behavior: 'instant', block: 'nearest', inline: 'nearest' });
    } catch (_) {
        element.scrollIntoView(false);
    }
};

const ensureFocusableElement = (element: HTMLElement) => {
    const naturallyFocusable = element.matches('a[href],button,input,textarea,select,[tabindex]');
    if (!naturallyFocusable && element.tabIndex < 0) {
        element.setAttribute('tabindex', '0');
    }
    return element;
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
        .slice(0, 40);
    const rect = element.getBoundingClientRect();
    return `${tag}${id}${classes} "${text}" @(${Math.round(rect.left)},${Math.round(rect.top)} ${Math.round(rect.width)}x${Math.round(rect.height)})`;
};

const isSidebarNavLikeElement = (element: HTMLElement) => {
    if (element.matches(SIDEBAR_NAV_SELECTOR) || element.matches(SIDEBAR_NAV_LIKE_SELECTOR)) {
        return true;
    }

    return Boolean(element.closest(SIDEBAR_NAV_SELECTOR) || element.closest(SIDEBAR_NAV_LIKE_SELECTOR));
};

const collectFocusableCandidates = (root: ParentNode, selectors: string[]) => {
    const seen = new Set<HTMLElement>();
    const candidates: HTMLElement[] = [];

    for (const selector of selectors) {
        const matches = Array.from(root.querySelectorAll<HTMLElement>(selector));
        for (const element of matches) {
            if (seen.has(element) || !isVisibleElement(element) || element.matches('[class*="see-all-container"]')) {
                continue;
            }
            seen.add(element);
            candidates.push(ensureFocusableElement(element));
        }
    }

    return candidates;
};

const scoreDirectionalCandidate = (current: DOMRect, candidate: DOMRect, direction: Direction) => {
    const currentCenterX = current.left + (current.width / 2);
    const currentCenterY = current.top + (current.height / 2);
    const candidateCenterX = candidate.left + (candidate.width / 2);
    const candidateCenterY = candidate.top + (candidate.height / 2);
    const dx = candidateCenterX - currentCenterX;
    const dy = candidateCenterY - currentCenterY;

    if (direction === 'right' && dx <= 0) return null;
    if (direction === 'left' && dx >= 0) return null;
    if (direction === 'down' && dy <= 0) return null;
    if (direction === 'up' && dy >= 0) return null;

    let primaryDist = 0;
    let secondaryDist = 0;

    if (direction === 'right' || direction === 'left') {
        primaryDist = direction === 'right' 
            ? Math.max(0, candidate.left - current.right) 
            : Math.max(0, current.left - candidate.right);
        secondaryDist = Math.max(0, Math.max(current.top, candidate.top) - Math.min(current.bottom, candidate.bottom));
    } else {
        primaryDist = direction === 'down'
            ? Math.max(0, candidate.top - current.bottom)
            : Math.max(0, current.top - candidate.bottom);
        secondaryDist = Math.max(0, Math.max(current.left, candidate.left) - Math.min(current.right, candidate.right));
    }

    const primaryCenterDelta = direction === 'right' || direction === 'left' ? Math.abs(dx) : Math.abs(dy);
    const secondaryCenterDelta = direction === 'right' || direction === 'left' ? Math.abs(dy) : Math.abs(dx);

    if (secondaryCenterDelta > primaryCenterDelta * 3) {
        return null;
    }

    const centerDistance = Math.hypot(dx, dy);
    return (primaryDist * 10) + (secondaryDist * 50) + centerDistance;
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

const getContextCandidates = (current: HTMLElement, direction: Direction, candidates: HTMLElement[]) => {
    if ((direction === 'up' || direction === 'down') && isSidebarNavLikeElement(current)) {
        const sidebarCandidates = candidates.filter((candidate) => isSidebarNavLikeElement(candidate));
        if (sidebarCandidates.length > 1) {
            console.log('[TVNav] context=sidebar vertical direction=', direction, 'sidebar candidates=', sidebarCandidates.length, 'current=', getElementDebugLabel(current));
            return sidebarCandidates;
        }
    }

    return candidates;
};

const moveFocusByDirection = (root: ParentNode, direction: Direction, selectors: string[]) => {
    const allCandidates = collectFocusableCandidates(root, selectors);

    console.log('[TVNav] moveFocusByDirection direction=', direction, 'candidates len=', allCandidates.length);

    if (allCandidates.length === 0) {
        return false;
    }

    const activeElement = document.activeElement;
    const current = resolveCurrentCandidate(activeElement, allCandidates, selectors);

    console.log('[TVNav] current activeElement=', getElementDebugLabel(activeElement), 'current=', getElementDebugLabel(current));

    if (!current) {
        focusElement(allCandidates[0]);
        console.log('[TVNav] no current match, focusing first candidate=', allCandidates[0]);
        return true;
    }

    const candidates = getContextCandidates(current, direction, allCandidates);
    const currentRect = current.getBoundingClientRect();
    const scoredCandidates = candidates
        .filter((candidate) => candidate !== current)
        .map((candidate) => ({
            candidate,
            score: scoreDirectionalCandidate(currentRect, candidate.getBoundingClientRect(), direction),
        }))
        .filter((entry): entry is { candidate: HTMLElement, score: number } => entry.score !== null)
        .sort((a, b) => a.score - b.score);

    console.log('[TVNav] scored candidates len=', scoredCandidates.length, 'best score=', scoredCandidates[0]?.score);
    if (scoredCandidates.length > 0) {
        const top = scoredCandidates
            .slice(0, 3)
            .map((entry) => `${entry.score.toFixed(1)} => ${getElementDebugLabel(entry.candidate)}`);
        console.log('[TVNav] top candidates', top);
    }

    const next = scoredCandidates[0]?.candidate;
    if (!next) {
        return false;
    }
    focusElement(next);
    return true;
};

const navigateTvDirection = (direction: Direction) => {
    const activeOverlay = getTopOverlayElement();
    if (activeOverlay) {
        return moveFocusByDirection(activeOverlay, direction, DEFAULT_FOCUSABLE_SELECTORS);
    }

    const routeSelectors = getRouteFocusSelectors(window.location.hash);
    const allSelectors = [...routeSelectors, ...DEFAULT_FOCUSABLE_SELECTORS];
    const focusCandidates = collectFocusableCandidates(document, allSelectors);
    const current = resolveCurrentCandidate(document.activeElement, focusCandidates, allSelectors);

    if (
        (direction === 'up' || direction === 'down')
        && current
        && isSidebarNavLikeElement(current)
    ) {
        console.log('[TVNav] bypassing window.navigate for sidebar vertical move direction=', direction, 'current=', getElementDebugLabel(current));
        return moveFocusByDirection(document, direction, allSelectors);
    }

    if (typeof window.navigate === 'function') {
        const before = document.activeElement;
        window.navigate(direction);
        const changedByNativeNavigate = document.activeElement !== before;
        if (changedByNativeNavigate) {
            console.log('[TVNav] window.navigate changed focus direction=', direction, 'before=', getElementDebugLabel(before), 'after=', getElementDebugLabel(document.activeElement));
            return true;
        }
        console.log('[TVNav] window.navigate kept focus direction=', direction, 'active=', getElementDebugLabel(document.activeElement));
    }

    return moveFocusByDirection(
        document,
        direction,
        allSelectors
    );
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
                focusRouteEntryPoint();
            }
            const moved = navigateTvDirection(direction);
            recordTvDiagnostic(`Host key ${key} moved=${moved}`);
        }
        return;
    }

    if (key === 'Enter') {
        if (!hasRealFocusedElement()) {
            focusRouteEntryPoint();
        }
        const activated = activateFocusedElement();
        recordTvDiagnostic(`Host key Enter activated=${activated}`);
    }
};

const hasRealFocusedElement = () => {
    const activeElement = document.activeElement;
    return activeElement instanceof HTMLElement && activeElement !== document.body && activeElement !== document.documentElement;
};

const isPlayerRoute = () => {
    return window.location.hash.startsWith('#/player');
};

const preventScrollGestures = (event: Event) => {
    event.preventDefault();
};

const getTopOverlayElement = () => {
    const overlays = Array.from(
        document.querySelectorAll<HTMLElement>(OVERLAY_SELECTORS.join(', '))
    ).filter((element) => isVisibleElement(element));

    return overlays.length > 0 ? overlays[overlays.length - 1] : null;
};

const ensureTvFocusRingStyles = () => {
    if (document.getElementById(TV_FOCUS_RING_STYLE_ID)) {
        return;
    }

    const style = document.createElement('style');
    style.id = TV_FOCUS_RING_STYLE_ID;
    style.textContent = `
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
        outline: none !important;
      }
      [class*="button-container"]:focus-visible,
      [class*="nav-tab-button-container"]:focus-visible,
      [class*="meta-item-container"]:focus-visible,
      [class*="stream-container"]:focus-visible {
        box-shadow: none !important;
      }
      [class*="nav-tab-button-container"]:focus-visible {
        background-color: var(--overlay-color) !important;
      }
      [class*="nav-tab-button-container"]:focus-visible [class*="label"] {
        opacity: 0.6 !important;
      }
      [class*="nav-tab-button-container"][class*="selected"]:focus-visible [class*="label"] {
        opacity: 1 !important;
      }
      [class*="button-container"]:focus-visible {
        background-color: var(--overlay-color);
      }
    `;
    document.head.appendChild(style);
};

const recordTvDiagnostic = (message: string) => {
    const formatted = `[TV] ${message}`;
    console.info(formatted);
    window.__stremioTvDiagnostics = window.__stremioTvDiagnostics || [];
    window.__stremioTvDiagnostics.push(formatted);
    if (window.__stremioTvDiagnostics.length > 80) {
        window.__stremioTvDiagnostics = window.__stremioTvDiagnostics.slice(-80);
    }
};

const OVERLAY_CLOSE_SETTLE_MS = 50;
const HOST_KEY_DEDUP_MS = 150;
let lastHostKeyProcessedAt = 0;

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
        '[class*="close-button"], [class*="back-button"], [aria-label="Close"], [title="Close"]'
    );
    if (closeButton && isVisibleElement(closeButton)) {
        closeButton.click();
        return {
            attempted: true as const,
            overlay,
            reason: 'overlay_close_button',
        };
    }

    dispatchEscapeKey();
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
        recordTvDiagnostic(`Back request ${requestId} handled=true reason=fullscreen_exit`);
        return;
    }

    const overlayAttempt = attemptOverlayClose();
    if (overlayAttempt.attempted) {
        window.setTimeout(() => {
            const closed = isOverlayClosed(overlayAttempt.overlay);
            const reason = closed ? overlayAttempt.reason : 'overlay_still_open';
            sendBackHandled(requestId, closed, reason);
            recordTvDiagnostic(`Back request ${requestId} handled=${closed} reason=${reason}`);
        }, OVERLAY_CLOSE_SETTLE_MS);
        return;
    }

    sendBackHandled(requestId, false, 'unhandled');
    recordTvDiagnostic(`Back request ${requestId} handled=false reason=unhandled`);
};

const handleDeepLinkEvent = (payload: Record<string, unknown>) => {
    const rawUrl = typeof payload.url === 'string' ? payload.url : '';
    const targetHash = normalizeDeepLinkToHash(rawUrl);
    if (!targetHash) {
        recordTvDiagnostic(`Deep link ignored url=${rawUrl}`);
        return;
    }

    if (window.location.hash !== targetHash) {
        window.location.hash = targetHash;
    }

    window.setTimeout(() => {
        if (!hasRealFocusedElement()) {
            focusRouteEntryPoint();
        }
    }, 80);
    recordTvDiagnostic(`Deep link routed to ${targetHash}`);
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

        if (direction && !editableTarget && shouldHandleArrowNavigation) {
            event.preventDefault();

            // Skip if this arrow key was already handled by the host-event path
            // (dedup for dual-path key delivery)
            if (Date.now() - lastHostKeyProcessedAt < HOST_KEY_DEDUP_MS) {
                return;
            }

            if (!isPlayerRoute()) {
                if (!hasRealFocusedElement()) {
                    const focused = focusRouteEntryPoint();
                    if (focused) {
                        recordTvDiagnostic(`Recovered missing focus on route ${window.location.hash || '#/'}`);
                    }
                }

                const moved = navigateTvDirection(direction);
                if (moved) {
                    recordTvDiagnostic(`Navigated ${direction} on route ${window.location.hash || '#/'}`);
                }
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
                focusRouteEntryPoint();
            }
        };

        const timer = window.setTimeout(recoverRouteFocus, 100);
        window.addEventListener('hashchange', recoverRouteFocus);
        return () => {
            window.clearTimeout(timer);
            window.removeEventListener('hashchange', recoverRouteFocus);
        };
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
                case 'lifecycle.changed':
                    recordTvDiagnostic(`Lifecycle ${String(payload.state ?? 'unknown')}`);
                    break;
                case 'network.changed':
                    recordTvDiagnostic(`Network connected=${String(payload.connected ?? 'unknown')} transport=${String(payload.transport ?? 'unknown')}`);
                    break;
                case 'tv.key':
                    handleTvKeyHostEvent(payload);
                    break;
            }
        };

        window.addEventListener(HOST_EVENT_NAME, onHostEvent as EventListener);
        return () => window.removeEventListener(HOST_EVENT_NAME, onHostEvent as EventListener);
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
