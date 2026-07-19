import * as tvNavigationCore from './tvNavigationCore';

export type Direction = 'up' | 'down' | 'left' | 'right';

export type TvZone = 'sidebar' | 'topbar' | 'content' | 'overlay' | 'unknown';

export type TvRouteKey =
    | 'board'
    | 'discover'
    | 'metaDetailsVideos'
    | 'metaDetailsStreams'
    | 'search'
    | 'settings'
    | 'player'
    | 'default';

type ZoneProfile = {
    selectors: string[],
    entrySelectors?: string[],
};

export type TvNavigationProfile = {
    routeKey: TvRouteKey,
    zones: Record<'sidebar' | 'topbar' | 'content' | 'overlay', ZoneProfile>,
    entrySelectors: string[],
};

export const DEFAULT_FOCUSABLE_SELECTORS = [
    'a[href]',
    'button',
    '[role="button"]',
    '[tabindex]:not([tabindex="-1"])',
    'input',
    'textarea',
    'select',
    '[class*="button-container"]',
    '[class*="meta-item-container"]',
    '[class*="stream-container"]',
    '[class*="menu-option-container"]',
];

export const SIDEBAR_SELECTORS = [
    '[class*="nav-tab-button-container"]',
    '[class*="nav-tab-button"]',
];

export const TOPBAR_SELECTORS = [
    '[class*="search-bar-container"] input',
    '[class*="search-bar-container"] [class*="button-container"]',
    '[class*="top"] [class*="button-container"]',
    '[class*="header"] [class*="button-container"]',
    '[class*="back-button"]',
    '[aria-label="Back"]',
];

export const OVERLAY_SELECTORS = [
    '[role="dialog"]',
    '[aria-modal="true"]',
    '[role="menu"]',
    '[role="listbox"]',
    '[role="alertdialog"]',
    '[class*="popup"]',
    '[class*="modal"]',
    '[class*="focus-lock"]',
    '[class*="context-menu"]',
    '[class*="menu-container"]',
    '[class*="dropdown"]',
    '[data-focus-lock-disabled]',
    '[data-radix-popper-content-wrapper]',
];

const META_DETAILS_VIDEO_SELECTORS = [
    '[class*="seasons-bar"] [class*="button-container"]',
    '[class*="season-picker"] [class*="button-container"]',
    '[class*="episode-picker"] [class*="button-container"]',
    '[class*="meta-item-container"]',
    '[class*="addon"] [class*="button-container"]',
    '[class*="install"] [class*="button-container"]',
    '[class*="button-container"]',
];

const META_DETAILS_STREAM_SELECTORS = [
    '[class*="stream-container"]',
    '[class*="streams-container"] [class*="button-container"]',
    '[class*="addon"] [class*="button-container"]',
    '[class*="filter"] [class*="button-container"]',
    '[class*="install"] [class*="button-container"]',
    '[class*="button-container"]',
];

const BOARD_CONTENT_SELECTORS = [
    '[class*="meta-item-container"]',
    '[class*="continue-watching"] [class*="button-container"]',
    '[class*="button-container"]',
];

const SETTINGS_CONTENT_SELECTORS = [
    '[class*="menu-option-container"]',
    '[class*="option-container"] [class*="button-container"]',
    '[class*="section-container"] [class*="button-container"]',
    '[role="switch"]',
    '[role="checkbox"]',
    '[class*="button-container"]',
];

const SEARCH_CONTENT_SELECTORS = [
    '[class*="meta-item-container"]',
    '[class*="button-container"]',
];

const BASE_PROFILE: TvNavigationProfile = {
    routeKey: 'default',
    zones: {
        sidebar: { selectors: SIDEBAR_SELECTORS },
        topbar: { selectors: TOPBAR_SELECTORS },
        content: { selectors: [...BOARD_CONTENT_SELECTORS, ...DEFAULT_FOCUSABLE_SELECTORS] },
        overlay: { selectors: OVERLAY_SELECTORS },
    },
    entrySelectors: [
        '[class*="nav-tab-button-container"][class*="selected"]',
        '[class*="meta-item-container"]',
        '[class*="button-container"]',
        ...DEFAULT_FOCUSABLE_SELECTORS,
    ],
};

