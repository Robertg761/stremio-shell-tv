const HOST_COMMAND_VERSION = 1;
const DEFAULT_ROUTE_SNAPSHOT_LIMIT = 100;

const ROUTE_FOCUS_SELECTORS = {
    board: [
        '[class*="nav-tab-button-container"][class*="selected"]',
        '[class*="nav-tab-button-container"]',
        '[class*="meta-item-container"]',
        '[class*="button-container"]'
    ],
    discover: [
        '[class*="search-bar-container"] [class*="button-container"]',
        '[class*="meta-item-container"]',
        '[class*="button-container"]'
    ],
    metaDetailsVideos: [
        '[class*="back-button"]',
        '[aria-label="Back"]',
        '[class*="seasons-bar"] [class*="button-container"]',
        '[class*="season-picker"] [class*="button-container"]',
        '[class*="episode-picker"] [class*="button-container"]',
        '[class*="meta-item-container"]',
        '[class*="addon"] [class*="button-container"]',
        '[class*="install"] [class*="button-container"]',
        '[class*="button-container"]'
    ],
    metaDetailsStreams: [
        '[class*="back-button"]',
        '[aria-label="Back"]',
        '[class*="stream-container"][class*="selected"]',
        '[class*="stream-container"]',
        '[class*="streams-container"] [class*="button-container"]',
        '[class*="filter"] [class*="button-container"]',
        '[class*="addon"] [class*="button-container"]',
        '[class*="install"] [class*="button-container"]',
        '[class*="button-container"]',
    ],
    settings: [
        '[class*="menu-option-container"][class*="selected"]',
        '[class*="menu-option-container"]',
        '[class*="section-container"] [class*="button-container"]',
        '[class*="option-container"] [class*="button-container"]'
    ],
    player: [
        '[class*="control-bar-button"]',
        '[class*="side-drawer-button"]',
        '[class*="next-video-popup-container"] [class*="button-container"]',
        '[class*="button-container"]'
    ],
    search: [
        '[class*="search-bar-container"] input',
        '[class*="search-bar-container"] [class*="button-container"]',
        '[class*="meta-item-container"]'
    ],
    default: [
        '[class*="nav-tab-button-container"][class*="selected"]',
        '[class*="nav-tab-button-container"]',
        '[class*="button-container"]',
        '[class*="meta-item-container"]',
        'input',
        'textarea',
        'select'
    ]
};

const isMetaDetailsStreamsRoute = (normalizedHash) => {
    if (!normalizedHash.startsWith('#/meta-details')) {
        return false;
    }

    return (
        normalizedHash.includes('/streams') ||
        normalizedHash.includes('streams=true') ||
        normalizedHash.includes('stream=true') ||
        normalizedHash.includes('selected=stream') ||
        normalizedHash.includes('streamid=')
    );
};

const classifyRoute = (hash = '') => {
    const normalized = String(hash || '').toLowerCase();
    if (normalized.startsWith('#/player')) return 'player';
    if (normalized.startsWith('#/discover')) return 'discover';
    if (normalized.startsWith('#/meta-details')) {
        return isMetaDetailsStreamsRoute(normalized) ? 'metaDetailsStreams' : 'metaDetailsVideos';
    }
    if (normalized.startsWith('#/settings')) return 'settings';
    if (normalized.startsWith('#/search')) return 'search';
    if (
        normalized.startsWith('#/board') ||
        normalized.startsWith('#/library') ||
        normalized.startsWith('#/calendar') ||
        normalized.startsWith('#/addons')
    ) {
        return 'board';
    }
    return 'default';
};

const getRouteFocusSelectors = (hash = '') => {
    const route = classifyRoute(hash);
    return ROUTE_FOCUS_SELECTORS[route] || ROUTE_FOCUS_SELECTORS.default;
};

const shouldHandleDirectionalKey = ({
    hasDirection = false,
    isEditableTarget = false,
    shouldHandleArrowNavigation = false,
    routeHash = '',
} = {}) => {
    return Boolean(
        hasDirection &&
        !isEditableTarget &&
        shouldHandleArrowNavigation &&
        classifyRoute(routeHash) !== 'player'
    );
};

const setBoundedRouteSnapshot = (snapshotMap, routeHash, snapshot, limit = DEFAULT_ROUTE_SNAPSHOT_LIMIT) => {
    if (!(snapshotMap instanceof Map)) {
        return;
    }
    if (typeof routeHash !== 'string' || !routeHash) {
        return;
    }
    if (!snapshot || typeof snapshot !== 'object') {
        return;
    }

    if (snapshotMap.has(routeHash)) {
        snapshotMap.delete(routeHash);
    }
    snapshotMap.set(routeHash, snapshot);

    const maxEntries = Number.isFinite(limit) && limit > 0 ? Math.floor(limit) : DEFAULT_ROUTE_SNAPSHOT_LIMIT;
    while (snapshotMap.size > maxEntries) {
        const oldestKey = snapshotMap.keys().next().value;
        if (typeof oldestKey !== 'string') {
            break;
        }
        snapshotMap.delete(oldestKey);
    }
};

const normalizeDeepLinkToHash = (rawUrl) => {
    if (typeof rawUrl !== 'string') {
        return null;
    }

    const trimmed = rawUrl.trim();
    if (!trimmed) {
        return null;
    }
    if (trimmed.startsWith('#')) {
        return trimmed;
    }

    try {
        const parsed = new URL(trimmed, typeof window !== 'undefined' ? window.location.href : 'https://localhost');
        if (parsed.protocol === 'stremio-shell:') {
            if (parsed.hash.startsWith('#/')) {
                return parsed.hash;
            }
            if (parsed.hash.startsWith('#')) {
                return parsed.hash;
            }

            const hostAndPath = [parsed.hostname, parsed.pathname]
                .filter(Boolean)
                .join('/')
                .replace(/\/+/g, '/')
                .replace(/^\//, '');

            if (hostAndPath.length > 0) {
                return `#/${hostAndPath}${parsed.search}`;
            }
            return null;
        }

        if (parsed.origin === (typeof window !== 'undefined' ? window.location.origin : parsed.origin)) {
            if (parsed.hash.startsWith('#')) {
                return parsed.hash;
            }
            if (parsed.pathname.startsWith('/')) {
                return `#${parsed.pathname}${parsed.search}`;
            }
        }
    } catch (_) {
        if (trimmed.startsWith('/')) {
            return `#${trimmed}`;
        }
    }

    return null;
};

const createBackHandledEnvelope = (requestId, handled, reason) => {
    if (typeof requestId !== 'string' || !requestId.trim()) {
        return null;
    }

    const payload = {
        requestId: requestId.trim(),
        handled: !!handled
    };
    if (typeof reason === 'string' && reason.trim()) {
        payload.reason = reason.trim();
    }

    return {
        type: 'back.handled',
        version: HOST_COMMAND_VERSION,
        payload,
        timestampMs: Date.now()
    };
};

module.exports = {
    classifyRoute,
    createBackHandledEnvelope,
    getRouteFocusSelectors,
    normalizeDeepLinkToHash,
    setBoundedRouteSnapshot,
    shouldHandleDirectionalKey,
};
