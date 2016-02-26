# Hello Minecraft! Launcher [![Build Status](https://travis-ci.org/huanghongxun/HMCL.svg?branch=master)](https://travis-ci.org/huanghongxun/HMCL)
License is GPL v3, see http://www.gnu.org/licenses/gpl.html

## Introduction

This application is a Minecraft launcher that supports mod management, game modification, auto-installing Minecraft/Forge/LiteLoader/OptiFine, modpack manufacture, UI theme customization, and so on.

This application has more than 100,000 users and would be opened more than 500,000 times per day.

But the code of this app is not strong, I hope someone could join the development of this app and make this app stronger.


## Contribution
If you really want to join the development, here's some requests.
* The Development Environment is Netbeans 8.0.2 with plugin: Gradle Support.
* The project is built on Java 1.8 using the retrolambda backporting to Java 1.7. So DO NOT use the libraries of Java 8 like Stream APIs.
* DO NOT modify any file whose suffix is `gradle`.

## Code tree
* Package `hmc.util` contains all the utilities that HMCL and HMCSM depend on.
* Package `hmc.launcher` contains HMCL ui, a few utility codes.
* Package `hmc.launcher.core` contains all the Minecraft Launcher logic that the HMCL supports.
* Package `hmc.svrmgr` contains all HMCSM logic.
* Folder `HMCLAPI/src/main/resources/org/jackhuang/hellominecraft/lang` contains all the localization files that HMCL and HMCSM used. If you are good at translation, you could join the localization plan by opening a pull request.
