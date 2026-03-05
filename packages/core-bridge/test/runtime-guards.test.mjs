import test, { afterEach } from "node:test";
import assert from "node:assert/strict";

import {
  __resetCoreModuleLoaderForTests,
  __setCoreModuleLoaderForTests,
  CoreBridgeContractError,
  createCoreBridge
} from "../dist/index.js";

afterEach(() => {
  __resetCoreModuleLoaderForTests();
});

test("createCoreBridge fails fast when core exports are missing", async () => {
  __setCoreModuleLoaderForTests(async () => ({
    initialize_runtime: async () => undefined,
    dispatch: () => undefined,
    get_state: () => ({}),
    analytics: () => undefined
    // decode_stream missing on purpose
  }));

  await assert.rejects(
    createCoreBridge(),
    (error) => error instanceof CoreBridgeContractError &&
      error.message.includes('export "decode_stream"'),
    "Expected missing export validation error"
  );
});

test("initializeRuntime rejects invalid envelope-shaped events", async () => {
  __setCoreModuleLoaderForTests(async () => ({
    initialize_runtime: async (emitToUi) => {
      emitToUi({
        type: "runtime.error",
        version: 99,
        payload: { code: "x", message: "bad", recoverable: false },
        timestampMs: Date.now()
      });
    },
    dispatch: () => undefined,
    get_state: () => ({ scope: "player", version: 1, updatedAtMs: Date.now(), data: {} }),
    analytics: () => undefined,
    decode_stream: () => ({})
  }));

  const bridge = await createCoreBridge();
  await assert.rejects(
    bridge.initializeRuntime(() => undefined),
    (error) => error instanceof CoreBridgeContractError &&
      error.message.includes("resembles a contract envelope"),
    "Expected invalid envelope-shaped runtime event to throw"
  );
});

test("initializeRuntime normalizes partial envelope-like events as runtime.raw", async () => {
  __setCoreModuleLoaderForTests(async () => ({
    initialize_runtime: async (emitToUi) => {
      emitToUi({ type: "runtime.error", details: "partial-shape" });
    },
    dispatch: () => undefined,
    get_state: () => ({ scope: "player", version: 1, updatedAtMs: Date.now(), data: {} }),
    analytics: () => undefined,
    decode_stream: () => ({})
  }));

  const bridge = await createCoreBridge();
  let emitted = null;
  await bridge.initializeRuntime((event) => {
    emitted = event;
  });

  assert.equal(emitted?.type, "runtime.raw");
  assert.equal(emitted?.payload?.rawEvent?.type, "runtime.error");
  assert.equal(emitted?.payload?.rawEvent?.details, "partial-shape");
});

test("getState rejects invalid snapshot-like responses", async () => {
  __setCoreModuleLoaderForTests(async () => ({
    initialize_runtime: async () => undefined,
    dispatch: () => undefined,
    get_state: () => ({
      scope: "player",
      version: 99,
      updatedAtMs: Date.now(),
      data: { bad: true }
    }),
    analytics: () => undefined,
    decode_stream: () => ({})
  }));

  const bridge = await createCoreBridge();
  assert.throws(
    () => bridge.getState({ scope: "player" }),
    (error) => error instanceof CoreBridgeContractError &&
      error.message.includes("CoreStateSnapshot"),
    "Expected invalid snapshot-like get_state payload to throw"
  );
});

test("getState normalizes partial snapshot-like responses", async () => {
  __setCoreModuleLoaderForTests(async () => ({
    initialize_runtime: async () => undefined,
    dispatch: () => undefined,
    get_state: () => ({ data: { bad: true } }),
    analytics: () => undefined,
    decode_stream: () => ({})
  }));

  const bridge = await createCoreBridge();
  const state = bridge.getState({ scope: "player" });

  assert.equal(state.scope, "player");
  assert.equal(state.version, 1);
  assert.deepEqual(state.data, { data: { bad: true } });
});
