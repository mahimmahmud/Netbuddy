# Third-party components

NetBuddy uses these published libraries through Gradle:

- JSch (maintained mwiede fork), `com.github.mwiede:jsch:2.28.8`: BSD-style license and bundled third-party notices. https://github.com/mwiede/jsch
- Bouncy Castle, `org.bouncycastle:bcprov-jdk18on:1.86`: Bouncy Castle license (MIT-style). https://www.bouncycastle.org/licence.html
- Apache Commons Net, `commons-net:commons-net:3.13.0`: Apache License 2.0. https://commons.apache.org/proper/commons-net/
- AndroidX Compose, Navigation, Lifecycle, Activity and Room: Apache License 2.0. https://developer.android.com/jetpack/androidx
- Kotlin and kotlinx.coroutines: Apache License 2.0. https://kotlinlang.org/ and https://github.com/Kotlin/kotlinx.coroutines
- OkHttp/Okio: Apache License 2.0. https://square.github.io/okhttp/
- JUnit, MockWebServer and JSON-java are test dependencies; see their upstream licenses.
- Gradle wrapper/scripts: upstream Gradle license notices are retained in the bundled scripts/JAR.

Android's system ping executable is invoked when present; it is not redistributed inside this project. The Android-only packaging exclusion removes duplicate OSGi manifest metadata from multi-release dependencies, not their crypto implementations or licenses.

The temporary Python SSH test server used during verification is not an app dependency or backend. No server/private key/test credential is included in the APK.
