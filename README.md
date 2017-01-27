# Hello Minecraft! Launcher [![Build Status](https://travis-ci.org/huanghongxun/HMCL.svg?branch=master)](https://travis-ci.org/huanghongxun/HMCL)
开源协议为GPL v3, 详情参见http://www.gnu.org/licenses/gpl.html

## Introduction

HMCL is a Minecraft launcher which supports Mod management, game customizing, auto installing(Forge, LiteLoader and OptiFine), modpack creating, UI customizing and so on.

## Contribution
If you want to submit a pull request, there're some requirements:
* IDE: Netbeans 8.1
* Compiler: Java 1.8 and libraries only supports Java 1.7(because of retrolambda).
* Do NOT modify `gradle` files.

## Code
* package `org.jackhuang.hellominecraft.util`: HMCL development utilities.
* package `org.jackhuang.hellominecraft.launcher`: HMCL UI core.
* package `org.jackhuang.hellominecraft.launcher.core`: HMCL game launcher core.
* package `org.jackhuang.hellominecraft.launcher.api`: Nothing here!
* package `org.jackhuang.hellominecraft.svrmgr`: All HMCSM codes.
* Folder `HMCUtils/src/main/resources/org/jackhuang/hellominecraft/lang` contains language files.

## Pay Attention
* When you do decide to modify this app, please and you MUST delete `org.jackhuang.hellominecraft.launcher.util.CrashReporter`, or errors your code cause will be sent to my server.
* package `org.jackhuang.hellominecraft.util.logging`: repackaged Apache Log4j, Apache License 2.0.
* package `com.google.gson`: Apache License 2.0
* package `org.jackhuang.hellominecraft.lookandfeel.ui`: contains some NimbusLAF's code belonging to Sun Microsystems under LGPL.