# Platform Support Status

|                            | Windows                             | Linux                                   | Mac OS                          | FreeBSD |
|----------------------------|:------------------------------------|:----------------------------------------|:--------------------------------|:--------|
| x86-64                     | F                                   | F                                       | F                               | N       |
| x86                        | F                                   | F                                       | /                               | /       |
| ARM64                      | P (1.19+)<br/>F (use x86 emulation) | T                                       | P (1.19+)<br/>F (use Rosetta 2) | /       |
| ARM32                      | /                                   | T                                       | /                               | /       |
| MIPS64el                   | /                                   | N                                       | /                               | /       |
| LoongArch64                | /                                   | F (for Old World)<br/>N (for New World) | /                               | /       |
| PowerPC-64 (Little-Endian) | /                                   | L                                       | /                               | /       |
| S390x                      | /                                   | L                                       | /                               | /       |
| RISC-V                     | /                                   | N                                       | /                               | /       |

* F: Fully supported platform.

  Supports all versions of Minecraft, including classic, alpha, beta, official release and snapshot versions.

  Fully supported by Mojang official. Problems encountered in the game should be directly reported to the Mojang.

* Y: Supported platforms.

  All official releases of Minecraft 1.6 and above are supported, snapshot versions may also work.

  Support is provided by HMCL, tested to work, but may have more issues than a fully supported platform.
  If you encounter a problem that does not exist on fully supported platforms, you can report it to HMCL.

* P: Partially supported platforms.

  Supports some versions of Minecraft, and more versions are still being adapted.

* L: Low level supported platforms.

  HMCL can run on this platform and has some basic support.
  However, launching the game directly is not yet available.
  If you want to start the game, 
  you'll need to get the native libraries needed by Minecraft in other way and specify the native path in the instance settings.

* N: Not currently supported, but plans to support it in the future.

  It is not possible to run HMCL directly on this platform, but we have plans to support it.

* /: Not support.

  We have no plans to support these platforms at this time, mainly because we don't have the equipment to test them.
  If you can help us adapt, please file a support request via issue.