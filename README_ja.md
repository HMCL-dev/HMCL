# ⛏ Hello Minecraft! Launcher 💎

[![Build Status](https://ci.huangyuhui.net/job/HMCL/badge/icon?.svg)](https://ci.huangyuhui.net/job/HMCL)
![Downloads](https://img.shields.io/github/downloads/HMCL-dev/HMCL/total?style=flat)
![Stars](https://img.shields.io/github/stars/HMCL-dev/HMCL?style=flat)
[![Discord](https://img.shields.io/discord/995291757799538688.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/jVvC7HfM6U)
[![QQ Group](https://img.shields.io/badge/QQ-HMCL-bright?label=&logo=qq&logoColor=ffffff&color=1EBAFC&labelColor=1DB0EF&logoSize=auto)](https://docs.hmcl.net/groups.html)

[English](README.md) | [简体中文](README_zh.md) | [繁體中文](README_zh_Hant.md) | [文言文](README_lzh.md) | **日本語** |
[español](README_es.md) | [русский](README_ru.md) | [українська](README_uk.md)

## 紹介

HMCLはオープンソースでクロスプラットフォーム対応のMinecraftランチャーです。Mod管理、ゲームカスタマイズ、Modローダーのインストール（Forge、NeoForge、Fabric、Quilt、LiteLoader、OptiFine）、Modパック作成、UIカスタマイズなど、さまざまな機能をサポートしています。

HMCLは優れたクロスプラットフォーム性能を持っています。Windows、Linux、macOS、FreeBSDなどの異なるオペレーティングシステムだけでなく、x86、ARM、RISC-V、MIPS、LoongArchなどのさまざまなCPUアーキテクチャにも対応しています。HMCLを使えば、さまざまなプラットフォームでMinecraftを簡単に楽しむことができます。

HMCLが対応しているシステムやCPUアーキテクチャについては、[この表](docs/PLATFORM.md)をご参照ください。

## ダウンロード

最新版は[公式サイト](https://hmcl.huangyuhui.net/download)からダウンロードできます。

また、[GitHub Releases](https://github.com/HMCL-dev/HMCL/releases)でも最新版を入手できます。

必要ではありませんが、上記の公式サイトからのみリリース版をダウンロードすることを推奨します。

## ライセンス

The software is distributed under [GPLv3](https://www.gnu.org/licenses/gpl-3.0.html) license with the following additional terms:

### Additional terms under GPLv3 Section 7

1. When you distribute a modified version of the software, you must change the software name or the version number in a reasonable way in order to distinguish it from the original version. (Under [GPLv3, 7(c)](https://github.com/HMCL-dev/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L372-L374))

   The software name and the version number can be edited [here](https://github.com/HMCL-dev/HMCL/blob/javafx/HMCL/src/main/java/org/jackhuang/hmcl/Metadata.java#L33-L35).

2. You must not remove the copyright declaration displayed in the software. (Under [GPLv3, 7(b)](https://github.com/HMCL-dev/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L368-L370))

## コントリビューション

プルリクエストを送信したい場合、以下の要件を満たしてください。

* IDE：IntelliJ IDEA
* コンパイラ：Java 17以上
* `gradle`ファイルは変更しないでください

### コンパイル方法

プロジェクトのルートディレクトリで次のコマンドを実行してください。

```bash
./gradlew clean build
```

Java 17以上がインストールされていることを確認してください。

## JVMオプション（デバッグ用）

| パラメータ                                        | 説明                                                                          |
|----------------------------------------------|-----------------------------------------------------------------------------|
| `-Dhmcl.home=<path>`                         | HMCLディレクトリを上書きします                                                           |
| `-Dhmcl.self_integrity_check.disable=true`   | アップデート時の自己整合性チェックをバイパスします                                                   |
| `-Dhmcl.bmclapi.override=<url>`              | BMCLAPIダウンロードプロバイダーのAPIルートを上書きします。デフォルトは`https://bmclapi2.bangbang93.com`です |
| `-Dhmcl.font.override=<font family>`         | フォントファミリーを上書きします                                                            |
| `-Dhmcl.version.override=<version>`          | バージョン番号を上書きします                                                              |
| `-Dhmcl.update_source.override=<url>`        | HMCL本体のアップデートソースを上書きします                                                     |
| `-Dhmcl.authlibinjector.location=<path>`     | 指定したauthlib-injectorを使用します（ダウンロードせずに）                                       |
| `-Dhmcl.openjfx.repo=<maven repository url>` | OpenJFXダウンロード用のカスタムMavenリポジトリを追加します                                         |
| `-Dhmcl.native.encoding=<encoding>`          | ネイティブエンコーディングを上書きします                                                        |
| `-Dhmcl.microsoft.auth.id=<App ID>`          | Microsoft OAuthアプリIDを上書きします                                                 |
| `-Dhmcl.microsoft.auth.secret=<App Secret>`  | Microsoft OAuthアプリシークレットを上書きします                                             |
| `-Dhmcl.curseforge.apikey=<Api Key>`         | CurseForge APIキーを上書きします                                                     |