const ROUTE_PROFILES: Record<TvRouteKey, TvNavigationProfile> = {
    board: {
        routeKey: 'board',
        zones: {
            sidebar: { selectors: SIDEBAR_SELECTORS },
            topbar: { selectors: TOPBAR_SELECTORS },
            content: { selectors: BOARD_CONTENT_SELECTORS },
            overlay: { selectors: OVERLAY_SELECTORS },
        },
        entrySelectors: [
            '[class*="nav-tab-button-container"][class*="selected"]',
            '[class*="meta-item-container"][class*="selected"]',
            '[class*="meta-item-container"]',
            '[class*="button-container"]',
        ],
    },
    discover: {
        routeKey: 'discover',
        zones: {
            sidebar: { selectors: SIDEBAR_SELECTORS },
            topbar: { selectors: TOPBAR_SELECTORS },
            content: { selectors: BOARD_CONTENT_SELECTORS },
            overlay: { selectors: OVERLAY_SELECTORS },
        },
        entrySelectors: [
            '[class*="search-bar-container"] input',
            '[class*="search-bar-container"] [class*="button-container"]',
            '[class*="meta-item-container"]',
            '[class*="button-container"]',
        ],
    },
    metaDetailsVideos: {
        routeKey: 'metaDetailsVideos',
        zones: {
            sidebar: { selectors: SIDEBAR_SELECTORS },
            topbar: {
                selectors: [
                    '[class*="back-button"]',
                    '[aria-label="Back"]',
                    '[class*="meta"] [class*="button-container"]',
                ],
            },
            content: { selectors: META_DETAILS_VIDEO_SELECTORS },
            overlay: { selectors: OVERLAY_SELECTORS },
        },
        entrySelectors: [
            '[class*="back-button"]',
            '[aria-label="Back"]',
            '[class*="seasons-bar"] [class*="button-container"]',
            '[class*="episode-picker"] [class*="button-container"]',
            '[class*="meta-item-container"]',
            '[class*="button-container"]',
        ],
    },
    metaDetailsStreams: {
        routeKey: 'metaDetailsStreams',
        zones: {
            sidebar: { selectors: SIDEBAR_SELECTORS },
            topbar: {
                selectors: [
                    '[class*="back-button"]',
                    '[aria-label="Back"]',
                    '[class*="meta"] [class*="button-container"]',
                ],
            },
            content: { selectors: META_DETAILS_STREAM_SELECTORS },
            overlay: { selectors: OVERLAY_SELECTORS },
        },
        entrySelectors: [
            '[class*="stream-container"][class*="selected"]',
            '[class*="stream-container"]',
            '[class*="streams-container"] [class*="button-container"]',
            '[class*="button-container"]',
        ],
    },
    search: {
        routeKey: 'search',
        zones: {
            sidebar: { selectors: SIDEBAR_SELECTORS },
            topbar: { selectors: TOPBAR_SELECTORS },
            content: { selectors: SEARCH_CONTENT_SELECTORS },
            overlay: { selectors: OVERLAY_SELECTORS },
        },
        entrySelectors: [
            '[class*="search-bar-container"] input',
            '[class*="search-bar-container"] [class*="button-container"]',
            '[class*="meta-item-container"]',
        ],
    },
    settings: {
        routeKey: 'settings',
        zones: {
            sidebar: { selectors: SIDEBAR_SELECTORS },
            topbar: { selectors: TOPBAR_SELECTORS },
            content: { selectors: SETTINGS_CONTENT_SELECTORS },
            overlay: { selectors: OVERLAY_SELECTORS },
        },
        entrySelectors: [
            '[class*="menu-option-container"][class*="selected"]',
            '[class*="menu-option-container"]',
            '[class*="section-container"] [class*="button-container"]',
            '[class*="option-container"] [class*="button-container"]',
        ],
    },
    player: {
        routeKey: 'player',
        zones: {
            sidebar: { selectors: SIDEBAR_SELECTORS },
            topbar: { selectors: TOPBAR_SELECTORS },
            content: {
                selectors: [
                    '[class*="control-bar-button"]',
                    '[class*="next-video-popup-container"] [class*="button-container"]',
                    '[class*="side-drawer-button"]',
                    '[class*="button-container"]',
                ],
            },
            overlay: { selectors: OVERLAY_SELECTORS },
        },
        entrySelectors: [
            '[class*="control-bar-button"]',
            '[class*="side-drawer-button"]',
            '[class*="button-container"]',
        ],
    },
    default: BASE_PROFILE,
};


export const classifyTvRoute = (hash = ''): TvRouteKey => {
    return tvNavigationCore.classifyTvRoute(hash) as TvRouteKey;
};

export const getRouteNavigationProfile = (hash = '') => {
    const routeKey = classifyTvRoute(hash);
    return ROUTE_PROFILES[routeKey] || BASE_PROFILE;
};

const FOCUSABLE_DESCENDANT_SELECTOR = DEFAULT_FOCUSABLE_SELECTORS.join(', ');

// Substring overlay selectors (e.g. [class*="popup"]) can false-match leaf
// controls like the 49x49 nav-menu button, which once scoped all spatial
// navigation into an empty container (see docs/fix-remote-navigation.md).
// A real overlay hosts focusable content; a matched control does not.
export const isPlausibleOverlayElement = (element: HTMLElement): boolean => {
    return element.querySelector(FOCUSABLE_DESCENDANT_SELECTOR) != null;
};

export const getZoneSelectors = (profile: TvNavigationProfile, zone: TvZone): string[] => {
    if (zone === 'unknown') {
        return profile.entrySelectors;
    }
    return profile.zones[zone]?.selectors || [];
};

export const resolveElementZone = (element: HTMLElement | null, profile: TvNavigationProfile): TvZone => {
    if (!element) {
        return 'unknown';
    }

    const overlaySelector = profile.zones.overlay.selectors.join(', ');
    if (overlaySelector) {
        const overlayMatch = element.closest<HTMLElement>(overlaySelector);
        if (overlayMatch && isPlausibleOverlayElement(overlayMatch)) {
            return 'overlay';
        }
    }

    const sidebarSelector = profile.zones.sidebar.selectors.join(', ');
    if (sidebarSelector && (element.matches(sidebarSelector) || element.closest(sidebarSelector))) {
        return 'sidebar';
    }

    const topbarSelector = profile.zones.topbar.selectors.join(', ');
    if (topbarSelector && (element.matches(topbarSelector) || element.closest(topbarSelector))) {
        return 'topbar';
    }

    const contentSelector = profile.zones.content.selectors.join(', ');
    if (contentSelector && (element.matches(contentSelector) || element.closest(contentSelector))) {
        return 'content';
    }

    return 'unknown';
};

export const getZoneTransferTargets = (
    routeKey: TvRouteKey,
    zone: TvZone,
    direction: Direction
): TvZone[] => {
    return tvNavigationCore.getZoneTransferTargets(routeKey, zone, direction) as TvZone[];
};
