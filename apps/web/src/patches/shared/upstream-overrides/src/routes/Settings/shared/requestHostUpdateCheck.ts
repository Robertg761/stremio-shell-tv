type HostBridgeWindow = Window & {
    stremioHost?: {
        sendCommand?: (commandJson: string) => void,
    },
};

type ToastLike = {
    show: (options: {
        type: 'success' | 'error',
        title: string,
        timeout: number,
    }) => void,
};

type TranslateLike = (key: string, options?: { defaultValue?: string }) => string;

type RequestHostUpdateCheckParams = {
    toast: ToastLike,
    t: TranslateLike,
};

const requestHostUpdateCheck = ({ toast, t }: RequestHostUpdateCheckParams) => {
    const hostBridge = (window as HostBridgeWindow).stremioHost;
    if (!hostBridge || typeof hostBridge.sendCommand !== 'function') {
        toast.show({
            type: 'error',
            title: t('SETTINGS_CHECK_FOR_UPDATES_UNAVAILABLE', { defaultValue: 'Update checks are not available on this platform.' }),
            timeout: 5000
        });
        return;
    }

    hostBridge.sendCommand(JSON.stringify({
        type: 'updates.check',
        version: 1,
        payload: { reason: 'manual' },
        timestampMs: Date.now()
    }));

    toast.show({
        type: 'success',
        title: t('SETTINGS_CHECK_FOR_UPDATES_STARTED', { defaultValue: 'Checking for updates...' }),
        timeout: 5000
    });
};

export default requestHostUpdateCheck;
