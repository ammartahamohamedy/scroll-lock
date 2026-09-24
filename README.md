# ScrollLock DPC

ScrollLock is a local-only Android Device Policy Controller (DPC) for a personally controlled Android phone. Its purpose is simple: selected distracting apps stay installed, but Android makes them effectively unavailable.

**End users (install on a phone, no building):** see **[PHONE_SETUP.md](PHONE_SETUP.md)** — from getting the APK to Device Owner, recovery key, and daily use.

For every locked package ScrollLock applies:

1. `DevicePolicyManager.setUninstallBlocked(..., true)`
2. `DevicePolicyManager.setPackagesSuspended(..., true)`
3. `DevicePolicyManager.setApplicationHidden(..., true)`
4. `DevicePolicyManager.setUserControlDisabledPackages(...)`
5. local persistence plus policy reapplication after boot/package replacement

Once a recovery key exists, ScrollLock also puts **its own package** in `setUserControlDisabledPackages`, preventing the normal Settings UI from force-stopping it or clearing its local recovery/package state.

Optional **Hardening** additionally applies these Device Owner user restrictions:

- `DISALLOW_DEBUGGING_FEATURES`
- `DISALLOW_FACTORY_RESET` (Settings reset path)
- `DISALLOW_INSTALL_UNKNOWN_SOURCES`
- `DISALLOW_SAFE_BOOT`
- `DISALLOW_ADD_USER`
- `DISALLOW_USER_SWITCH`
- `DISALLOW_ADD_PRIVATE_PROFILE` on Android 15+ where applicable

It deliberately does **not** enable `DISALLOW_INSTALL_APPS`, because that would freeze all app installation rather than only the apps you chose to lock.

---

## Requirements

- Android 11 / API 30 or newer.
- Windows, macOS, or Linux computer.
- Android Studio (current project uses Android Gradle Plugin 9.4.0).
- JDK 17+ (Android Studio includes a suitable JBR/JDK).
- Android SDK Platform 36 and Build Tools 36.x.
- Android SDK Platform Tools (`adb`).
- A device/OEM that supports standard Android Device Owner provisioning.

Project identity:

```text
Application ID:       dev.scrolllock.dpc
Device admin receiver: dev.scrolllock.dpc/.ScrollLockAdminReceiver
minSdk:               30
compileSdk:           36
targetSdk:            36
AGP:                  9.4.0
Gradle:               9.6.0+
Java/JDK:             17+
```

**Do not casually change the application ID, admin receiver, or release signing key after provisioning.** Normal Device Owner updates must retain the same identity and signing key.

---

# Part A — Build from source

## A1. Install Android Studio

Install Android Studio and, from its SDK Manager, make sure the following are present:

- Android SDK Platform 36
- Android SDK Build-Tools 36.x
- Android SDK Platform-Tools

The source has no third-party runtime libraries and does not need Compose or AndroidX UI components.

## A2. Generate the Gradle Wrapper once

The source bundle contains a bootstrap script rather than a copied binary wrapper JAR. The script downloads the official Gradle 9.6.0 binary distribution, verifies its SHA-256 checksum, and asks Gradle itself to generate the standard wrapper files.

### Windows PowerShell

From the project root:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\bootstrap-gradle.ps1
.\gradlew.bat --version
```

### macOS / Linux

```bash
./bootstrap-gradle.sh
./gradlew --version
```

After this you will have the normal wrapper files:

```text
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
```

You only need to bootstrap once.

## A3. Open in Android Studio

Open the **ScrollLockDPC** folder as an existing project and let Gradle sync.

If Android Studio asks for a Gradle JDK, select its bundled JDK/JBR 17 or newer.

## A4. Build a debug APK

Windows:

```powershell
.\gradlew.bat clean assembleDebug
```

macOS/Linux:

```bash
./gradlew clean assembleDebug
```

Expected output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Do not use the debug build as your permanent installation if you care about long-term updates. Create your permanent signing key first.

---

# Part B — Create the permanent release APK

## B1. Generate one signing key

Run `keytool` (bundled with a JDK):

### Windows PowerShell

```powershell
keytool -genkeypair -v `
  -keystore scrolllock-release.jks `
  -alias scrolllock `
  -keyalg RSA `
  -keysize 4096 `
  -validity 10000
```

