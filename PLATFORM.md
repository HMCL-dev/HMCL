# Platform Support Status

|                            | Windows                             | Linux                                    | Mac OS                                              | FreeBSD |
|----------------------------|:------------------------------------|:-----------------------------------------|:----------------------------------------------------|:--------|
| x86-64                     | ✅️                                  | ✅️                                       | ✅️                                                  | ❔       |
| x86                        | ✅️                                  | ✅️                                       | /                                                   | /       |
| ARM64                      | 👌 (1.8+)<br/>✅ (use x86 emulation) | 👌                                       | 👌 (1.6~1.18.2)<br/>✅ (1.19+)<br/>✅ (use Rosetta 2) | ❔       |
| ARM32                      | /️                                  | 👌                                       | /                                                   | /       |
| MIPS64el                   | /                                   | 👌                                       | /                                                   | /       |
| LoongArch64                | /                                   | 👌 (for Old World)<br/>❔ (for New World) | /                                                   | /       |
| PowerPC-64 (Little-Endian) | /                                   | ❔                                        | /                                                   | /       |
| S390x                      | /                                   | ❔                                        | /                                                   | /       |
| RISC-V                     | /                                   | ❔                                        | /                                                   | /       |

Legend:

* ✅: Official supported platform.

  Fully supported by Mojang official. Problems encountered in the game should be directly reported to the Mojang.

* 👌: Supported platforms.

  Support is provided by HMCL, tested to work, but may have more issues than a fully supported platform.
  Support for versions below 1.6 is not guaranteed.
  If you encounter a problem that does not exist on fully supported platforms, you can report it to HMCL.

* ❔: Low level supported platforms.

  HMCL can run on this platform and has some basic support.
  However, launching the game directly is not yet available.
  If you want to start the game, 
  you'll need to get the native libraries needed by Minecraft in other way and specify the native path in the instance settings.

* /: Not applicable.

  We have no plans to support these platforms at this time, mainly because we don't have the equipment to test them.
  If you can help us adapt, please file a support request via issue.