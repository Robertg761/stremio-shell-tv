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
    '.button-container',
    '.nav-tab-button-container',
    '.meta-item-container',
    '.stream-container',
    '.control-bar-button',
    'input',
    'textarea',
    'select',
];

const OVERLAY_SELECTORS = [
    '[role="dialog"]',
    '[aria-modal="true"]',
];

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
            if (seen.has(element) || !isVisibleElement(element)) {
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

    let primaryDelta = 0;
    let secondaryDelta = 0;
    if (direction === 'right') {
        if (dx <= 2) return null;
        primaryDelta = dx;
        secondaryDelta = Math.abs(dy);
    } else if (direction === 'left') {
        if (dx >= -2) return null;
        primaryDelta = -dx;
        secondaryDelta = Math.abs(dy);
    } else if (direction === 'down') {
        if (dy <= 2) return null;
        primaryDelta = dy;
        secondaryDelta = Math.abs(dx);
    } else {
        if (dy >= -2) return null;
        primaryDelta = -dy;
        secondaryDelta = Math.abs(dx);
    }

    const distance = Math.hypot(dx, dy);
    return (primaryDelta * 1000) + (secondaryDelta * 10) + distance;
};

const moveFocusByDirection = (root: ParentNode, direction: Direction, selectors: string[]) => {
    const candidates = collectFocusableCandidates(root, selectors);

    console.log('[TVNav] moveFocusByDirection direction=', direction, 'candidates len=', candidates.length);

    if (candidates.length === 0) {
        return false;
    }

    const activeElement = document.activeElement;
    const current = activeElement instanceof HTMLElement && candidates.includes(activeElement) ? activeElement : null;

    console.log('[TVNav] current activeElement=', activeElement, 'current=', current);

    if (!current) {
        focusElement(candidates[0]);
        console.log('[TVNav] no current match, focusing first candidate=', candidates[0]);
        return true;
    }

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

    if (typeof window.navigate === 'function') {
        const before = document.activeElement;
        window.navigate(direction);
        if (document.activeElement !== before) {
            return true;
        }
    }

    const routeSelectors = getRouteFocusSelectors(window.location.hash);
    return moveFocusByDirection(
        document,
        direction,
        [...routeSelectors, ...DEFAULT_FOCUSABLE_SELECTORS]
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
      :focus-visible {
        outline: 4px solid #f7b500 !important;
        outline-offset: 2px !important;
      }
      [class*="button-container"]:focus-visible,
      [class*="nav-tab-button-container"]:focus-visible,
      [class*="meta-item-container"]:focus-visible,
      [class*="stream-container"]:focus-visible {
        box-shadow: 0 0 0 4px rgba(247, 181, 0, 0.45) !important;
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
