import test from "node:test";
import assert from "node:assert/strict";
import { isHostEnvelope, createHostEnvelope, HOST_BRIDGE_VERSION } from "../host-bridge.ts";

test("isHostEnvelope accepts valid HostEnvelope objects", () => {
  const validEnvelope = {
    type: "test.event",
    version: HOST_BRIDGE_VERSION,
    payload: { key: "value", nested: { num: 42 } },
    timestampMs: Date.now()
  };

  assert.equal(isHostEnvelope(validEnvelope), true);
});

test("isHostEnvelope rejects non-record values", () => {
  assert.equal(isHostEnvelope(null), false);
  assert.equal(isHostEnvelope(undefined), false);
  assert.equal(isHostEnvelope("string"), false);
  assert.equal(isHostEnvelope(123), false);
  assert.equal(isHostEnvelope(true), false);
});

test("isHostEnvelope rejects missing or invalid type", () => {
  const baseEnvelope = {
    version: HOST_BRIDGE_VERSION,
    payload: {},
    timestampMs: Date.now()
  };

  assert.equal(isHostEnvelope({ ...baseEnvelope }), false);
  assert.equal(isHostEnvelope({ ...baseEnvelope, type: 123 }), false);
  assert.equal(isHostEnvelope({ ...baseEnvelope, type: null }), false);
  assert.equal(isHostEnvelope({ ...baseEnvelope, type: {} }), false);
});

test("isHostEnvelope rejects invalid version", () => {
  const baseEnvelope = {
    type: "test.event",
    payload: {},
    timestampMs: Date.now()
  };

  assert.equal(isHostEnvelope({ ...baseEnvelope }), false);
  assert.equal(isHostEnvelope({ ...baseEnvelope, version: "1" }), false);
  assert.equal(isHostEnvelope({ ...baseEnvelope, version: HOST_BRIDGE_VERSION + 1 }), false);
  assert.equal(isHostEnvelope({ ...baseEnvelope, version: null }), false);
});

test("isHostEnvelope rejects missing or invalid payload", () => {
  const baseEnvelope = {
    type: "test.event",
    version: HOST_BRIDGE_VERSION,
    timestampMs: Date.now()
  };

  assert.equal(isHostEnvelope({ ...baseEnvelope }), false);
  assert.equal(isHostEnvelope({ ...baseEnvelope, payload: null }), false);
  assert.equal(isHostEnvelope({ ...baseEnvelope, payload: "not an object" }), false);
  assert.equal(isHostEnvelope({ ...baseEnvelope, payload: [] }), false);
  assert.equal(isHostEnvelope({ ...baseEnvelope, payload: { fn: () => {} } }), false); // Not a valid JsonObject
});

test("isHostEnvelope rejects missing or invalid timestampMs", () => {
  const baseEnvelope = {
    type: "test.event",
    version: HOST_BRIDGE_VERSION,
    payload: {}
  };

  assert.equal(isHostEnvelope({ ...baseEnvelope }), false);
  assert.equal(isHostEnvelope({ ...baseEnvelope, timestampMs: "12345" }), false);
  assert.equal(isHostEnvelope({ ...baseEnvelope, timestampMs: null }), false);
  assert.equal(isHostEnvelope({ ...baseEnvelope, timestampMs: NaN }), false);
  assert.equal(isHostEnvelope({ ...baseEnvelope, timestampMs: Infinity }), false);
  assert.equal(isHostEnvelope({ ...baseEnvelope, timestampMs: -Infinity }), false);
});

test("createHostEnvelope creates valid envelope with default timestampMs", () => {
  const type = "test.create";
  const payload = { test: true };

  const envelope = createHostEnvelope(type, payload);

  assert.equal(envelope.type, type);
  assert.equal(envelope.version, HOST_BRIDGE_VERSION);
  assert.deepEqual(envelope.payload, payload);
  assert.equal(typeof envelope.timestampMs, "number");
  assert.ok(envelope.timestampMs <= Date.now());
});

test("createHostEnvelope uses provided timestampMs", () => {
  const type = "test.create";
  const payload = { test: true };
  const customTimestamp = 1600000000000;

  const envelope = createHostEnvelope(type, payload, customTimestamp);

  assert.equal(envelope.type, type);
  assert.equal(envelope.version, HOST_BRIDGE_VERSION);
  assert.deepEqual(envelope.payload, payload);
  assert.equal(envelope.timestampMs, customTimestamp);
});
