# Hello Minecraft! Launcher [![Build Status](https://ci.huangyuhui.net/job/HMCL/badge/icon?.svg)](https://ci.huangyuhui.net/job/HMCL)
GPL v3, see http://www.gnu.org/licenses/gpl.html

## Introduction

HMCL is a Minecraft launcher which supports Mod management, game customizing, auto installing(Forge, LiteLoader and OptiFine), modpack creating, UI customizing and so on.

No plugin API is provided.

## Contribution

If you want to submit a pull request, there're some requirements:
* IDE: Intellij IDEA.
* Compiler: Java 1.8.
* Do NOT modify `gradle` files.

## JVM Options (for debugging)
|Parameter|Description|
|---------|-----------|
|`-Dhmcl.self_integrity_check.disable=true`|Bypass the self integrity check when checking for update.|
|`-Dhmcl.version.override=<version>`|Override the version number.|
|`-Dhmcl.update_source.override=<url>`|Override the update source.|
|`-Dhmcl.authlibinjector.location=<path>`|Use specified authlib-injector (instead of downloading one).|