### macOS/Linux

```bash
keytool -genkeypair -v \
  -keystore scrolllock-release.jks \
  -alias scrolllock \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Back up `scrolllock-release.jks` somewhere offline. Losing the signing key means you cannot produce a normal same-package update for the installed DPC.

## B2. Configure release signing

Copy:

```text
keystore.properties.example
```

to:

```text
keystore.properties
```

Example:

```properties
storeFile=C:/Users/you/secure/scrolllock-release.jks
storePassword=YOUR_KEYSTORE_PASSWORD
keyAlias=scrolllock
keyPassword=YOUR_KEY_PASSWORD
```

`keystore.properties`, `*.jks`, and `*.keystore` are Git-ignored.

## B3. Build the signed release APK

Windows:

```powershell
.\gradlew.bat clean assembleRelease
```

macOS/Linux:

```bash
./gradlew clean assembleRelease
```

With `keystore.properties` configured, expected output is:

```text
app/build/outputs/apk/release/app-release.apk
```

You can also use Android Studio's **Build → Generate Signed App Bundle / APK → APK** workflow if you prefer a GUI.

---

# Part C — Test before touching your real phone

A Device Owner is a powerful Android management role. First use an emulator or a disposable/test phone.

Minimum test sequence:

1. Provision ScrollLock as Device Owner.
2. Launch ScrollLock and create a recovery key.
3. Lock one harmless user app.
4. Verify its launcher icon disappears.
5. Verify it cannot normally launch/notify.
6. Verify its uninstall/force-stop/clear-data controls are unavailable as expected.
7. Unlock it using the recovery key.
8. Lock it again, reboot, and verify the policy remains effective.
9. Only after that test **Hardening**.

Do not use a critical package as your test app.

---

# Part D — Provision your own phone with ADB

For a one-person/personal installation, this is the simplest route.

## D1. Back up the phone

Back up anything you care about before beginning. Device Owner provisioning is designed for fully managed devices and is strongest on a fresh device.

## D2. Start from a clean device

The most reliable route is a factory-reset phone with:

- no Google/user accounts yet;
- no work profile;
- no secondary users;
- no other Device Owner.

OEM behavior varies. Android's official development flow also requires that no other users/work profile or accounts exist before `dpm set-device-owner`.

## D3. Enable ADB for provisioning

During the clean setup, get to the point where your OEM allows Developer options / USB debugging without adding personal accounts.

On Windows, install your OEM USB driver if necessary.

Connect by USB and run:

```powershell
adb devices
```

Accept the RSA debugging prompt on the phone. The output should show the serial with state `device`, not `unauthorized`.

Useful check:

```powershell
adb shell pm list users
```

## D4. Install the signed APK

From the project root:

```powershell
adb install -r .\app\build\outputs\apk\release\app-release.apk
```

Check:

```powershell
adb shell pm list packages dev.scrolllock.dpc
```

Expected:

```text
package:dev.scrolllock.dpc
```

## D5. Set ScrollLock as Device Owner

Run exactly:

```powershell
adb shell dpm set-device-owner dev.scrolllock.dpc/.ScrollLockAdminReceiver
```

Launch ScrollLock. The banner should read:

```text
Status: DEVICE OWNER active. Android policy control is available.
```

If `dpm set-device-owner` rejects the device because it is already provisioned, has accounts/profiles/users, or already has an owner, do not try random destructive shell commands. Remove the stated blocker or use the factory-reset/QR provisioning route below.

---

# Part E — First real use

## E1. Create the recovery key

Tap **Create recovery key**.

ScrollLock generates a random 160-bit key, shows it in grouped hexadecimal form, and stores only its SHA-256 hash locally.

Store the full key **outside the phone**. A good self-control setup is to put it somewhere deliberately inconvenient or let a trusted person hold it.

The key is needed for:

- unlocking an app;
- rotating the recovery key;
- disabling Hardening;
- clearing every ScrollLock policy;
- using the explicit Device Owner recovery exit.

As soon as a recovery key exists, ScrollLock reapplies its policy and includes its own package in the user-control-disabled set.

## E2. Lock one harmless app first

Find an expendable user app and tap **Lock**.

ScrollLock performs, in order:

```text
block uninstall
suspend package
hide package
persist package name
disable normal user control for the locked package + ScrollLock
```

It then checks whether Android actually reports the package hidden and suspended. If Android refuses—common for protected system components—the code attempts to roll the operation back rather than leaving a partial lock.

## E3. Lock the distracting apps

Once the test is successful, search for each target application and tap **Lock**.

By default ScrollLock lists normal user-installed apps. **Show system apps** exposes system packages as well, but this is intentionally marked advanced.

ScrollLock prevents locking several obvious critical packages, including:

- itself;
- active launcher/Home packages;
- Android Settings;
- System UI;
- Permission Controller;
- package installer;
- default dialer;
- active device administrators.

OEMs can have additional critical packages with unfamiliar names. Do not lock an unknown system package merely because it appears in the list.

---

# Part F — Hardening

Hardening is optional and should come **after** you have verified that locking/unlocking and reboot persistence work.

When enabled it blocks:

```text
USB / debugging features
factory reset from Settings
unknown-source installation toggle
safe boot
creating users
switching users
private-profile creation where applicable
```

The most important operational consequence is:

> **ADB will disconnect when `DISALLOW_DEBUGGING_FEATURES` takes effect.**

Before enabling Hardening confirm:

- your recovery key works;
- the key exists off-device;
- you have tested lock + unlock;
- you have reboot-tested the DPC;
- you have a current backup.

Then tap **Enable hardening**.

Hardening does not block Play Store installation of every possible alternative app. ScrollLock intentionally does not set `DISALLOW_INSTALL_APPS`; doing so would be a much broader “no new apps at all” policy.

Locked packages stay uninstall-blocked. If one is removed somehow and reinstalled (APK or store), ScrollLock re-locks that package on install/replace.

---

# Part G — Daily operation

To unlock a package:

1. open ScrollLock;
2. find the `[LOCKED]` app;
3. tap **Unlock…**;
4. enter the recovery key.

To reassert all policies manually, tap **Reapply**.

To turn off Hardening, tap **Disable hardening…** and provide the recovery key.

A reboot receiver and a package-added/replaced receiver also ask the policy controller to reassert saved state. The Android policy state itself is system-managed; this is an additional consistency measure.

---

# Part H — Recovery and complete removal

## H1. Clear ScrollLock policies but keep it Device Owner

Use:

```text
Recovery / exit → Clear all app locks and hardening
```

After recovery-key verification, ScrollLock tries to:

- unhide every saved locked package;
- unsuspend it;
- remove its uninstall block;
- restore normal user control;
- clear every Hardening restriction it added.

## H2. Relinquish Device Owner

Use:

```text
Recovery / exit → Relinquish Device Owner and exit ScrollLock
```

The app first clears its own package policies and Hardening, removes its own user-control protection, then invokes Android's legacy `clearDeviceOwnerApp()` recovery path.

Android has deprecated `clearDeviceOwnerApp()` and documents it as testing-oriented/best-effort. Therefore, a factory reset remains the cleanest guaranteed way to return a formerly fully-managed phone to unmanaged state.

After a successful recovery relinquish, reboot once and uninstall ScrollLock normally.

---

# Part I — Updating ScrollLock

For a normal in-place update, keep all three unchanged:

```text
applicationId = dev.scrolllock.dpc
receiver class = dev.scrolllock.dpc.ScrollLockAdminReceiver
release signing key = same original key
```

If Hardening is on and you use ADB for updates:

1. disable Hardening with the recovery key;
2. turn USB debugging back on if necessary;
3. build the new release with the same key;
4. install it:

```powershell
adb install -r .\app\build\outputs\apk\release\app-release.apk
```

Device Owner status should survive a normal same-package, same-signature update.

---

# Part J — Optional: true factory-reset QR provisioning

For repeated/end-user provisioning, Android recommends QR enrollment on a factory-reset device rather than relying on the development ADB command.

This project includes Android 12+ handlers for:

```text
android.app.action.GET_PROVISIONING_MODE
android.app.action.ADMIN_POLICY_COMPLIANCE
```

and always selects `PROVISIONING_MODE_FULLY_MANAGED_DEVICE`.

## J1. Build and host the signed APK

Host `app-release.apk` at a direct **HTTPS** URL that the setup wizard can download without cookies/login/HTML redirects.

Example only:

```text
https://example.com/downloads/app-release.apk
```

## J2. Generate the provisioning JSON

The included script computes the APK file's SHA-256, converts it to Android's URL-safe Base64 form, and writes the correct DPC fields.

```powershell
.\tools\make-provisioning-json.ps1 `
  -ApkPath .\app\build\outputs\apk\release\app-release.apk `
  -HttpsUrl "https://example.com/downloads/app-release.apk" `
  -Output .\provisioning.json
```

