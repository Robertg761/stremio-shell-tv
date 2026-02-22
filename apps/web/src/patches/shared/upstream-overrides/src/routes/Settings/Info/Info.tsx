import React, { useCallback, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useServices } from 'stremio/services';
import { usePlatform, useToast } from 'stremio/common';
import { Button } from 'stremio/components';
import { Option, Section } from '../components';
import requestHostUpdateCheck from '../shared/requestHostUpdateCheck';
import styles from './Info.less';

type Props = {
    streamingServer: StreamingServer,
};

const Info = ({ streamingServer }: Props) => {
    const { shell } = useServices();
    const { t } = useTranslation();
    const toast = useToast();
    const platform = usePlatform();

    const settings = useMemo(() => (
        streamingServer?.settings?.type === 'Ready' ?
            streamingServer.settings.content as StreamingServerSettings : null
    ), [streamingServer?.settings]);

    const onCheckForUpdates = useCallback(() => {
        requestHostUpdateCheck({ toast, t });
    }, [toast, t]);

    return (
        <Section className={styles['info']}>
            <Option label={t('SETTINGS_APP_VERSION')}>
                <div className={styles['label']}>
                    {process.env.VERSION}
                </div>
            </Option>
            <Option label={t('SETTINGS_BUILD_VERSION')}>
                <div className={styles['label']}>
                    {process.env.COMMIT_HASH}
                </div>
            </Option>
            {
                settings?.serverVersion &&
                    <Option label={t('SETTINGS_SERVER_VERSION')}>
                        <div className={styles['label']}>
                            {settings.serverVersion}
                        </div>
                    </Option>
            }
            {
                typeof shell?.transport?.props?.shellVersion === 'string' &&
                    <Option label={t('SETTINGS_SHELL_VERSION')}>
                        <div className={styles['label']}>
                            {shell.transport.props.shellVersion}
                        </div>
                    </Option>
            }
            {
                platform.isMobile &&
                    <Option label={'SETTINGS_CHECK_FOR_UPDATES'}>
                        <Button
                            className={'button'}
                            title={t('SETTINGS_CHECK_FOR_UPDATES', { defaultValue: 'Check for updates' })}
                            tabIndex={-1}
                            onClick={onCheckForUpdates}
                        >
                            {t('SETTINGS_CHECK_FOR_UPDATES', { defaultValue: 'Check for updates' })}
                        </Button>
                    </Option>
            }
        </Section>
    );
};

export default Info;
