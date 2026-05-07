# 平臺支援狀態

<!-- #BEGIN LANGUAGE_SWITCHER -->
[English](PLATFORM.md) | **中文** ([简体](PLATFORM_zh.md), **繁體**)
<!-- #END LANGUAGE_SWITCHER -->

## 啟動器相容性

<!-- #BEGIN COPY -->
<!-- #PROPERTY NAME=LAUNCHER_COMPATIBILITY -->
<!-- #PROPERTY REPLACE="Fully supported" "完整支援" -->
<!-- #PROPERTY REPLACE="Limited support" "有限支援" -->
<!-- #PROPERTY REPLACE="New World" "新世界" -->
<!-- #PROPERTY REPLACE="Old World" "舊世界" -->
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
        ✅️ 完整支援 (Windows 7 ~ Windows 11)
        <br>
        ✅️ 完整支援 (Windows Server 2008 R2 ~ 2025)
        <br>
        🕰️ <a href="https://github.com/HMCL-dev/HMCL/releases?q=3.6">HMCL 3.6</a> (Windows Vista)
        <br>
        🕰️ <a href="https://github.com/HMCL-dev/HMCL/releases?q=3.6">HMCL 3.6</a> (Windows Server 2003 ~ 2008) 
      </td>
      <td>✅️ 完整支援</td>
      <td>✅️ 完整支援</td>
      <td>✅ 完整支援</td>
    </tr>
    <tr>
      <td>x86</td>
      <td>
        🕰️ 有限支援 (Windows 7 ~ Windows 10)
        <br>
        🕰️ <a href="https://github.com/HMCL-dev/HMCL/releases?q=3.6">HMCL 3.6</a> (Windows XP/Vista)
      </td>
      <td>🕰️ 有限支援</td>
      <td>/</td>
      <td>/</td>
    </tr>
    <tr>
      <td>ARM64</td>
      <td>✅️ 完整支援</td>
      <td>✅️ 完整支援</td>
      <td>✅️ 完整支援</td>
      <td>/</td>
    </tr>
    <tr>
      <td>ARM32</td>
      <td>/️</td>
      <td>🕰️ 有限支援</td>
      <td>/</td>
      <td>/</td>
    </tr>
    <tr>
      <td>MIPS64el</td>
      <td>/</td>
      <td>🕰️ 有限支援</td>
      <td>/</td>
      <td>/</td>
    </tr>
    <tr>
      <td>RISC-V 64</td>
      <td>/</td>
      <td>✅️ 完整支援</td>
      <td>/</td>
      <td>/</td>
    </tr>
    <tr>
      <td>LoongArch64</td>
      <td>/</td>
      <td>
        ✅️ 完整支援 (新世界)
        <br>
        🕰️ 有限支援 (舊世界)
      </td>
      <td>/</td>
      <td>/</td>
    </tr>
  </tbody>
</table>
<!-- #END COPY -->

圖例：

* ✅️ 完整支援

  受到完整支援的平臺。HMCL 會盡可能為此平臺提供支援。

* 🕰️ 有限支援

  這些平臺通常是老舊的遺留平臺。

  HMCL 可以在這些平臺上運作，但部分功能可能無法使用。

  我們可能會為了降低維護成本而放棄為此平臺提供部分功能。

* 🕰️ HMCL 3.6（有限支援）

  HMCL 主分支不再支援此平臺。

  我們透過 HMCL 3.6 LTS 分支繼續為該平臺提供安全修補與錯誤修復，
  但此平臺上將無法獲得功能更新。

* /（不支援）

  HMCL 尚未支援此平臺。我們未來可能會支援此平臺。

## 遊戲相容性

<!-- #BEGIN COPY -->
<!-- #PROPERTY NAME=GAME_COMPATIBILITY -->
<!-- #PROPERTY REPLACE="New World" "新世界" -->
<!-- #PROPERTY REPLACE="Old World" "舊世界" -->
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
| LoongArch64 (舊世界) | /                                                 | 👌 (Minecraft 1.6~1.20.1)  | /                                                                       | /                           |
| PowerPC-64 (Little-Endian)  | /                                                 | ❔                          | /                                                                       | /                           |
| S390x                       | /                                                 | ❔                          | /                                                                       | /                           |
<!-- #END COPY -->

圖例：

* ✅: 官方支援的平臺

  受 Mojang 官方支援。在遊戲中遇到的問題應該直接向 Mojang 回報。

* 👌: 支援的平臺

  由 HMCL 提供支援，經過測試可以正常執行，但可能比得到全面支援的平臺有更多問題。  
  不保證支援 Minecraft 1.6 以下的版本。  
  如果你遇到在得到全面支援的平臺上不存在的問題，可以向 HMCL 回報。

* ❔: 低級別支援的平臺

  HMCL 可以在這個平臺上執行，並且有一些基本的支援。但是，還不能正常地啟動遊戲。  
  如果你想正常啟動遊戲，則需要透過其他方式獲得遊戲所需的本機庫 (LWJGL)，並在（全域）遊戲設定中指定本機庫路徑。

* `/`: 不支援的平臺

  我們目前還沒有打算支援這些平臺，主要是因為我們沒有測試這些平臺的裝置。  
  如果你能幫助我們進行測試，請透過 Issue 提出支援請求。

## 陶瓦聯機相容性

<!-- #BEGIN COPY -->
<!-- #PROPERTY NAME=TERRACOTTA_COMPATIBILITY -->
<!-- #PROPERTY REPLACE="New World" "新世界" -->
<!-- #PROPERTY REPLACE="Old World" "舊世界" -->
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
        ❌ (舊世界)
      </td>
      <td>/</td>
      <td>/</td>
    </tr>
  </tbody>
</table>
<!-- #END COPY -->
