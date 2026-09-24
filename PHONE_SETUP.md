# ScrollLock — phone setup guide (end users)

This guide takes you from **getting ScrollLock** to **using it successfully on your phone**.

You do **not** need to build the APK. The project maintainers (or the people who maintain the fork you use) provide a ready-made APK. Your job is the phone + `adb` provisioning steps below.

**Requirements**

- Android **11 or newer** (API 30+)
- A phone you are willing to **factory-reset** (prefer a spare for the first try)
- A Windows, macOS, or Linux computer
- A USB cable that supports data
- [Android Platform Tools](https://developer.android.com/tools/releases/platform-tools) (`adb`)

**Project identity (do not change these when installing)**

```text
Application ID:        dev.scrolllock.dpc
Device admin receiver: dev.scrolllock.dpc/.ScrollLockAdminReceiver
```

---

## What ScrollLock does (short)

After it is Device Owner, you pick apps to lock. Locked apps stay installed but are hidden, suspended, and uninstall-blocked. Unlocking requires your recovery key.

Optional **Hardening** adds extra friction (blocks USB debugging, Settings factory reset, unknown-source APKs, safe boot, extra users). It does **not** block installing normal apps from the Play Store.

This is impulse control, not an unbreakable prison. OEM recovery / wipe can still reset the device.

---

## Step 1 — Get the project and the APK

1. Open the ScrollLock repository (the original or a fork you trust).
2. If you want your own copy of the docs/source: **Fork** the repo on GitHub (optional for install; useful if you track updates).
3. Download the **prebuilt APK** the maintainers published for that repo/fork:
   - often under **Releases** (for example `app-release.apk` or `app-debug.apk`), or
   - whatever download link / file they give you.

Save the APK somewhere easy to find, for example:

```text
Windows:  Downloads\ScrollLock.apk
macOS:    ~/Downloads/ScrollLock.apk
Linux:    ~/Downloads/ScrollLock.apk
```

You only need that file plus `adb`. You do not need Android Studio.

---

## Step 2 — Install ADB on your computer

ADB (Android Debug Bridge) is a small official Google tool. It lets your computer talk to the phone over USB. You need it to install ScrollLock and set Device Owner. You do **not** need Android Studio.

Works on **Windows, macOS, and Linux**.

Official download page: [SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools)

### 2.1 Download the package for your operating system

1. Open the page above.
2. Under **Downloads**, pick the matching link:
   - **Windows** → Download SDK Platform-Tools for Windows  
   - **macOS** → Download SDK Platform-Tools for Mac  
   - **Linux** → Download SDK Platform-Tools for Linux  
3. Accept the terms if asked and save the ZIP.

(The macOS build works on both Intel and Apple silicon Macs.)

Direct ZIP links (always the latest build for that OS):

| OS | ZIP |
|----|-----|
| Windows | https://dl.google.com/android/repository/platform-tools-latest-windows.zip |
| macOS | https://dl.google.com/android/repository/platform-tools-latest-darwin.zip |
| Linux | https://dl.google.com/android/repository/platform-tools-latest-linux.zip |

### 2.2 Unzip somewhere easy to find

Extract the ZIP. Use the folder that contains the `adb` program (on Windows that file is named `adb.exe`).

Examples:

```text
Windows:  C:\platform-tools
macOS:    ~/platform-tools
Linux:    ~/platform-tools
```

If you get a nested `platform-tools` folder inside another folder, open the **inner** one until you see `adb` / `adb.exe`.

**macOS / Linux only:** if `./adb` says “Permission denied”, make it executable once:

```bash
chmod +x adb
```

**Linux only:** if `adb devices` stays empty while the phone is connected, you may need USB access rights (udev). Quick workaround for a one-time setup:

```bash
sudo ./adb kill-server
sudo ./adb start-server
sudo ./adb devices
```

For a lasting fix, install your distro’s Android udev rules package (name varies; often something like `android-udev` / `android-sdk-platform-tools`) or follow Google’s Linux device access notes—then run `adb` as your normal user again.

### 2.3 Open a terminal in that folder

**Windows**

1. Open the folder in File Explorer.
2. Click the address bar, type `powershell`, press Enter.  
   Or: Shift + right‑click empty space → **Open in Terminal** / **Open PowerShell window here**.

**macOS**

1. Open **Terminal** (Spotlight → “Terminal”).
2. Type `cd ` (with a space), drag the `platform-tools` folder into the Terminal window, press Enter.  
   Or: Finder → folder → right‑click → **New Terminal at Folder** (if available).

**Linux**

1. Open your terminal app.
2. `cd` into the extracted folder, for example:

```bash
cd ~/platform-tools
```

Many file managers also offer “Open terminal here” from a right‑click on the folder.

### 2.4 How to run `adb` in this guide

From that `platform-tools` folder:

| OS | Command form |
|----|----------------|
| Windows | `.\adb.exe …` |
| macOS / Linux | `./adb …` |

If you already added Platform-Tools to your system PATH, plain `adb …` works everywhere. The examples below use **`adb`** for readability — swap in `.\adb.exe` or `./adb` if your computer says the command was not found.

### 2.5 Check that ADB works

```text
Windows:        .\adb.exe version
macOS / Linux:  ./adb version
```

You should see a line like `Android Debug Bridge version …`.

### 2.6 Connect the phone and verify it shows up

Do this after the phone is set up with USB debugging on (Step 5) and **without** a Google account yet (Step 4). You can come back to this check then.

1. Use a **data** USB cable (charge‑only cables fail silently).
2. Plug the phone into the computer.
3. On the phone, if you see **Allow USB debugging?** → tap **Allow** (you can check “Always allow from this computer”).
4. If the phone asks for USB mode, choose **File transfer / MTP** when that option exists (not “Charge only”).
5. In the same terminal, run:

```text
Windows:        .\adb.exe devices
macOS / Linux:  ./adb devices
```

**Good result:**

```text
List of devices attached
XXXXXXXXX    device
```

The status must be **`device`**.

**If you see `unauthorized`:** unlock the phone, accept the debugging prompt, run `devices` again.

**If the list is empty:**

- Try another cable or USB port.
- On **Windows**, install your phone maker’s USB driver or the [Google USB Driver](https://developer.android.com/studio/run/win-usb).
- On **macOS**, drivers are usually not needed; try another cable/port and keep the phone unlocked.
- On **Linux**, try the `sudo ./adb` workaround in §2.2 if permission rules are missing.
- Unplug, replug, run `devices` again.

Once the phone shows as `device`, continue with the install and Device Owner steps later in this guide.

---


## Step 3 — Back up, then factory-reset the phone

Device Owner provisioning is reliable only on a **clean** device.

1. Back up anything you care about.
2. Factory-reset the phone:
   - **Settings → System → Reset options → Erase all data (factory reset)**, or  
   - recovery wipe if you prefer.
3. Let it reboot into the setup wizard.

---

## Step 4 — Finish setup **without** a Google account

Goal: reach the home screen with **no Google/user accounts**, ready for USB debugging.

During the wizard:

1. Choose language.
2. Connect to Wi‑Fi if needed (often fine).
3. When asked for a **Google account → Skip / Skip for now**. This is critical.
4. Skip or set a simple PIN/fingerprint for the test.
5. Finish to the launcher / home screen.

Leave Google sign-in for **after** Device Owner is set (Step 9).

If your OEM **forces** a Google account with no skip, the simple `adb` Device Owner path usually fails until accounts are gone. Use a different phone/OEM, or ask the maintainers about QR provisioning.

---

## Step 5 — Enable Developer options and USB debugging

On the phone:

1. **Settings → About phone → tap Build number 7 times**.
2. Back to **Settings → System → Developer options** (wording varies by OEM):
   - turn **USB debugging** ON  
   - if present, turn **Install via USB** ON  

Plug the phone into the computer. Accept **Allow USB debugging** on the phone (you can check “Always allow”).

On the computer (terminal open in your `platform-tools` folder — see Step 2):

```text
adb devices
```

You want something like:

```text
XXXXXXXX    device
```

Not `unauthorized` and not `offline`.

Optional checks (still before installing accounts):

```text
adb shell pm list users
adb shell dumpsys account
```

You want a single user (`0`) and no Google accounts listed.

---

## Step 6 — Install the APK

In your `platform-tools` terminal (Step 2), install the APK. Use your real download path:

```text
Windows:        .\adb.exe install -r "%USERPROFILE%\Downloads\ScrollLock.apk"
macOS / Linux:  ./adb install -r ~/Downloads/ScrollLock.apk
```

If Platform-Tools is already on your PATH, plain `adb install -r …` is fine on any OS.

Expect: `Success`.

Confirm:

```text
adb shell pm list packages dev.scrolllock.dpc
```

Expected:

```text
package:dev.scrolllock.dpc
```

---

## Step 7 — Register ScrollLock as Device Owner

Still in the same `platform-tools` terminal, run **exactly** (use `.\adb.exe` or `./adb` if needed):

```text
adb shell dpm set-device-owner dev.scrolllock.dpc/.ScrollLockAdminReceiver
```


Success looks like:

```text
Success: Device owner set to package dev.scrolllock.dpc
```

(Wording can vary slightly.)

### If this command fails

| Likely cause | What to do |
|--------------|------------|
| Google/account already on the phone | Factory-reset again; **skip** Google; retry before signing in |
| Another Device Owner / MDM already set | Reset; don’t install other device-admin apps first |
| Setup wizard not finished | Complete setup to the home screen, then retry |
| APK missing / wrong package | Reinstall the APK; confirm `pm list packages` |

Do **not** try random destructive `pm` commands. Fix the clean-device precondition, then run `set-device-owner` again.

---

## Step 8 — First launch and recovery key

1. Open **ScrollLock** on the phone.
2. The status banner should say **Device Owner active** (or equivalent).
3. Create a recovery key (**Create recovery key** on the main screen / onboarding card, or in **Settings**).
4. When the key is shown:
   - tap **Copy to Clipboard** if you want, and/or write it down  
   - store it **off the phone** (password manager on another device, paper, trusted person)  
   - then tap **I saved it**
5. You should now see the searchable app list.

**If you lose the recovery key**, unlocking apps and turning off Hardening become very hard. Treat the key like a password.

---

## Step 9 — Verify locking works

1. Lock one **harmless** app (not Phone, Settings, or your only launcher).
2. Confirm: icon gone / won’t open / uninstall blocked.
3. Reboot the phone → still locked.
4. Tap **Unlock…**, enter the recovery key → app returns.

Only after that works, optionally open **Settings → Enable hardening**.

Before Hardening, understand:

- USB debugging / `adb` will stop working until you disable Hardening with the recovery key  
- Settings factory reset and unknown-source APKs get restricted  
- Play Store installs of normal apps still work  

Install any sideloaded tools you need **before** enabling Hardening.

---

## Step 10 — Sign into Google and use the phone normally

After Device Owner is set and your recovery key is saved:

1. Add your Google account.
2. Install / restore apps as usual.
3. In ScrollLock, lock the distracting apps you care about.
4. Daily use of everything else stays normal.

---

## Day-to-day cheat sheet

| Action | How |
|--------|-----|
| Lock an app | ScrollLock → find app → **Lock** |
| Unlock an app | **Unlock…** → recovery key |
| Reapply policies | Settings → **Reapply policies** |
| Turn Hardening on/off | Settings → Enable/Disable hardening (key required to disable) |
| Clear all locks | Settings → Recovery / exit → clear all (key required) |
| Leave Device Owner | Settings → Recovery / exit → relinquish (key required), or factory reset via OEM recovery if needed |

Updating ScrollLock later: install a newer APK from the maintainers with `adb install -r` (or `.\adb.exe` / `./adb`) while Hardening is **off** (disable Hardening first if it is on).

---

## Checklist

```text
[ ] Fork / open the trusted ScrollLock repo (optional but recommended)
[ ] Download the prebuilt APK from maintainers / Releases
[ ] Download Platform-Tools for your OS (Windows, macOS, or Linux)
[ ] Unzip; open a terminal in that folder; adb version works
[ ] Back up the phone
[ ] Factory-reset
[ ] Complete setup and SKIP Google account
[ ] Enable Developer options + USB debugging
[ ] adb devices → device
[ ] adb install -r ScrollLock.apk
[ ] adb shell dpm set-device-owner dev.scrolllock.dpc/.ScrollLockAdminReceiver
[ ] App shows Device Owner active
[ ] Create recovery key and store it OFF the phone
[ ] Lock a test app → reboot → still locked → unlock with key
[ ] Optional: Hardening
[ ] Then sign into Google / restore normal apps
[ ] Lock the real distracting apps
```

---

## Safety notes

- Prefer testing on a **spare phone** before a daily driver.
- Device Owner is powerful; only install an APK from a source you trust.
- ScrollLock is local-only (no cloud MDM server in this project).
- Locked apps can still be opened as **websites** in a browser unless you also lock browsers or use other network controls.
- “Forever” is not absolute: hardware recovery / wipe can remove Device Owner.
