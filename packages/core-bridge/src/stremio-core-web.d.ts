declare module "@stremio/stremio-core-web" {
  export function initialize_runtime(emitToUi: (event: unknown) => void): Promise<void>;
  export function dispatch(action: unknown, field: unknown, locationHash: unknown): void;
  export function get_state(field: unknown): unknown;
  export function analytics(event: unknown, locationHash: unknown): void;
  export function decode_stream(stream: unknown): unknown;
}
