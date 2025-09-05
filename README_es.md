# ⛏ Hello Minecraft! Launcher 💎

[![Build Status](https://ci.huangyuhui.net/job/HMCL/badge/icon?.svg)](https://ci.huangyuhui.net/job/HMCL)
![Downloads](https://img.shields.io/github/downloads/HMCL-dev/HMCL/total?style=flat)
![Stars](https://img.shields.io/github/stars/HMCL-dev/HMCL?style=flat)
[![Discord](https://img.shields.io/discord/995291757799538688.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/jVvC7HfM6U)
[![QQ Group](https://img.shields.io/badge/QQ-HMCL-bright?label=&logo=qq&logoColor=ffffff&color=1EBAFC&labelColor=1DB0EF&logoSize=auto)](https://docs.hmcl.net/groups.html)

[English](README.md) | [简体中文](README_zh.md) | [繁體中文](README_zh_Hant.md) | [文言文](README_lzh.md) | [日本語](README_ja.md) |
**español** | [русский](README_ru.md) | [українська](README_uk.md)

## Introducción

HMCL es un lanzador de Minecraft de código abierto y multiplataforma que admite la gestión de mods, personalización del juego, instalación de ModLoaders (Forge, NeoForge, Fabric, Quilt, LiteLoader y OptiFine), creación de modpacks, personalización de la interfaz de usuario y más.

HMCL tiene increíbles capacidades multiplataforma. No solo funciona en diferentes sistemas operativos como Windows, Linux, macOS y FreeBSD, sino que también es compatible con varias arquitecturas de CPU como x86, ARM, RISC-V, MIPS y LoongArch. Puedes disfrutar fácilmente de Minecraft en diferentes plataformas a través de HMCL.

Para los sistemas y arquitecturas de CPU compatibles con HMCL, consulta [esta tabla](docs/PLATFORM.md).

## Descarga

Descarga la última versión desde el [sitio web oficial](https://hmcl.huangyuhui.net/download).

También puedes encontrar la última versión de HMCL en [GitHub Releases](https://github.com/HMCL-dev/HMCL/releases).

Aunque no es necesario, se recomienda descargar las versiones solo de los sitios web oficiales mencionados anteriormente.

## Licencia

The software is distributed under [GPLv3](https://www.gnu.org/licenses/gpl-3.0.html) license with the following additional terms:

### Additional terms under GPLv3 Section 7

1. When you distribute a modified version of the software, you must change the software name or the version number in a reasonable way in order to distinguish it from the original version. (Under [GPLv3, 7(c)](https://github.com/HMCL-dev/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L372-L374))

   The software name and the version number can be edited [here](https://github.com/HMCL-dev/HMCL/blob/javafx/HMCL/src/main/java/org/jackhuang/hmcl/Metadata.java#L33-L35).

2. You must not remove the copyright declaration displayed in the software. (Under [GPLv3, 7(b)](https://github.com/HMCL-dev/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L368-L370))

## Contribución

Si deseas enviar un pull request, aquí tienes algunos requisitos:

* IDE: IntelliJ IDEA
* Compilador: Java 17+
* NO modifiques los archivos de `gradle`

### Compilación

Simplemente ejecuta el siguiente comando en el directorio raíz del proyecto:

```bash
./gradlew clean build
```

Asegúrate de tener instalado Java 17 o una versión posterior.

## Opciones de JVM (para depuración)

| Parámetro                                         | Descripción                                                                                                     |
|---------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| `-Dhmcl.home=<ruta>`                              | Sobrescribe el directorio de HMCL                                                                               |
| `-Dhmcl.self_integrity_check.disable=true`        | Omite la verificación de integridad propia al buscar actualizaciones                                            |
| `-Dhmcl.bmclapi.override=<url>`                   | Sobrescribe la raíz de la API del proveedor de descargas BMCLAPI. Por defecto `https://bmclapi2.bangbang93.com` |
| `-Dhmcl.font.override=<familia de fuente>`        | Sobrescribe la familia de fuente                                                                                |
| `-Dhmcl.version.override=<versión>`               | Sobrescribe el número de versión                                                                                |
| `-Dhmcl.update_source.override=<url>`             | Sobrescribe la fuente de actualizaciones de HMCL                                                                |
| `-Dhmcl.authlibinjector.location=<ruta>`          | Usa el authlib-injector especificado (en vez de descargar uno)                                                  |
| `-Dhmcl.openjfx.repo=<url del repositorio maven>` | Añade un repositorio Maven personalizado para descargar OpenJFX                                                 |
| `-Dhmcl.native.encoding=<codificación>`           | Sobrescribe la codificación nativa                                                                              |
| `-Dhmcl.microsoft.auth.id=<ID de App>`            | Sobrescribe el ID de la App OAuth de Microsoft                                                                  |
| `-Dhmcl.microsoft.auth.secret=<Secreto de App>`   | Sobrescribe el secreto de la App OAuth de Microsoft                                                             |
| `-Dhmcl.curseforge.apikey=<Clave API>`            | Sobrescribe la clave API de CurseForge                                                                          |