import { createHostEnvelope, isHostEnvelope, type HostCommand, type HostEvent } from "./types/host-bridge";

type HostEventListener = (event: HostEvent) => void;

declare global {
  interface Window {
    stremioHost?: {
      sendCommand: (command: HostCommand) => void;
    };
  }
}

const HOST_EVENT_NAME = "stremio:host-event";
const HOST_COMMAND_NAME = "stremio:host-command";

export function subscribeToHostEvents(listener: HostEventListener): () => void {
  const handler = (event: Event) => {
    const customEvent = event as CustomEvent<unknown>;
    if (!isHostEnvelope(customEvent.detail)) {
      return;
    }
    listener(customEvent.detail as HostEvent);
  };

  window.addEventListener(HOST_EVENT_NAME, handler as EventListener);
  return () => window.removeEventListener(HOST_EVENT_NAME, handler as EventListener);
}

export function sendHostCommand(command: HostCommand): void {
  if (window.stremioHost) {
    window.stremioHost.sendCommand(command);
    return;
  }

  // Fallback for browser-only development before native host wiring.
  window.dispatchEvent(new CustomEvent(HOST_COMMAND_NAME, { detail: command }));
}

export function createDiagnosticsExportCommand(reason: "manual" | "error" | "support" = "manual"): HostCommand {
  return createHostEnvelope("diagnostics.export", { reason });
}
