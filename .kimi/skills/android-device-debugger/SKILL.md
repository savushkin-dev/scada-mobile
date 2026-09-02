---
name: android-device-debugger
description: 'Debug and test the SCADA Mobile web frontend on a physical Android device connected via USB. Use ONLY when the user explicitly asks for testing on the physical Android terminal/phone. Orchestrates Android MCP (system-level control) and Playwright Android MCP (browser-level automation via CDP).'
argument-hint: 'Опиши, что нужно проверить или отладить на физическом Android-терминале'
user-invocable: true
disable-model-invocation: false
---

# Android Device Debugger — Physical USB Testing

## ⚠️ Activation Rule — CRITICAL

**This skill is OPT-IN ONLY.**

By default, test and debug web applications using the **desktop browser** (local Playwright, Vite dev server, standard browser automation).

**Use this skill ONLY when the user explicitly and unambiguously requests testing on the physical Android device.** Valid triggers:
- «Проверь на реальном терминале»
- «Test this on the Android device»
- «Отладь на телефоне»
- «Run this on the physical terminal»
- Any direct mention of the Android device, USB connection, or physical hardware testing

**Do NOT** invoke Android MCP (`mcp__android__*`) or Playwright Android MCP (`mcp__playwright-android__*`) for:
- Routine code fixes
- General UI checks
- Layout adjustments
- Console error investigation
- Any task where the user did not specifically mention the Android device

When in doubt — **ask the user** whether they want desktop or device testing. Do not assume.

---

## Overview

This skill enables debugging and interaction with the SCADA Mobile web frontend running on a **physical Android device** connected via USB. The agent orchestrates two MCP layers and shell commands to create a seamless debugging loop.

### Architecture

```
┌─────────────┐     USB      ┌──────────────────┐
│  Developer  │ ◄──────────► │ Android Device   │
│  Machine    │   ADB        │ (Chrome/WebView) │
│             │              └──────────────────┘
│  AI Agent   │                   ▲
│  (VS Code)  │                   │ CDP (port 9222)
│             │              ┌────┴──────────────┐
│  MCP Stack   │              │ playwright-android│
│             │              │ (web automation)  │
│  ┌─────────┴─┐            └──────────────────┘
│  │ mobile-mcp│  ← System control (tap, swipe, screenshot, hardware keys)
│  └─────────┘
└─────────────┘
```

### Tool layers

| Layer | Exact tool prefix | Scope |
|-------|-------------------|-------|
| System-level device control | `mcp__android__mobile_*` | Screenshots, taps, swipes, hardware buttons, app launch, URL open, device info |
| Web-level browser control | `mcp__playwright-android__browser_*` | DOM, console logs, network, element clicks, form input, page screenshots, JS evaluation |
| Infrastructure | shell (`adb`) | Port forwarding, logcat, tunnel setup |

---

## Prerequisites

- Android Platform Tools (`adb`) installed and in PATH.
- Device connected via USB with **USB Debugging enabled** and authorized.
- Dev server running and accessible (SCADA Mobile frontend normally on `http://localhost:5500`, backend on `http://localhost:8080`).
- Chrome must be installed on the Android device.

---

## Pre-Session Checklist

Before every debugging/testing session, verify connectivity:

```bash
adb devices
```

**If empty or `unauthorized`:**
- Stop immediately.
- Instruct the user to check the USB cable and accept the "Allow USB debugging?" dialog on the device screen.
- Do not proceed until `adb devices` lists the device as `device`.

---

## Happy Path Workflow

### Step 1: Establish Tunnels

Ensure both the app server and Chrome DevTools Protocol (CDP) are reachable from the host.

```bash
# 1a. Make the dev server reachable from the device
adb reverse tcp:5500 tcp:5500
adb reverse tcp:8080 tcp:8080

# 1b. Forward Chrome DevTools port for Playwright CDP connection
adb forward tcp:9222 localabstract:chrome_devtools_remote
```

