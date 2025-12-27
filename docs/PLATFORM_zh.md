# 平台支持状态

<!-- #BEGIN LANGUAGE_SWITCHER -->
[English](PLATFORM.md) | **中文** (**简体**, [繁體](PLATFORM_zh_Hant.md))
<!-- #END LANGUAGE_SWITCHER -->

## 启动器兼容性

<!-- #BEGIN COPY -->
<!-- #PROPERTY NAME=LAUNCHER_COMPATIBILITY -->
<!-- #PROPERTY REPLACE="Fully supported" "完整支持" -->
<!-- #PROPERTY REPLACE="Limited support" "有限支持" -->
<!-- #PROPERTY REPLACE="Old World" "旧世界" -->
<!-- #PROPERTY REPLACE="New World" "新世界" -->
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
        ✅️ 完整支持 (Windows 7 ~ Windows 11)
        <br>
        ✅️ 完整支持 (Windows Server 2008 R2 ~ 2025)
        <br>
        🕰️ <a href="https://github.com/HMCL-dev/HMCL/releases?q=3.6">HMCL 3.6</a> (Windows Vista)
        <br>
        🕰️ <a href="https://github.com/HMCL-dev/HMCL/releases?q=3.6">HMCL 3.6</a> (Windows Server 2003 ~ 2008) 
      </td>
      <td>✅️ 完整支持</td>
      <td>✅️ 完整支持</td>
      <td>✅ 完整支持</td>
    </tr>
    <tr>
      <td>x86</td>
      <td>
        🕰️ 有限支持 (Windows 7 ~ Windows 10)
        <br>
        🕰️ <a href="https://github.com/HMCL-dev/HMCL/releases?q=3.6">HMCL 3.6</a> (Windows XP/Vista)
      </td>
      <td>🕰️ 有限支持</td>
      <td>/</td>
      <td>/</td>
    </tr>
    <tr>
      <td>ARM64</td>
      <td>✅️ 完整支持</td>
      <td>✅️ 完整支持</td>
      <td>✅️ 完整支持</td>
      <td>/</td>
    </tr>
    <tr>
      <td>ARM32</td>
      <td>/️</td>
      <td>🕰️ 有限支持</td>
      <td>/</td>
      <td>/</td>
    </tr>
    <tr>
      <td>MIPS64el</td>
      <td>/</td>
      <td>🕰️ 有限支持</td>
      <td>/</td>
      <td>/</td>
    </tr>
    <tr>
      <td>RISC-V 64</td>
      <td>/</td>
      <td>✅️ 完整支持</td>
      <td>/</td>
      <td>/</td>
    </tr>
    <tr>
      <td>LoongArch64</td>
      <td>/</td>
      <td>
        ✅️ 完整支持 (新世界)
        <br>
        🕰️ 有限支持 (旧世界)
      </td>
      <td>/</td>
      <td>/</td>
    </tr>
  </tbody>
</table>
<!-- #END COPY -->

图例：

* ✅️ 完整支持

  受到完整支持的平台。HMCL 会尽可能为此平台提供支持。

* 🕰️ 有限支持

  这些平台通常是老旧的遗留平台。

  HMCL 可以在这些平台上工作，但部分功能可能无法使用。

  我们可能会为了降低维护成本而放弃为此平台提供部分功能。

* 🕰️ HMCL 3.6 (有限支持)

  HMCL 主分支不再支持此平台。

  我们通过 HMCL 3.6 LTS 分支继续为该平台提供安全补丁和错误修复，
  但此平台上将无法接受到功能更新。

* / (不受支持)

  HMCL 尚不支持此平台。我们可能会在未来支持此平台。

## 游戏兼容性

<!-- #BEGIN COPY -->
<!-- #PROPERTY NAME=GAME_COMPATIBILITY -->
<!-- #PROPERTY REPLACE="Old World" "旧世界" -->
<!-- #PROPERTY REPLACE="New World" "新世界" -->
<!-- #PROPERTY REPLACE="\\(use Rosetta 2\\)" "(使用 Rosetta 2)" -->
|                             | Windows                                           | Linux                      | macOS                                                                   | FreeBSD                     |
|-----------------------------|:--------------------------------------------------|:---------------------------|:------------------------------------------------------------------------|:----------------------------|
| x86-64                      | ✅️                                                | ✅️                         | ✅️                                                                      | 👌 (Minecraft 1.13~1.21.11) |
| x86                         | ✅️ (~1.20.4)                                      | ✅️ (~1.20.4)               | /                                                                       | /                           |
| ARM64                       | 👌 (Minecraft 1.8~1.18.2)<br/>✅ (Minecraft 1.19+) | 👌 (Minecraft 1.8~1.21.11) | 👌 (Minecraft 1.6~1.18.2)<br/>✅ (Minecraft 1.19+)<br/>✅ (使用 Rosetta 2) | ❔                           |
| ARM32                       | /️                                                | 👌 (Minecraft 1.8~1.20.1)  | /                                                                       | /                           |
| MIPS64el                    | /                                                 | 👌 (Minecraft 1.8~1.20.1)  | /                                                                       | /                           |
| RISC-V 64                   | /                                                 | 👌 (Minecraft 1.13~1.21.5) | /                                                                       | /                           |
| LoongArch64 (新世界) | /                                                 | 👌 (Minecraft 1.6~1.21.11) | /                                                                       | /                           |
| LoongArch64 (旧世界) | /                                                 | 👌 (Minecraft 1.6~1.20.1)  | /                                                                       | /                           |
| PowerPC-64 (Little-Endian)  | /                                                 | ❔                          | /                                                                       | /                           |
| S390x                       | /                                                 | ❔                          | /                                                                       | /                           |
<!-- #END COPY -->

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

## 陶瓦联机兼容性

<!-- #BEGIN COPY -->
<!-- #PROPERTY NAME=TERRACOTTA_COMPATIBILITY -->
<!-- #PROPERTY REPLACE="Old World" "旧世界" -->
<!-- #PROPERTY REPLACE="New World" "新世界" -->
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
      <td>/</td>
      <td>/</td>
      <td>/</td>
      <td>/</td>
    </tr>
    <tr>
      <td>ARM64</td>
      <td>✅️</td>
      <td>✅️</td>
      <td>✅️</td>
      <td>/</td>
    </tr>
    <tr>
      <td>ARM32</td>
      <td>/️</td>
      <td>/</td>
      <td>/</td>
      <td>/</td>
    </tr>
    <tr>
      <td>MIPS64el</td>
      <td>/</td>
      <td>/</td>
      <td>/</td>
      <td>/</td>
    </tr>
    <tr>
      <td>RISC-V 64</td>
      <td>/</td>
      <td>❔</td>
      <td>/</td>
      <td>/</td>
    </tr>
    <tr>
      <td>LoongArch64</td>
      <td>/</td>
      <td>
        ✅️ (新世界)
        <br>
        ❌ (旧世界)
      </td>
      <td>/</td>
      <td>/</td>
    </tr>
  </tbody>
</table>
<!-- #END COPY -->
