import Bowser from 'bowser';

const APPLE_MOBILE_DEVICES = [
    'iPad Simulator',
    'iPhone Simulator',
    'iPod Simulator',
    'iPad',
    'iPhone',
    'iPod',
];

const { userAgent, platform, maxTouchPoints } = globalThis.navigator;

// this detects ipad properly in safari
// while bowser does not
const isIOS = APPLE_MOBILE_DEVICES.includes(platform) || (userAgent.includes('Mac') && 'ontouchend' in document);

// Edge case: iPad is included in this function
// Keep in mind maxTouchPoints for Vision Pro might change in the future
const isVisionOS = userAgent.includes('Macintosh') && maxTouchPoints === 5;

const bowser = Bowser.getParser(userAgent);
const os = bowser.getOSName().toLowerCase();

const name = isVisionOS ? 'visionos' : isIOS ? 'ios' : os || 'unknown';

// Android TV identifies as Android in UA, but should use non-mobile layouts.
const isAndroidTv =
    userAgent.includes('Android TV') ||
    userAgent.includes('Google TV') ||
    userAgent.includes('AFT') ||
    userAgent.includes('BRAVIA') ||
    userAgent.includes('SmartTV') ||
    userAgent.includes('TV;');

const isMobile = ['ios', 'android'].includes(name) && !isAndroidTv;

export {
    name,
    isMobile,
};
