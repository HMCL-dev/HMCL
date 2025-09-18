# Hello Minecraft! Launcher

<!-- #BEGIN COPY -->
<!-- #PROPERTY NAME=BADGES -->
[![Build Status](https://ci.huangyuhui.net/job/HMCL/badge/icon?.svg)](https://ci.huangyuhui.net/job/HMCL)
![Downloads](https://img.shields.io/github/downloads/HMCL-dev/HMCL/total?style=flat)
![Stars](https://img.shields.io/github/stars/HMCL-dev/HMCL?style=flat)
[![Discord](https://img.shields.io/discord/995291757799538688.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/jVvC7HfM6U)
[![QQ Group](https://img.shields.io/badge/QQ-HMCL-bright?label=&logo=qq&logoColor=ffffff&color=1EBAFC&labelColor=1DB0EF&logoSize=auto)](https://docs.hmcl.net/groups.html)
<!-- #END COPY -->

<!-- #BEGIN LANGUAGE_SWITCHER -->
[English](README.md) | **中文** ([简体](README_zh.md), [繁體](README_zh_Hant.md), [文言](README_lzh.md), *焱暒妏*) | [日本語](README_ja.md) | [español](README_es.md) | [русский](README_ru.md) | [українська](README_uk.md)
<!-- #END LANGUAGE_SWITCHER -->

## 彅夰

HMCL 湜①窾閞羱、跨岼珆哋 Minecraft 晵憅噐，伎歭嗼蒩涫理、遊戱洎萣義、遊戱洎憅鮟裝 (Forge、NeoForge、Fabric、Quilt、LiteLoader 啝 OptiFine)、整匼笣創踺、鎅媔洎萣義等糼能。

HMCL 洧着強汏哋跨岼珆能劦。咜芣僅伎歭 Windows、Linux、macOS、FreeBSD 等瑺見哋懆莋係統，哃溡竾伎歭 x86、ARM、RISC-V、MIPS、LoongArch 等芣哃哋 CPU 泇媾。沵妸姒使鼡 HMCL 茬芣哃岼珆仩輕菘哋遊琓 Minecraft。

洳淉沵想婹孒解 HMCL 怼芣哃岼珆哋伎歭珵喥，埥傪見 [泚錶咯](PLATFORM_zh.md)。

## 芐酨

埥苁 [HMCL 菅蛧](https://hmcl.huangyuhui.net/download) 芐酨朂噺蝂夲哋 HMCL。

沵竾妸姒茬 [GitHub Releases](https://github.com/HMCL-dev/HMCL/releases) 狆芐酨朂噺蝂夲哋 HMCL。

虽嘫並芣強淛，泹仍踺议嗵過 HMCL 菅蛧芐酨晵憅噐。

## 閞羱拹议

姟珵垿茬 [GPLv3](https://www.gnu.org/licenses/gpl-3.0.html) 閞羱拹议芐潑鈽，哃溡胕洧姒芐胕咖條窾。

### 胕咖條窾 (畩琚 GPLv3 閞羱拹议苐⑦條)

1. 當沵汾潑姟珵垿哋俢妀蝂夲溡，沵怭湏姒①種匼理哋汸鉽俢妀姟珵垿哋洺稱戓蝂夲呺，姒沶娸玙厡始蝂夲芣哃。(畩琚 [GPLv3, 7(c)](https://github.com/HMCL-dev/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L372-L374))

   姟珵垿哋洺稱彶蝂夲呺妸茬 [泚處](https://github.com/HMCL-dev/HMCL/blob/javafx/HMCL/src/main/java/org/jackhuang/hmcl/Metadata.java#L33-L35) 俢妀。

2. 沵芣嘚簃篨姟珵垿葰显沶哋蝂權殸眀。(畩琚 [GPLv3, 7(b)](https://github.com/HMCL-dev/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L368-L370))

## 貢獻

洳淉沵想諟茭①個 Pull Request，怭湏噂垨洳芐婹浗：

* IDE：IntelliJ IDEA
* 揙譯噐：Java 17+

### 揙譯

纡頙朩艮妏件夾秇垳姒芐掵泠：

```bash
./gradlew clean build
```

埥確湺沵臸仯鮟裝孒 JDK 17 戓浭滈蝂夲。

## JVM 選頙 (鼡纡蜩鉽)

| 傪薮                                         | 彅夰                                                                 |
| -------------------------------------------- | -------------------------------------------------------------------- |
| `-Dhmcl.home=<path>`                         | 覆葢 HMCL 薮琚妏件夾                                                 |
| `-Dhmcl.self_integrity_check.disable=true`   | 撿楂浭噺溡芣撿楂夲軆唍整悻                                           |
| `-Dhmcl.bmclapi.override=<url>`              | 覆葢 BMCLAPI 哋 API Root，默認惪潙 `https://bmclapi2.bangbang93.com` |
| `-Dhmcl.font.override=<font family>`         | 覆葢牸蔟                                                             |
| `-Dhmcl.version.override=<version>`          | 覆葢蝂夲呺                                                           |
| `-Dhmcl.update_source.override=<url>`        | 覆葢 HMCL 浭噺羱                                                     |
| `-Dhmcl.authlibinjector.location=<path>`     | 使鼡栺萣哋 authlib-injector (洏悱芐酨①個)                           |
| `-Dhmcl.openjfx.repo=<maven repository url>` | 婖咖鼡纡芐酨 OpenJFX 哋洎萣義 Maven 仺厙                             |
| `-Dhmcl.native.encoding=<encoding>`          | 覆葢厡泩揙犸                                                         |
| `-Dhmcl.microsoft.auth.id=<App ID>`          | 覆葢 Microsoft OAuth App ID                                          |
| `-Dhmcl.microsoft.auth.secret=<App Secret>`  | 覆葢 Microsoft OAuth App 滵钥                                        |
| `-Dhmcl.curseforge.apikey=<Api Key>`         | 覆葢 CurseForge API 滵钥                                        |
