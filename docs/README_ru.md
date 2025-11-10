# Hello Minecraft! Launcher

<!-- #BEGIN COPY -->
<!-- #PROPERTY NAME=BADGES -->
[![Downloads](https://img.shields.io/github/downloads/HMCL-dev/HMCL/total?label=Downloads&style=flat)](https://github.com/HMCL-dev/HMCL/releases)
![Stars](https://img.shields.io/github/stars/HMCL-dev/HMCL?style=flat)
[![Discord](https://img.shields.io/discord/995291757799538688.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/jVvC7HfM6U)
[![QQ Group](https://img.shields.io/badge/QQ-HMCL-bright?label=&logo=qq&logoColor=ffffff&color=1EBAFC&labelColor=1DB0EF&logoSize=auto)](https://docs.hmcl.net/groups.html)
<!-- #END COPY -->

<!-- #BEGIN LANGUAGE_SWITCHER -->
English ([Standard](README.md), [uʍoᗡ ǝpᴉsd∩](README_en_Qabs.md)) | 中文 ([简体](README_zh.md), [繁體](README_zh_Hant.md), [文言](README_lzh.md)) | [日本語](README_ja.md) | [español](README_es.md) | **русский** | [українська](README_uk.md)
<!-- #END LANGUAGE_SWITCHER -->

## Введение

HMCL — это открытый, кроссплатформенный лаунчер для Minecraft с поддержкой управления модами, настройки игры, установки загрузчиков модов (Forge, NeoForge, Cleanroom, Fabric, Quilt, LiteLoader и OptiFine), создания модпаков, настройки интерфейса и многого другого.

HMCL обладает отличной кроссплатформенностью. Он работает не только на различных операционных системах, таких как Windows, Linux, macOS и FreeBSD, но и поддерживает различные архитектуры процессоров: x86, ARM, RISC-V, MIPS и LoongArch. Благодаря HMCL вы можете легко наслаждаться Minecraft на разных платформах.

Список поддерживаемых систем и архитектур процессоров HMCL смотрите в [этой таблице](PLATFORM.md).

## Загрузка

Скачайте последнюю версию с [официального сайта](https://hmcl.huangyuhui.net/download).

Также вы можете найти последнюю версию HMCL в [релизах на GitHub](https://github.com/HMCL-dev/HMCL/releases).

Хотя это не обязательно, рекомендуется скачивать релизы только с указанных выше официальных сайтов.

## Лицензия

См. [README.md](README.md#license).

## Вклад

Если вы хотите отправить pull request, ознакомьтесь с требованиями:

* IDE: IntelliJ IDEA
* Компилятор: Java 17+

### Сборка

См. страницу [Руководство по сборке](./Building.md).

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
