const { classifyTvRoute, getZoneTransferTargets } = require('../tvNavigationCore');

describe('classifyTvRoute', () => {
    it('classifies primary routes', () => {
        expect(classifyTvRoute('#/player/abc')).toBe('player');
        expect(classifyTvRoute('#/discover/catalog')).toBe('discover');
        expect(classifyTvRoute('#/settings')).toBe('settings');
        expect(classifyTvRoute('#/search?query=x')).toBe('search');
    });

    it('groups board-like routes into board', () => {
        expect(classifyTvRoute('#/board')).toBe('board');
        expect(classifyTvRoute('#/library')).toBe('board');
        expect(classifyTvRoute('#/calendar')).toBe('board');
        expect(classifyTvRoute('#/addons')).toBe('board');
    });

    it('splits meta-details into videos and streams', () => {
        expect(classifyTvRoute('#/meta-details/movie/tt1')).toBe('metaDetailsVideos');
        expect(classifyTvRoute('#/meta-details/movie/tt1/streams')).toBe('metaDetailsStreams');
        expect(classifyTvRoute('#/meta-details/movie/tt1?streams=true')).toBe('metaDetailsStreams');
        expect(classifyTvRoute('#/meta-details/series/tt2?streamId=abc')).toBe('metaDetailsStreams');
    });

    it('is case-insensitive and defaults safely', () => {
        expect(classifyTvRoute('#/Player/ABC')).toBe('player');
        expect(classifyTvRoute('')).toBe('default');
        expect(classifyTvRoute(undefined)).toBe('default');
        expect(classifyTvRoute('#/intro')).toBe('default');
    });
});

describe('getZoneTransferTargets', () => {
    it('keeps focus inside overlays', () => {
        expect(getZoneTransferTargets('board', 'overlay', 'left')).toEqual(['overlay']);
        expect(getZoneTransferTargets('settings', 'overlay', 'down')).toEqual(['overlay']);
    });

    it('moves right from the sidebar into topbar/content', () => {
        expect(getZoneTransferTargets('board', 'sidebar', 'right')).toEqual(['topbar', 'content']);
        expect(getZoneTransferTargets('settings', 'sidebar', 'right')).toEqual(['content']);
        expect(getZoneTransferTargets('board', 'sidebar', 'left')).toEqual([]);
        expect(getZoneTransferTargets('board', 'sidebar', 'up')).toEqual([]);
    });

    it('moves from the topbar to sidebar or content only', () => {
        expect(getZoneTransferTargets('board', 'topbar', 'left')).toEqual(['sidebar']);
        expect(getZoneTransferTargets('board', 'topbar', 'down')).toEqual(['content']);
        expect(getZoneTransferTargets('board', 'topbar', 'right')).toEqual([]);
        expect(getZoneTransferTargets('board', 'topbar', 'up')).toEqual([]);
    });

    it('moves from content to chrome zones except on settings', () => {
        expect(getZoneTransferTargets('board', 'content', 'left')).toEqual(['sidebar', 'topbar']);
        expect(getZoneTransferTargets('settings', 'content', 'left')).toEqual([]);
        expect(getZoneTransferTargets('board', 'content', 'up')).toEqual(['topbar']);
        expect(getZoneTransferTargets('board', 'content', 'down')).toEqual([]);
        expect(getZoneTransferTargets('board', 'content', 'right')).toEqual([]);
    });

    it('searches all zones from unknown positions', () => {
        expect(getZoneTransferTargets('board', 'unknown', 'left')).toEqual(['sidebar', 'topbar', 'content']);
        expect(getZoneTransferTargets('board', 'unknown', 'right')).toEqual(['content', 'topbar', 'sidebar']);
        expect(getZoneTransferTargets('board', 'unknown', 'down')).toEqual(['content', 'topbar', 'sidebar']);
    });
});
