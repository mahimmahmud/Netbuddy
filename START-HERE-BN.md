# NetBuddy 1.2 — Terminal ও ICMP Ping

## Install / Open

১. ZIP extract করো। ফোনে সরাসরি test করতে `APK/NetBuddy-debug.apk` install করো।
২. Source খুলতে Android Studio → Open → নতুন `NetBuddy` folder নির্বাচন করো। পুরোনো project-এ ফাইল merge করতে হবে না।
৩. JDK 17, Android SDK Platform 35 দিয়ে Gradle Sync → Run করো। প্রথম Sync-এ internet লাগবে।
৪. আগের app-এর signing key একই হলে update install হতে পারে এবং history থাকবে। Signing key আলাদা হলে সরাসরি update হবে না; uninstall করলে পুরোনো app-এর local data মুছে যাবে।

## Terminal

Home / Tools → Terminal খুলে নিচের commands দাও:

```text
ping 192.168.1.1
ssh admin@192.168.88.1
telnet 192.168.88.1 23
help
clear
```

`NetBuddy >` prompt নিজে লিখতে হবে না। `ping` চারটি real ICMP probe পাঠায়। SSH দিলে password dialog খুলবে; নতুন server হলে SHA-256 fingerprint trusted server/admin-এর fingerprint-এর সঙ্গে মিলিয়ে তবেই Trust and connect দাও। Saved key বদলে গেলে connection block হবে।

SSH/Telnet connect হলে input remote device-এ যাবে। MikroTik হলে MikroTik commands, Linux হলে Linux shell commands ব্যবহার করবে। Send/Enter, Tab/arrows, Ctrl+C/Esc buttons আছে। Stop/Disconnect দিয়ে local prompt-এ ফেরত আসবে।

Telnet encrypted নয়। নিজের lab-এ ব্যবহার করো। Password prompt-এ Hide input ব্যবহার করো; চিনতে পারলে app নিজেও mask করবে। App কোনো SSH/Telnet password বা transcript database-এ রাখে না। এটি basic line console—পূর্ণ Termux/Linux বা full-screen editor নয়।

## আলাদা Ping screen

Home / Tools → ICMP Ping → target দিয়ে Start ping। Live replies, RTT, TTL পাওয়া গেলে, sent/received, packet loss, min/avg/max ও graph দেখা যাবে।

Settings:

- Count: 1–1000; `0` দিলে continuous।
- Minimum interval: 1–10 seconds।
- Timeout: 1–10 seconds।
- Payload: 0–1472 bytes।

Stop দিয়ে থামাও। App background-এ গেলে monitoring থামবে ও terminal connection বন্ধ হবে। Ping report History → Ping filter-এ থাকবে।

কোনো ফোনে Android-এর ICMP executable/permission না থাকলে app সেটা স্পষ্টভাবে জানাবে। TCP timing-কে ICMP ping হিসেবে দেখাবে না। Reply না এলেই host বন্ধ, এমন সিদ্ধান্ত নেওয়া যাবে না—ICMP block থাকতে পারে।

## সংযোগের শর্ত

নিজের device-এর actual IP, port, username/password ব্যবহার করো। ফোন থেকে router/server reachable হতে হবে এবং SSH/Telnet service চালু থাকতে হবে। Example `192.168.88.1` সব network-এ কাজ করবে না। VMware/CHR VM network-এ ফোনের routing/bridge ঠিক থাকতে হবে।

## Build ও tests

```text
gradlew.bat :app:assembleDebug
gradlew.bat :app:testDebugUnitTest
gradlew.bat :app:connectedDebugAndroidTest
```

শেষ command-এর জন্য emulator/device লাগবে। নতুন Home dashboard-এ local/public IP, ISP, approximate IP location এবং Wi-Fi signal quality আছে। Public IP ipify.org থেকে এবং ISP/location ipwho.is বা ipapi.co থেকে আসতে পারে; এসব service public IP পায়। Tools tab-এ diagnostics থাকবে। Appearance: Home gear → Appearance। Updated APK পেতে এই source থেকে `:app:assembleDebug` চালাও। Architecture ও code overview `README.md`-এ আছে।
