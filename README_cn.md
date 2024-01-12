# ⛏ Hello Minecraft! Launcher 💎

[![Build Status](https://ci.huangyuhui.net/job/HMCL/badge/icon?.svg)](https://ci.huangyuhui.net/job/HMCL)
![Downloads](https://img.shields.io/github/downloads/huanghongxun/HMCL/total)
![Stars](https://img.shields.io/github/stars/huanghongxun/HMCL)
[![Discord](https://img.shields.io/discord/995291757799538688.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/jVvC7HfM6U)
[![KOOK](https://img.shields.io/badge/KOOK-HMCL-brightgreen)](https://kook.top/Kx7n3t)

[English](README.md) | 中文

## 简介

HMCL 是一款跨平台 Minecraft 启动器, 支持 Mod 管理, 游戏自定义, 游戏自动安装 (Forge, Fabric, Quilt, LiteLoader 与 OptiFine), 模组包创建, 界面自定义等功能.

HMCL 有着强大的跨平台能力. 它不仅支持 Windows、Linux、macOS 等常见的操作系统，同时也支持 x86、ARM、MIPS 和 LoongArch 等不同的 CPU 架构.
您可以使用 HMCL 在不同平台上轻松的游玩 Minecraft.

如果您想要了解 HMCL 对不同平台的支持程度，请参见[此表格](PLATFORM_cn.md).

## 下载

请从 [HMCL 官网](https://hmcl.huangyuhui.net/download) 下载最新版本的 HMCL.

你也可以在 [GitHub Releases](https://github.com/huanghongxun/HMCL/releases) 中下载最新版本的 HMCL.

虽然并不强制, 但仍建议通过 HMCL 官网下载启动器.

## 开源协议

该程序在 [GPLv3](https://www.gnu.org/licenses/gpl-3.0.html) 开源协议下发布, 同时附有附加条款.

### 附加条款 (依据 GPLv3 开源协议第七条)

1. 当您分发该程序的修改版本时, 您必须以一种合理的方式修改该程序的名称或版本号, 以示其与原始版本不同. (依据 [GPLv3, 7(c)](https://github.com/huanghongxun/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L372-L374))

   该程序的名称及版本号可在[此处](https://github.com/huanghongxun/HMCL/blob/javafx/HMCL/src/main/java/org/jackhuang/hmcl/Metadata.java#L33-L35)修改.

2. 您不得移除该程序所显示的版权声明. (依据 [GPLv3, 7(b)](https://github.com/huanghongxun/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L368-L370))

## 贡献

如果您想提交一个 Pull Request, 必须遵守如下要求:

* IDE: Intellij IDEA
* 编译器: Java 1.8
* **不要**修改 `gradle` 相关文件

### 编译

于项目根目录执行以下命令:

```bash
./gradlew clean build
```

请确保您至少安装了含有 JavaFX 8 的 Java. 建议使用 Liberica Full JDK 8 或更高版本.

### Dynamic Remote Resource

这是一个 HMCL 的功能，用于 mod_data.txt 和 modpack_data.txt 等需要动态变化资源的自动更新独立于 HMCL 的版本系统，方便更新。

为了网络连接不稳定的用户考虑，HMCL 依然会正常的内置一份需要更新的资源。并且，下载的资源会保存到 HMCL 的全局文件夹中，不会直接修改 HMCL 内置的文件，保存路径为：远程 -> HMCL 全局文件夹 -> 内置文件。

目前更新源为一个独立的 Dynamic Remote Resources 系统，更新 URL 在[此处](https://github.com/HMCL-dev/HMCL/blob/javafx/HMCL/src/main/java/org/jackhuang/hmcl/Metadata.java#L42)，如需更新资源，只需将资源更新并构建项目，并指定 [data-json/dynamic-remote-resources.json](https://github.com/HMCL-dev/HMCL/blob/javafx/data-json/dynamic-remote-resources.json) 为更新 URL 即可。

注意：当前 HMCL 指定的是站外资源，当更新资源时需要一并更新站外资源，详细见[此处](https://github.com/HMCL-dev/HMCL/blob/javafx/data-json#readme).

## JVM 选项 (用于调试)

| 参数                                             | 简介                                                                                              |
|------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `-Dhmcl.home=<path>`                           | 覆盖 HMCL 数据文件夹.                                                                                  |
| `-Dhmcl.self_integrity_check.disable=true`     | 检查更新时绕过本体完整性检查.                                                                                 |
| `-Dhmcl.bmclapi.override=<version>`            | 覆盖 BMCLAPI 的 API Root, 默认值为 `https://bmclapi2.bangbang93.com`. 例如 `https://download.mcbbs.net`. |
| `-Dhmcl.font.override=<font family>`           | 覆盖字族.                                                                                           |
| `-Dhmcl.version.override=<version>`            | 覆盖版本号.                                                                                          |
| ~~`-Dhmcl.update_source.override=<url>`~~      | 覆盖 HMCL 更新源（已弃用，请使用 `hmcl.hmcl_update_source.override`）.                                        |
| `-Dhmcl.hmcl_update_source.override=<url>`     | 覆盖 HMCL 更新源.                                                                                    |
| `-Dhmcl.resource_update_source.override=<url>` | 覆盖动态远程资源更新源.                                                                                    |
| `-Dhmcl.authlibinjector.location=<path>`       | 使用指定的 authlib-injector (而非下载一个).                                                                |
| `-Dhmcl.openjfx.repo=<maven repository url>`   | 添加用于下载 OpenJFX 的自定义 Maven 仓库                                                                    |
| `-Dhmcl.native.encoding=<encoding>`            | 覆盖原生编码.                                                                                         |
| `-Dhmcl.microsoft.auth.id=<App ID>`            | 覆盖 Microsoft OAuth App ID.                                                                      |
| `-Dhmcl.microsoft.auth.secret=<App Secret>`    | 覆盖 Microsoft OAuth App 密钥.                                                                      |
