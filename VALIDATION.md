# NetBuddy 1.2 verification

The checks below document version 1.2 (`1.2-terminal`, version code 3). The 1.3 and 1.3.1 dashboard source was edited afterward. A fresh Android build was attempted for 1.3, but Gradle could not download its 8.9 distribution in this workspace because the network connection was unavailable. Do not treat the 1.2 build results as validation of 1.3.1. Build and check the updated source in Android Studio before installing it.

## Android build

The final source was built with full JDK 17, Gradle 8.9, Android SDK Platform 35:

```text
clean :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
BUILD SUCCESSFUL
```

Gradle returned exit code 0. APK metadata: application ID `com.example.netbuddy`, version code 3, version name `1.2-terminal`, min API 26 / target API 35. Build-tools apksigner verified the app APK signature (exit code 0). Source XML parsed successfully. No source file is newer than the packaged APK.

## Unit tests

27 tests passed, no failures or errors:

- com.example.netbuddy.domain.model.SubnetCalculatorTest: 10 tests, 0 failures, 0 errors
- com.example.netbuddy.network.DiagnosticsTest: 5 tests, 0 failures, 0 errors
- com.example.netbuddy.terminal.IcmpPingTests: 7 tests, 0 failures, 0 errors
- com.example.netbuddy.terminal.TerminalTests: 5 tests, 0 failures, 0 errors

The same pure calculation/network/protocol tests were also compiled and run on the JVM with Kotlin 2.0.21 / JUnit 4.13.2. Tests inject ping process output only in test code; production uses the actual Android system executable.

## Actual loopback protocol integration

The production SshConnection/TelnetConnection implementations were exercised on the JVM against temporary loopback-only test servers:

- SSH password authentication and interactive PTY shell channel.
- Sending a line and receiving the test server's response.
- First-use fingerprint approval and persisted known-host reuse on a new client.
- A changed server host key on the same host/port was rejected before password authentication: two successful authentications across three connection attempts, with no third password authentication.
- Telnet WILL ECHO negotiation acknowledged with DO ECHO, plus bidirectional line exchange.
- Integration process exited 0; no server errors.

These tests use temporary synthetic test accounts/keys; none are shipped in the app or used against an external device. The temporary test server is not a runtime/backend dependency.

## Compiled but not run on a device

The Android instrumented test APK compiled. It contains the two Room operation/persistence tests and one loopback ICMP/platform-restriction test. `connectedDebugAndroidTest` was not run because no Android device/emulator is connected.

Not verified here: live ICMP execution on a phone, Android-specific SSH crypto behavior, a real MikroTik/server login, Telnet from Android, on-device UI/keyboard rendering, rotation/background cleanup under device conditions, or live external endpoints from the app. JVM protocol tests do not replace those checks. Use START-HERE-BN.md / README.md for device testing.

## Build notes

Duplicate OSGi manifests from the multi-release SSH/crypto dependencies are excluded from Android Java resources. A clean build resolves stale incremental compiler outputs; caches are not packaged. Kapt emits its Kotlin 1.9 stub-generation fallback warning with Kotlin 2.0; the final build succeeds.
