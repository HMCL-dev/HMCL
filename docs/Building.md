# Build Guide

<!-- #BEGIN LANGUAGE_SWITCHER -->
**English** | [中文](Building_zh.md)
<!-- #END LANGUAGE_SWITCHER -->

## Requirements

To build the HMCL launcher, you need to install JDK 17 (or higher). You can download it here: [Download Liberica JDK](https://bell-sw.com/pages/downloads/#jdk-25-lts).

After installing the JDK, make sure the `JAVA_HOME` environment variable points to the required JDK directory.
You can check the JDK version that `JAVA_HOME` points to like this:

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

## Get HMCL Source Code

- You can get the latest source code via [Git](https://git-scm.com/downloads):
  ```shell
  git clone https://github.com/HMCL-dev/HMCL.git
  cd HMCL
  ```
- You can manually download a specific version of the source code from the [GitHub Release page](https://github.com/HMCL-dev/HMCL/releases).

## Build HMCL

To build HMCL, switch to the root directory of the HMCL project and run the following command:

```shell
./gradlew clean makeExecutables
```

The built HMCL program files are located in the `HMCL/build/libs` subdirectory under the project root.
