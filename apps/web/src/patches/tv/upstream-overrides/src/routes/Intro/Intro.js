// Copyright (C) 2017-2023 Smart code 203358507

const React = require('react');
const { useTranslation } = require('react-i18next');
const PropTypes = require('prop-types');
const classnames = require('classnames');
const { default: Icon } = require('@stremio/stremio-icons/react');
const { Modal, useRouteFocused } = require('stremio-router');
const { useServices } = require('stremio/services');
const { useBinaryState, usePlatform } = require('stremio/common');
const { Button, Image, Checkbox } = require('stremio/components');
const CredentialsTextInput = require('./CredentialsTextInput');
const PasswordResetModal = require('./PasswordResetModal');
const useFacebookLogin = require('./useFacebookLogin');
const { default: useAppleLogin } = require('./useAppleLogin');
const usePhoneQrLogin = require('./usePhoneQrLogin');

const styles = require('./styles');

const SIGNUP_FORM = 'signup';
const LOGIN_FORM = 'login';

const sourceContainsUserAuthenticated = (source) => {
    if (!source || typeof source !== 'object') {
        return false;
    }

    if (source.event === 'UserAuthenticated') {
        return true;
    }

    if (source.source && typeof source.source === 'object') {
        return sourceContainsUserAuthenticated(source.source);
    }

    return false;
};

const getCoreErrorMessage = (args) => {
    if (args && args.error && typeof args.error === 'object' && typeof args.error.message === 'string' && args.error.message.length > 0) {
        return args.error.message;
    }

    return 'Authentication failed. Please try again.';
};

const getDispatchErrorMessage = (error) => {
    if (error && typeof error === 'object' && typeof error.message === 'string' && error.message.length > 0) {
        return error.message;
    }

    if (typeof error === 'string' && error.length > 0) {
        return error;
    }

    return 'Authentication failed. Please try again.';
};

