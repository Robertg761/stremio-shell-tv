import { DEFAULT_FOCUSABLE_SELECTORS, type TvZone } from './tvNavigationProfile';

export type TvFocusSnapshot = {
    routeHash: string,
    zone: TvZone,
    focusKey?: string,
    containerKey?: string,
    indexInContainer?: number,
    scrollY: number,
    timestampMs: number,
    sessionId: string,
};

export type FocusRestoreReason =
    | 'exact'
    | 'same_container_prior'
    | 'container_entry'
    | 'route_entry'
    | 'failed'
    | 'no_snapshot';

export type FocusRestoreResult = {
    restored: boolean,
    reason: FocusRestoreReason,
    element?: HTMLElement | null,
};

type CaptureFocusSnapshotArgs = {
    element: HTMLElement,
    routeHash: string,
    zone: TvZone,
    scrollY: number,
    sessionId: string,
};

type RestoreFocusSnapshotArgs = {
    snapshot: TvFocusSnapshot | null | undefined,
    routeHash: string,
    routeEntryCandidates: HTMLElement[],
    isVisibleElement: (element: HTMLElement) => boolean,
    focusElement: (element: HTMLElement) => void,
};

const FOCUS_KEY_ATTR = 'data-stremio-tv-focus-key';
const CONTAINER_KEY_ATTR = 'data-stremio-tv-container-key';
const FOCUSABLE_SELECTOR = DEFAULT_FOCUSABLE_SELECTORS.join(', ');

const CONTAINER_SELECTOR = [
    '[role="list"]',
    '[role="grid"]',
    '[class*="row"]',
    '[class*="list"]',
    '[class*="grid"]',
    '[class*="container"]',
];

const sanitizeToken = (value: string) => {
    return value
        .trim()
        .toLowerCase()
        .replace(/\s+/g, '-')
        .replace(/[^a-z0-9_-]/g, '')
        .slice(0, 64);
};

const getElementToken = (element: HTMLElement) => {
    const fromData = [
        element.getAttribute('data-id'),
        element.getAttribute('data-item-id'),
        element.getAttribute('data-track-id'),
        element.getAttribute('aria-label'),
    ].find((candidate) => typeof candidate === 'string' && candidate.trim().length > 0);

    if (fromData) {
        return sanitizeToken(fromData);
    }

    if (element.id) {
        return sanitizeToken(element.id);
    }

    const text = (element.textContent || '').replace(/\s+/g, ' ').trim();
    if (text.length > 0) {
        return sanitizeToken(text.slice(0, 40));
    }

    const className = typeof element.className === 'string' ? element.className : '';
    if (className.trim().length > 0) {
        return sanitizeToken(className.split(/\s+/).slice(0, 2).join('-'));
    }

    return 'focus-item';
};

const getNodeOrdinal = (element: HTMLElement) => {
    const parent = element.parentElement;
    if (!parent) {
        return 0;
    }
    const siblings = Array.from(parent.children);
    return Math.max(0, siblings.indexOf(element));
};

const ensureElementFocusKey = (element: HTMLElement) => {
    const existing = element.getAttribute(FOCUS_KEY_ATTR);
    if (existing && existing.trim().length > 0) {
        return existing;
    }

    const ordinal = getNodeOrdinal(element);
    const token = getElementToken(element);
    const generated = `${token}:${ordinal}`;
    element.setAttribute(FOCUS_KEY_ATTR, generated);
    return generated;
};

const resolveContainer = (element: HTMLElement) => {
    for (const selector of CONTAINER_SELECTOR) {
        const container = element.closest<HTMLElement>(selector);
        if (container) {
            return container;
        }
    }
    return element.parentElement || null;
};

const ensureContainerKey = (container: HTMLElement) => {
    const existing = container.getAttribute(CONTAINER_KEY_ATTR);
    if (existing && existing.trim().length > 0) {
        return existing;
    }

    const token = getElementToken(container);
    const ordinal = getNodeOrdinal(container);
    const generated = `${token}:container:${ordinal}`;
    container.setAttribute(CONTAINER_KEY_ATTR, generated);
    return generated;
};