> **Note:** Neither MCP server exposes port-forwarding tools. These shell steps are mandatory and cannot be skipped.

### Step 2: Launch Browser on Device

Use Android MCP to open the target URL in Chrome:

- `mcp__android__mobile_open_url` with `url: http://localhost:5500`

If Chrome is not responding or you need a fresh instance:

- `mcp__android__mobile_launch_app` with `packageName: com.android.chrome`

### Step 3: Verify CDP Connectivity

Before invoking Playwright, confirm the CDP tunnel is alive:

```bash
# Git Bash / Linux / macOS
curl -s http://localhost:9222/json/version

# PowerShell
Invoke-WebRequest -Uri http://localhost:9222/json/version -UseBasicParsing
```

If this fails:
1. Re-run `adb forward tcp:9222 localabstract:chrome_devtools_remote`.
2. Ensure Chrome is running on the device.
3. Use Android MCP `mcp__android__mobile_launch_app` with `packageName: com.android.chrome`.
4. Wait 2 seconds and retry.

### Step 4: Web Automation & Debugging (Playwright Android)

Connect via Playwright Android MCP:

- `mcp__playwright-android__browser_navigate` with `url: http://localhost:5500`

Common debugging actions:
- `mcp__playwright-android__browser_click` — interact with buttons/links.
- `mcp__playwright-android__browser_type` — fill forms.
- `mcp__playwright-android__browser_fill_form` — fill multiple fields at once.
- `mcp__playwright-android__browser_snapshot` — inspect accessibility tree / DOM structure.
- `mcp__playwright-android__browser_console_messages` — collect JavaScript errors and logs.
- `mcp__playwright-android__browser_screenshot` — capture the rendered page.
- `mcp__playwright-android__browser_evaluate` — run arbitrary JS in the page context.
- `mcp__playwright-android__browser_find` — locate element by text/regex in the snapshot.

### Step 5: System-Level Fallbacks (Android MCP)

When Playwright cannot see or interact with an element (e.g., obscured by OS keyboard, native dialogs, or touch-specific behavior):

- `mcp__android__mobile_take_screenshot` — full-device screenshot.
- `mcp__android__mobile_press_button` — HOME, BACK, ENTER, VOLUME_UP/DOWN.
- `mcp__android__mobile_swipe_on_screen` — scroll / dismiss overlays.
- `mcp__android__mobile_list_elements_on_screen` — raw UI hierarchy if needed.
- `mcp__android__mobile_click_on_screen_at_coordinates` / `mcp__android__mobile_long_press_on_screen_at_coordinates` — native touch fallback.

### Step 6: Artifact Collection

After each interaction cycle, gather evidence:

| Artifact | Source | Tool / Command |
|----------|--------|----------------|
| Page screenshot | Playwright Android | `mcp__playwright-android__browser_screenshot` |
| Full-device screenshot | Android MCP | `mcp__android__mobile_take_screenshot` |
| Console logs | Playwright Android | `mcp__playwright-android__browser_console_messages` |
| Network trace | Playwright Android | `mcp__playwright-android__browser_network_requests` |
| DOM snapshot | Playwright Android | `mcp__playwright-android__browser_snapshot` |
| System logs | Shell | `adb logcat -d -s chromium:D *:S` |

### Step 7: Iterate

1. Analyze collected artifacts.
2. Modify source code based on findings.
3. Re-run the dev server if needed.
4. Return to **Step 2** (or **Step 4** if Chrome is still open).

---

## Tool Selection Matrix

| Task | Correct layer | Why |
|------|--------------|-----|
| Open Chrome / navigate to URL | Android MCP (`mcp__android__mobile_open_url`) | Playwright cannot launch the browser on Android; it only connects to an existing instance via CDP. |
| Forward ports (`adb forward/reverse`) | **Shell** | Neither MCP server exposes port-forwarding tools. |
| Click element by selector | Playwright Android (`mcp__playwright-android__browser_click`) | Precise, auto-waiting, reliable selectors. |
| Type text into input | Playwright Android (`mcp__playwright-android__browser_type`) | Handles focus, events, and validation correctly. |
| Screenshot of **rendered page** | Playwright Android (`mcp__playwright-android__browser_screenshot`) | Clean DOM capture without system UI. |
| Screenshot of **entire device screen** | Android MCP (`mcp__android__mobile_take_screenshot`) | Captures OS keyboard, toasts, dialogs, status bar. |
| Press BACK / HOME / ENTER | Android MCP (`mcp__android__mobile_press_button`) | System hardware keys are outside Playwright's scope. |
| Swipe / scroll when Playwright fails | Android MCP (`mcp__android__mobile_swipe_on_screen`) | Native touch gestures for stubborn mobile UIs. |
| Read JS console / network / DOM | Playwright Android | CDP provides full DevTools capabilities. |
| Read native Android logs | **Shell** (`adb logcat`) | Outside both MCP servers' APIs. |

---

## Edge Cases & Recovery

### Case A: Device Not Detected
**Symptom:** `adb devices` returns empty list or `unauthorized`.
**Action:** Halt. Ask user to verify USB connection and authorize debugging on the device screen.

### Case B: CDP Port Unreachable
**Symptom:** `http://localhost:9222/json/version` fails.
**Action:**
1. Re-run `adb forward tcp:9222 localabstract:chrome_devtools_remote`.
2. If still failing, use Android MCP `mcp__android__mobile_launch_app` with `packageName: com.android.chrome`.
3. Wait 2 seconds, retry.

### Case C: Page Loads but Playwright Cannot Find Element
**Symptom:** `browser_click` or `browser_type` fails with "element not found".
**Action:**
1. Run `mcp__playwright-android__browser_snapshot` to inspect current DOM.
2. Run `mcp__android__mobile_take_screenshot` to verify visual state (element may be hidden behind OS keyboard or overlay).
3. If off-screen, use `mcp__android__mobile_swipe_on_screen` to scroll.
4. If inside a frame/WebView, verify CDP is attached to the correct target.

### Case D: Console Empty but Bug Reproduces
**Symptom:** No JS errors in `mcp__playwright-android__browser_console_messages`, yet behavior is wrong.
**Action:**
1. Collect `adb logcat -d | grep chromium` (Git Bash) or `adb logcat -d | findstr chromium` (PowerShell) for low-level browser errors.
2. Check `mcp__android__mobile_take_screenshot` for visual anomalies.
3. Verify network requests via Playwright Android `mcp__playwright-android__browser_network_requests`.

### Case E: Application Server Unreachable from Device
**Symptom:** Chrome shows "This site can't be reached".
**Action:**
1. Confirm dev server is running on the host (Vite on `http://localhost:5500` for SCADA Mobile).
2. Re-run `adb reverse tcp:5500 tcp:5500` (and `adb reverse tcp:8080 tcp:8080` for backend if needed).
3. Verify the correct port is used.

---

## Constraints & Reminders

- **OPT-IN ONLY.** Never use this skill unless the user explicitly asked for physical device testing. Default to desktop browser automation.
- **Never assume ports are forwarded.** Always run `adb reverse` / `adb forward` at the start of a session.
- **Never assume Chrome is running.** Use Android MCP to open the URL before invoking Playwright.
- **If Playwright fails, fall back to Android MCP + shell.** Do not get stuck retrying the same broken selector.
- **Collect screenshots before and after interactions.** Visual evidence is the fastest debugging signal.
- **Respect the boundary:**
  - Android MCP (`mcp__android__*`) = system/device layer.
  - Playwright Android MCP (`mcp__playwright-android__*`) = web page layer.
  - Shell (`adb`) = infrastructure layer.
- Do not allocate remote cloud devices through this skill; it is specifically for a USB-connected physical device.
