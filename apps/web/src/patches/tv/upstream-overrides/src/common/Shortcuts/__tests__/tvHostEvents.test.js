const {
    classifyRoute,
    createBackHandledEnvelope,
    getRouteFocusSelectors,
    normalizeDeepLinkToHash,
} = require('../tvHostEvents');

describe('tvHostEvents', () => {
    test('createBackHandledEnvelope builds deterministic payload', () => {
        const envelope = createBackHandledEnvelope('req-1', true, 'overlay_close');

        expect(envelope.type).toBe('back.handled');
        expect(envelope.version).toBe(1);
        expect(envelope.payload.requestId).toBe('req-1');
        expect(envelope.payload.handled).toBe(true);
        expect(envelope.payload.reason).toBe('overlay_close');
        expect(typeof envelope.timestampMs).toBe('number');
    });

    test('normalizeDeepLinkToHash maps stremio-shell urls to router hashes', () => {
        expect(normalizeDeepLinkToHash('stremio-shell://board')).toBe('#/board');
        expect(normalizeDeepLinkToHash('stremio-shell://player/abc?x=1')).toBe('#/player/abc?x=1');
        expect(normalizeDeepLinkToHash('#/settings')).toBe('#/settings');
    });

    test('route classifier selects player and default route buckets', () => {
        expect(classifyRoute('#/player/test')).toBe('player');
        expect(classifyRoute('#/discover/movie')).toBe('discover');
        expect(classifyRoute('#/unknown')).toBe('default');
    });

    test('focus selector map always returns a non-empty list', () => {
        const selectors = getRouteFocusSelectors('#/settings');
        expect(Array.isArray(selectors)).toBe(true);
        expect(selectors.length).toBeGreaterThan(0);
    });
});
