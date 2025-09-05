# ⛏ Hello Minecraft! Launcher 💎

[![Build Status](https://ci.huangyuhui.net/job/HMCL/badge/icon?.svg)](https://ci.huangyuhui.net/job/HMCL)
![Downloads](https://img.shields.io/github/downloads/HMCL-dev/HMCL/total?style=flat)
![Stars](https://img.shields.io/github/stars/HMCL-dev/HMCL?style=flat)
[![Discord](https://img.shields.io/discord/995291757799538688.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/jVvC7HfM6U)
[![QQ Group](https://img.shields.io/badge/QQ-HMCL-bright?label=&logo=qq&logoColor=ffffff&color=1EBAFC&labelColor=1DB0EF&logoSize=auto)](https://docs.hmcl.net/groups.html)

[English](README.md) | [简体中文](README_zh.md) | [繁體中文](README_zh_Hant.md) | [文言文](README_lzh.md) | [日本語](README_ja.md) |
[español](README_es.md) | [русский](README_ru.md) | **українська**

## Вступ

HMCL — це відкритий, кросплатформний лаунчер для Minecraft, який підтримує керування модами, налаштування гри, встановлення модлоадерів (Forge, NeoForge, Fabric, Quilt, LiteLoader та OptiFine), створення модпаків, налаштування інтерфейсу та багато іншого.

HMCL має чудові кросплатформні можливості. Він працює не лише на різних операційних системах, таких як Windows, Linux, macOS і FreeBSD, а й підтримує різні архітектури процесорів, такі як x86, ARM, RISC-V, MIPS і LoongArch. Ви можете легко насолоджуватися Minecraft на різних платформах за допомогою HMCL.

Щодо підтримуваних систем і архітектур процесорів дивіться [цю таблицю](docs/PLATFORM.md).

## Завантаження

Завантажте останню версію з [офіційного сайту](https://hmcl.huangyuhui.net/download).

Також ви можете знайти останню версію HMCL у [релізах GitHub](https://github.com/HMCL-dev/HMCL/releases).

Хоча це не обовʼязково, рекомендується завантажувати релізи лише з офіційних сайтів, зазначених вище.

### Ліцензія

The software is distributed under [GPLv3](https://www.gnu.org/licenses/gpl-3.0.html) license with the following additional terms:

### Additional terms under GPLv3 Section 7

1. When you distribute a modified version of the software, you must change the software name or the version number in a reasonable way in order to distinguish it from the original version. (Under [GPLv3, 7(c)](https://github.com/HMCL-dev/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L372-L374))

   The software name and the version number can be edited [here](https://github.com/HMCL-dev/HMCL/blob/javafx/HMCL/src/main/java/org/jackhuang/hmcl/Metadata.java#L33-L35).

2. You must not remove the copyright declaration displayed in the software. (Under [GPLv3, 7(b)](https://github.com/HMCL-dev/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L368-L370))

## Внесок

Якщо ви хочете надіслати pull request, ознайомтеся з наступними вимогами:

* IDE: IntelliJ IDEA
* Компілятор: Java 17+
* НЕ змінюйте файли `gradle`

### Компіляція

Просто виконайте наступну команду в кореневому каталозі проєкту:

```bash
./gradlew clean build
```

Переконайтеся, що у вас встановлено Java 17 або новішої версії.

## JVM Options (for debugging)

| Parameter                                    | Description                                                                                   |
|----------------------------------------------|-----------------------------------------------------------------------------------------------|
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
