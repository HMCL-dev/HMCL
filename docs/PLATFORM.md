# Platform Support Status

## Launcher Compatibility

<!-- #BEGIN BLOCK -->
<!-- #PROPERTY NAME=LAUNCHER_COMPATIBILITY -->
<table>
  <thead>
    <tr>
      <th></th>
      <th>Windows</th>
      <th>Linux</th>
      <th>macOS</th>
      <th>FreeBSD</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>x86-64</td>
      <td>
        ✅️ Fully supported (Windows 7 ~ Windows 11)
        <br>
        ✅️ Fully supported (Windows Server 2008 R2 ~ 2025)
      </td>
      <td>✅️ Fully supported</td>
      <td>✅️ Fully supported</td>
      <td>✅ Fully supported</td>
    </tr>
    <tr>
      <td>x86</td>
      <td>
        🕰️ Limited support (Windows 7 ~ Windows 10)
      </td>
      <td>🕰️ Limited support</td>
      <td>N/A</td>
      <td>N/A</td>
    </tr>
    <tr>
      <td>ARM64</td>
      <td>✅️ Fully supported</td>
      <td>✅️ Fully supported</td>
      <td>✅️ Fully supported</td>
      <td>N/A</td>
    </tr>
    <tr>
      <td>ARM32</td>
      <td>/️</td>
      <td>🕰️ Limited support</td>
      <td>N/A</td>
      <td>N/A</td>
    </tr>
    <tr>
      <td>MIPS64el</td>
      <td>N/A</td>
      <td>🕰️ Limited support</td>
      <td>N/A</td>
      <td>N/A</td>
    </tr>
    <tr>
      <td>RISC-V 64</td>
      <td>N/A</td>
      <td>✅️ Fully supported</td>
      <td>N/A</td>
      <td>N/A</td>
    </tr>
    <tr>
      <td>LoongArch64</td>
      <td>N/A</td>
      <td>
        ✅️ Fully supported (New World)
        <br>
        🕰️ Limited support (Old World)
      </td>
      <td>N/A</td>
      <td>N/A</td>
    </tr>
  </tbody>
</table>
<!-- #END BLOCK -->

Legend:

* ✅️ Fully supported

  Platforms that are fully supported. HMCL will provide support for these platforms as much as possible.

* 🕰️ Limited support

  These platforms are usually legacy platforms.

  HMCL can work on these platforms, but some features may not be available.

  We may drop some features for these platforms to reduce maintenance costs.

* N/A (Not supported)

  HMCL does not support these platforms yet. We may support them in the future.

## Game Compatibility

<!-- #BEGIN BLOCK -->
<!-- #PROPERTY NAME=GAME_COMPATIBILITY -->
|                             | Windows                                           | Linux                      | macOS                                                                   | FreeBSD                     |
|-----------------------------|:--------------------------------------------------|:---------------------------|:------------------------------------------------------------------------|:----------------------------|
| x86-64                      | ✅️                                                | ✅️                         | ✅️                                                                      | 👌 (Minecraft 1.13~1.21.11) |
| x86                         | ✅️ (~1.20.4)                                      | ✅️ (~1.20.4)               | /                                                                       | /                           |
| ARM64                       | 👌 (Minecraft 1.8~1.18.2)<br/>✅ (Minecraft 1.19+) | 👌 (Minecraft 1.8~1.21.11) | 👌 (Minecraft 1.6~1.18.2)<br/>✅ (Minecraft 1.19+)<br/>✅ (use Rosetta 2) | ❔                           |
| ARM32                       | /️                                                | 👌 (Minecraft 1.8~1.20.1)  | /                                                                       | /                           |
| MIPS64el                    | /                                                 | 👌 (Minecraft 1.8~1.20.1)  | /                                                                       | /                           |
| RISC-V 64                   | /                                                 | 👌 (Minecraft 1.13~1.21.5) | /                                                                       | /                           |
| LoongArch64 (New World) | /                                                 | 👌 (Minecraft 1.6~1.21.11) | /                                                                       | /                           |
| LoongArch64 (Old World) | /                                                 | 👌 (Minecraft 1.6~1.20.1)  | /                                                                       | /                           |
| PowerPC-64 (Little-Endian)  | /                                                 | ❔                          | /                                                                       | /                           |
| S390x                       | /                                                 | ❔                          | /                                                                       | /                           |
<!-- #END BLOCK -->

Legend:

* ✅: Officially supported platform.

  Fully supported by Mojang officials. Problems encountered in the game should be directly reported to the Mojang.

* 👌: Supported platforms.

  Support is provided by HMCL, tested to work, but may have more problems than a fully supported platform.  
  Support for versions below Minecraft 1.6 is not guaranteed.  
  If you encounter a problem that does not exist on fully supported platforms, you can report it to HMCL.

* ❔: Low-level supported platforms.

  HMCL can run on this platform and has some basic support. However, launching the game directly is not yet available.  
  If you want to start the game, you will need to get the native libraries needed by Minecraft in another way and specify the native path in the instance settings.

* `/`: Not applicable.

  We have no plans to support these platforms at this time, mainly because we do not have the equipment to test them.  
  If you can help us adapt, please file a support request via GitHub Issue.

## Terracotta Compatibility

<!-- #BEGIN BLOCK -->
<!-- #PROPERTY NAME=TERRACOTTA_COMPATIBILITY -->
<table>
  <thead>
    <tr>
      <th></th>
      <th>Windows</th>
      <th>Linux</th>
      <th>macOS</th>
      <th>FreeBSD</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>x86-64</td>
      <td>
        ✅️ (Windows 10 ~ Windows 11)
        <br>
        ✅️ (Windows Server 2016 ~ 2025)
      </td>
      <td>✅️</td>
      <td>✅️</td>
      <td>✅️</td>
    </tr>
    <tr>
      <td>x86</td>
      <td>N/A</td>
      <td>N/A</td>
      <td>N/A</td>
      <td>N/A</td>
    </tr>
    <tr>
      <td>ARM64</td>
      <td>✅️</td>
      <td>✅️</td>
      <td>✅️</td>
      <td>N/A</td>
    </tr>
    <tr>
      <td>ARM32</td>
      <td>/️</td>
      <td>N/A</td>
      <td>N/A</td>
      <td>N/A</td>
    </tr>
    <tr>
      <td>MIPS64el</td>
      <td>N/A</td>
      <td>N/A</td>
      <td>N/A</td>
      <td>N/A</td>
    </tr>
    <tr>
      <td>RISC-V 64</td>
      <td>N/A</td>
      <td>❔</td>
      <td>N/A</td>
      <td>N/A</td>
    </tr>
    <tr>
      <td>LoongArch64</td>
      <td>N/A</td>
      <td>
        ✅️ (New World)
        <br>
        ❌ (Old World)
      </td>
      <td>N/A</td>
      <td>N/A</td>
    </tr>
  </tbody>
</table>
<!-- #END BLOCK -->
