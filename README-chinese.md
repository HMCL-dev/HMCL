# Hello Minecraft! Launcher [![Build Status](https://ci.huangyuhui.net/job/HMCL/badge/icon?.svg)](https://ci.huangyuhui.net/job/HMCL)

English|[简体中文](README-chinese.md)

加入聊天！ [Discord](https://discord.gg/jVvC7HfM6U) [开黑啦](https://kaihei.co/Kx7n3t)

## 介绍

Hello Minecraft! Launcher (HMCL) 是一款 Minecraft启动器 ，支持Mod管理、游戏定制、自动安装（Forge、Fabric、LiteLoader和OptiFine）、整合包制作、UI定制……

HMCL没有提供插件API。

## 下载

从[官方网站](https://hmcl.huangyuhui.net/download)上下载最新版本

注意：Github Releases中的最新版本是测试版，与官方网站上的稳定版相比，它包含额外的测试功能。但是，测试版可能不稳定，您更有可能遇到bug或意外问题。

如果没有必要，建议从官方网站下载。

## 赞助

​      您的赞助将帮助 HMCL      获得更好的发展、支持稳定高速的游戏安装与文件下载服务。    

​      访问[爱发电主页](https://afdian.net/@huanghongxun)以获得详细信息    

## 许可证

该软件在 [GPL v3](https://www.gnu.org/licenses/gpl-3.0.html) 下分发，附带附加条款。

## 附加条款（依据 GPLv3 协议第七条）

1. 当你分发本程序的修改版本时，你必须以一种合理的方式修改本程序的名称或版本号，以示其与原始版本不同。\[[依据 GPLv3, 7(c).](https://github.com/huanghongxun/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L372-L374)\]

   本程序的名称及版本号可在[此处](https://github.com/huanghongxun/HMCL/blob/javafx/HMCL/src/main/java/org/jackhuang/hmcl/Metadata.java#L32-L34)修改。

2. 你不得移除本程序所显示的版权声明。\[[依据 GPLv3, 7(b).](https://github.com/huanghongxun/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L368-L370)\]

## 贡献

如果要提交拉取请求，有一些要求：
* IDE：Intellij IDEA。
* 编译器：Java 1.8。
* 不要修改 `gradle` 文件。

### 编译

只需执行以下命令： 
```bash
./gradlew clean build
```
确保您至少安装了 JavaFX 8 。建议使用 Liberica 完整的 JDK 8 或更高版本。

## JVM 选项（用于调试）

|参数|说明|
|---------|-----------|
|`-Dhmcl.self_integrity_check.disable=true`|检查更新时绕过自检。|
|`-Dhmcl.bmclapi.override=<version>`|覆盖 BMCLAPI 下载提供程序的 api 根目录，默认为 `https://bmclapi2.bangbang93.com`。 例如 `https://download.mcbbs.net`。|
|`-Dhmcl.font.override=<font family>`|覆盖字体系列。|
|`-Dhmcl.version.override=<version>`|覆盖版本号。|
|`-Dhmcl.update_source.override=<url>`|覆盖更新源。|
|`-Dhmcl.authlibinjector.location=<path>`|使用指定的 authlib-injector（而不是下载）。|
|`-Dhmcl.openjfx.repo=<maven repository url>`|添加自定义 Maven 存储库以下载 OpenJFX。|
|`-Dhmcl.native.encoding=<encoding>`|覆盖本机编码。|
|`-Dhmcl.microsoft.auth.id=<App ID>`|覆盖 Microsoft OAuth App ID。|
|`-Dhmcl.microsoft.auth.secret=<App Secret>`|覆盖 Microsoft OAuth 应用密码。|
