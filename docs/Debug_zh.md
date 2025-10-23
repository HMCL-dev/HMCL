# 调试选项

> [!WARNING]
> 本文介绍的是 HMCL 的内部功能，我们不保证这些功能的稳定性，并且随时可能修改或删除这些功能。
>
> 使用这些功能时请务必小心，错误地使用这些功能可能会导致 HMCL 行为异常甚至崩溃。

HMCL 提供了一系列调试选项，用于控制启动器的行为。

这些选项可以通过环境变量或 JVM 参数指定。如果两者同时存在，那么 JVM 参数会覆盖环境变量的设置。

| 环境变量                        | JVM 参数                                       | 功能                             | 默认值                                                                                                         | 额外说明         |
|-----------------------------|----------------------------------------------|--------------------------------|-------------------------------------------------------------------------------------------------------------|--------------|
| `HMCL_JAVA_HOME`            |                                              | 指定用于启动 HMCL 的 Java             |                                                                                                             | 仅对 exe/sh 生效 |
| `HMCL_JAVA_OPTS`            |                                              | 指定启动 HMCL 时的默认 JVM 参数          |                                                                                                             | 仅对 exe/sh 生效 |
| `HMCL_FORCE_GPU`            |                                              | 指定是否强制使用 GPU 加速渲染              | `false`                                                                                                     |
| `HMCL_ANIMATION_FRAME_RATE` |                                              | 指定 HMCL 的动画帧率                  | `60`                                                                                                        |              |
| `HMCL_LANGUAGE`             |                                              | 指定 HMCL 的默认语言                  | 使用系统默认语言                                                                                                    |
|                             | `-Dhmcl.dir=<path>`                          | 指定 HMCL 的当前数据文件夹               | `./.hmcl`                                                                                                   |              |
|                             | `-Dhmcl.home=<path>`                         | 指定 HMCL 的用户数据文件夹               | Windows: `%APPDATA\.hmcl`<br>Linux/BSD: `$XDG_DATA_HOME/hmcl`<br>macOS: `~Library/Application Support/hmcl` |              |
|                             | `-Dhmcl.self_integrity_check.disable=true`   | 检查更新时不检查本体完整性                  |                                                                                                             |              |
|                             | `-Dhmcl.bmclapi.override=<url>`              | 指定 BMCLAPI 的 API Root          | `https://bmclapi2.bangbang93.com`                                                                           |              |
|                             | `-Dhmcl.discoapi.override=<url>`             | 指定 foojay Disco API 的 API Root | `https://api.foojay.io/disco/v3.0`                                                                          |
| `HMCL_FONT`                 | `-Dhmcl.font.override=<font family>`         | 指定 HMCL 默认字体                   | 使用系统默认字体                                                                                                    |              |
|                             | `-Dhmcl.update_source.override=<url>`        | 指定 HMCL 更新源                    | `https://hmcl.huangyuhui.net/api/update_link`                                                               |              |
|                             | `-Dhmcl.authlibinjector.location=<path>`     | 指定 authlib-injector JAR 文件的位置  | 使用 HMCL 内嵌的 authlib-injector                                                                                |              |
|                             | `-Dhmcl.openjfx.repo=<maven repository url>` | 添加用于下载 OpenJFX 的自定义 Maven 仓库   |                                                                                                             |              |
|                             | `-Dhmcl.native.encoding=<encoding>`          | 指定原生编码                         | 使用系统的本机编码                                                                                                   |              |
|                             | `-Dhmcl.microsoft.auth.id=<App ID>`          | 指定 Microsoft OAuth App ID      | 使用 HMCL 内置的 Microsoft OAuth App ID                                                                          |              |
|                             | `-Dhmcl.microsoft.auth.secret=<App Secret>`  | 指定 Microsoft OAuth App 密钥      | 使用 HMCL 内置的 Microsoft OAuth App 密钥                                                                          |              |
|                             | `-Dhmcl.curseforge.apikey=<Api Key>`         | 指定 CurseForge API 密钥           | 使用 HMCL 内置的 CurseForge API 密钥                                                                               |              |
|                             | `-Dhmcl.native.backend=<auto/jna/none>`      | 指定HMCL使用的本机后端                  | `auto`                                                                                                      |
|                             | `-Dhmcl.hardware.fastfetch=<true/false>`     | 指定是否使用 fastfetch 检测硬件信息        | `true`                                                                                                      |







