# ⛏ Hello Minecraft! Launcher 💎

[![Build Status](https://ci.huangyuhui.net/job/HMCL/badge/icon?.svg)](https://ci.huangyuhui.net/job/HMCL)
![Downloads](https://img.shields.io/github/downloads/HMCL-dev/HMCL/total?style=flat)
![Stars](https://img.shields.io/github/stars/HMCL-dev/HMCL?style=flat)
[![Discord](https://img.shields.io/discord/995291757799538688.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/jVvC7HfM6U)
[![QQ Group](https://img.shields.io/badge/QQ-HMCL-bright?label=&logo=qq&logoColor=ffffff&color=1EBAFC&labelColor=1DB0EF&logoSize=auto)](https://docs.hmcl.net/groups.html)

[English](README.md) | [简体中文](README_zh.md) | [繁體中文](README_zh_Hant.md) | [文言文](README_lzh.md) | [日本語](README_ja.md) |
[español](README_es.md) | **русский** | [українська](README_uk.md)

## Введение

HMCL — это открытый, кроссплатформенный лаунчер для Minecraft с поддержкой управления модами, настройки игры, установки загрузчиков модов (Forge, NeoForge, Fabric, Quilt, LiteLoader и OptiFine), создания модпаков, настройки интерфейса и многого другого.

HMCL обладает отличной кроссплатформенностью. Он работает не только на различных операционных системах, таких как Windows, Linux, macOS и FreeBSD, но и поддерживает различные архитектуры процессоров: x86, ARM, RISC-V, MIPS и LoongArch. Благодаря HMCL вы можете легко наслаждаться Minecraft на разных платформах.

Список поддерживаемых систем и архитектур процессоров HMCL смотрите в [этой таблице](docs/PLATFORM.md).

## Загрузка

Скачайте последнюю версию с [официального сайта](https://hmcl.huangyuhui.net/download).

Также вы можете найти последнюю версию HMCL в [релизах на GitHub](https://github.com/HMCL-dev/HMCL/releases).

Хотя это не обязательно, рекомендуется скачивать релизы только с указанных выше официальных сайтов.

## Лицензия

The software is distributed under [GPLv3](https://www.gnu.org/licenses/gpl-3.0.html) license with the following additional terms:

### Additional terms under GPLv3 Section 7

1. When you distribute a modified version of the software, you must change the software name or the version number in a reasonable way in order to distinguish it from the original version. (Under [GPLv3, 7(c)](https://github.com/HMCL-dev/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L372-L374))

   The software name and the version number can be edited [here](https://github.com/HMCL-dev/HMCL/blob/javafx/HMCL/src/main/java/org/jackhuang/hmcl/Metadata.java#L33-L35).

2. You must not remove the copyright declaration displayed in the software. (Under [GPLv3, 7(b)](https://github.com/HMCL-dev/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L368-L370))

## Вклад

Если вы хотите отправить pull request, ознакомьтесь с требованиями:

* IDE: IntelliJ IDEA
* Компилятор: Java 17+
* Не изменяйте файлы `gradle`

### Сборка

Выполните следующую команду в корневой директории проекта:

```bash
./gradlew clean build
```

Убедитесь, что у вас установлена Java 17 или новее.

## Параметры JVM (для отладки)

| Параметр                                      | Описание                                                                                                      |
|-----------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| `-Dhmcl.home=<путь>`                          | Переопределить директорию HMCL                                                                                |
| `-Dhmcl.self_integrity_check.disable=true`    | Отключить проверку целостности при проверке обновлений                                                        |
| `-Dhmcl.bmclapi.override=<url>`               | Переопределить корневой API-адрес провайдера загрузки BMCLAPI. По умолчанию `https://bmclapi2.bangbang93.com` |
| `-Dhmcl.font.override=<название шрифта>`      | Переопределить семейство шрифтов                                                                              |
| `-Dhmcl.version.override=<версия>`            | Переопределить номер версии                                                                                   |
| `-Dhmcl.update_source.override=<url>`         | Переопределить источник обновлений для самого HMCL                                                            |
| `-Dhmcl.authlibinjector.location=<путь>`      | Использовать указанный authlib-injector (вместо загрузки)                                                     |
| `-Dhmcl.openjfx.repo=<url репозитория maven>` | Добавить пользовательский Maven-репозиторий для загрузки OpenJFX                                              |
| `-Dhmcl.native.encoding=<кодировка>`          | Переопределить нативную кодировку                                                                             |
| `-Dhmcl.microsoft.auth.id=<App ID>`           | Переопределить Microsoft OAuth App ID                                                                         |
| `-Dhmcl.microsoft.auth.secret=<App Secret>`   | Переопределить Microsoft OAuth App Secret                                                                     |
| `-Dhmcl.curseforge.apikey=<Api Key>`          | Переопределить CurseForge API Key                                                                             |
