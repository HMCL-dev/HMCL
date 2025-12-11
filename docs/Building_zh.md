# 构建指南

<!-- #BEGIN LANGUAGE_SWITCHER -->
[English](Building.md) | **中文**
<!-- #END LANGUAGE_SWITCHER -->

## 环境需求

构建 HMCL 启动器需要安装 JDK 17 (或更高版本)。你可以从此处下载它: [Download Liberica JDK](https://bell-sw.com/pages/downloads/#jdk-25-lts)。

在安装 JDK 后，请确保 `JAVA_HOME` 环境变量指向符合需求的 JDK 目录。
你可以这样查看 `JAVA_HOME` 指向的 JDK 版本:

<details>
<summary>Windows</summary>

PowerShell:
```
PS > & "$env:JAVA_HOME/bin/java.exe" -version
openjdk version "25" 2025-09-16 LTS
OpenJDK Runtime Environment (build 25+37-LTS)
OpenJDK 64-Bit Server VM (build 25+37-LTS, mixed mode, sharing)
```

</details>

<details>
<summary>Linux/macOS/FreeBSD</summary>

```
> $JAVA_HOME/bin/java -version
openjdk version "25" 2025-09-16 LTS
OpenJDK Runtime Environment (build 25+37-LTS)
OpenJDK 64-Bit Server VM (build 25+37-LTS, mixed mode, sharing)
```

</details>

## 获取 HMCL 源码

- 通过 [Git](https://git-scm.com/downloads) 可以获取最新源码:
  ```shell
  git clone https://github.com/HMCL-dev/HMCL.git
  cd HMCL
  ```
- 从 [GitHub Release 页面](https://github.com/HMCL-dev/HMCL/releases)可以手动下载特定版本的源码。

## 构建 HMCL

想要构建 HMCL，请切换到 HMCL 项目的根目录下，并执行以下命令:

```shell
./gradlew clean makeExecutables
```

构建出的 HMCL 程序文件位于根目录下的 `HMCL/build/libs` 子目录中。
