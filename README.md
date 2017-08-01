# Hello Minecraft! Launcher [![Build Status](https://travis-ci.org/huanghongxun/HMCL.svg?branch=master)](https://travis-ci.org/huanghongxun/HMCL)
GPL v3, see http://www.gnu.org/licenses/gpl.html

## Introduction

HMCL is a Minecraft launcher which supports Mod management, game customizing, auto installing(Forge, LiteLoader and OptiFine), modpack creating, UI customizing and so on.

## Contribution
If you want to submit a pull request, there're some requirements:
* IDE: Netbeans 8.1
* Compiler: Java 1.8 and libraries only supports Java 1.7(because of retrolambda).
* Do NOT modify `gradle` files.

## Code
* package `HMCLCore/org.jackhuang.hmcl.util`: HMCL development utilities.
* package `HMCL/org.jackhuang.hmcl`: HMCL UI core.
* package `HMCLCore/org.jackhuang.hmcl.core`: HMCL game launcher core.
* package `HMCLAPI(HMCL)/org.jackhuang.hmcl.api`: HMCL API, see API section.
* Folder `HMCLCore/src/main/resources/org/jackhuang/hmcl/lang` contains language files.

## Pay Attention
* When you do decide to modify this app, please and you MUST delete `HMCL/org.jackhuang.hmcl.util.CrashReporter`, or errors your code cause will be sent to my server.
* package `org.jackhuang.hmcl.util.logging`: repackaged Apache Log4j, Apache License 2.0.
* package `com.google.gson`: Apache License 2.0
* package `org.jackhuang.hmcl.laf.ui`: contains some NimbusLAF's code belonging to Sun Microsystems under LGPL.

## API
HMCLAPI is based on Event bus. There are all events below.
* org.jackhuang.hmcl.api.event
 - OutOfDateEvent - you can cancel checking new versions and upgrading by this event.
* org.jackhuang.hmcl.api.event.config
 - AuthenticatorChangedEvent
 - DownloadTypeChangedEvent
 - ThemeChangedEvent
* org.jackhuang.hmcl.api.event.launch
 - LaunchEvent
 - LaunchSucceededEvent
 - LaunchingStateChangedEvent
 - ProcessingLaunchOptionsEvent
 - ProcessingLoginResultEvent
* org.jackhuang.hmcl.api.event.process
 - JVMLaunchFailedEvent
 - JavaProcessExitedAbnormallyEvent
 - JavaProcessStartingEvent
 - JavaProcessStoppedEvent
* org.jackhuang.hmcl.api.event.version
 - LoadedOneVersionEvent
 - RefreshedVersionsEvent
 - RefreshingVersionsEvent

You can also add tabs to root window or add authenticators through IPlugin.

### Remember
* A valid plugin will have a main class that implements `org.jackhuang.hmcl.api.IPlugin`. HMCL will search all jar files in `plugins` folder and load classes that implements IPlugin.
* If you want to debug, use option: `--plugin=<Your IPlugin Class Name>` and add your jar to classpath.
* You'd better only access `org.jackhuang.hmcl.api.*`, and other classes may change in different versions.
