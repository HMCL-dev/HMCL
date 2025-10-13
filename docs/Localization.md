# Localization

<!-- #BEGIN LANGUAGE_SWITCHER -->
**English** | [中文](Localization_zh.md)
<!-- #END LANGUAGE_SWITCHER -->

HMCL provides localization support for multiple languages.

This document describes the status of language support in HMCL and provides a guide for contributors who want to help with localization.

| Language              | Language Tag | Preferred Localization Key | Preferred Localization File Suffix | [Game Language File](https://minecraft.wiki/w/Language) | Support Status | Volunteers                                |
|-----------------------|--------------|----------------------------|------------------------------------|---------------------------------------------------------|----------------|-------------------------------------------|
| English               | `en`         | `default`                  | (empty)                            | `en_us`                                                 | **Primary**    | [Glavo](https://github.com/Glavo)         |  
| English (Upside Down) | `en-Qabs`    | `en-Qabs`                  | `en_Qabs`                          | `en_ud`                                                 | Auto-Generated |                                           |  
| Chinese (Simplified)  | `zh-Hans`    | `zh`                       | `_zh`                              | `zh_cn`                                                 | **Primary**    | [Glavo](https://github.com/Glavo)         |
| Chinese (Traditional) | `zh-Hant`    | `zh-Hant`                  | `_zh_Hant`                         | `zh_tw` <br/> `zh_hk`                                   | **Primary**    | [Glavo](https://github.com/Glavo)         |
| Chinese (Classical)   | `lzh`        | `lzh`                      | `_lzh`                             | `lzh`                                                   | Secondary      |                                           |
| Japanese              | `ja`         | `ja`                       | `_ja`                              | `ja_jp`                                                 | Secondary      |                                           |
| Spanish               | `es`         | `es`                       | `_es`                              | `es_es`                                                 | Secondary      | [3gf8jv4dv](https://github.com/3gf8jv4dv) |
| Russian               | `ru`         | `ru`                       | `_ru`                              | `ru_ru`                                                 | Secondary      | [3gf8jv4dv](https://github.com/3gf8jv4dv) |
| Ukrainian             | `uk`         | `uk`                       | `_uk`                              | `uk_ua`                                                 | Secondary      |                                           |

<details>
<summary>About Language Tags</summary>

HMCL uses language tags that conform to the IETF BCP 47 standard. In addition, we follow these principles when choosing language tags:

* For languages defined in the ISO 639 standard, if both two-letter and three-letter language codes exist, the two-letter code should be preferred.

  For example, for English, we use `en` instead of `eng` as the language code.

* For non-standard languages defined by Minecraft, the code defined in the language file's `language.code` should be preferred over the game language file name
  (but for languages with a two-letter code, the three-letter code should be replaced with the corresponding two-letter code).

  This is because Minecraft sometimes uses real-world country/region codes to represent fictional languages (for example, Pirate English uses the language file `en_pt`, but `PT` is actually the country code for Portugal).

  For example, for Upside Down English, we use `en-Qabs` as the language code instead of `en-UD`.

* In addition, language tags should be as region-neutral as possible.

  For example, for Simplified Chinese and Traditional Chinese, we use `zh-Hans` and `zh-Hant` as language codes, instead of `zh-CN` and `zh-TW`.

</details>

<details>
<summary>About Localization Keys and File Suffixes</summary>

Localization file suffixes and keys are used to name [localization resources](#localization-resources).

Generally, the localization key is the language code corresponding to the localization resource, and the localization file suffix is obtained by replacing `-` in the language code with `_` and adding a leading underscore.

As a special case, for the default resource, the localization key is `default` and the localization file suffix is empty.

Due to the existence of the [resource fallback mechanism](#resource-fallback-mechanism), if there is no resource that exactly matches the current language environment, HMCL will derive a search list based on the current language tag and search for resources in order according to this list.

We recommend always providing the default resource (with the `default` localization key and an empty file suffix) when providing localization resources, to ensure that all users can load resources properly.

We also recommend using broader language tags for localization resources whenever possible, so that users are less likely to fall back to the default resource.

For example, if you provide a Simplified Chinese localization resource, we recommend using `zh` as the localization key instead of the more specific `zh-Hans`, so that it applies to all Chinese users and avoids falling back to the default resource for them.

If you want to provide both Simplified and Traditional Chinese resources, it is recommended to use the broader `zh` as the localization key for the resource with a higher user share, making it the default Chinese resource, and use the more specific `zh-Hans`/`zh-Hant` as the localization key for the resource with a lower user share.

</details>

HMCL requires all Pull Requests that update documentation or localization resources to also update the resources for all **primary** supported languages.
If the PR author is not familiar with the relevant languages, they can request translations in the comments,
and maintainers will help translate these texts before merging the PR.

For **secondary** supported languages, we cannot guarantee that these localization resources will always be updated in sync.
We need collaborators who are proficient in these languages to help us maintain them.

We record volunteers who are willing to help maintain these language localization resources in the documentation.
If contributors wish to have newly added localized texts translated into these languages in a timely manner,
they can @ these volunteers in the PR to seek help.

If you are willing to help us maintain localization resources for certain languages, please open a PR
and add your GitHub username to the volunteer list above.
We greatly appreciate your help.

## Adding Support for New Languages

HMCL welcomes anyone to participate in translation and contribution. However, maintaining translations for more languages requires additional maintenance effort, so we have some requirements for newly added languages.
Please confirm the following requirements before contributing:

- We prioritize [languages officially supported by Minecraft](https://minecraft.wiki/w/Language).
  Unless there are special reasons, we will not support languages that are not officially supported by Minecraft.
- We hope to provide long-term maintenance support for all languages.
  Since the maintainers of this project are proficient in only a limited number of languages, to avoid new language support becoming outdated due to lack of maintainers,
  we hope to find people proficient in the language to help us maintain the newly added localization files in the long term.
  If there may be a lack of long-term maintainers, we will be more cautious about whether to add support for that language.

We recommend that contributors submit a [feature request Issue](https://github.com/HMCL-dev/HMCL/issues/new?template=feature.yml) before providing a new language translation,
discuss with other contributors first, and determine the future maintenance plan before starting the translation work.

### Getting Started with Translation

If you want to add support for a new language in HMCL, start by translating [`I18N.properties`](../HMCL/src/main/resources/assets/lang/I18N.properties).
The vast majority of HMCL's texts are in this file; translating it will localize the entire interface.

This is a Java Properties file, which is very simple in format.
Before translating, please read the introduction to this format: [Properties file](https://en.wikipedia.org/wiki/.properties).

As the first step, look up the two-letter or three-letter language tag for your language from [this table](https://en.wikipedia.org/wiki/List_of_ISO_639_language_codes).
For example, the language tag for English is `en`.

After determining the language tag, create a file named `I18N_<language tag>.properties` (e.g., `I18N_en.properties`) next to the [`I18N.properties` file](../HMCL/src/main/resources/assets/lang).
You can then start translating in this file.

The `I18N.properties` file follows the [resource fallback mechanism](#resource-fallback-mechanism) to look up missing translations.
This means you can translate entry by entry, and any untranslated entries will automatically fall back to English.

After translating part of the file, you can [build HMCL yourself](./README.md#compilation), and your translations will be included in the compiled HMCL.
If your computer's default environment is not this language, you can set the environment variable `HMCL_LANGUAGE` to the language tag you just found,
and HMCL will automatically switch to that language.

At this point, you can push the file to GitHub and submit a PR to HMCL.
The maintainers will handle the remaining steps for you.

## Localization Resources

All documentation and localization resource files follow the naming rule `<resource name><localization file suffix>.<extension>`.

For example, for `README.md`, the localized versions for different languages are named as follows:

- English: `README.md`
- Chinese (Simplified): `README_zh.md`
- Chinese (Traditional): `README_zh_Hant.md`
- Chinese (Classical): `README_lzh.md`

```json5
{
    "<Localization Key 1>": "<Localized Text 1>",
    "<Localization Key 2>": "<Localized Text 2>",
    // ...
    "<Localization Key N>": "<Localized Text N>"
}
```

For example, for the following text field:

```json
{
    "meow": "Meow"
}
```

it can be rewritten as a localized text:

```json
{
    "meow": {
        "default": "Meow",
        "zh": "喵呜",
        "zh-Hant": "喵嗚"
    }
}
```

## Resource Fallback Mechanism

For missing resources in a certain language, HMCL supports a resource fallback mechanism, which derives a search list based on different language tags and searches for resources in order according to this list.

For example, if the current environment's language tag is `en-US`, HMCL will search for the corresponding localized resources in the following order:

1. `en-US`
2. `en`
3. `und`

For resources that can be merged (such as `.properties` files), HMCL will merge resources according to the priority of this list; for resources that are difficult to merge (such as font files), HMCL will load the highest-priority resource found in this list.

If the current language uses a three-letter ISO 639 code, but a corresponding two-letter code also exists, HMCL will map it to the two-letter code before searching for resources.

For example, if the current environment's language tag is `eng-US`, HMCL will map it to `en-US` and then search for localized resources according to the above rules.

If the current language is a sublanguage of an [ISO 639 macrolanguage](https://en.wikipedia.org/wiki/ISO_639_macrolanguage), HMCL will also search for resources corresponding to the macrolanguage.

### Additional Rules for Chinese

For Chinese (and its sub-language tags, such as Classical Chinese (`lzh`), Mandarin (`cmn`), Cantonese (`yue`), etc.), HMCL provides additional support.

If the current environment's language is Chinese (or its sub-languages) and the script is not specified, HMCL will infer the default script based on the language and region tags.

For the language `lzh` or regions `TW`, `HK`, or `MO`, the default script is Traditional Chinese (`Hant`);
for other languages and regions, the default script is Simplified Chinese (`Hans`).

In addition, HMCL will add `zh-CN` to the search list for all Chinese environments, and add `zh-TW` to the search list for all Traditional Chinese environments,
to cover more scenarios.

Below are the localization resource search lists for several common Chinese environments.

- `zh-CN`:
    1. `zh-Hans-CN`
    2. `zh-Hans`
    3. `zh-CN`
    4. `zh`
    5. `und`
- `zh-SG`:
    1. `zh-Hans-SG`
    2. `zh-Hans`
    3. `zh-SG`
    4. `zh-CN`
    5. `zh`
    6. `und`
- `zh-TW`:
    1. `zh-Hant-TW`
    2. `zh-Hant`
    3. `zh-TW`
    4. `zh`
    5. `zh-CN`
    6. `und`
- `zh-HK`:
    1. `zh-Hant-HK`
    2. `zh-Hant`
    3. `zh-HK`
    4. `zh-TW`
    5. `zh`
    6. `zh-CN`
    7. `und`
- `lzh`:
    1. `lzh-Hant`
    2. `lzh`
    3. `zh-Hant`
    4. `zh`
    5. `und`

## Automatic Synchronization of Documentation Content

To simplify documentation maintenance, HMCL uses a macro mechanism to automatically maintain parts of the documentation content. Run the following command in the terminal

```bash
./gradlew updateDocuments
```

This will automatically update all documentation content.

For example, to create links for switching between different language versions of the same document, add the following content under the document title:

```markdown
<!-- #BEGIN LANGUAGE_SWITCHER -->
[English](Localization.md) | **中文**
<!-- #END LANGUAGE_SWITCHER -->
```

After running `./gradlew updateDocuments`, these two lines will be automatically replaced with language switcher links like this:

```markdown
**English** (**Standard**, [uʍoᗡ ǝpᴉsd∩](README_en_Qabs.md)) | 中文 ([简体](README_zh.md), [繁體](README_zh_Hant.md), [文言](README_lzh.md)) | [日本語](README_ja.md) | [español](README_es.md) | [русский](README_ru.md) | [українська](README_uk.md)
```

For more about macros, see [MacroProcessor.java](../buildSrc/src/main/java/org/jackhuang/hmcl/gradle/docs/MacroProcessor.java).
