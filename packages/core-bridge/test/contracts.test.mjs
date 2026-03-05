import test from "node:test";
import assert from "node:assert/strict";

import {
  CONTRACT_VERSION,
  createEnvelope,
  isContractEnvelope,
  isCoreStateQuery,
  isCoreStateSnapshot,
  isJsonObject,
  isJsonValue
} from "../dist/types.js";

test("createEnvelope uses v1 contract shape", () => {
  const envelope = createEnvelope("runtime.initialize", { source: "web-shell" }, 1234);
  assert.equal(envelope.type, "runtime.initialize");
  assert.equal(envelope.version, CONTRACT_VERSION);
  assert.equal(envelope.timestampMs, 1234);
  assert.deepEqual(envelope.payload, { source: "web-shell" });
});

test("isContractEnvelope accepts valid envelopes and rejects invalid payloads", () => {
  const valid = {
    type: "runtime.error",
    version: CONTRACT_VERSION,
    payload: { code: "x", message: "failure", recoverable: false },
    timestampMs: Date.now()
  };
  assert.equal(isContractEnvelope(valid), true);

  const invalidVersion = { ...valid, version: 99 };
  assert.equal(isContractEnvelope(invalidVersion), false);

  const invalidPayload = { ...valid, payload: "not-json-object" };
  assert.equal(isContractEnvelope(invalidPayload), false);
});

test("query and snapshot validators enforce allowed state contracts", () => {
  const validQuery = { scope: "library", key: "items", params: { limit: 20 } };
  assert.equal(isCoreStateQuery(validQuery), true);

  const invalidQuery = { scope: "not-a-scope" };
  assert.equal(isCoreStateQuery(invalidQuery), false);

  const validSnapshot = {
    scope: "player",
    version: CONTRACT_VERSION,
    updatedAtMs: Date.now(),
    data: { positionMs: 20 }
  };
  assert.equal(isCoreStateSnapshot(validSnapshot), true);
});

test("isJsonObject rejects arrays and non-object values", () => {
  assert.equal(isJsonObject({ a: 1, b: true, c: ["ok"] }), true);
  assert.equal(isJsonObject(["array"]), false);
  assert.equal(isJsonObject("text"), false);
  assert.equal(isJsonObject(null), false);
});

test("isJsonValue rejects non-finite numbers", () => {
  assert.equal(isJsonValue(NaN), false);
  assert.equal(isJsonValue(Infinity), false);
  assert.equal(isJsonValue(-Infinity), false);
  assert.equal(isJsonValue(42), true);
});
