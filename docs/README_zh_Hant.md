# Hello Minecraft! Launcher

<!-- #BEGIN COPY -->
<!-- #PROPERTY NAME=BADGES -->
[![Downloads](https://img.shields.io/github/downloads/HMCL-dev/HMCL/total?label=Downloads&style=flat)](https://github.com/HMCL-dev/HMCL/releases)
![Stars](https://img.shields.io/github/stars/HMCL-dev/HMCL?style=flat)
[![Discord](https://img.shields.io/discord/995291757799538688.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/jVvC7HfM6U)
[![QQ Group](https://img.shields.io/badge/QQ-HMCL-bright?label=&logo=qq&logoColor=ffffff&color=1EBAFC&labelColor=1DB0EF&logoSize=auto)](https://docs.hmcl.net/groups.html)
<!-- #END COPY -->

<!-- #BEGIN LANGUAGE_SWITCHER -->
English ([Standard](README.md), [uʍoᗡ ǝpᴉsd∩](README_en_Qabs.md)) | **中文** ([简体](README_zh.md), **繁體**, [文言](README_lzh.md)) | [日本語](README_ja.md) | [español](README_es.md) | [русский](README_ru.md) | [українська](README_uk.md)
<!-- #END LANGUAGE_SWITCHER -->

## 簡介

HMCL 是一款開源、跨平臺的 Minecraft 啟動器，支援模組管理、遊戲客製化、遊戲自動安裝 (Forge、NeoForge、Cleanroom、Fabric、Quilt、LiteLoader 和 OptiFine)、模組包建立、介面客製化等功能。

HMCL 有著強大的跨平臺能力。它不僅支援 Windows、Linux、macOS、FreeBSD 等常見的作業系統，同時也支援 x86、ARM、RISC-V、MIPS、LoongArch 等不同的 CPU 架構。你可以使用 HMCL 在不同平臺上輕鬆地遊玩 Minecraft。

如果你想要了解 HMCL 對不同平臺的支援程度，請參見 [此表格](PLATFORM_zh_Hant.md)。

## 下載

請從 [HMCL 官網](https://hmcl.huangyuhui.net/download) 下載最新版本的 HMCL。

你也可以在 [GitHub Releases](https://github.com/HMCL-dev/HMCL/releases) 中下載最新版本的 HMCL。

雖然並不強制，但仍建議透過 HMCL 官網下載啟動器。

## 開源協議

該程式在 [GPLv3](https://www.gnu.org/licenses/gpl-3.0.html) 開源協議下發布，同時附有以下附加條款。

### 附加條款 (依據 GPLv3 開源協議第七條)

1. 當你分發該程式的修改版本時，你必須以一種合理的方式修改該程式的名稱或版本號，以示其與原始版本不同。(依據 [GPLv3, 7(c)](https://github.com/HMCL-dev/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L372-L374))

   該程式的名稱及版本號可在 [此處](https://github.com/HMCL-dev/HMCL/blob/javafx/HMCL/src/main/java/org/jackhuang/hmcl/Metadata.java#L33-L35) 修改。

2. 你不得移除該程式所顯示的版權宣告。(依據 [GPLv3, 7(b)](https://github.com/HMCL-dev/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L368-L370))

## 貢獻

如果你想提交一個 Pull Request，必須遵守如下要求：

* IDE：IntelliJ IDEA
* 編譯器：Java 17+

### 編譯

參閱[構建指南](./Building_zh.md)頁面。

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
