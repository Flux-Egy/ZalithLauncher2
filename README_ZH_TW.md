# Zalith Launcher 2
![Downloads](https://img.shields.io/github/downloads/ZalithLauncher/ZalithLauncher2/total)
[![Sponsor](https://img.shields.io/badge/sponsor-30363D?logo=GitHub-Sponsors)](https://afdian.com/a/MovTery)

[English](README_EN_US.md) | [简体中文](README.md)


> [!IMPORTANT]
> 該專案與 [ZalithLauncher](https://github.com/ZalithLauncher/ZalithLauncher) 屬於兩個完全不同的專案  

**Zalith Launcher 2** 是一個全新設計、面向 **Android 裝置** 的 [Minecraft: Java Edition](https://www.minecraft.net/) 啟動器。專案使用 [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher/tree/v3_openjdk/app_pojavlauncher/src/main/jni) 作為啟動核心，採用 **Jetpack Compose** 與 **Material Design 3** 構建現代化 UI 體驗。  
我們目前正在搭建自己的官方網站 [zalithlauncher.cn](https://zalithlauncher.cn)  
此外，我們已注意到有第三方使用「Zalith Launcher」名稱搭建了一個看似官方的網站。請注意：**該網站並非我們創建**，其透過冒用名義並植入廣告牟利。我們對此類行為**不參與、不認可、不信任**。  
請務必提高警覺，**謹防個人隱私資訊洩露**！



## 🌐 語言與翻譯支援

我們正在使用 Weblate 平台翻譯 Zalith Launcher 2，歡迎您前往我們的 [Weblate 專案](https://hosted.weblate.org/projects/zalithlauncher2) 參與翻譯！  
感謝每一位語言貢獻者的支持，讓 Zalith Launcher 2 更加多語化、更加國際化！





## 📦 構建方式（開發者）

> 以下內容適用於希望參與開發或自行構建應用的使用者。
> 工具鏈版本以 CI 為準：請參閱 `.github/workflows/build.yml` 與 `pr_ci.yml`。

### 環境要求

| 元件 | 版本 / 說明 |
|---|---|
| JDK | **21**（CI 使用 Temurin 21；Java/Kotlin 位元組碼目標為 17） |
| Android Studio | 支援 **AGP 9.3** / Gradle 9.5 的較新版本（Gradle Wrapper 會自動下載 Gradle） |
| Android SDK | **compileSdk 37（minor 2）**，minSdk 26，targetSdk 34 |
| Android NDK | ndk-build 模組使用 **25.2.9519653**（`ZalithLauncher/build.gradle.kts` 中的 `ndkVersion`）；cargo-ndk 另需一個 NDK（CI 透過 `ANDROID_NDK_HOME` 固定為 **27.0.12077973**） |
| Rust | 透過 `rustup` 安裝目標 `aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android` |
| cargo-ndk | `cargo install cargo-ndk`（CI 使用 v4.1.2），構建 `PetLib` 所必需 |
| 記憶體 | Gradle 以 `-Xmx4g` 執行（`gradle.properties`），建議 8 GB 以上記憶體 |

官方 Android 工具鏈不支援在 ARM64 Linux 主機（例如 Termux/proot）上構建；請使用 x86_64 Linux、macOS、Windows 或 GitHub Actions。

### 構建步驟

```bash
git clone https://github.com/ZalithLauncher/ZalithLauncher2.git
cd ZalithLauncher2
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
cargo install cargo-ndk

# 單一架構的 Debug APK：all（通用包，預設）| arm | arm64 | x86 | x86_64
./gradlew :ZalithLauncher:assembleDebug -Darch=arm64
# 輸出目錄：ZalithLauncher/build/outputs/apk/debug/
```

### 可選金鑰

從環境變數或專案根目錄下的檔案讀取（這些檔案皆已被 git 忽略）。缺少金鑰時構建仍會成功並輸出警告，但相關功能將無法使用。

| 用途 | 環境變數 | 檔案 |
|---|---|---|
| 微軟登入（Azure OAuth Client ID） | `OAUTH_CLIENT_ID` | `.oauth_client_id.txt` |
| CurseForge API | `CURSEFORGE_API_KEY` | `.curseforge_api.txt` |
| 僅限 Release 簽章 | `STORE_PASSWORD`、`KEY_PASSWORD` | `.store_password.txt`、`.key_password.txt` |

切勿提交這些值。

### 測試與檢查

```bash
# 單元測試（-PexcludeNetworkTests 會略過存取真實外部服務的測試）
./gradlew :ZalithLauncher:testDebugUnitTest :CardGrid:testDebugUnitTest :Guide:testDebugUnitTest -Darch=arm64 -PexcludeNetworkTests

# 檢查 APK 中 64 位元原生函式庫的 16 KB 頁面對齊
scripts/check-16kb-alignment.sh --allowlist .github/16kb-alignment-allowlist.txt ZalithLauncher/build/outputs/apk/debug/*.apk
```

拉取請求會由 `.github/workflows/pr_ci.yml` 自動驗證。

## 📜 License

本專案程式碼遵循 **[GPL-3.0 license](LICENSE)** 開源協議。

### 附加條款（依據 GPLv3 開源授權條款第七條）

1. 當你分發本程式的修改版本時，必須以合理方式修改該程式的名稱或版本號，以區別於原始版本。（依據 [GPLv3, 7(c)](https://github.com/ZalithLauncher/ZalithLauncher2/blob/969827b/LICENSE#L372-L374)）
    - 修改版本 **不得在名稱中包含原程式名稱「ZalithLauncher」或其縮寫「ZL」，亦不得使用與官方名稱相近、可能造成混淆的名稱**。
    - 所有修改版本 **必須在程式啟動畫面或主介面中以明顯方式標示其為「非官方修改版」**。
    - 程式的應用名稱可於 [gradle.properties](./ZalithLauncher/gradle.properties) 中進行修改。

2. 你不得移除本程式所顯示的版權聲明。（依據 [GPLv3, 7(b)](https://github.com/ZalithLauncher/ZalithLauncher2/blob/969827b/LICENSE#L368-L370)）

## 引用開源專案
  
本軟體使用以下開源函式庫:

| Library                               | Copyright                                                                                                     | License              | Official Link                                                                    |
|---------------------------------------|---------------------------------------------------------------------------------------------------------------|----------------------|----------------------------------------------------------------------------------|
| androidx-appcompat                    | Copyright © The Android Open Source Project                                                                   | Apache 2.0           | [連結↗](https://developer.android.com/jetpack/androidx/releases/appcompat)         |
| androidx-constraintlayout-compose     | Copyright © The Android Open Source Project                                                                   | Apache 2.0           | [連結↗](https://developer.android.com/develop/ui/compose/layouts/constraintlayout) |
| androidx-webkit                       | Copyright © The Android Open Source Project                                                                   | Apache 2.0           | [連結↗](https://developer.android.com/jetpack/androidx/releases/webkit)            |
| ANGLE                                 | Copyright 2018 The ANGLE Project Authors                                                                      | BSD 3-Clause License | [連結↗](http://angleproject.org/)                                                  |
| Apache Commons Codec                  | -                                                                                                             | Apache 2.0           | [連結↗](https://commons.apache.org/proper/commons-codec)                           |
| Apache Commons Compress               | -                                                                                                             | Apache 2.0           | [連結↗](https://commons.apache.org/proper/commons-compress)                        |
| Apache Commons IO                     | -                                                                                                             | Apache 2.0           | [連結↗](https://commons.apache.org/proper/commons-io)                              |
| ByteHook                              | Copyright © 2020-2024 ByteDance, Inc.                                                                         | MIT License          | [連結↗](https://github.com/bytedance/bhook)                                        |
| BuildKeys                             | Copyright © 2026 MovTery                                                                                      | Aoache 2.0           | [連結↗](https://github.com/MovTery/BuildKeys)                                      |
| Coil Compose                          | Copyright © 2025 Coil Contributors                                                                            | Apache 2.0           | [連結↗](https://github.com/coil-kt/coil)                                           |
| Coil Gifs                             | Copyright © 2025 Coil Contributors                                                                            | Apache 2.0           | [連結↗](https://github.com/coil-kt/coil)                                           |
| Coil SVG                              | Copyright © 2025 Coil Contributors                                                                            | Apache 2.0           | [連結↗](https://github.com/coil-kt/coil)                                           |
| Fishnet                               | Copyright © 2025 Kyant                                                                                        | Apache 2.0           | [連結↗](https://github.com/Kyant0/Fishnet)                                         |
| gl4es_extra_extra                     | Copyright © 2016-2018 Sebastien Chevalier; Copyright (c) 2013-2016 Ryan Hileman                               | MIT License          | [連結↗](https://github.com/PojavLauncherTeam/gl4es_extra_extra)                    |
| Gson                                  | Copyright © 2008 Google Inc.                                                                                  | Apache 2.0           | [連結↗](https://github.com/google/gson)                                            |
| kotlinx.coroutines                    | Copyright © 2000-2020 JetBrains s.r.o.                                                                        | Apache 2.0           | [連結↗](https://github.com/Kotlin/kotlinx.coroutines)                              |
| ktor-client-content-negotiation       | Copyright © 2000-2023 JetBrains s.r.o.                                                                        | Apache 2.0           | [連結↗](https://ktor.io)                                                           |
| ktor-client-core                      | Copyright © 2000-2023 JetBrains s.r.o.                                                                        | Apache 2.0           | [連結↗](https://ktor.io)                                                           |
| ktor-client-okhttp                    | Copyright © 2000-2023 JetBrains s.r.o.                                                                        | Apache 2.0           | [連結↗](https://ktor.io)                                                           |
| ktor-http                             | Copyright © 2000-2023 JetBrains s.r.o.                                                                        | Apache 2.0           | [連結↗](https://ktor.io)                                                           |
| ktor-serialization-kotlinx-json       | Copyright © 2000-2023 JetBrains s.r.o.                                                                        | Apache 2.0           | [連結↗](https://ktor.io)                                                           |
| LWJGL - Lightweight Java Game Library | Copyright © 2012-present Lightweight Java Game Library All rights reserved.                                   | BSD 3-Clause License | [連結↗](https://github.com/LWJGL/lwjgl3)                                           |
| material-color-utilities              | Copyright 2021 Google LLC                                                                                     | Apache 2.0           | [連結↗](https://github.com/material-foundation/material-color-utilities)           |
| Maven Artifact                        | Copyright © The Apache Software Foundation                                                                    | Apache 2.0           | [連結↗](https://github.com/apache/maven/tree/maven-3.9.9/maven-artifact)           |
| Media3                                | Copyright © The Android Open Source Project                                                                   | Apache 2.0           | [連結↗](https://developer.android.com/jetpack/androidx/releases/media3)            |
| Mesa                                  | Copyright © The Mesa Authors                                                                                  | MIT License          | [連結↗](https://mesa3d.org/)                                                       |
| MobileGlues                           | Copyright © 2025-2026 MobileGL-Dev                                                                           | LGPL-2.1 License     | [連結↗](https://github.com/MobileGL-Dev/MobileGlues)                         |
| MMKV                                  | Copyright © 2018 THL A29 Limited, a Tencent company.                                                          | BSD 3-Clause License | [連結↗](https://github.com/Tencent/MMKV)                                           |
| Navigation 3                          | Copyright © The Android Open Source Project                                                                   | Apache 2.0           | [連結↗](https://developer.android.com/jetpack/androidx/releases/navigation3)       |
| NG-GL4ES                              | Copyright © 2016-2018 Sebastien Chevalier; Copyright © 2013-2016 Ryan Hileman; Copyright (c) 2025-2026 BZLZHH | MIT License          | [連結↗](https://github.com/BZLZHH/NG-GL4ES)                                        |
| OkHttp                                | Copyright © 2019 Square, Inc.                                                                                 | Apache 2.0           | [連結↗](https://github.com/square/okhttp)                                          |
| Okio                                  | Copyright © 2013 Square, Inc.                                                                                 | Apache 2.0           | [連結↗](https://square.github.io/okio/)                                            |
| OpenNBT                               | Copyright © 2013-2021 Steveice10.                                                                             | MIT License          | [連結↗](https://github.com/GeyserMC/OpenNBT)                                       |
| Process Phoenix                       | Copyright © 2015 Jake Wharton                                                                                 | Apache 2.0           | [連結↗](https://github.com/JakeWharton/ProcessPhoenix)                             |
| proxy-client-android                  | -                                                                                                             | LGPL-3.0 License     | [連結↗](https://github.com/TouchController/TouchController)                        |
| Reorderable                           | Copyright © 2023 Calvin Liang                                                                                 | Apache 2.0           | [連結↗](https://github.com/Calvin-LL/Reorderable)                                  |
| sdl2-compat                           | Copyright (C) 2026 Sam Lantinga <slouken@libsdl.org>                                                          | Zlib License         | [連結↗](https://github.com/libsdl-org/sdl2-compat)                                 |
| SDL3                                  | Copyright (C) 1997-2026 Sam Lantinga <slouken@libsdl.org>                                                     | Zlib License         | [連結↗](https://github.com/libsdl-org/SDL)                                         |
| SimpleFPEWrapper                      | Copyright © 2026 MobileGL-Dev                                                                                | LGPL-3.0 License     | [連結↗](https://github.com/MobileGL-Dev/SimpleFPEWrapper)                    |
| sora-editor                           | Copyright (C) 2020-2026  Rosemoe                                                                              | LGPL-2.1 License     | [連結↗](https://github.com/Rosemoe/sora-editor)                                    |
| StringFog                             | Copyright © 2016-2023, Megatron King                                                                          | Apache 2.0           | [連結↗](https://github.com/MegatronKing/StringFog)                                 |
| tm4e (TextMate for Eclipse)           | Copyright © Eclipse Foundation                                                                                | EPL-2.0 License      | [連結↗](https://github.com/eclipse-tm4e/tm4e)                                      |
| XZ for Java                           | Copyright © The XZ for Java authors and contributors                                                          | 0BSD License         | [連結↗](https://tukaani.org/xz/java.html)                                          |