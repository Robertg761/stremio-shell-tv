// Copyright (C) 2017-2026 Smart code 203358507

const React = require('react');

const LINK_API_BASE_URL = 'https://link.stremio.com/api';
const CREATE_SESSION_PATH = 'create';
const READ_SESSION_PATH = 'read';
const MAX_TRIES = 180;
const POLL_INTERVAL_MS = 1000;

const PHONE_LOGIN_CANCELLED_CODE = 'PHONE_LOGIN_CANCELLED';

const createCancelledError = () => {
    const error = new Error('Phone login cancelled.');
    error.code = PHONE_LOGIN_CANCELLED_CODE;
    return error;
};

const createTokenAuthArgs = (token) => ({
    type: 'LoginWithToken',
    token
});

const mapSessionInfo = (payload) => {
    if (!payload || typeof payload !== 'object') {
        return null;
    }

    const result = payload.result && typeof payload.result === 'object' ? payload.result : null;
    const code = typeof payload.code === 'string' && payload.code.length > 0 ?
        payload.code :
        (result && typeof result.code === 'string' && result.code.length > 0 ? result.code : null);
    const loginUrl = typeof payload.link === 'string' && payload.link.length > 0 ?
        payload.link :
        (result && typeof result.link === 'string' && result.link.length > 0 ? result.link : null);
    const qrCodeUrl = typeof payload.qrcode === 'string' && payload.qrcode.length > 0 ?
        payload.qrcode :
        (result && typeof result.qrcode === 'string' && result.qrcode.length > 0 ? result.qrcode : null);

    if (!code || !loginUrl) {
        return null;
    }

    return {
        code,
        loginUrl,
        qrCodeUrl
    };
};

const mapPayloadToAuthArgs = (payload) => {
    if (!payload || typeof payload !== 'object') {
        return null;
    }

    if (typeof payload.authKey === 'string' && payload.authKey.length > 0) {
        return createTokenAuthArgs(payload.authKey);
    }

    if (typeof payload.key === 'string' && payload.key.length > 0) {
        return createTokenAuthArgs(payload.key);
    }

    if (payload.data && typeof payload.data === 'object' && typeof payload.data.authKey === 'string' && payload.data.authKey.length > 0) {
        return createTokenAuthArgs(payload.data.authKey);
    }

    if (payload.result && typeof payload.result === 'object' && typeof payload.result.authKey === 'string' && payload.result.authKey.length > 0) {
        return createTokenAuthArgs(payload.result.authKey);
    }

    if (payload.auth && typeof payload.auth === 'object' && typeof payload.auth.key === 'string' && payload.auth.key.length > 0) {
        return createTokenAuthArgs(payload.auth.key);
    }

    const user = payload.user && typeof payload.user === 'object' ? payload.user : null;

    if (user && typeof user.token === 'string' && user.token.length > 0 && typeof user.sub === 'string' && user.sub.length > 0) {
        return {
            type: 'Apple',
            token: user.token,
            sub: user.sub,
            email: typeof user.email === 'string' ? user.email : '',
            name: typeof user.name === 'string' ? user.name : ''
        };
    }

    if (user && typeof user.email === 'string' && user.email.length > 0) {
        if (typeof user.password === 'string' && user.password.length > 0) {
            return {
                type: 'Login',
                email: user.email,
                password: user.password
            };
        }

        if (typeof user.fbLoginToken === 'string' && user.fbLoginToken.length > 0) {
            return {
                type: 'Login',
                email: user.email,
                password: user.fbLoginToken,
                facebook: true
            };
        }
    }

    return null;
};

const usePhoneQrLogin = () => {
    const pendingRef = React.useRef(null);

    const stop = React.useCallback(() => {
        const pending = pendingRef.current;

        if (!pending) {
            return;
        }

        pending.stopped = true;

        if (pending.timeout !== null) {
            clearTimeout(pending.timeout);
        }

        if (typeof pending.reject === 'function') {
            pending.reject(createCancelledError());
        }

        pendingRef.current = null;
    }, []);

    const start = React.useCallback(() => {
        stop();

        const pending = {
            timeout: null,
            reject: null,
            stopped: false
        };

        pendingRef.current = pending;

        return fetch(`${LINK_API_BASE_URL}/${CREATE_SESSION_PATH}`)
            .then((response) => {
                if (!response.ok) {
                    throw new Error(`Phone login is unavailable (${response.status}).`);
                }

                return response.json();
            })
            .then((payload) => {
                if (pending.stopped) {
                    throw createCancelledError();
                }

                const sessionInfo = mapSessionInfo(payload);

                if (!sessionInfo) {
                    throw new Error('Phone login session could not be started.');
                }

                const complete = new Promise((resolve, reject) => {
                    pending.reject = reject;
                    let tries = 0;

                    const pollCredentials = () => {
                        if (pending.stopped) {
                            return;
                        }

                        pending.timeout = setTimeout(() => {
                            if (pending.stopped) {
                                return;
                            }

                            if (tries >= MAX_TRIES) {
                                pendingRef.current = null;
                                reject(new Error('Phone login timed out. Please try again.'));
                                return;
                            }

                            tries += 1;

                            fetch(`${LINK_API_BASE_URL}/${READ_SESSION_PATH}?code=${encodeURIComponent(sessionInfo.code)}`)
                                .then((response) => {
                                    if (!response.ok) {
                                        throw new Error(`Phone login is pending (${response.status}).`);
                                    }

                                    return response.json();
                                })
                                .then((readPayload) => {
                                    const authArgs = mapPayloadToAuthArgs(readPayload);

                                    if (!authArgs) {
                                        throw new Error('Phone login is still pending.');
                                    }

                                    pendingRef.current = null;
                                    resolve(authArgs);
                                })
                                .catch(() => {
                                    if (!pending.stopped) {
                                        pollCredentials();
                                    }
                                });
                        }, POLL_INTERVAL_MS);
                    };

                    pollCredentials();
                });

                return {
                    loginUrl: sessionInfo.loginUrl,
                    qrCodeUrl: sessionInfo.qrCodeUrl,
                    complete
                };
            })
            .catch((error) => {
                if (pendingRef.current === pending) {
                    pendingRef.current = null;
                }

                throw error;
            });
    }, [stop]);

    React.useEffect(() => {
        return () => {
            stop();
        };
    }, [stop]);

    return [start, stop];
};

usePhoneQrLogin.PHONE_LOGIN_CANCELLED_CODE = PHONE_LOGIN_CANCELLED_CODE;

module.exports = usePhoneQrLogin;