The result is structurally equivalent to:

```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "dev.scrolllock.dpc/dev.scrolllock.dpc.ScrollLockAdminReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://example.com/downloads/app-release.apk",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM": "URL_SAFE_BASE64_SHA256",
  "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": true
}
```

Turn the exact JSON content into a QR code.

## J3. Scan during Setup Wizard

On Android devices that support the standard QR enrollment gesture, factory reset and tap the initial welcome screen repeatedly (commonly six times) to invoke QR provisioning, connect to Wi-Fi, and scan the QR code.

The exact gesture/wording can vary by OEM. Follow the device's Android Enterprise QR provisioning UI.

Android downloads the APK, checks the package checksum, invokes ScrollLock's provisioning-mode activity, provisions it as the fully-managed Device Owner, leaves normal system apps enabled, and then invokes its policy-compliance activity.

After setup completes, launch ScrollLock and create the recovery key.

---

# Part K — Google Play vs private distribution

For this personal project, **direct/private distribution is the cleaner route**.

ScrollLock requests `QUERY_ALL_PACKAGES` because its core UI is an installed-app management picker. Android's package-visibility documentation explicitly lists device-management apps as a class where broad package visibility can be appropriate. Google Play, however, treats installed-app inventory as sensitive and restricts `QUERY_ALL_PACKAGES`; a Play-distributed build would need to satisfy the policy and submit the required declaration.

As of August 31, 2026, new phone apps/updates submitted to Google Play must target Android 16 / API 36 or higher. This source targets API 36.

For a self-installed DPC, none of that requires publishing the APK on Google Play.

---

# Part L — Limits and threat model

ScrollLock is designed to remove **easy, impulsive bypasses**, not to make personally owned hardware cryptographically unrecoverable.

It does not promise to defeat:

- OEM recovery/reflash tools;
- bootloader-level restoration where the hardware permits it;
- every hardware-key factory-reset route;
- installing a different distracting app from an allowed app store;
- web versions of services in a browser;
- an unknown OEM-specific system-management escape hatch.

`DISALLOW_FACTORY_RESET` specifically blocks the Settings factory-reset path; it is not a guarantee that every physical recovery mechanism disappears.

That boundary is intentional. The tool should create meaningful friction without converting a self-control experiment into an unrecoverable device.

---

# Source tree

```text
ScrollLockDPC/
├─ app/
│  ├─ build.gradle.kts
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/dev/scrolllock/dpc/
│     │  ├─ AppCatalog.kt
│     │  ├─ BootReceiver.kt
│     │  ├─ GetProvisioningModeActivity.kt
│     │  ├─ MainActivity.kt
│     │  ├─ PackageChangedReceiver.kt
│     │  ├─ PolicyComplianceActivity.kt
│     │  ├─ PolicyController.kt
│     │  ├─ ScrollLockAdminReceiver.kt
│     │  └─ SettingsStore.kt
│     └─ res/
│        ├─ values/strings.xml
│        └─ xml/device_admin_receiver.xml
├─ tools/
│  └─ make-provisioning-json.ps1
├─ bootstrap-gradle.ps1
├─ bootstrap-gradle.sh
├─ build.gradle.kts
├─ gradle.properties
├─ keystore.properties.example
├─ settings.gradle.kts
└─ README.md
```
