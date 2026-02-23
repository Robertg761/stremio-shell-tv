# Android TV QA Matrix (Balanced)

## Device matrix

1. Android TV Emulator (Google APIs, API 34)
2. Google TV physical device (Chromecast with Google TV class)
3. Non-Google OEM Android TV / streamer (different remote profile)

## Remote profiles

1. Standard D-pad remote (arrows, center, back, menu/info)
2. Remote variant with dedicated media keys (play/pause/ff/rew)

## Smoke checklist

1. Cold launch reaches usable UI (no black screen).
2. Initial focus appears on primary route control.
3. D-pad reaches all primary interactive controls on:
   - Board/Home
   - Discover
   - Meta Details / Streams
   - Settings
   - Search
4. Back policy follows: modal close -> route back -> app exit.
5. Deep link opens expected route and focus is recovered.
6. Playback starts and remote controls work:
   - play/pause
   - seek forward/back
   - subtitles/audio menus
   - playback speed
   - video mode
   - back from player
7. Native fallback route behavior does not loop.
8. Diagnostics export contains:
   - recent host events
   - back decision records
   - focus recovery logs

## Signoff template

- Build SHA:
- Device:
- Remote profile:
- Result: PASS / FAIL
- Defects:
- Tester:
- Date:
