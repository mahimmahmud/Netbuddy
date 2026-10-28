# NetBuddy 1.3.1 — Network Troubleshooting Assistant

Complete Android Studio source project: Kotlin, Compose / Material 3, MVVM, Room, Coroutines/Flow and Navigation Compose. API 26+, JDK 17, Gradle 8.9 and SDK Platform 35. Includes source, custom app logo, light/navy UI and tests. Build a fresh APK from this source for the updated Home dashboard.

## Setup

Extract the ZIP into a new folder. Android Studio > Open > select `NetBuddy` (contains `settings.gradle.kts`). Select JDK 17, install SDK Platform 35, sync Gradle, and run `app`. Open this complete project rather than merging files into the old one.

Windows: `gradlew.bat :app:assembleDebug`; macOS/Linux: `./gradlew :app:assembleDebug`. Output: `app/build/outputs/apk/debug/app-debug.apk`.

Version code 5, version name `1.3.1-dashboard`. The application ID and Room database schema/name remain unchanged. Updating an APK with the same signing certificate can preserve reports. A differently signed installation cannot be updated directly; uninstalling it deletes its local data. This source ZIP does not include a newly built APK.

Choose Home gear > Appearance > Light, Dark, or System. Open the Tools tab to find diagnostics.

## Existing tools

- Home dashboard: local IP, public IP, ISP, approximate IP-based location and Wi-Fi signal quality. Public IP and metadata lookups use independent HTTPS providers with fallbacks. The services receive the public IP. Location is approximate and the app requests no device-location permission. Wi-Fi connection does not guarantee internet reachability.
- IPv4 subnet calculator: /0–/32, mask, network/broadcast boundaries, complete/usable ranges, counts, reset, copy and save. /31 uses point-to-point semantics; /32 is a host route. Long avoids /0 overflow. Reserved-range assignment rules still apply.
- TCP latency: five connections to one host/port; attempts, failures, min/avg/max, stop and history. This is not ICMP ping.
- DNS: real A/AAAA results via Google Public DNS over HTTPS. Queries are sent to dns.google. Empty, NXDOMAIN and network failures are distinct.
- HTTPS check: configurable secure endpoint, HTTP status, time to response headers and retry. Default `https://www.google.com/generate_204`. Redirects are reported without following them. HTTP errors do not prove the internet is down.
- Room History: newest-first reports, type filters, details/copy, individual deletion and confirmed clear-all. Offline troubleshooting guide and persisted appearance setting.

## NEW: ICMP Ping monitor

The standalone Ping screen is inspired by the linked Ping utility's basic ICMP workflow, using NetBuddy's own interface. It does not clone its brand or include traceroute.

- Enter an IP/hostname, Start/Stop, view live replies, RTT, TTL when reported and a latency graph.
- Settings: count 1–1000, or 0 for continuous; minimum probe interval 1–10 seconds; timeout 1–10 seconds; payload 0–1472 bytes.
- Sent, received, loss and min/avg/max update throughout the run. An unsent probe/DNS setup failure is not counted as packet loss. If no packet was confirmed sent, loss is unavailable.
- Each probe invokes the installed Android `/system/bin/ping` or `/system/xbin/ping` with a fixed argument list, never a shell. Each invocation uses one packet; the app assigns its own attempt sequence numbers. RTT comes from the native ICMP response output, not the Java process duration. IPv6 literals use the tool's IPv6 mode.
- The OS may restrict ICMP or omit the executable. Missing/restricted/unsupported execution is explicitly reported; TCP is never silently substituted.
- Replies marked `<` retain that upper-bound label; aggregate statistics use the reported bound and say so. Some replies can lack a parseable RTT/TTL; absent values are not invented.
- Chart: latest 50 attempts; visible log: latest 20; report: latest 200 samples. Counters cover the full run. Lost/untimed probes break the RTT line; no synthetic measurements fill gaps.
- Interval is a minimum start-to-start interval. A long timeout or process setup can make actual spacing longer.
- Completed, stopped and failed diagnostic reports are saved to Room. Clear/reset does not delete saved history.
- Foreground only: monitoring stops when the app is backgrounded or the activity stops for reasons other than configuration recreation. No background service, wake lock or notification permission.

ICMP filtering, congestion and destination policies can prevent replies. Missing replies do not prove the destination is offline. This feature does not require or request root.

## NEW: Network Terminal

Local commands:

```text
help
clear
ping 192.168.1.1
ssh admin@192.168.88.1
ssh admin@192.168.88.1 2222
telnet 192.168.88.1 23
```

`ping` performs four actual ICMP probes and saves its diagnostic report to Ping history. Use the separate Ping screen for continuous/custom settings. `clear` clears the local display; `help` lists commands. Unknown commands and extra arguments are rejected. Local commands never enter `sh` or another arbitrary shell.

After SSH/Telnet connects, typed input is sent to the remote device. That device determines the available commands: a MikroTik CLI is different from a Linux shell. Enter/Send submits a line. Tab/arrows/Esc/Ctrl+C buttons send control input; any locally typed fragment is sent first. Use Stop/Disconnect to return to the local NetBuddy prompt.

### SSH

- Maintained JSch client with password authentication, an interactive PTY shell and modern default algorithms. No key-file/agent or keyboard-interactive login UI is included.
- A secure password dialog is used; passwords are not command arguments and are never written to logs/Room/preferences.
- For a new server, the app displays host, algorithm and SHA-256 fingerprint. Compare it with a trusted value from the administrator before choosing Trust and connect.
- Accepted public host keys are stored privately in `ssh_known_hosts`. Known keys are verified on later connections. Changed keys are blocked before password authentication; there is no accept-all verifier.
- Terminal Help > Saved SSH keys can remove all saved public pins after confirmation. Verify a legitimate key change out of band before doing this.
- Connection/channel timeout 10 seconds; keepalive interval 15 seconds. Stop closes the connection; an OS/library operation already connecting can take up to its timeout to return. No automatic reconnection or replay of commands.

### Telnet

Apache Commons Net handles Telnet option negotiation and NVT streams. Telnet is intentionally plaintext; use it only where appropriate for your own lab. Username/password prompts come from the remote device. Password prompts automatically enable Hide input where recognized; the checkbox also lets you mask input manually. Input is not echoed locally; what the server echoes is controlled by that server. SSH is the encrypted option.

### Terminal limitations and lifecycle

This is a bounded line-oriented console with basic streaming ANSI cleanup, carriage returns, backspace and horizontal cursor handling. It is not a full VT100/Linux environment; full-screen editors and complex terminal applications are not supported. No Linux package manager, local root shell or terminal transcript export/history is implemented. Use the display's Clear button if a remote clear-screen sequence does not redraw as expected.

Output is limited to 300 lines / 240 columns per line. Remote transcripts stay in memory and are cleared when backgrounded. Pending input is reset when the session changes. SSH/Telnet sessions disconnect on backgrounding; rotation/configuration recreation retains the ViewModel session but resets unsent field text. Password fields are not saved in state bundles. Only Ping diagnostic results, not SSH/Telnet transcripts, are saved to Room.

## Network prerequisites

Your phone must be able to route to the chosen target. Example private IPs are examples, not automatic discoveries. The target must run SSH/Telnet on the selected port, accept your account and be reachable through its firewall. For nested VMware/CHR labs, ensure the phone has a valid route to the VM network. The app does not create that route or enable services on the router.

## Wi-Fi signal quality

When Android provides a Wi-Fi signal reading, the Home dashboard labels -70 dBm or stronger as Good and weaker than -70 dBm as Poor. If the current connection is not Wi-Fi or Android has no reading, the dashboard shows that status instead.

## Privacy and permissions

Permissions: INTERNET and ACCESS_NETWORK_STATE. No Firebase/backend, ads, analytics, device-location permission, contacts or traffic capture. The Home dashboard contacts ipify.org for public IP fallback and ipwho.is / ipapi.co for ISP and approximate location metadata; these services receive the public IP. HTTPS is required for HTTP diagnostics. Telnet uses its explicitly selected raw protocol connection and is unencrypted. DNS queries go to the identified public resolver; probes and remote logins contact only the targets the user selects. Diagnostic reports contain targets/IPs and should be reviewed before copying. App backups are disabled.

