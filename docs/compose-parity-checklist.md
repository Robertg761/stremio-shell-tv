# Compose TV Parity Checklist

Use this checklist together with `/Users/robert/Documents/Projects/stremio-shell-tv/docs/tv-qa-matrix.md` for release signoff.

## Routes

- [ ] Intro
- [ ] Board
- [ ] Discover
- [ ] Search
- [ ] Meta Details
- [ ] Streams
- [ ] Player
- [ ] Library
- [ ] Addons
- [ ] Calendar
- [ ] Settings
- [ ] NotFound fallback

## Runtime/Core

- [x] Runtime initializes without WebView
- [x] Envelope dispatch works (`runtime.initialize`, `auth.*`, `library.sync`, `playback.*`)
- [x] State query works for `session`, `library`, `player`
- [ ] Runtime event stream is stable under soak

## TV UX

- [ ] Focus visible and deterministic on all primary routes
- [ ] D-pad traversal complete for all controls
- [ ] Back policy: modal close -> route back -> app exit
- [ ] Media keys work in player

## Diagnostics and Ops

- [x] Diagnostics export contains runtime/lifecycle/network/back traces
- [x] Deep links route correctly (`stremio-shell://`)
- [x] Update checks/download/install flow matches host behavior

## Evidence

- Build SHA:
- Device(s):
- Tester:
- Date:
- Notes:
