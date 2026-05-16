# 📺 TV Remote Overlay

An Android app that puts a **floating D-pad remote** on top of any app on your phone — letting you navigate Android TV apps that only support remote control input.

---

## How it works

| Component | Role |
|---|---|
| **Floating Overlay** (`WindowManager`) | Renders the D-pad UI on top of all apps |
| **Foreground Service** | Keeps the overlay alive while you use other apps |
| **Accessibility Service** | Intercepts D-pad taps and injects real Android focus traversal events — identical to what a physical TV remote sends |

No root required. Works with any standard Android TV / Leanback app.

---

## Build via GitHub Actions

1. **Fork or push** this repo to your GitHub account
2. Go to **Actions** tab → select **"Build Debug APK"** → click **"Run workflow"**
3. Wait ~3–4 minutes for the build to finish
4. Download the APK from the **Artifacts** section of the completed run
5. Enable **"Install from unknown sources"** on your phone and install it

---

## Setup (one-time, on your phone)

Once installed, open the app and complete **3 steps**:

### Step 1 — Overlay Permission
Tap **Grant Permission** → find the app → toggle **"Allow display over other apps"** ON.

### Step 2 — Accessibility Service
Tap **Open Accessibility Settings** → scroll to find **"TV Remote Navigation Service"** under "Downloaded apps" → tap it → toggle ON.  
> Android will warn you this service can read screen content — this is required for focus navigation to work. The app never reads or transmits any data.

### Step 3 — Show the Remote
Tap **Show Remote**. The app minimises itself and the floating D-pad appears on top of whatever app is open.

---

## Remote buttons

| Button | Action |
|---|---|
| ▲ ▼ ◄ ► | Move focus between elements |
| **OK** | Click / select the focused element |
| ⌂ | Home screen |
| ↩ | Back |
| ☰ | Recent apps |
| ✕ | Close overlay |

**Drag** the title bar to reposition the remote anywhere on screen.

---

## Permissions explained

| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Required to draw the floating window |
| `FOREGROUND_SERVICE` | Required to keep the overlay alive when app is in background |
| `POST_NOTIFICATIONS` | Required (Android 13+) for the persistent service notification |
| Accessibility Service | Sends D-pad key events to the focused app |

---

## Project structure

```
app/src/main/java/com/tvremote/overlay/
├── MainActivity.kt               — Setup UI & permission checks
├── OverlayService.kt             — Floating D-pad window (foreground service)
└── RemoteAccessibilityService.kt — Focus traversal & action engine

.github/workflows/
└── build.yml                     — GitHub Actions CI (builds debug APK)
```

---

## Troubleshooting

**Buttons do nothing**  
→ Make sure the Accessibility Service is still enabled (it can get disabled after restarting the phone). Open the app and check Step 2.

**Overlay not showing after "Show Remote"**  
→ Check that Overlay Permission is still granted in Settings → Apps → TV Remote Overlay → Display over other apps.

**Focus jumps unexpectedly**  
→ Some TV apps use custom focus logic. Try pressing a D-pad arrow first to anchor focus, then navigate normally.

**Build fails in GitHub Actions**  
→ Check the Actions log for errors. Most common issue is SDK license — the workflow already auto-accepts them, but you can re-run the workflow if it fails transiently.