const Intro = ({ queryParams }) => {
    const { core } = useServices();
    const platform = usePlatform();
    const { t } = useTranslation();
    const routeFocused = useRouteFocused();
    const [startFacebookLogin, stopFacebookLogin] = useFacebookLogin();
    const [startAppleLogin, stopAppleLogin] = useAppleLogin();
    const [startPhoneQrLogin, stopPhoneQrLogin] = usePhoneQrLogin();
    const emailRef = React.useRef(null);
    const passwordRef = React.useRef(null);
    const confirmPasswordRef = React.useRef(null);
    const termsRef = React.useRef(null);
    const privacyPolicyRef = React.useRef(null);
    const marketingRef = React.useRef(null);
    const errorRef = React.useRef(null);
    const [passwordRestModalOpen, openPasswordRestModal, closePasswordResetModal] = useBinaryState(false);
    const [loaderModalOpen, openLoaderModal, closeLoaderModal] = useBinaryState(false);
    const [phoneLoginSession, setPhoneLoginSession] = React.useState(null);
    const [state, dispatch] = React.useReducer(
        (state, action) => {
            switch (action.type) {
                case 'set-form':
                    if (state.form !== action.form) {
                        return {
                            form: action.form,
                            email: '',
                            password: '',
                            confirmPassword: '',
                            termsAccepted: false,
                            privacyPolicyAccepted: false,
                            marketingAccepted: false,
                            error: ''
                        };
                    }
                    return state;
                case 'change-credentials':
                    return {
                        ...state,
                        error: '',
                        [action.name]: action.value
                    };
                case 'toggle-checkbox':
                    return {
                        ...state,
                        error: '',
                        [action.name]: !state[action.name]
                    };
                case 'error':
                    return {
                        ...state,
                        error: action.error
                    };
                default:
                    return state;
            }
        },
        {
            form: [LOGIN_FORM, SIGNUP_FORM].includes(queryParams.get('form')) ? queryParams.get('form') : LOGIN_FORM,
            email: '',
            password: '',
            confirmPassword: '',
            termsAccepted: false,
            privacyPolicyAccepted: false,
            marketingAccepted: false,
            error: ''
        }
    );
    const dispatchAuthenticate = React.useCallback((authenticateArgs) => {
        try {
            Promise.resolve(
                core.transport.dispatch({
                    action: 'Ctx',
                    args: {
                        action: 'Authenticate',
                        args: authenticateArgs
                    }
                })
            )
                .catch((error) => {
                    closeLoaderModal();
                    dispatch({ type: 'error', error: getDispatchErrorMessage(error) });
                });
        } catch (error) {
            closeLoaderModal();
            dispatch({ type: 'error', error: getDispatchErrorMessage(error) });
        }
    }, [closeLoaderModal, core.transport]);
    const loginWithFacebook = React.useCallback(() => {
        openLoaderModal();
        startFacebookLogin()
            .then(({ email, password }) => {
                dispatchAuthenticate({
                    type: 'Login',
                    email,
                    password,
                    facebook: true
                });
            })
            .catch((error) => {
                closeLoaderModal();
                dispatch({ type: 'error', error: getDispatchErrorMessage(error) });
            });
    }, [closeLoaderModal, dispatchAuthenticate, openLoaderModal, startFacebookLogin]);
    const cancelLoginWithFacebook = React.useCallback(() => {
        stopFacebookLogin();
        closeLoaderModal();
    }, [closeLoaderModal, stopFacebookLogin]);
    const loginWithApple = React.useCallback(() => {
        openLoaderModal();
        startAppleLogin()
            .then(({ token, sub, email, name }) => {
                dispatchAuthenticate({
                    type: 'Apple',
                    token,
                    sub,
                    email,
                    name
                });
            })
            .catch((error) => {
                closeLoaderModal();
                dispatch({ type: 'error', error: getDispatchErrorMessage(error) });
            });
    }, [closeLoaderModal, dispatchAuthenticate, openLoaderModal, startAppleLogin]);
    const cancelLoginWithApple = React.useCallback(() => {
        stopAppleLogin();
        closeLoaderModal();
    }, [closeLoaderModal, stopAppleLogin]);
    const cancelPhoneLogin = React.useCallback(() => {
        stopPhoneQrLogin();
        setPhoneLoginSession(null);
    }, [stopPhoneQrLogin]);
    const openPhoneLoginLink = React.useCallback(() => {
        if (phoneLoginSession !== null) {
            try {
                Promise.resolve(platform.openExternal(phoneLoginSession.loginUrl))
                    .catch((error) => {
                        dispatch({ type: 'error', error: getDispatchErrorMessage(error) });
                    });
            } catch (error) {
                dispatch({ type: 'error', error: getDispatchErrorMessage(error) });
            }
        }
    }, [phoneLoginSession, platform]);
    const loginWithPhone = React.useCallback(() => {
        cancelPhoneLogin();
        startPhoneQrLogin()
            .then((session) => {
                setPhoneLoginSession({
                    loginUrl: session.loginUrl,
                    qrCodeUrl: session.qrCodeUrl || `https://link.stremio.com/qr?data=${encodeURIComponent(session.loginUrl)}`
                });
                return session.complete;
            })
            .then((authArgs) => {
                setPhoneLoginSession(null);
                openLoaderModal();
                dispatchAuthenticate(authArgs);
            })
            .catch((error) => {
                if (error && error.code === usePhoneQrLogin.PHONE_LOGIN_CANCELLED_CODE) {
                    return;
                }

                setPhoneLoginSession(null);
                dispatch({ type: 'error', error: getDispatchErrorMessage(error) });
            });
    }, [cancelPhoneLogin, dispatchAuthenticate, openLoaderModal, startPhoneQrLogin]);
    const refreshPhoneLogin = React.useCallback(() => {
        loginWithPhone();
    }, [loginWithPhone]);
    const cancelAllLoginFlows = React.useCallback(() => {
        cancelLoginWithFacebook();
        cancelLoginWithApple();
        cancelPhoneLogin();
    }, [cancelLoginWithFacebook, cancelLoginWithApple, cancelPhoneLogin]);
    const loginWithEmail = React.useCallback(() => {
        if (typeof state.email !== 'string' || state.email.length === 0 || !emailRef.current.validity.valid) {
            dispatch({ type: 'error', error: t('INVALID_EMAIL') });
            return;
        }
        if (typeof state.password !== 'string' || state.password.length === 0) {
            dispatch({ type: 'error', error: t('INVALID_PASSWORD') });
            return;
        }
        openLoaderModal();
        dispatchAuthenticate({
            type: 'Login',
            email: state.email,
            password: state.password
        });
    }, [dispatchAuthenticate, openLoaderModal, state.email, state.password, t]);
    const loginAsGuest = React.useCallback(() => {
        if (!state.termsAccepted) {
            dispatch({ type: 'error', error: t('MUST_ACCEPT_TERMS') });
            return;
        }
        window.location = '#/';
    }, [state.termsAccepted, t]);
    const signup = React.useCallback(() => {
        if (typeof state.email !== 'string' || state.email.length === 0 || !emailRef.current.validity.valid) {
            dispatch({ type: 'error', error: t('INVALID_EMAIL') });
            return;
        }
        if (typeof state.password !== 'string' || state.password.length === 0) {
            dispatch({ type: 'error', error: t('INVALID_PASSWORD') });
            return;
        }
        if (state.password !== state.confirmPassword) {
            dispatch({ type: 'error', error: t('PASSWORDS_NOMATCH') });
            return;
        }
        if (!state.termsAccepted) {
            dispatch({ type: 'error', error: t('MUST_ACCEPT_TERMS') });
            return;
        }
        if (!state.privacyPolicyAccepted) {
            dispatch({ type: 'error', error: t('MUST_ACCEPT_PRIVACY_POLICY') });
            return;
        }
        openLoaderModal();
        dispatchAuthenticate({
            type: 'Register',
            email: state.email,
            password: state.password,
            gdpr_consent: {
                tos: state.termsAccepted,
                privacy: state.privacyPolicyAccepted,
                marketing: state.marketingAccepted,
                from: 'web'
            }
        });
    }, [dispatchAuthenticate, openLoaderModal, state.email, state.password, state.confirmPassword, state.termsAccepted, state.privacyPolicyAccepted, state.marketingAccepted, t]);
    const emailOnChange = React.useCallback((event) => {
        dispatch({
            type: 'change-credentials',
            name: 'email',
            value: event.currentTarget.value
        });
    }, []);
    const emailOnSubmit = React.useCallback(() => {
        passwordRef.current.focus();
    }, []);
    const passwordOnChange = React.useCallback((event) => {
        dispatch({
            type: 'change-credentials',
            name: 'password',
            value: event.currentTarget.value
        });
    }, []);
    const passwordOnSubmit = React.useCallback(() => {
        if (state.form === SIGNUP_FORM) {
            confirmPasswordRef.current.focus();
        } else {
            loginWithEmail();
        }
    }, [state.form, loginWithEmail]);
    const confirmPasswordOnChange = React.useCallback((event) => {
        dispatch({
            type: 'change-credentials',
            name: 'confirmPassword',
            value: event.currentTarget.value
        });
    }, []);
    const confirmPasswordOnSubmit = React.useCallback(() => {
        termsRef.current.focus();
    }, []);
    const toggleTermsAccepted = React.useCallback(() => {
        dispatch({ type: 'toggle-checkbox', name: 'termsAccepted' });
    }, []);
    const togglePrivacyPolicyAccepted = React.useCallback(() => {
        dispatch({ type: 'toggle-checkbox', name: 'privacyPolicyAccepted' });
    }, []);
    const toggleMarketingAccepted = React.useCallback(() => {
        dispatch({ type: 'toggle-checkbox', name: 'marketingAccepted' });
    }, []);
    const switchFormOnClick = React.useCallback(() => {
        const queryParams = new URLSearchParams([['form', state.form === SIGNUP_FORM ? LOGIN_FORM : SIGNUP_FORM]]);
        window.location = `#/intro?${queryParams.toString()}`;
    }, [state.form]);
    React.useEffect(() => {
        if ([LOGIN_FORM, SIGNUP_FORM].includes(queryParams.get('form'))) {
            dispatch({ type: 'set-form', form: queryParams.get('form') });
        }
    }, [queryParams]);
    React.useEffect(() => {
        if (routeFocused && typeof state.error === 'string' && state.error.length > 0) {
            errorRef.current.scrollIntoView();
        }
    }, [state.error]);
    React.useEffect(() => {
        if (routeFocused) {
            emailRef.current.focus();
        }
    }, [state.form, routeFocused]);
    React.useEffect(() => {
        if (state.form === LOGIN_FORM) {
            loginWithPhone();
            return;
        }

        cancelPhoneLogin();
    }, [cancelPhoneLogin, loginWithPhone, state.form]);
    React.useEffect(() => {
        return () => {
            stopPhoneQrLogin();
        };
    }, [stopPhoneQrLogin]);
    React.useEffect(() => {
        const onCoreEvent = ({ event, args }) => {
            switch (event) {
                case 'UserAuthenticated': {
                    closeLoaderModal();
                    if (routeFocused) {
                        window.location = '#/';
                    }
                    break;
                }
                case 'Error': {
                    if (args && sourceContainsUserAuthenticated(args.source)) {
                        closeLoaderModal();
                        dispatch({ type: 'error', error: getCoreErrorMessage(args) });
                        break;
                    }

                    if (loaderModalOpen) {
                        closeLoaderModal();
                        dispatch({ type: 'error', error: getCoreErrorMessage(args) });
                    }

                    break;
                }
            }
        };
        core.transport.on('CoreEvent', onCoreEvent);
        return () => {
            core.transport.off('CoreEvent', onCoreEvent);
        };
    }, [closeLoaderModal, core.transport, loaderModalOpen, routeFocused]);
    return (
        <div className={styles['intro-container']}>
            <div className={styles['background-container']} />
            <div className={styles['heading-container']}>
                <div className={styles['logo-container']}>
                    <Image className={styles['logo']} src={require('/assets/images/logo.png')} alt={' '} />
                </div>
                <div className={styles['title-container']}>
                    {t('WEBSITE_SLOGAN_NEW_NEW')}
                </div>
                <div className={styles['slogan-container']}>
                    {t('WEBSITE_SLOGAN_ALL')}
                </div>
            </div>
            <div className={styles['content-container']}>
                <div className={styles['form-container']}>
                    <CredentialsTextInput
                        ref={emailRef}
                        className={styles['credentials-text-input']}
                        type={'email'}
                        placeholder={t('EMAIL')}
                        value={state.email}
                        onChange={emailOnChange}
                        onSubmit={emailOnSubmit}
                    />
                    <CredentialsTextInput
                        ref={passwordRef}
                        className={styles['credentials-text-input']}
                        type={'password'}
                        placeholder={t('PASSWORD')}
                        value={state.password}
                        onChange={passwordOnChange}
                        onSubmit={passwordOnSubmit}
                    />
                    {
                        state.form === SIGNUP_FORM ?
                            <React.Fragment>
                                <CredentialsTextInput
                                    ref={confirmPasswordRef}
                                    className={styles['credentials-text-input']}
                                    type={'password'}
                                    placeholder={t('PASSWORD_CONFIRM')}
                                    value={state.confirmPassword}
                                    onChange={confirmPasswordOnChange}
                                    onSubmit={confirmPasswordOnSubmit}
                                />
                                <Checkbox
                                    ref={termsRef}
                                    label={t('READ_AND_AGREE')}
                                    link={t('TOS')}
                                    href={'https://www.stremio.com/tos'}
                                    checked={state.termsAccepted}
                                    onChange={toggleTermsAccepted}
                                />
                                <Checkbox
                                    ref={privacyPolicyRef}
                                    label={t('READ_AND_AGREE')}
                                    link={t('PRIVACY_POLICY')}
                                    href={'https://www.stremio.com/privacy'}
                                    checked={state.privacyPolicyAccepted}
                                    onChange={togglePrivacyPolicyAccepted}
                                />
                                <Checkbox
                                    ref={marketingRef}
                                    label={t('MARKETING_AGREE')}
                                    checked={state.marketingAccepted}
                                    onChange={toggleMarketingAccepted}
                                />
                            </React.Fragment>
                            :
                            <div className={styles['forgot-password-link-container']}>
                                <Button className={styles['forgot-password-link']} onClick={openPasswordRestModal}>{t('FORGOT_PASSWORD')}</Button>
                            </div>
                    }
                    {
                        state.error && state.error.length > 0 ?
                            <div ref={errorRef} className={styles['error-message']}>{state.error}</div>
                            :
                            null
                    }
                    <Button className={classnames(styles['form-button'], styles['submit-button'])} onClick={state.form === SIGNUP_FORM ? signup : loginWithEmail}>
                        <div className={styles['label']}>{state.form === SIGNUP_FORM ? t('SIGN_UP') : t('LOG_IN')}</div>
                    </Button>
                </div>
                <div className={styles['options-container']}>
                    <Button className={classnames(styles['form-button'], styles['facebook-button'])} onClick={loginWithFacebook}>
                        <Icon className={styles['icon']} name={'facebook'} />
                        <div className={styles['label']}>{t('FB_LOGIN')}</div>
                    </Button>
                    <Button className={classnames(styles['form-button'], styles['apple-button'])} onClick={loginWithApple}>
                        <Icon className={styles['icon']} name={'macos'} />
                        <div className={styles['label']}>{t('APPLE_LOGIN')}</div>
                    </Button>
                    {
                        state.form === LOGIN_FORM && phoneLoginSession !== null ?
                            <div className={styles['phone-login-inline-panel']}>
                            <div className={styles['phone-login-inline-title']}>
                                    {t('PHONE_QR_LOGIN_TITLE', { defaultValue: 'Scan with your phone to login' })}
                            </div>
                            <div className={styles['phone-login-inline-subtitle']}>
                                    {t('PHONE_QR_LOGIN_SUBTITLE', { defaultValue: 'Open this QR in your phone browser and sign in with email/password or any provider.' })}
                            </div>
                                <img
                                    className={styles['phone-login-inline-qr-code']}
                                    src={phoneLoginSession.qrCodeUrl}
                                    alt={t('PHONE_QR_LOGIN_ALT', { defaultValue: 'Phone login QR code' })}
                                />
                                <div className={styles['phone-login-inline-url']}>
                                    {phoneLoginSession.loginUrl}
                                </div>
                                <div className={styles['phone-login-inline-actions']}>
                                    <Button className={styles['phone-login-inline-action-button']} onClick={openPhoneLoginLink}>
                                        {t('OPEN_LINK', { defaultValue: 'Open link' })}
                                    </Button>
                                    <Button className={styles['phone-login-inline-action-button']} onClick={refreshPhoneLogin}>
                                        {t('REFRESH', { defaultValue: 'Refresh' })}
                                    </Button>
                                </div>
                            </div>
                            :
                            null
                    }
                    {
                        state.form === SIGNUP_FORM ?
                            <Button className={classnames(styles['form-button'], styles['login-form-button'])} onClick={switchFormOnClick}>
                                <div className={styles['label']}>{t('LOG_IN')}</div>
                            </Button>
                            :
                            null
                    }
                    {
                        state.form === LOGIN_FORM ?
                            <Button className={classnames(styles['form-button'], styles['signup-form-button'])} onClick={switchFormOnClick}>
                                <div className={styles['label']}>{t('SIGN_UP_EMAIL')}</div>
                            </Button>
                            :
                            null
                    }
                    {
                        state.form === SIGNUP_FORM ?
                            <Button className={classnames(styles['form-button'], styles['guest-login-button'])} onClick={loginAsGuest}>
                                <div className={styles['label']}>{t('GUEST_LOGIN')}</div>
                            </Button>
                            :
                            null
                    }
                </div>
            </div>
            {
                passwordRestModalOpen ?
                    <PasswordResetModal email={state.email} onCloseRequest={closePasswordResetModal} />
                    :
                    null
            }
            {
                loaderModalOpen ?
                    <Modal className={styles['loading-modal-container']}>
                        <div className={styles['loader-container']}>
                            <Icon className={styles['icon']} name={'person'} />
                            <div className={styles['label']}>{t('AUTHENTICATING')}</div>
                            <Button className={styles['button']} onClick={cancelAllLoginFlows}>
                                {t('BUTTON_CANCEL')}
                            </Button>
                        </div>
                    </Modal>
                    :
                    null
            }
        </div>
    );
};

Intro.propTypes = {
    queryParams: PropTypes.instanceOf(URLSearchParams)
};

module.exports = Intro;
