# Hello Minecraft! Launcher

<!-- #BEGIN COPY -->
<!-- #PROPERTY NAME=BADGES -->
[![CNB](https://img.shields.io/badge/cnb-mirror-ff6200?logo=cloudnativebuild)](https://cnb.cool/HMCL-dev/HMCL)
[![Downloads](https://img.shields.io/github/downloads/HMCL-dev/HMCL/total?label=Downloads&style=flat)](https://github.com/HMCL-dev/HMCL/releases)
![Stars](https://img.shields.io/github/stars/HMCL-dev/HMCL?style=flat)
[![Discord](https://img.shields.io/discord/995291757799538688.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/jVvC7HfM6U)
[![QQ Group](https://img.shields.io/badge/QQ-HMCL-bright?label=&logo=qq&logoColor=ffffff&color=1EBAFC&labelColor=1DB0EF&logoSize=auto)](https://docs.hmcl.net/groups.html)
<!-- #END COPY -->

<!-- #BEGIN LANGUAGE_SWITCHER -->
English ([Standard](README.md), [uʍoᗡ ǝpᴉsd∩](README_en_Qabs.md)) | **中文** (**简体**, [繁體](README_zh_Hant.md), [文言](README_lzh.md)) | [日本語](README_ja.md) | [español](README_es.md) | [русский](README_ru.md) | [українська](README_uk.md)
<!-- #END LANGUAGE_SWITCHER -->

## 简介

HMCL 是一款开源、跨平台的 Minecraft 启动器，支持模组管理、游戏自定义、游戏自动安装 (Forge、NeoForge、Cleanroom、Fabric、Quilt、LiteLoader 和 OptiFine)、整合包创建、界面自定义等功能。

HMCL 有着强大的跨平台能力。它不仅支持 Windows、Linux、macOS、FreeBSD 等常见的操作系统，同时也支持 x86、ARM、RISC-V、MIPS、LoongArch 等不同的 CPU 架构。你可以使用 HMCL 在不同平台上轻松地游玩 Minecraft。

如果你想要了解 HMCL 对不同平台的支持程度，请参见 [此表格](PLATFORM_zh.md)。

## 下载

请从 [HMCL 官网](https://hmcl.huangyuhui.net/download) 下载最新版本的 HMCL。

你也可以在 [GitHub Releases](https://github.com/HMCL-dev/HMCL/releases) 中下载最新版本的 HMCL。

虽然并不强制，但仍建议通过 HMCL 官网下载启动器。

## 开源协议

该程序在 [GPLv3](https://www.gnu.org/licenses/gpl-3.0.html) 开源协议下发布，同时附有以下附加条款。

### 附加条款 (依据 GPLv3 开源协议第七条)

1. 当你分发该程序的修改版本时，你必须以一种合理的方式修改该程序的名称或版本号，以示其与原始版本不同。(依据 [GPLv3, 7(c)](https://github.com/HMCL-dev/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L372-L374))

   该程序的名称及版本号可在 [此处](https://github.com/HMCL-dev/HMCL/blob/javafx/HMCL/src/main/java/org/jackhuang/hmcl/Metadata.java#L33-L35) 修改。

2. 你不得移除该程序所显示的版权声明。(依据 [GPLv3, 7(b)](https://github.com/HMCL-dev/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L368-L370))

## 贡献

如果你想提交一个 Pull Request，必须遵守如下要求：

* IDE：IntelliJ IDEA
* 编译器：Java 17+

### 构建 HMCL

参见[构建指南](./Building_zh.md)页面。

## JVM 选项 (用于调试)

| 参数                                         | 简介                                                                 |
| -------------------------------------------- | -------------------------------------------------------------------- |
| `-Dhmcl.home=<path>`                         | 覆盖 HMCL 数据文件夹                                                 |
| `-Dhmcl.self_integrity_check.disable=true`   | 检查更新时不检查本体完整性                                           |
| `-Dhmcl.bmclapi.override=<url>`              | 覆盖 BMCLAPI 的 API Root，默认值为 `https://bmclapi2.bangbang93.com` |
| `-Dhmcl.font.override=<font family>`         | 覆盖字族                                                             |
| `-Dhmcl.version.override=<version>`          | 覆盖版本号                                                           |
| `-Dhmcl.update_source.override=<url>`        | 覆盖 HMCL 更新源                                                     |
| `-Dhmcl.authlibinjector.location=<path>`     | 使用指定的 authlib-injector (而非下载一个)                           |
| `-Dhmcl.openjfx.repo=<maven repository url>` | 添加用于下载 OpenJFX 的自定义 Maven 仓库                             |
| `-Dhmcl.native.encoding=<encoding>`          | 覆盖原生编码                                                         |
| `-Dhmcl.microsoft.auth.id=<App ID>`          | 覆盖 Microsoft OAuth App ID                                          |
| `-Dhmcl.microsoft.auth.secret=<App Secret>`  | 覆盖 Microsoft OAuth App 密钥                                        |
| `-Dhmcl.curseforge.apikey=<Api Key>`         | 覆盖 CurseForge API 密钥                                        |