const collectFocusableChildren = (
    container: HTMLElement,
    isVisibleElement: (element: HTMLElement) => boolean
) => {
    return Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)).filter((candidate) => {
        if (!isVisibleElement(candidate)) {
            return false;
        }
        const tabIndex = candidate.getAttribute('tabindex');
        return tabIndex !== '-1';
    });
};

const selectNearestPriorCandidate = (
    candidates: HTMLElement[],
    index: number
) => {
    if (candidates.length === 0) {
        return null;
    }

    const clampedIndex = Math.max(0, Math.min(index, candidates.length - 1));
    for (let current = clampedIndex; current >= 0; current -= 1) {
        const candidate = candidates[current];
        if (candidate) {
            return candidate;
        }
    }

    return candidates[0] || null;
};

const escapeForAttributeSelector = (value: string) => {
    return value.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
};

const findByFocusKey = (
    focusKey: string | undefined,
    isVisibleElement: (element: HTMLElement) => boolean
) => {
    if (!focusKey) {
        return null;
    }

    const selector = `[${FOCUS_KEY_ATTR}="${escapeForAttributeSelector(focusKey)}"]`;
    const candidate = document.querySelector<HTMLElement>(selector);
    if (candidate && isVisibleElement(candidate)) {
        return candidate;
    }
    return null;
};

const findContainerByKey = (containerKey: string | undefined) => {
    if (!containerKey) {
        return null;
    }
    const selector = `[${CONTAINER_KEY_ATTR}="${escapeForAttributeSelector(containerKey)}"]`;
    return document.querySelector<HTMLElement>(selector);
};

export const createFocusSnapshot = ({
    element,
    routeHash,
    zone,
    scrollY,
    sessionId,
}: CaptureFocusSnapshotArgs): TvFocusSnapshot | null => {
    if (!element || !element.isConnected) {
        return null;
    }

    const focusKey = ensureElementFocusKey(element);
    const container = resolveContainer(element);
    const containerKey = container ? ensureContainerKey(container) : undefined;
    const indexInContainer = container
        ? collectFocusableChildren(container, () => true).indexOf(element)
        : undefined;

    return {
        routeHash: routeHash || '#/',
        zone,
        focusKey,
        containerKey,
        indexInContainer: indexInContainer != null && indexInContainer >= 0 ? indexInContainer : undefined,
        scrollY: Number.isFinite(scrollY) ? scrollY : 0,
        timestampMs: Date.now(),
        sessionId,
    };
};

export const restoreFocusSnapshot = ({
    snapshot,
    routeHash,
    routeEntryCandidates,
    isVisibleElement,
    focusElement,
}: RestoreFocusSnapshotArgs): FocusRestoreResult => {
    if (!snapshot) {
        const routeEntry = routeEntryCandidates.find((candidate) => isVisibleElement(candidate));
        if (routeEntry) {
            focusElement(routeEntry);
            return { restored: true, reason: 'route_entry', element: routeEntry };
        }
        return { restored: false, reason: 'no_snapshot' };
    }

    const exact = findByFocusKey(snapshot.focusKey, isVisibleElement);
    if (exact) {
        focusElement(exact);
        return { restored: true, reason: 'exact', element: exact };
    }

    const container = findContainerByKey(snapshot.containerKey);
    if (container) {
        const children = collectFocusableChildren(container, isVisibleElement);
        if (children.length > 0) {
            const prior = selectNearestPriorCandidate(children, snapshot.indexInContainer ?? 0);
            if (prior) {
                focusElement(prior);
                return { restored: true, reason: 'same_container_prior', element: prior };
            }

            const entry = children[0];
            if (entry) {
                focusElement(entry);
                return { restored: true, reason: 'container_entry', element: entry };
            }
        }
    }

    const routeEntry = routeEntryCandidates.find((candidate) => isVisibleElement(candidate));
    if (routeEntry) {
        focusElement(routeEntry);
        return { restored: true, reason: 'route_entry', element: routeEntry };
    }

    if (routeHash !== snapshot.routeHash) {
        return { restored: false, reason: 'failed' };
    }

    return { restored: false, reason: 'failed' };
};
