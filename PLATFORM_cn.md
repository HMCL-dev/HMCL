# 平台支持状态

[English](PLATFORM.md) | **简体中文** | [繁體中文](PLATFORM_tw.md)

|                            | Windows                                           | Linux                      | macOS                                                                  | FreeBSD                   |
|----------------------------|:--------------------------------------------------|:---------------------------|:-----------------------------------------------------------------------|:--------------------------|
| x86-64                     | ✅️                                                | ✅️                         | ✅️                                                                     | 👌(Minecraft 1.13~1.21.7) |
| x86                        | ✅️ (~1.20.4)                                      | ✅️ (~1.20.4)               | /                                                                      | /                         |
| ARM64                      | 👌 (Minecraft 1.8~1.18.2)<br/>✅ (Minecraft 1.19+) | 👌 (Minecraft 1.8~1.21.7)  | 👌 (Minecraft 1.6~1.18.2)<br/>✅ (Minecraft 1.19+)<br/>✅ (使用 Rosetta 2) | ❔                         |
| ARM32                      | /️                                                | 👌 (Minecraft 1.8~1.20.1)  | /                                                                      | /                         |
| MIPS64el                   | /                                                 | 👌 (Minecraft 1.8~1.20.1)  | /                                                                      | /                         |
| RISC-V 64                  | /                                                 | 👌 (Minecraft 1.13~1.21.7) | /                                                                      | /                         |
| LoongArch64                | /                                                 | 👌 (Minecraft 1.6~1.21.7)  | /                                                                      | /                         |
| LoongArch64 (旧世界)          | /                                                 | 👌 (Minecraft 1.6~1.20.1)  | /                                                                      | /                         |
| PowerPC-64 (Little-Endian) | /                                                 | ❔                          | /                                                                      | /                         |
| S390x                      | /                                                 | ❔                          | /                                                                      | /                         |

图例：

* ✅: 官方支持的平台

  受 Mojang 官方支持。在游戏中遇到的问题应该直接向 Mojang 反馈。

* 👌: 支持的平台

  由 HMCL 提供支持，经过测试可以正常运行，但可能比得到全面支持的平台有更多问题。  
  不保证支持 Minecraft 1.6 以下的版本。  
  如果你遇到在得到全面支持的平台上不存在的问题，可以向 HMCL 反馈。

* ❔: 低级别支持的平台

  HMCL 可以在这个平台上运行，并且有一些基本的支持。但是，还不能正常地启动游戏。  
  如果你想正常启动游戏，则需要通过其他方式获得游戏所需的本地库 (LWJGL)，并在（全局）游戏设置中指定本地库路径。

* `/`: 不支持的平台

  我们目前还没有打算支持这些平台，主要是因为我们没有测试这些平台的设备。  
  如果你能帮助我们进行测试，请通过提交 Issue 提出支持请求。