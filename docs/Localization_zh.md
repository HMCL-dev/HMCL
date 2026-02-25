# 本地化

<!-- #BEGIN LANGUAGE_SWITCHER -->
[English](Localization.md) | **中文** (**简体**, [繁體](Localization_zh_Hant.md))
<!-- #END LANGUAGE_SWITCHER -->

HMCL 为多种语言提供本地化支持。

本文档介绍了 HMCL 对这些语言的支持状态，并给想要为 HMCL 的本地化工作做出贡献的贡献者提供了一份指南。

## 支持的语言

目前，HMCL 为这些语言提供支持:

| 语言      | 语言标签      | 首选本地化键    | 首选本地化文件后缀  | [游戏语言文件](https://minecraft.wiki/w/Language) | 支持状态   | 志愿者                                       | 
|---------|-----------|-----------|------------|---------------------------------------------|--------|-------------------------------------------|
| 英语      | `en`      | `default` | (空)        | `en_us`                                     | **主要** | [Glavo](https://github.com/Glavo)         |  
| 英语 (颠倒) | `en-Qabs` | `en-Qabs` | `en_Qabs`  | `en_ud`                                     | 自动     |                                           |  
| 中文 (简体) | `zh-Hans` | `zh`      | `_zh`      | `zh_cn`                                     | **主要** | [Glavo](https://github.com/Glavo)         |
| 中文 (繁体) | `zh-Hant` | `zh-Hant` | `_zh_Hant` | `zh_tw` <br/> `zh_hk`                       | **主要** | [Glavo](https://github.com/Glavo)         |
| 中文 (文言) | `lzh`     | `lzh`     | `_lzh`     | `lzh`                                       | 次要     |                                           |
| 日语      | `ja`      | `ja`      | `_ja`      | `ja_jp`                                     | 次要     |                                           |
| 西班牙语    | `es`      | `es`      | `_es`      | `es_es`                                     | 次要     | [3gf8jv4dv](https://github.com/3gf8jv4dv) |
| 俄语      | `ru`      | `ru`      | `_ru`      | `ru_ru`                                     | 次要     | [3gf8jv4dv](https://github.com/3gf8jv4dv) |
| 乌克兰语    | `uk`      | `uk`      | `_uk`      | `uk_ua`                                     | 次要     |                                           |

<details>
<summary>关于语言标签</summary>

HMCL 使用符合 IETF BCP 47 规范的语言标签。

在选择语言标签时，我们会遵循以下原则:

1. 对于 ISO 639 标准中定义的语言，如果已经在 [IANA 语言子标签注册表](https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry)中注册，我们总是使用经过注册的标签。

   例如，对于英语，我们使用 `en` 而不是 `eng` 作为语言代码。

2. 对于 Minecraft 所定义的非标准语言，应当优先使用语言文件的 `language.code` 中定义的代码，而非游戏语言文件的名称。

   这是因为 Minecraft 有时候会用现实中实际存在的国家/地区代码来表示虚构语言 (比如说海盗英语的语言文件为 `en_pt`，但 `PT` 其实是葡萄牙的国家代码)。

   例如，对于颠倒的英语，我们使用 `en-Qabs` 作为语言代码，而不是 `en-UD`。

</details>

<details>
<summary>关于本地化键和本地化文件后缀</summary>

本地化文件后缀和本地化键用于为[本地化资源](#本地化资源)命名。

通常来说，本地化键就是这份本地化资源对应的语言代码，而本地化文件后缀是将语言代码中的 `-` 替换为 `_`，并加上一个前缀下划线得到的。

作为特例，对于默认的资源，本地化键为 `default`，本地化文件后缀为空。

由于[资源回退机制](#资源回退机制)的存在。
如果没有完全匹配当前语言环境的资源，HMCL 会根据当前环境的语言标签推导出一个搜索列表，根据该列表依次搜索资源。

我们建议在提供本地化资源时，总是提供默认资源 (对应 `default` 本地化键和空的本地化文件后缀)，
以确保所有用户都能正常加载资源。

并且我们建议尽可能为本地化资源使用更宽泛的语言标签，使用户更不容易回退到默认资源上。

例如，如果你提供了一份简体中文的本地化资源，那么我们推荐使用 `zh` 作为本地化键，而不是更具体的 `zh-Hans`，
这样它会对于所有使用中文的用户生效，避免对于这些用户回退到默认资源上。

如果你想同时提供简体中文和繁体中文的资源，那么推荐对用户占比更高的资源使用更宽泛的 `zh` 作为本地化键，使其作为默认的中文资源，
而对用户占比更低的资源使用更具体的 `zh-Hans`/`zh-Hant` 作为本地化键。

</details>

HMCL 会要求所有 Pull Request 在更新文档和本地化资源时同步更新所有**主要**支持的语言对应的资源。
如果 PR 作者对相关语言并不了解，那么可以直接在评论中提出翻译请求，
维护者会在合并 PR 前帮助 PR 作者翻译这些文本。

而对于**次要**支持的语言，我们不能保证这些本地化资源总是会同步更新。
我们需要熟练掌握这些语言的协作者帮助我们进行维护。

我们会在文档中记录愿意帮助我们维护这些语言本地化资源的志愿者。
如果贡献者希望及时将新增的本地化文本翻译至这些语言，
那么可以在 PR 中 @ 这些志愿者寻求帮助。

如果你愿意帮助我们维护一些语言的本地化资源，那么请打开一个 PR，
将自己的 GitHub 用户名加入上方的志愿者列表。
我们非常感谢你的帮助。

## 添加新的语言支持

HMCL 欢迎任何人参与翻译和贡献。但是维护更多语言的翻译需要付出更多维护成本，所以我们对新增加的语言有一些要求。
请在贡献前确认以下要求:

- 我们优先考虑 [Minecraft 官方支持的语言](https://minecraft.wiki/w/Language)。
  如果没有特殊理由，我们不会为 Minecraft 官方尚未支持的语言提供支持。
- 我们希望对所有语言都提供长久的维护支持。
  由于本项目的维护者们擅长的语言有限，为了避免对新语言的支持很快就因无人维护而过时，
  我们希望能够找到擅长该语言者帮助我们长期维护新增的本地化文件。
  如果可能缺少长期维护者，我们会更慎重地考虑是否要加入对该语言的支持。

我们建议贡献者在提供新语言翻译之前先通过 [Issue](https://github.com/HMCL-dev/HMCL/issues/new?template=feature.yml) 提出一个功能请求，
与其他贡献者先进行讨论，确定了未来的维护方式后再进行翻译工作。

### 开始翻译

如果你想为 HMCL 添加新的语言支持，请从翻译 [`I18N.properties`](../HMCL/src/main/resources/assets/lang/I18N.properties) 开始。
HMCL 的绝大多数文本都位于这个文件中，翻译此文件就能翻译整个界面。

这是一个 Java Properties 文件，格式非常简单。
在翻译前请先阅读该格式的介绍: [Properties 文件](https://en.wikipedia.org/wiki/.properties)。

作为翻译的第一步，请从[这张表格](https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry)中查询这个语言对应的两字母或三字母语言标签。
例如，英语的语言标签为 `en`。

在确定了语言标签后，请在 [`I18N.properties` 文件旁](../HMCL/src/main/resources/assets/lang)创建 `I18N_<本地化文件后缀>.properties` (例如 `I18N_en.properties`) 文件。
随后，你就可以开始在这个文件中进行翻译工作了。

`I18N.properties` 文件会遵循[资源回退机制](#资源回退机制)查询缺失的译文。
也就是说，你可以逐条目进行翻译，而你尚未翻译的条目会自动回退到英语上。

在翻译了一部分后，你可以[自行构建 HMCL](./Contributing_zh.md#构建-hmcl)，编译出的 HMCL 中就会包含你的译文。
如果你的电脑默认环境不是该语言，你可以将环境变量 `HMCL_LANGUAGE` 指定为你刚刚从表格中找到的语言标签，
HMCL 会自动切换至这个语言。

到这里，你就可以把文件推送到 GitHub 上，并向 HMCL 提交 PR 了。
HMCL 的维护者会替你完成其他步骤。

## 本地化资源

所有文档和本地化资源文件的命名规则为 `<资源名><本地化文件后缀>.<扩展名>`。

例如，对于 `README.md` 来说，不同语言的本地化版本命名如下:

- 英语: `README.md`
- 中文 (简体): `README_zh.md`
- 中文 (繁体): `README_zh_Hant.md`
- 中文 (文言): `README_lzh.md`

除了本地化文件，HMCL 还支持本地化 JSON 中的部分文本字段。JSON 中的本地化文本使用以下格式:

```json5
{
    "<本地化键 1>": "<本地化文本 1>",
    "<本地化键 2>": "<本地化文本 2>",
    // ...
    "<本地化键 N>": "<本地化文本 N>"
}
```

例如，对于以下文本字段:

```json
{
    "meow": "Meow"
}
```

可以将其改写为本地化文本:

```json
{
    "meow": {
        "default": "Meow",
        "zh": "喵呜",
        "zh-Hant": "喵嗚"
    }
}
```

## 资源回退机制

对于某个语言下的缺失的资源，HMCL 支持一套资源回退机制，会根据不同的语言标签推导出一个搜索列表，
根据该列表依次搜索资源。

在搜索前，我们会先通过以下步骤对语言标签进行细化推导。

1. 归一化语言代码

   如果当前语言标签中的语言代码子标签未在 IANA 语言子标签注册表中进行注册，HMCL 会先尝试将其映射为注册表中已注册的标签。

   例如，HMCL 会将语言代码 `eng` 替换为 `en`。

2. 映射宏语言至子语言

   如果当前语言代码是一个 [ISO 639 宏语言](https://en.wikipedia.org/wiki/ISO_639_macrolanguage)，
   且该宏语言通常指代某个个体语言，HMCL 会将其替换为该个体语言。

   例如 `zh` (中文) 通常实际指代 `cmn` (官话)，所以我们会将语言代码 `zh` 替换为 `cmn`。

3. 推导拼写脚本

   如果当前语言标签中未指定拼写脚本，HMCL 会依次根据以下规则尝试推导拼写脚本:

   1. 如果当前语言标签指定了语言变体，该语言变体已在 IANA 语言子标签注册表中，
      且注册表中其所有 `Prefix` 都包含相同的拼写脚本，则将当前拼写脚本指定为该脚本。

      例如，如果当前语言变体为 `pinyin` (汉语拼音)，则当前拼写脚本会被指定为 `Latn` (拉丁文)。

   2. 如果当前语言代码在 IANA 语言子标签注册表中被指定了 `Suppress-Script`，则将当前拼写脚本指定为该脚本。

      例如，如果当前语言代码为 `en` (英语)，则当前拼写脚本会被指定为 `Latn` (拉丁文);
      如果当前语言代码为 `ru` (俄语)，则当前拼写脚本会被指定为 `Cyrl` (西里尔文)。

   3. 如果当前语言代码是 `lzh` (文言)，则将当前拼写脚本指定为 `Hant` (繁体汉字)。

   4. 如果当前语言代码是 `zh` 或 `zh` 的子语言，则检查当前国家/地区代码是否为 `TW`、`HK`、`MO` 之一。
      如果结果为真，则将当前拼写脚本指定为 `Hant` (繁体汉字)；否则将当前拼写脚本指定为 `Hans` (简体汉字)。

在对语言代码细化推导完成后，HMCL 会开始根据此语言标签推导出一个语言标签列表。

例如，对于语言标签 `en-US`，HMCL 会将其细化为 `en-Latn-US`，并据此推导出以下搜索列表:

1. `en-Latn-US`
2. `en-Latn`
3. `en-US`
4. `en`
5. `und`

对于语言标签 `zh-CN`，HMCL 会将其细化为 `cmn-Hans-CN`，并据此推导出以下搜索列表:

1. `cmn-Hans-CN`
2. `cmn-Hans`
3. `cmn-CN`
4. `cmn`
5. `zh-Hans-CN`
6. `zh-Hans`
7. `zh-CN`
8. `zh`
9. `und`

对于能够混合的资源 (例如 `.properties` 文件)，HMCL 会根据此列表的优先级混合资源；
对于难以混合的资源 (例如字体文件)，HMCL 会根据此列表加载找到的最高优先级的资源。

如果当前语言使用 ISO 639 标准的三字符标签，但同时也存在对应的两字符标签，那么 HMCL 会将其映射至两字符后再搜索资源。

例如，如果当前环境的语言标签为 `eng-US`，那么 HMCL 会将其映射至 `en-US` 后再根据上述规则搜索本地化资源。

### 对于中文的额外规则

HMCL 总是会将 `zh-CN` 加入所有中文环境的搜索列表中，将 `zh-TW` 加入所有繁体中文环境的搜索列表中。

以下是几个常见中文环境对应的本地化资源搜索列表。

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

## 自动同步文档内容

<!-- #BEGIN BLOCK -->
<!-- #PROPERTY PROCESS_LINK=false -->
为了简化文档的维护，HMCL 使用了一套宏机制自动维护文档的部分内容。在命令行中执行

```bash
./gradlew updateDocuments
```

即可自动更新所有文档内容。

例如，为了创建在同一文档的不同语言译文之间跳转的链接，请在文档的标题下添加以下内容:

```markdown
<!-- #BEGIN LANGUAGE_SWITCHER -->
<!-- #END LANGUAGE_SWITCHER -->
```

随后执行 `./gradlew updateDocuments`，这两行内容会被自动替换为类似这样的跳转链接:

```markdown
**English** (**Standard**, [uʍoᗡ ǝpᴉsd∩](README_en_Qabs.md)) | 中文 ([简体](README_zh.md), [繁體](README_zh_Hant.md), [文言](README_lzh.md)) | [日本語](README_ja.md) | [español](README_es.md) | [русский](README_ru.md) | [українська](README_uk.md)
```

关于宏的更多内容，请见 [MacroProcessor.java](../buildSrc/src/main/java/org/jackhuang/hmcl/gradle/docs/MacroProcessor.java)。
<!-- #END BLOCK -->
