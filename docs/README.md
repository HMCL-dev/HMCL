# Hello Minecraft! Launcher

<!-- #BEGIN BLOCK -->
<!-- #PROPERTY NAME=BADGES -->
[![Downloads](https://img.shields.io/github/downloads/HMCL-dev/HMCL/total?label=Downloads&style=flat)](https://github.com/HMCL-dev/HMCL/releases)
![Stars](https://img.shields.io/github/stars/HMCL-dev/HMCL?style=flat)
[![Discord](https://img.shields.io/discord/995291757799538688.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/jVvC7HfM6U)
[![QQ Group](https://img.shields.io/badge/QQ-HMCL-bright?label=&logo=qq&logoColor=ffffff&color=1EBAFC&labelColor=1DB0EF&logoSize=auto)](https://docs.hmcl.net/groups.html)
<!-- #END BLOCK -->

<!-- #BEGIN LANGUAGE_SWITCHER -->
**English** (**Standard**, [uʍoᗡ ǝpᴉsd∩](README_en_Qabs.md)) | 中文 ([简体](README_zh.md), [繁體](README_zh_Hant.md), [文言](README_lzh.md)) | [日本語](README_ja.md) | [español](README_es.md) | [русский](README_ru.md) | [українська](README_uk.md)
<!-- #END LANGUAGE_SWITCHER -->

## Introduction

HMCL is an open-source, cross-platform Minecraft launcher that supports Mod Management, Game Customizing, ModLoader Installing (Forge, NeoForge, Cleanroom, Fabric, Quilt, LiteLoader, and OptiFine), Modpack Creating, UI Customization, and more.

HMCL has amazing cross-platform capabilities. Not only does it run on different operating systems like Windows, Linux, macOS, and FreeBSD, but it also supports various CPU architectures such as x86, ARM, RISC-V, MIPS, and LoongArch. You can easily enjoy Minecraft across different platforms through HMCL.

For systems and CPU architectures supported by HMCL, please refer to [this table](PLATFORM.md).

## Download

Download the latest version from the [official website](https://hmcl.huangyuhui.net/download).

You can also find the latest version of HMCL in [GitHub Releases](https://github.com/HMCL-dev/HMCL/releases).

Although not necessary, it is recommended only to download releases from the official websites listed above.

## License

The software is distributed under [GPLv3](https://www.gnu.org/licenses/gpl-3.0.html) license with the following additional terms:

### Additional terms under GPLv3 Section 7

1. When you distribute a modified version of the software, you must change the software name or the version number in a reasonable way in order to distinguish it from the original version. (Under [GPLv3, 7(c)](https://github.com/HMCL-dev/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L372-L374))

   The software name and the version number can be edited [here](https://github.com/HMCL-dev/HMCL/blob/javafx/HMCL/src/main/java/org/jackhuang/hmcl/Metadata.java#L33-L35).

2. You must not remove the copyright declaration displayed in the software. (Under [GPLv3, 7(b)](https://github.com/HMCL-dev/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L368-L370))

## Contribution

If you want to submit a pull request, here are some requirements:

* IDE: IntelliJ IDEA
* Compiler: Java 17+

### Compilation

See the [Build Guide](./Building.md) page.

## JVM Options (for debugging)

| Parameter                                    | Description                                                                                   |
| -------------------------------------------- | --------------------------------------------------------------------------------------------- |
| `-Dhmcl.home=<path>`                         | Override HMCL directory                                                                       |
| `-Dhmcl.self_integrity_check.disable=true`   | Bypass the self integrity check when checking for updates                                     |
| `-Dhmcl.bmclapi.override=<url>`              | Override API Root of BMCLAPI download provider. Defaults to `https://bmclapi2.bangbang93.com` |
| `-Dhmcl.font.override=<font family>`         | Override font family                                                                          |
| `-Dhmcl.version.override=<version>`          | Override the version number                                                                   |
| `-Dhmcl.update_source.override=<url>`        | Override the update source for HMCL itself                                                    |
| `-Dhmcl.authlibinjector.location=<path>`     | Use the specified authlib-injector (instead of downloading one)                               |
| `-Dhmcl.openjfx.repo=<maven repository url>` | Add custom Maven repository for downloading OpenJFX                                           |
| `-Dhmcl.native.encoding=<encoding>`          | Override the native encoding                                                                  |
| `-Dhmcl.microsoft.auth.id=<App ID>`          | Override Microsoft OAuth App ID                                                               |
| `-Dhmcl.microsoft.auth.secret=<App Secret>`  | Override Microsoft OAuth App Secret                                                           |
| `-Dhmcl.curseforge.apikey=<Api Key>`         | Override CurseForge API Key                                                                   |
