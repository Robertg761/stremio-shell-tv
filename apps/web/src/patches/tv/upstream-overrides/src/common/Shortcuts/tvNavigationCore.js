// Pure TV navigation decisions shared between the TS navigation profile and
// jest suites. CommonJS on purpose: the staged upstream jest has no TypeScript
// transform (same reason tvHostEvents.js is CommonJS).

const isMetaDetailsStreamsRoute = (normalizedHash) => {
    if (!normalizedHash.startsWith('#/meta-details')) {
        return false;
    }

    return (
        normalizedHash.includes('/streams') ||
        normalizedHash.includes('streams=true') ||
        normalizedHash.includes('stream=true') ||
        normalizedHash.includes('streamid=') ||
        normalizedHash.includes('selected=stream')
    );
};

const classifyTvRoute = (hash = '') => {
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

const getZoneTransferTargets = (routeKey, zone, direction) => {
    if (zone === 'overlay') {
        return ['overlay'];
    }

    if (zone === 'sidebar') {
        if (direction === 'right') {
            return routeKey === 'settings' ? ['content'] : ['topbar', 'content'];
        }
        return [];
    }

    if (zone === 'topbar') {
        if (direction === 'left') return ['sidebar'];
        if (direction === 'down') return ['content'];
        return [];
    }

    if (zone === 'content') {
        if (direction === 'left') {
            return routeKey === 'settings' ? [] : ['sidebar', 'topbar'];
        }
        if (direction === 'up') {
            return ['topbar'];
        }
        return [];
    }

    if (zone === 'unknown') {
        if (direction === 'left') return ['sidebar', 'topbar', 'content'];
        if (direction === 'right') return ['content', 'topbar', 'sidebar'];
        return ['content', 'topbar', 'sidebar'];
    }

    return [];
};

module.exports = {
    classifyTvRoute,
    getZoneTransferTargets,
};
