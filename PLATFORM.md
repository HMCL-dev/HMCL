# Platform Support Status

**English** | [简体中文](PLATFORM_cn.md)

|                            | Windows                                           | Linux                     | macOS                                                                  | FreeBSD                  |
|----------------------------|:--------------------------------------------------|:--------------------------|:------------------------------------------------------------------------|:-------------------------|
| x86-64                     | ✅️                                                | ✅️                        | ✅️                                                                      | 👌 (Minecraft 1.13~1.21) |
| x86                        | ✅️ (~1.20.4)                                      | ✅️ (~1.20.4)              | /                                                                       | /                        |
| ARM64                      | 👌 (Minecraft 1.8~1.18.2)<br/>✅ (Minecraft 1.19+) | 👌 (Minecraft 1.8~1.20.6) | 👌 (Minecraft 1.6~1.18.2)<br/>✅ (Minecraft 1.19+)<br/>✅ (use Rosetta 2) | ❔                        |
| ARM32                      | /️                                                | 👌 (Minecraft 1.8~1.20.1) | /                                                                       | /                        |
| MIPS64el                   | /                                                 | 👌 (Minecraft 1.8~1.20.1) | /                                                                       | /                        |
| RISC-V 64                  | /                                                 | 👌 (Minecraft 1.13~1.21)  | /                                                                       | /                        |
| LoongArch64                | /                                                 | 👌 (Minecraft 1.6~1.20.1) | /                                                                       | /                        |
| PowerPC-64 (Little-Endian) | /                                                 | ❔                         | /                                                                       | /                        |
| S390x                      | /                                                 | ❔                         | /                                                                       | /                        |

Legend:

* ✅ Official supported platform.

  Fully supported by Mojang official. Problems encountered in the game should be directly reported to the Mojang.

* 👌 Supported platforms.

  Support is provided by HMCL, tested to work, but may have more issues than a fully supported platform.  
  Support for versions below Minecraft 1.6 is not guaranteed.  
  If you encounter a problem that does not exist on fully supported platforms, you can report it to HMCL.

* ❔ Low level supported platforms.

  HMCL can run on this platform and has some basic support. However, launching the game directly is not yet available.  
  If you want to start the game, you will need to get the native libraries needed by Minecraft in other way and specify the native path in the instance settings.

* ／ Not applicable.

  We have no plans to support these platforms at this time, mainly because we do not have the equipment to test them.  
  If you can help us adapt, please file a support request via Issues.
