# Hello Minecraft! Launcher

<!-- #BEGIN COPY -->
<!-- #PROPERTY NAME=BADGES -->
[![Downloads](https://img.shields.io/github/downloads/HMCL-dev/HMCL/total?label=Downloads&style=flat)](https://github.com/HMCL-dev/HMCL/releases)
![Stars](https://img.shields.io/github/stars/HMCL-dev/HMCL?style=flat)
[![Discord](https://img.shields.io/discord/995291757799538688.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/jVvC7HfM6U)
[![QQ Group](https://img.shields.io/badge/QQ-HMCL-bright?label=&logo=qq&logoColor=ffffff&color=1EBAFC&labelColor=1DB0EF&logoSize=auto)](https://docs.hmcl.net/groups.html)
<!-- #END COPY -->

<!-- #BEGIN LANGUAGE_SWITCHER -->
English ([Standard](README.md), [uʍoᗡ ǝpᴉsd∩](README_en_Qabs.md)) | 中文 ([简体](README_zh.md), [繁體](README_zh_Hant.md), [文言](README_lzh.md)) | **日本語** | [español](README_es.md) | [русский](README_ru.md) | [українська](README_uk.md)
<!-- #END LANGUAGE_SWITCHER -->

## 紹介

HMCLはオープンソースでクロスプラットフォーム対応のMinecraftランチャーです。Mod管理、ゲームカスタマイズ、Modローダーのインストール（Forge、NeoForge、Cleanroom、Fabric、Quilt、LiteLoader、OptiFine）、Modパック作成、UIカスタマイズなど、さまざまな機能をサポートしています。

HMCLは優れたクロスプラットフォーム性能を持っています。Windows、Linux、macOS、FreeBSDなどの異なるオペレーティングシステムだけでなく、x86、ARM、RISC-V、MIPS、LoongArchなどのさまざまなCPUアーキテクチャにも対応しています。HMCLを使えば、さまざまなプラットフォームでMinecraftを簡単に楽しむことができます。

HMCLが対応しているシステムやCPUアーキテクチャについては、[この表](PLATFORM.md)をご参照ください。

## ダウンロード

最新版は[公式サイト](https://hmcl.huangyuhui.net/download)からダウンロードできます。

また、[GitHub Releases](https://github.com/HMCL-dev/HMCL/releases)でも最新版を入手できます。

必要ではありませんが、上記の公式サイトからのみリリース版をダウンロードすることを推奨します。

## ライセンス

ライセンスについては [README.md](README.md#license) をご参照ください。

## コントリビューション

プルリクエストを送信したい場合、以下の要件を満たしてください。

* IDE：IntelliJ IDEA
* コンパイラ：Java 17以上

### コンパイル方法

ビルド方法については、[ビルドガイド](./Building.md)ページをご覧ください。

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
