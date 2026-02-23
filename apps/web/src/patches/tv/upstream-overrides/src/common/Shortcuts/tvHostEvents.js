const HOST_COMMAND_VERSION = 1;

const ROUTE_FOCUS_SELECTORS = {
    board: [
        '.nav-tab-button-container.selected',
        '.nav-tab-button-container',
        '.meta-item-container',
        '.button-container'
    ],
    discover: [
        '.search-bar-container .button-container',
        '.meta-item-container',
        '.button-container'
    ],
    metaDetails: [
        '.stream-container',
        '.button-container',
        '.meta-item-container'
    ],
    settings: [
        '.menu-option-container.selected',
        '.menu-option-container',
        '.section-container .button-container',
        '.option-container .button-container'
    ],
    player: [
        '.control-bar-button',
        '.side-drawer-button',
        '.next-video-popup-container .button-container',
        '.button-container'
    ],
    search: [
        '.search-bar-container input',
        '.search-bar-container .button-container',
        '.meta-item-container'
    ],
    default: [
        '.nav-tab-button-container.selected',
        '.nav-tab-button-container',
        '.button-container',
        '.meta-item-container',
        'input',
        'textarea',
        'select'
    ]
};

const classifyRoute = (hash = '') => {
    const normalized = String(hash || '').toLowerCase();
    if (normalized.startsWith('#/player')) return 'player';
    if (normalized.startsWith('#/discover')) return 'discover';
    if (normalized.startsWith('#/meta-details')) return 'metaDetails';
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
    normalizeDeepLinkToHash
};
