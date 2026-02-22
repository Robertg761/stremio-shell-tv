import React, { createContext, useCallback, useContext, useEffect, useRef } from 'react';
import shortcuts from './shortcuts.json';
import { usePlatform } from '../Platform';

const SHORTCUTS = shortcuts.map(({ shortcuts }) => shortcuts).flat();

export type ShortcutName = string;
export type ShortcutListener = (combo: number) => void;

interface ShortcutsContext {
    grouped: ShortcutGroup[],
    on: (name: ShortcutName, listener: ShortcutListener) => void,
    off: (name: ShortcutName, listener: ShortcutListener) => void,
}

const ShortcutsContext = createContext<ShortcutsContext>({} as ShortcutsContext);

type Props = {
    children: JSX.Element,
    onShortcut: (name: ShortcutName) => void,
};

type Direction = 'up' | 'down' | 'left' | 'right';

const ARROW_KEY_TO_DIRECTION: Record<string, Direction> = {
    ArrowUp: 'up',
    ArrowDown: 'down',
    ArrowLeft: 'left',
    ArrowRight: 'right'
};

const isEditableTarget = (target: EventTarget | null) => {
    if (!(target instanceof HTMLElement)) {
        return false;
    }

    const tagName = target.tagName.toUpperCase();
    return tagName === 'INPUT' || tagName === 'TEXTAREA' || target.isContentEditable;
};

const isVisibleElement = (element: HTMLElement) => {
    if (!element.isConnected || element.hidden || element.getAttribute('aria-hidden') === 'true') {
        return false;
    }

    const style = window.getComputedStyle(element);
    if (style.visibility === 'hidden' || style.display === 'none' || style.pointerEvents === 'none') {
        return false;
    }

    const rect = element.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
};

const focusFirstInteractiveElement = () => {
    const firstFocusable = document.querySelector<HTMLElement>(
        [
            'a[href]',
            'button',
            '[role="button"]',
            '[tabindex]',
            '.button-container',
            '.nav-tab-button-container',
            '.meta-item-container',
            'input',
            'textarea',
            'select'
        ].join(', ')
    );

    if (firstFocusable && isVisibleElement(firstFocusable)) {
        firstFocusable.focus();
    }
};

const hasRealFocusedElement = () => {
    const activeElement = document.activeElement;
    return activeElement instanceof HTMLElement && activeElement !== document.body && activeElement !== document.documentElement;
};

const isPlayerRoute = () => {
    return window.location.hash.startsWith('#/player');
};

const preventScrollGestures = (event: Event) => {
    event.preventDefault();
};

const ShortcutsProvider = ({ children, onShortcut }: Props) => {
    const platform = usePlatform();
    const listeners = useRef<Map<ShortcutName, Set<ShortcutListener>>>(new Map());
    const isTvRuntime = platform.name === 'android' && !platform.isMobile;
    const hasTvNavigator = typeof window.navigate === 'function';
    const shouldHandleArrowNavigation = isTvRuntime || hasTvNavigator;

    const onKeyDown = useCallback((event: KeyboardEvent) => {
        const direction = ARROW_KEY_TO_DIRECTION[event.key];
        const editableTarget = isEditableTarget(event.target);

        if (direction && !editableTarget && shouldHandleArrowNavigation) {
            event.preventDefault();

            if (!isPlayerRoute()) {
                if (!hasRealFocusedElement()) {
                    focusFirstInteractiveElement();
                }

                if (typeof window.navigate === 'function') {
                    window.navigate(direction);
                }
            }
        }

        const { ctrlKey, shiftKey, code, key } = event;
        SHORTCUTS.forEach(({ name, combos }) => combos.forEach((keys) => {
            const modifers = (keys.includes('Ctrl') ? ctrlKey : true)
                && (keys.includes('Shift') ? shiftKey : true);

            if (modifers && (keys.includes(code) || keys.includes(key.toUpperCase()))) {
                const combo = combos.indexOf(keys);
                listeners.current.get(name)?.forEach((listener) => listener(combo));

                onShortcut(name as ShortcutName);
            }
        }));
    }, [onShortcut, shouldHandleArrowNavigation]);

    const on = (name: ShortcutName, listener: ShortcutListener) => {
        !listeners.current.has(name) && listeners.current.set(name, new Set());
        listeners.current.get(name)!.add(listener);
    };

    const off = (name: ShortcutName, listener: ShortcutListener) => {
        listeners.current.get(name)?.delete(listener);
    };

    useEffect(() => {
        const listenerOptions: AddEventListenerOptions = { capture: true };
        document.addEventListener('keydown', onKeyDown, listenerOptions);
        return () => document.removeEventListener('keydown', onKeyDown, listenerOptions);
    }, [onKeyDown]);

    useEffect(() => {
        if (!isTvRuntime) {
            return;
        }

        const wheelOptions: AddEventListenerOptions = { capture: true, passive: false };
        const touchOptions: AddEventListenerOptions = { capture: true, passive: false };

        document.addEventListener('wheel', preventScrollGestures, wheelOptions);
        document.addEventListener('touchmove', preventScrollGestures, touchOptions);

        return () => {
            document.removeEventListener('wheel', preventScrollGestures, wheelOptions);
            document.removeEventListener('touchmove', preventScrollGestures, touchOptions);
        };
    }, [isTvRuntime]);

    return (
        <ShortcutsContext.Provider value={{ grouped: shortcuts, on, off }}>
            {children}
        </ShortcutsContext.Provider>
    );
};

const useShortcuts = () => {
    return useContext(ShortcutsContext);
};

export {
    ShortcutsProvider,
    useShortcuts,
};
