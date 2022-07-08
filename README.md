# ⛏ Hello Minecraft! Launcher 💎
[![Build Status](https://ci.huangyuhui.net/job/HMCL/badge/icon?.svg)](https://ci.huangyuhui.net/job/HMCL)

Join chat! ![Discord](https://img.shields.io/discord/879708313435123742?style=plastic) [Discord](https://discord.gg/jVvC7HfM6U)  [KaiHeiLa](https://kaihei.co/Kx7n3t)

## Introduction

HMCL is a Minecraft launcher which supports Mod management, game customizing, auto installing(Forge, Fabric, LiteLoader and OptiFine), modpack creating, UI customization, and more.

No plugin API is provided.

## Download
Download the latest version [from the official website](https://hmcl.huangyuhui.net/download).

Note: The recent version released in Github is the beta version, which contains extra testing functions compared to the release versions on the official website. However, they may be unstable and you're more likely to encounter bugs or unexpected problems.

Although not necessary, it is recommended to download the ones from the official website.

## License
The software is distributed under [GPL v3](https://www.gnu.org/licenses/gpl-3.0.html) with additional terms.

### Additional terms under GPLv3 Section 7
1. When you distribute a modified version of the software, you must change the software name or the version number in a reasonable way in order to distinguish it from the original version. \[[under GPLv3, 7(c).](https://github.com/huanghongxun/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L372-L374)\]

   The software name and the version number can be edited [here](https://github.com/huanghongxun/HMCL/blob/javafx/HMCL/src/main/java/org/jackhuang/hmcl/Metadata.java#L32-L34).

2. You must not remove the copyright declaration displayed in the software. \[[under GPLv3, 7(b).](https://github.com/huanghongxun/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L368-L370)\]

Chinese Translation:
### 附加条款（依据 GPLv3 协议第七条）
1. 当你分发本程序的修改版本时，你必须以一种合理的方式修改本程序的名称或版本号，以示其与原始版本不同。\[[依据 GPLv3, 7(c).](https://github.com/huanghongxun/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L372-L374)\]

   本程序的名称及版本号可在[此处](https://github.com/huanghongxun/HMCL/blob/javafx/HMCL/src/main/java/org/jackhuang/hmcl/Metadata.java#L32-L34)修改。

2. 你不得移除本程序所显示的版权声明。\[[依据 GPLv3, 7(b).](https://github.com/huanghongxun/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L368-L370)\]

## Contribution

If you want to submit a pull request, there are some requirements:
* IDE: Intellij IDEA.
* Compiler: Java 1.8.
* Do NOT modify `gradle` files.

### Compilation

Simply execute the following command:
```bash
./gradlew clean build
```
Make sure you have Java installed with JavaFX 8 at least. Liberica full JDK 8 or later is recommended.

## JVM Options (for debugging)
|Parameter|Description|
|---------|-----------|
|`-Dhmcl.self_integrity_check.disable=true`|Bypass the self integrity check when checking for update.|
|`-Dhmcl.bmclapi.override=<version>`|Override api root of BMCLAPI download provider, defaults to `https://bmclapi2.bangbang93.com`. e.g. `https://download.mcbbs.net`.|
|`-Dhmcl.font.override=<font family>`|Override font family.|
|`-Dhmcl.version.override=<version>`|Override the version number.|
|`-Dhmcl.update_source.override=<url>`|Override the update source.|
|`-Dhmcl.authlibinjector.location=<path>`|Use specified authlib-injector (instead of downloading one).|
|`-Dhmcl.openjfx.repo=<maven repository url>`|Add custom maven repository for download OpenJFX.|
|`-Dhmcl.native.encoding=<encoding>`|Override the native encoding.|
|`-Dhmcl.microsoft.auth.id=<App ID>`|Override Microsoft OAuth App ID.|
|`-Dhmcl.microsoft.auth.secret=<App Secret>`|Override Microsoft OAuth App secret.|