## Tests

```text
gradlew.bat :app:testDebugUnitTest
gradlew.bat :app:assembleDebugAndroidTest
gradlew.bat :app:connectedDebugAndroidTest
```

Use `./gradlew` instead on macOS/Linux. Connected tests require an emulator/device. Unit tests cover subnet edge cases, all CIDR sizes, invalid input, HTTP/DNS handling, ICMP parsing/counts/upper bounds/cancellation, command parsing/argument injection rejection, bounded ANSI cleanup and SSH trust/change rejection. Instrumented tests cover Room operations/persistence and a loopback ICMP attempt or explicit platform restriction. See VALIDATION.md for actual results and limits.

## Architecture and viva

- `NetBuddyApplication`: manual dependency container for database/repository, network monitor and HTTP client.
- `NetBuddyModel`: DNS/TCP/HTTPS state, History, and public IP metadata refresh coordination.
- `PingViewModel`: foreground ICMP job, bounded samples, aggregate statistics and Room reports. `IcmpPing`/`PingProcess` separate actual process execution from parsing/testable logic.
- `TerminalViewModel`: local parser, remote-session lifecycle, trust decisions, ordered bounded output queue, secret-input state and in-memory transcript. `TerminalCommand` prevents arbitrary local shell execution.
- `SshConnection`: protocol library, verified host keys and PTY channel. `TelnetConnection`: Commons Net negotiation and streams. `LineTerminal`: bounded text/escape rendering.
- `MainActivity`/`UiKit`/screen composables: navigation, lifecycle observation, the network dashboard, presentation and input. `NetworkMonitor` reads local IPv4, Android validation and available Wi-Fi signal strength. `PublicIpLookup` merges ipwho.is/ipapi.co metadata and uses ipify.org as an independent public-address fallback. Pure subnet calculations and diagnostic operations remain outside the UI; network/blocking work runs off the main thread.
- `data/History.kt`: entity, DAO, database and repository. Schema version remains 1 because new report types use an existing string field.

Third-party coordinates and licenses are in THIRD-PARTY-NOTICES.md. No fake diagnostic results are bundled into production code.


## Added Tools (1.4)

- Smart Troubleshooting: symptom-based guided sequence for existing DNS, TCP and HTTPS checks.
- Saved Targets: on-device saved hosts with a one-tap TCP 443 check.
- Report Export: share locally saved reports using Android share sheet.
- Network Comparison: compare current connection snapshot with a saved baseline.
- Speed Test: opens Cloudflare speed test in the browser for download, upload, latency and jitter measurements.

LAN Device Discovery was intentionally not included. Speed Test is provided by an external service and opens outside NetBuddy.


## ICMP Ping update (1.4.1)

ICMP Ping starts in continuous mode by default (count 0). Count, interval, timeout and payload settings persist locally. Live replies, running packet statistics, and the rolling latency chart remain visible while monitoring; stop the run to save a report.


## ICMP Ping screen update (1.4.2)

Ping screen now follows the requested segmented Ping/History/My IP layout, with adjacent Traceroute and Start/Stop controls, live statistics, latency chart, and replies. Traceroute uses bounded TTL probes (up to 20 hops) and may be limited by the Android device or network.


## ICMP follow-up (1.4.4)

Removed duplicate Ping/History/My IP tabs from the ICMP tool. The page follows the reference order, auto-scrolls to the latest ping/trace output while active, and traceroute resolves the destination and stops when its address is reached. Route probe output parsing excludes the separator colon in IPv4 replies.


## Ping dashboard redesign (1.5.0)

The ICMP screen now uses a dedicated compact dashboard: destination and controls at top, a combined latency graph/statistics panel, and an independently auto-following reply list. Redundant app navigation is hidden while the tool is open. Ping settings remain locally saved; unlimited count remains the default. Traceroute stops when the resolved destination responds.


## Ping reply detail update (1.5.1)

Live rows now show sequence, responding address, bytes, RTT, TTL, and a color-coded latency value (green below 50 ms, amber below 100 ms, red at 100 ms or above; timeouts red). The traceroute terminates on the resolved destination address, not merely on a successful probe from an intermediate hop.
