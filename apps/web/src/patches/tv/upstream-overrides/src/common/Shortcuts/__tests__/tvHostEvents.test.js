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

    test('createBackHandledEnvelope requires requestId', () => {
        expect(createBackHandledEnvelope('', true, 'overlay_close')).toBeNull();
        expect(createBackHandledEnvelope('   ', false, 'unhandled')).toBeNull();
    });

    test('createBackHandledEnvelope trims requestId and reason', () => {
        const envelope = createBackHandledEnvelope('  req-2  ', false, '  overlay_still_open  ');

        expect(envelope.payload.requestId).toBe('req-2');
        expect(envelope.payload.handled).toBe(false);
        expect(envelope.payload.reason).toBe('overlay_still_open');
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

    test('route classifier distinguishes meta details videos and streams', () => {
        expect(classifyRoute('#/meta-details/tt123')).toBe('metaDetailsVideos');
        expect(classifyRoute('#/meta-details/tt123/streams')).toBe('metaDetailsStreams');
        expect(classifyRoute('#/meta-details/tt123?selected=stream')).toBe('metaDetailsStreams');
    });

    test('focus selector map always returns a non-empty list', () => {
        const selectors = getRouteFocusSelectors('#/settings');
        expect(Array.isArray(selectors)).toBe(true);
        expect(selectors.length).toBeGreaterThan(0);
    });

    test('meta details stream selectors include stream controls', () => {
        const selectors = getRouteFocusSelectors('#/meta-details/tt100/streams');
        expect(selectors.some((selector) => selector.includes('stream-container'))).toBe(true);
    });
});
