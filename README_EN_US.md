# Zalith Launcher 2
![Downloads](https://img.shields.io/github/downloads/ZalithLauncher/ZalithLauncher2/total)
[![Sponsor](https://img.shields.io/badge/sponsor-30363D?logo=GitHub-Sponsors)](https://afdian.com/a/MovTery)

[简体中文](README.md) | [繁體中文](README_ZH_TW.md)


> [!IMPORTANT]
> This project is **completely separate** from [ZalithLauncher](https://github.com/ZalithLauncher/ZalithLauncher).  

**Zalith Launcher 2** is a newly designed launcher for **Android devices** tailored for [Minecraft: Java Edition](https://www.minecraft.net/). The project uses [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher/tree/v3_openjdk/app_pojavlauncher/src/main/jni) as its core launching engine and features a modern UI built with **Jetpack Compose** and **Material Design 3**.  
We are currently building our official website [zalithlauncher.cn](https://zalithlauncher.cn)  
Additionally, we are aware that a third-party website has been set up using the name “Zalith Launcher”, appearing to be official. Please note: **this site was not created by us**. It exploits the name to display ads for profit. We **do not participate in, endorse, or trust** such content.  
Please stay vigilant and **protect your personal privacy**!

## 🌐 Language and Translation Support

We are using the Weblate platform to translate Zalith Launcher 2. You're welcome to join our [Weblate project](https://hosted.weblate.org/projects/zalithlauncher2) and contribute to the translations!  
Thank you to every language contributor for helping make Zalith Launcher 2 more multilingual and global!

## 📦 Build Instructions (For Developers)

> The following section is for developers who wish to contribute or build the project locally.
> CI is the source of truth for toolchain versions: see `.github/workflows/build.yml` and `pr_ci.yml`.

### Requirements

| Component | Version / notes |
|---|---|
| JDK | **21** (CI uses Temurin 21; Java/Kotlin bytecode target is 17) |
| Android Studio | A recent release that supports **AGP 9.3** / Gradle 9.5 (the Gradle wrapper downloads Gradle automatically) |
| Android SDK | **compileSdk 37 (minor 2)**, minSdk 26, targetSdk 34 |
| Android NDK | **25.2.9519653** for the ndk-build modules (`ndkVersion` in `ZalithLauncher/build.gradle.kts`), plus an NDK for cargo-ndk (CI pins **30.0.16248370** via `ANDROID_NDK_HOME`) |
| Rust | `rustup` with targets `aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android` |
| cargo-ndk | `cargo install cargo-ndk` (CI uses v4.1.2) — required to build `PetLib` |
| Memory | Gradle runs with `-Xmx4g` (`gradle.properties`); 8 GB+ RAM recommended |

Building on ARM64 Linux hosts (for example Termux/proot) is not supported by the official Android toolchains; use an x86_64 Linux, macOS or Windows machine, or GitHub Actions.

### Build Steps

```bash
git clone https://github.com/ZalithLauncher/ZalithLauncher2.git
cd ZalithLauncher2
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
cargo install cargo-ndk

# Debug APK for one ABI: all (universal, default) | arm | arm64 | x86 | x86_64
./gradlew :ZalithLauncher:assembleDebug -Darch=arm64
# Output: ZalithLauncher/build/outputs/apk/debug/
```

### Optional keys

Read from an environment variable, or from a file in the project root (all of these files are git-ignored). If a key is missing the build still succeeds and prints a warning, but the related feature will not work.

| Purpose | Environment variable | File |
|---|---|---|
| Microsoft login (Azure OAuth client ID) | `OAUTH_CLIENT_ID` | `.oauth_client_id.txt` |
| CurseForge API | `CURSEFORGE_API_KEY` | `.curseforge_api.txt` |
| Release signing only | `STORE_PASSWORD`, `KEY_PASSWORD` | `.store_password.txt`, `.key_password.txt` |

Never commit these values.

### Tests and checks

```bash
# Unit tests (-PciExcludedTests skips tests that need external services or developer-local files, plus 2 known-broken upstream assertions)
./gradlew :ZalithLauncher:testDebugUnitTest :CardGrid:testDebugUnitTest :Guide:testDebugUnitTest -Darch=arm64 -PciExcludedTests

# 16 KB page-size alignment of 64-bit native libraries in the built APK
scripts/check-16kb-alignment.sh --allowlist .github/16kb-alignment-allowlist.txt ZalithLauncher/build/outputs/apk/debug/*.apk
```

Pull requests are verified automatically by `.github/workflows/pr_ci.yml`.

## 📜 License

This project is licensed under the **[GPL-3.0 license](LICENSE)**.

### Additional Terms (Pursuant to Section 7 of the GPLv3 License)

1. When distributing a modified version of this program, you must reasonably modify the program's name or version number to distinguish it from the original version. (According to [GPLv3, 7(c)](https://github.com/ZalithLauncher/ZalithLauncher2/blob/969827b/LICENSE#L372-L374))
    - Modified versions **must not include the original program name "ZalithLauncher" or its abbreviation "ZL" in their name, nor use any name that is similar enough to cause confusion with the official name**.
    - All modified versions **must clearly indicate that they are “Unofficial Modified Versions” on the program’s startup screen or main interface**.
    - The application name of the program can be modified in [gradle.properties](./ZalithLauncher/gradle.properties).

2. You must not remove the copyright notices displayed by the program. (According to [GPLv3, 7(b)](https://github.com/ZalithLauncher/ZalithLauncher2/blob/969827b/LICENSE#L368-L370))

## Open Source Libraries and Licenses

This software uses the following open source libraries:

| Library                               | Copyright                                                                                                     | License              | Official Link                                                                      |
|---------------------------------------|---------------------------------------------------------------------------------------------------------------|----------------------|------------------------------------------------------------------------------------|
| androidx-appcompat                    | Copyright © The Android Open Source Project                                                                   | Apache 2.0           | [Link↗](https://developer.android.com/jetpack/androidx/releases/appcompat)         |
| androidx-constraintlayout-compose     | Copyright © The Android Open Source Project                                                                   | Apache 2.0           | [Link↗](https://developer.android.com/develop/ui/compose/layouts/constraintlayout) |
| androidx-webkit                       | Copyright © The Android Open Source Project                                                                   | Apache 2.0           | [Link↗](https://developer.android.com/jetpack/androidx/releases/webkit)            |
| ANGLE                                 | Copyright 2018 The ANGLE Project Authors                                                                      | BSD 3-Clause License | [Link↗](http://angleproject.org/)                                                  |
| Apache Commons Codec                  | -                                                                                                             | Apache 2.0           | [Link↗](https://commons.apache.org/proper/commons-codec)                           |
| Apache Commons Compress               | -                                                                                                             | Apache 2.0           | [Link↗](https://commons.apache.org/proper/commons-compress)                        |
| Apache Commons IO                     | -                                                                                                             | Apache 2.0           | [Link↗](https://commons.apache.org/proper/commons-io)                              |
| ByteHook                              | Copyright © 2020-2024 ByteDance, Inc.                                                                         | MIT License          | [Link↗](https://github.com/bytedance/bhook)                                        |
| BuildKeys                             | Copyright © 2026 MovTery                                                                                      | Aoache 2.0           | [Link↗](https://github.com/MovTery/BuildKeys)                                      |
| Coil Compose                          | Copyright © 2025 Coil Contributors                                                                            | Apache 2.0           | [Link↗](https://github.com/coil-kt/coil)                                           |
| Coil Gifs                             | Copyright © 2025 Coil Contributors                                                                            | Apache 2.0           | [Link↗](https://github.com/coil-kt/coil)                                           |
| Coil SVG                              | Copyright © 2025 Coil Contributors                                                                            | Apache 2.0           | [Link↗](https://github.com/coil-kt/coil)                                           |
| Fishnet                               | Copyright © 2025 Kyant                                                                                        | Apache 2.0           | [Link↗](https://github.com/Kyant0/Fishnet)                                         |
| gl4es_extra_extra                     | Copyright © 2016-2018 Sebastien Chevalier; Copyright (c) 2013-2016 Ryan Hileman                               | MIT License          | [Link↗](https://github.com/PojavLauncherTeam/gl4es_extra_extra)                    |
| Gson                                  | Copyright © 2008 Google Inc.                                                                                  | Apache 2.0           | [Link↗](https://github.com/google/gson)                                            |
| kotlinx.coroutines                    | Copyright © 2000-2020 JetBrains s.r.o.                                                                        | Apache 2.0           | [Link↗](https://github.com/Kotlin/kotlinx.coroutines)                              |
| ktor-client-content-negotiation       | Copyright © 2000-2023 JetBrains s.r.o.                                                                        | Apache 2.0           | [Link↗](https://ktor.io)                                                           |
| ktor-client-core                      | Copyright © 2000-2023 JetBrains s.r.o.                                                                        | Apache 2.0           | [Link↗](https://ktor.io)                                                           |
| ktor-client-okhttp                    | Copyright © 2000-2023 JetBrains s.r.o.                                                                        | Apache 2.0           | [Link↗](https://ktor.io)                                                           |
| ktor-http                             | Copyright © 2000-2023 JetBrains s.r.o.                                                                        | Apache 2.0           | [Link↗](https://ktor.io)                                                           |
| ktor-serialization-kotlinx-json       | Copyright © 2000-2023 JetBrains s.r.o.                                                                        | Apache 2.0           | [Link↗](https://ktor.io)                                                           |
| LWJGL - Lightweight Java Game Library | Copyright © 2012-present Lightweight Java Game Library All rights reserved.                                   | BSD 3-Clause License | [Link↗](https://github.com/LWJGL/lwjgl3)                                           |
| material-color-utilities              | Copyright 2021 Google LLC                                                                                     | Apache 2.0           | [Link↗](https://github.com/material-foundation/material-color-utilities)           |
| Maven Artifact                        | Copyright © The Apache Software Foundation                                                                    | Apache 2.0           | [Link↗](https://github.com/apache/maven/tree/maven-3.9.9/maven-artifact)           |
| Media3                                | Copyright © The Android Open Source Project                                                                   | Apache 2.0           | [Link↗](https://developer.android.com/jetpack/androidx/releases/media3)            |
| Mesa                                  | Copyright © The Mesa Authors                                                                                  | MIT License          | [Link↗](https://mesa3d.org/)                                                       |
| MobileGlues                           | Copyright © 2025-2026 MobileGL-Dev                                                                           | LGPL-2.1 License     | [Link↗](https://github.com/MobileGL-Dev/MobileGlues)                             |
| MMKV                                  | Copyright © 2018 THL A29 Limited, a Tencent company.                                                          | BSD 3-Clause License | [Link↗](https://github.com/Tencent/MMKV)                                           |
| Navigation 3                          | Copyright © The Android Open Source Project                                                                   | Apache 2.0           | [Link↗](https://developer.android.com/jetpack/androidx/releases/navigation3)       |
| NG-GL4ES                              | Copyright © 2016-2018 Sebastien Chevalier; Copyright © 2013-2016 Ryan Hileman; Copyright (c) 2025-2026 BZLZHH | MIT License          | [Link↗](https://github.com/BZLZHH/NG-GL4ES)                                        |
| OkHttp                                | Copyright © 2019 Square, Inc.                                                                                 | Apache 2.0           | [Link↗](https://github.com/square/okhttp)                                          |
| Okio                                  | Copyright © 2013 Square, Inc.                                                                                 | Apache 2.0           | [Link↗](https://square.github.io/okio/)                                            |
| OpenNBT                               | Copyright © 2013-2021 Steveice10.                                                                             | MIT License          | [Link↗](https://github.com/GeyserMC/OpenNBT)                                       |
| Process Phoenix                       | Copyright © 2015 Jake Wharton                                                                                 | Apache 2.0           | [Link↗](https://github.com/JakeWharton/ProcessPhoenix)                             |
| proxy-client-android                  | -                                                                                                             | LGPL-3.0 License     | [Link↗](https://github.com/TouchController/TouchController)                        |
| Reorderable                           | Copyright © 2023 Calvin Liang                                                                                 | Apache 2.0           | [Link↗](https://github.com/Calvin-LL/Reorderable)                                  |
| sdl2-compat                           | Copyright (C) 2026 Sam Lantinga <slouken@libsdl.org>                                                          | Zlib License         | [Link↗](https://github.com/libsdl-org/sdl2-compat)                                 |
| SDL3                                  | Copyright (C) 1997-2026 Sam Lantinga <slouken@libsdl.org>                                                     | Zlib License         | [Link↗](https://github.com/libsdl-org/SDL)                                         |
| SimpleFPEWrapper                      | Copyright © 2026 MobileGL-Dev                                                                                | LGPL-3.0 License     | [Link↗](https://github.com/MobileGL-Dev/SimpleFPEWrapper)                        |
| sora-editor                           | Copyright (C) 2020-2026  Rosemoe                                                                              | LGPL-2.1 License     | [Link↗](https://github.com/Rosemoe/sora-editor)                                    |
| StringFog                             | Copyright © 2016-2023, Megatron King                                                                          | Apache 2.0           | [Link↗](https://github.com/MegatronKing/StringFog)                                 |
| tm4e (TextMate for Eclipse)           | Copyright © Eclipse Foundation                                                                                | EPL-2.0 License      | [Link↗](https://github.com/eclipse-tm4e/tm4e)                                      |
| XZ for Java                           | Copyright © The XZ for Java authors and contributors                                                          | 0BSD License         | [Link↗](https://tukaani.org/xz/java.html)                                          |
