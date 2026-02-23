# Fix: Google Streamer Remote Navigation

## Problem

Pressing any button on the Google Streamer remote had no visible effect — DPAD arrows, center/select, and Enter all did nothing.

## Root Cause

A **false-positive overlay detection** in `Shortcuts.tsx` caused all focus navigation to be scoped to a tiny, empty container instead of the full page.

`OVERLAY_SELECTORS` contained `[class*="popup"]`, which matched a CSS-module-hashed class name on a regular navigation menu button:

```
nav-menu-popup-label-XmUBo
```

When `getTopOverlayElement()` found this 49×49px button, it treated it as a modal overlay. `navigateTvDirection()` then called `moveFocusByDirection(this_button, ...)` instead of `moveFocusByDirection(document, ...)`. Since the button had zero focusable children, spatial navigation always returned `candidates len= 0` and `moved=false`.

Meanwhile, the full page had **73 links** and **61 tabindex elements** that were never searched.

## Fix

### 1. Remove overly-broad overlay selectors (`Shortcuts.tsx`)

The `[class*=...]` substring selectors are incompatible with CSS modules, which append random hashes to class names. Only semantic ARIA attributes reliably identify real overlays.

```diff
 const OVERLAY_SELECTORS = [
     '[role="dialog"]',
     '[aria-modal="true"]',
-    '[class*="modal-container"]',
-    '[class*="modal-dialog-container"]',
-    '[class*="popup"]',
-    '[class*="context-menu-container"]',
-    '[class*="side-drawer"]',
 ];
```

### 2. Dual-path key delivery (`MainActivity.kt` + `Shortcuts.tsx`)

`forwardTvKeyEventToWeb()` now returns `false` instead of `true`, allowing native key events to also reach the WebView as DOM `keydown` events. This provides a fallback if the host-event listener isn't registered yet (e.g., during app startup).

A 150ms deduplication timestamp in `Shortcuts.tsx` prevents double-processing when both paths are active.

## Files Changed

| File | Change |
|---|---|
| `apps/web/.../Shortcuts/Shortcuts.tsx` | Removed false-positive overlay selectors, added key dedup |
| `apps/android-tv-host/.../MainActivity.kt` | Native key event fallback (`return false`) |

## How It Was Debugged

1. **Logcat** showed the full key pipeline was working: keys received → forwarded → JS handler fired
2. But `moveFocusByDirection` logged `candidates len= 0` — zero focusable elements found
3. Injected diagnostic JS to count DOM elements — **73 `a[href]`** and **61 `[tabindex]`** elements existed and were visible
4. Injected overlay diagnostic — found `[class*="popup"]` matching `nav-menu-popup-label-XmUBo` (a 49×49px nav button falsely treated as a modal overlay)
5. Removed the broad selectors → `candidates len= 43`, `moved=true` ✅
