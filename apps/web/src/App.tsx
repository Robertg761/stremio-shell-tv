import { useEffect, useMemo, useState } from "react";

import { createCoreBridge, type CoreBridge } from "@stremio-shell/core-bridge";
import { createDiagnosticsExportCommand, sendHostCommand, subscribeToHostEvents } from "./host-bridge";
import type { HostEvent } from "./types/host-bridge";

export function App() {
  const [bridge, setBridge] = useState<CoreBridge | null>(null);
  const [status, setStatus] = useState("idle");
  const [events, setEvents] = useState<string[]>([]);
  const [hostEvents, setHostEvents] = useState<string[]>([]);

  const canInit = useMemo(() => status !== "initializing" && !bridge, [status, bridge]);

  useEffect(() => {
    return subscribeToHostEvents((event: HostEvent) => {
      setHostEvents((prev) => [JSON.stringify(event), ...prev].slice(0, 20));
    });
  }, []);

  async function initialize() {
    setStatus("initializing");

    try {
      const runtime = await createCoreBridge();
      await runtime.initializeRuntime((event) => {
        const asText = JSON.stringify(event);
        setEvents((prev) => [asText, ...prev].slice(0, 20));
      });
      setBridge(runtime);
      setStatus("ready");
    } catch (error) {
      setStatus("error");
      setEvents((prev) => [String(error), ...prev].slice(0, 20));
    }
  }

  function requestDiagnosticsExport() {
    sendHostCommand(createDiagnosticsExportCommand("manual"));
  }

  return (
    <main className="app">
      <section className="hero">
        <h1>Stremio Shell</h1>
        <p>Android/Google TV-first shell workspace with shared core runtime bridge.</p>
      </section>

      <section className="panel">
        <p>
          Runtime status: <strong>{status}</strong>
        </p>
        <button disabled={!canInit} onClick={initialize}>
          Initialize stremio-core runtime
        </button>
        {bridge ? <p>Bridge loaded and runtime initialized.</p> : <p>Bridge not initialized yet.</p>}
      </section>

      <section className="panel">
        <h2>Event feed</h2>
        {events.length === 0 ? <p>No events yet.</p> : null}
        <ul>
          {events.map((event, index) => (
            <li key={`${index}-${event.slice(0, 20)}`}>{event}</li>
          ))}
        </ul>
      </section>

      <section className="panel">
        <h2>Host bridge</h2>
        <button onClick={requestDiagnosticsExport}>Request diagnostics export</button>
        {hostEvents.length === 0 ? <p>No host events yet.</p> : null}
        <ul>
          {hostEvents.map((event, index) => (
            <li key={`${index}-${event.slice(0, 20)}`}>{event}</li>
          ))}
        </ul>
      </section>
    </main>
  );
}
