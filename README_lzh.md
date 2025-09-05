# Hello Minecraft! Launcher

[![Build Status](https://ci.huangyuhui.net/job/HMCL/badge/icon?.svg)](https://ci.huangyuhui.net/job/HMCL)
![Downloads](https://img.shields.io/github/downloads/HMCL-dev/HMCL/total?style=flat)
![Stars](https://img.shields.io/github/stars/HMCL-dev/HMCL?style=flat)
[![Discord](https://img.shields.io/discord/995291757799538688.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/jVvC7HfM6U)
[![QQ Group](https://img.shields.io/badge/QQ-HMCL-bright?label=&logo=qq&logoColor=ffffff&color=1EBAFC&labelColor=1DB0EF&logoSize=auto)](https://docs.hmcl.net/groups.html)

[English](README.md) | [简体中文](README_zh.md) | [繁體中文](README_zh_Hant.md) | **文言** | [日本語](README_ja.md) |
[español](README_es.md) | [русский](README_ru.md) | [українська](README_uk.md)

## 簡介

HMCL 是一款開源、跨平臺的 Minecraft 啟動器，支援模組管理、遊戲客製化、遊戲自動安裝 (Forge、NeoForge、Fabric、Quilt、LiteLoader 和 OptiFine)、模組包建立、介面客製化等功能。

HMCL 有著強大的跨平臺能力。它不僅支援 Windows、Linux、macOS、FreeBSD 等常見的作業系統，同時也支援 x86、ARM、RISC-V、MIPS、LoongArch 等不同的 CPU 架構。你可以使用 HMCL 在不同平臺上輕鬆地遊玩 Minecraft。

如果你想要了解 HMCL 對不同平臺的支援程度，請參見 [此表格](docs/PLATFORM_zh_Hant.md)。

## 下載

請從 [HMCL 官網](https://hmcl.huangyuhui.net/download) 下載最新版本的 HMCL。

你也可以在 [GitHub Releases](https://github.com/HMCL-dev/HMCL/releases) 中下載最新版本的 HMCL。

雖然並不強制，但仍建議透過 HMCL 官網下載啟動器。

## 開源協議

請參見 [README_zh_Hant.md](README_zh_Hant.md#開源協議)。

## 貢獻

如果你想提交一個 Pull Request，必須遵守如下要求：

* IDE：IntelliJ IDEA
* 編譯器：Java 17+
* **不要**修改 `gradle` 相關文件

### 編譯

於項目根目錄執行以下指令：

```bash
./gradlew clean build
```

請確保你至少安裝了 JDK 17 或更高版本。

## JVM 選項 (用於除錯)

| 參數                                         | 簡介                                                                 |
| -------------------------------------------- | -------------------------------------------------------------------- |
| `-Dhmcl.home=<path>`                         | 覆蓋 HMCL 使用者目錄                                                 |
| `-Dhmcl.self_integrity_check.disable=true`   | 檢查更新時不檢查本體完整性                                           |
| `-Dhmcl.bmclapi.override=<url>`              | 覆蓋 BMCLAPI 的 API Root，預設值為 `https://bmclapi2.bangbang93.com` |
| `-Dhmcl.font.override=<font family>`         | 覆蓋字族                                                             |
| `-Dhmcl.version.override=<version>`          | 覆蓋版本號                                                           |
| `-Dhmcl.update_source.override=<url>`        | 覆蓋 HMCL 更新來源                                                   |
| `-Dhmcl.authlibinjector.location=<path>`     | 使用指定的 authlib-injector (而非下載一個)                           |
| `-Dhmcl.openjfx.repo=<maven repository url>` | 添加用於下載 OpenJFX 的自訂 Maven 倉庫                               |
| `-Dhmcl.native.encoding=<encoding>`          | 覆蓋原生編碼                                                         |
| `-Dhmcl.microsoft.auth.id=<App ID>`          | 覆蓋 Microsoft OAuth App ID                                          |
| `-Dhmcl.microsoft.auth.secret=<App Secret>`  | 覆蓋 Microsoft OAuth App 金鑰                                        |
| `-Dhmcl.curseforge.apikey=<Api Key>`         | 覆蓋 CurseForge API 金鑰                                        |
