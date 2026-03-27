# 在地化

<!-- #BEGIN LANGUAGE_SWITCHER -->
[English](Localization.md) | **中文** ([简体](Localization_zh.md), **繁體**)
<!-- #END LANGUAGE_SWITCHER -->

HMCL 為多種語言提供在地化支援。

本文件介紹了 HMCL 對這些語言的支援狀態，並給想要為 HMCL 的在地化工作做出貢獻的貢獻者提供了一份指南。

## 支援的語言

目前，HMCL 為這些語言提供支援:

| 語言      | 語言標籤      | 首選在地化機碼    | 首選在地化檔案後綴  | [遊戲語言檔案](https://minecraft.wiki/w/Language) | 支援狀態   | 志願者                                       | 
|---------|-----------|-----------|------------|---------------------------------------------|--------|-------------------------------------------|
| 英語      | `en`      | `default` | (空)        | `en_us`                                     | **主要** | [Glavo](https://github.com/Glavo)         |  
| 英語 (顛倒) | `en-Qabs` | `en-Qabs` | `en_Qabs`  | `en_ud`                                     | 自動     |                                           |  
| 中文 (簡體) | `zh-Hans` | `zh`      | `_zh`      | `zh_cn`                                     | **主要** | [Glavo](https://github.com/Glavo)         |
| 中文 (繁體) | `zh-Hant` | `zh-Hant` | `_zh_Hant` | `zh_tw` <br/> `zh_hk`                       | **主要** | [Glavo](https://github.com/Glavo)         |
| 中文 (文言) | `lzh`     | `lzh`     | `_lzh`     | `lzh`                                       | 次要     |                                           |
| 日語      | `ja`      | `ja`      | `_ja`      | `ja_jp`                                     | 次要     |                                           |
| 西班牙語    | `es`      | `es`      | `_es`      | `es_es`                                     | 次要     | [3gf8jv4dv](https://github.com/3gf8jv4dv) |
| 俄語      | `ru`      | `ru`      | `_ru`      | `ru_ru`                                     | 次要     | [3gf8jv4dv](https://github.com/3gf8jv4dv) |
| 烏克蘭語    | `uk`      | `uk`      | `_uk`      | `uk_ua`                                     | 次要     |                                           |

<details>
<summary>關於語言標籤</summary>

HMCL 使用符合 IETF BCP 47 規範的語言標籤。

在選取語言標籤時，我們會遵循以下原則:

1. 對於 ISO 639 標準中定義的語言，如果已經在 [IANA 語言子標籤登錄檔](https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry)中註冊，我們總是使用經過註冊的標籤。

   例如，對於英語，我們使用 `en` 而不是 `eng` 作為語言代碼。

2. 對於 Minecraft 所定義的非標準語言，應當優先使用語言檔案的 `language.code` 中定義的程式碼，而非遊戲語言檔案的名稱。

   這是因為 Minecraft 有時候會用現實中實際存在的國家/地區代碼來表示虛構語言 (比如說海盜英語的語言檔案為 `en_pt`，但 `PT` 其實是葡萄牙的國家代碼)。

   例如，對於顛倒的英語，我們使用 `en-Qabs` 作為語言代碼，而不是 `en-UD`。

</details>

<details>
<summary>關於在地化機碼和在地化檔案後綴</summary>

在地化檔案後綴和在地化機碼用於為[在地化資源](#在地化資源)命名。

通常來說，在地化機碼就是這份在地化資源對應的語言代碼，而在地化檔案後綴是將語言代碼中的 `-` 取代為 `_`，並加上一個前綴底線得到的。

作為特例，對於預設的資源，在地化機碼為 `default`，在地化檔案後綴為空。

由於[資源回退機制](#資源回退機制)的存在。
如果沒有完全匹配目前語言環境的資源，HMCL 會根據目前環境的語言標籤推導出一個搜尋列表，根據該列表依次搜尋資源。

我們建議在提供在地化資源時，總是提供預設資源 (對應 `default` 在地化機碼和空的在地化檔案後綴)，
以確保所有使用者都能正常載入資源。

並且我們建議盡可能為在地化資源使用更寬泛的語言標籤，使使用者更不容易回退到預設資源上。

例如，如果你提供了一份簡體中文的在地化資源，那麼我們推薦使用 `zh` 作為在地化機碼，而不是更具體的 `zh-Hans`，
這樣它會對於所有使用中文的使用者生效，避免對於這些使用者回退到預設資源上。

如果你想同時提供簡體中文和繁體中文的資源，那麼推薦對使用者占比更高的資源使用更寬泛的 `zh` 作為在地化機碼，使其作為預設的中文資源，
而對使用者占比更低的資源使用更具體的 `zh-Hans`/`zh-Hant` 作為在地化機碼。

</details>

HMCL 會要求所有 Pull Request 在更新文件和在地化資源時同步更新所有「**主要**」支援的語言對應的資源。
如果 PR 作者對相關語言並不了解，那麼可以直接在評論中提出翻譯請求，
維護者會在合併 PR 前幫助 PR 作者翻譯這些文字。

而對於「**次要**」支援的語言，我們不能保證這些在地化資源總是會同步更新。
我們需要熟練掌握這些語言的協作者幫助我們進行維護。

我們會在文件中記錄願意幫助我們維護這些語言在地化資源的志願者。
如果貢獻者希望及時將新增的在地化文字翻譯至這些語言，
那麼可以在 PR 中 @ 這些志願者尋求幫助。

如果你願意幫助我們維護一些語言的在地化資源，那麼請打開一個 PR，
將自己的 GitHub 使用者名稱加入上方的志願者列表。
我們非常感謝你的幫助。

## 新增新的語言支援

HMCL 歡迎任何人參與翻譯和貢獻。但是維護更多語言的翻譯需要付出更多維護成本，所以我們對新增加的語言有一些要求。
請在貢獻前確認以下要求:

- 我們優先考慮 [Minecraft 官方支援的語言](https://minecraft.wiki/w/Language)。
  如果沒有特殊理由，我們不會為 Minecraft 官方尚未支援的語言提供支援。
- 我們希望對所有語言都提供長久的維護支援。
  由於本項目的維護者們擅長的語言有限，為了避免對新語言的支援很快就因無人維護而過時，
  我們希望能夠找到擅長該語言者幫助我們長期維護新增的在地化檔案。
  如果可能缺少長期維護者，我們會更慎重地考慮是否要加入對該語言的支援。

我們建議貢獻者在提供新語言翻譯之前先透過 [Issue](https://github.com/HMCL-dev/HMCL/issues/new?template=feature.yml) 提出一個功能請求，
與其他貢獻者先進行討論，確定了未來的維護方式後再進行翻譯工作。

### 開始翻譯

如果你想為 HMCL 新增新的語言支援，請從翻譯 [`I18N.properties`](../HMCL/src/main/resources/assets/lang/I18N.properties) 開始。
HMCL 的絕大多數文字都位於這個檔案中，翻譯此檔案就能翻譯整個介面。

這是一個 Java Properties 檔案，格式非常簡單。
在翻譯前請先閱讀該格式的介紹: [Properties 檔案](https://en.wikipedia.org/wiki/.properties)。

作為翻譯的第一步，請從[這張表格](https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry)中查詢這個語言對應的兩字母或三字母語言標籤。
例如，英語的語言標籤為 `en`。

在確定了語言標籤後，請在 [`I18N.properties` 檔案旁](../HMCL/src/main/resources/assets/lang)建立 `I18N_<在地化檔案後綴>.properties` (例如 `I18N_en.properties`) 檔案。
隨後，你就可以開始在這個檔案中進行翻譯工作了。

`I18N.properties` 檔案會遵循[資源回退機制](#資源回退機制)查詢缺失的譯文。
也就是說，你可以逐條目進行翻譯，而你尚未翻譯的條目會自動回退到英語上。

在翻譯了一部分後，你可以[自行構建 HMCL](./Contributing_zh_Hant.md#構建-hmcl)，編譯出的 HMCL 中就會包含你的譯文。
如果你的電腦預設環境不是該語言，你可以將環境變數 `HMCL_LANGUAGE` 指定為你剛剛從表格中找到的語言標籤，
HMCL 會自動切換至這個語言。

到這裡，你就可以把檔案推送到 GitHub 上，並向 HMCL 提交 PR 了。
HMCL 的維護者會替你完成其他步驟。

## 在地化資源

所有文件和在地化資源檔案的命名規則為 `<資源名><在地化檔案後綴>.<副檔名>`。

例如，對於 `README.md` 來說，不同語言的在地化版本命名如下:

- 英語: `README.md`
- 中文 (簡體): `README_zh.md`
- 中文 (繁體): `README_zh_Hant.md`
- 中文 (文言): `README_lzh.md`

除了在地化檔案，HMCL 還支援在地化 JSON 中的部分文字欄位。JSON 中的在地化文字使用以下格式:

```json5
{
    "<在地化機碼 1>": "<在地化文字 1>",
    "<在地化機碼 2>": "<在地化文字 2>",
    // ...
    "<在地化機碼 N>": "<在地化文字 N>"
}
```

例如，對於以下文字欄位:

```json
{
    "meow": "Meow"
}
```

可以將其改寫為在地化文字:

```json
{
    "meow": {
        "default": "Meow",
        "zh": "喵呜",
        "zh-Hant": "喵嗚"
    }
}
```

## 資源回退機制

對於某個語言下的缺失的資源，HMCL 支援一套資源回退機制，會根據不同的語言標籤推導出一個搜尋列表，
根據該列表依次搜尋資源。

在搜尋前，我們會先透過以下步驟對語言標籤進行細化推導。

1. 歸一化語言代碼

   如果目前語言標籤中的語言代碼子標籤未在 IANA 語言子標籤登錄檔中進行註冊，HMCL 會先嘗試將其映射為登錄檔中已註冊的標籤。

   例如，HMCL 會將語言代碼 `eng` 取代為 `en`。

2. 映射巨集語言至子語言

   如果目前語言代碼是一個 [ISO 639 巨集語言](https://en.wikipedia.org/wiki/ISO_639_macrolanguage)，
   且該巨集語言通常指代某個個體語言，HMCL 會將其取代為該個體語言。

   例如 `zh` (中文) 通常實際指代 `cmn` (國語)，所以我們會將語言代碼 `zh` 取代為 `cmn`。

3. 推導拼寫指令碼

   如果目前語言標籤中未指定拼寫指令碼，HMCL 會依次根據以下規則嘗試推導拼寫指令碼:

   1. 如果目前語言標籤指定了語言變體，該語言變體已在 IANA 語言子標籤登錄檔中，
      且登錄檔中其所有 `Prefix` 都包含相同的拼寫指令碼，則將目前拼寫指令碼指定為該指令碼。

      例如，如果目前語言變體為 `pinyin` (漢語拼音)，則目前拼寫指令碼會被指定為 `Latn` (拉丁文)。

   2. 如果目前語言代碼在 IANA 語言子標籤登錄檔中被指定了 `Suppress-Script`，則將目前拼寫指令碼指定為該指令碼。

      例如，如果目前語言代碼為 `en` (英語)，則目前拼寫指令碼會被指定為 `Latn` (拉丁文);
      如果目前語言代碼為 `ru` (俄語)，則目前拼寫指令碼會被指定為 `Cyrl` (西里爾文)。

   3. 如果目前語言代碼是 `lzh` (文言)，則將目前拼寫指令碼指定為 `Hant` (繁體漢字)。

   4. 如果目前語言代碼是 `zh` 或 `zh` 的子語言，則檢查目前國家/地區代碼是否為 `TW`、`HK`、`MO` 之一。
      如果結果為真，則將目前拼寫指令碼指定為 `Hant` (繁體漢字)；否則將目前拼寫指令碼指定為 `Hans` (簡體漢字)。

在對語言代碼細化推導完成後，HMCL 會開始根據此語言標籤推導出一個語言標籤列表。

例如，對於語言標籤 `en-US`，HMCL 會將其細化為 `en-Latn-US`，並據此推導出以下搜尋列表:

1. `en-Latn-US`
2. `en-Latn`
3. `en-US`
4. `en`
5. `und`

對於語言標籤 `zh-CN`，HMCL 會將其細化為 `cmn-Hans-CN`，並據此推導出以下搜尋列表:

1. `cmn-Hans-CN`
2. `cmn-Hans`
3. `cmn-CN`
4. `cmn`
5. `zh-Hans-CN`
6. `zh-Hans`
7. `zh-CN`
8. `zh`
9. `und`

對於能夠混合的資源 (例如 `.properties` 檔案)，HMCL 會根據此列表的優先度混合資源；
對於難以混合的資源 (例如字體檔案)，HMCL 會根據此列表載入找到的最高優先度的資源。

如果目前語言使用 ISO 639 標準的三字元標籤，但同時也存在對應的兩字元標籤，那麼 HMCL 會將其映射至兩字元後再搜尋資源。

例如，如果目前環境的語言標籤為 `eng-US`，那麼 HMCL 會將其映射至 `en-US` 後再根據上述規則搜尋在地化資源。

### 對於中文的額外規則

HMCL 總是會將 `zh-CN` 加入所有中文環境的搜尋列表中，將 `zh-TW` 加入所有繁體中文環境的搜尋列表中。

以下是幾個常見中文環境對應的在地化資源搜尋列表。

- `zh-CN`:
    1. `cmn-Hans-CN`
    2. `cmn-Hans`
    3. `cmn-CN`
    4. `cmn`
    5. `zh-Hans-CN`
    6. `zh-Hans`
    7. `zh-CN`
    8. `zh`
    9. `und`
- `zh-SG`:
    1.  `cmn-Hans-SG`
    2.  `cmn-Hans`
    3.  `cmn-SG`
    4.  `cmn`
    5.  `zh-Hans-SG`
    6.  `zh-Hans`
    7.  `zh-SG`
    8.  `zh-CN`
    9.  `zh`
    10. `und`
- `zh-TW`:
    1. `zh-Hant-TW`
    2. `zh-Hant`
    3. `zh-TW`
    4. `zh`
    5. `zh-CN`
    6. `und`
- `zh-HK`:
    1.  `cmn-Hant-HK`
    2.  `cmn-Hant`
    3.  `cmn-HK`
    4.  `cmn`
    5.  `zh-Hant-HK`
    6.  `zh-Hant`
    7.  `zh-HK`
    8.  `zh-TW`
    9.  `zh`
    10. `zh-CN`
    11. `und`
- `lzh`:
    1. `lzh-Hant`
    2. `lzh`
    3. `zh-Hant`
    4. `zh-TW`
    5. `zh`
    6. `zh-CN`
    7. `und`

## 自動同步文件內容

<!-- #BEGIN BLOCK -->
<!-- #PROPERTY PROCESS_LINK=false -->
為了簡化文件的維護，HMCL 使用了一套巨集機制自動維護文件的部分內容。在命令列中執行

```bash
./gradlew updateDocuments
```

即可自動更新所有文件內容。

例如，為了建立在同一文件的不同語言譯文之間跳轉的連結，請在文件的標題下新增以下內容:

```markdown
<!-- #BEGIN LANGUAGE_SWITCHER -->
<!-- #END LANGUAGE_SWITCHER -->
```

隨後執行 `./gradlew updateDocuments`，這兩行內容會被自動取代為類似這樣的跳轉連結:

```markdown
**English** (**Standard**, [uʍoᗡ ǝpᴉsd∩](README_en_Qabs.md)) | 中文 ([简体](README_zh.md), [繁體](README_zh_Hant.md), [文言](README_lzh.md)) | [日本語](README_ja.md) | [español](README_es.md) | [русский](README_ru.md) | [українська](README_uk.md)
```

關於巨集的更多內容，請見 [MacroProcessor.java](../buildSrc/src/main/java/org/jackhuang/hmcl/gradle/docs/MacroProcessor.java)。
<!-- #END BLOCK -->
