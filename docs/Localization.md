# Localization

<!-- #BEGIN LANGUAGE_SWITCHER -->
**English** | 中文 ([简体](Localization_zh.md), [繁體](Localization_zh_Hant.md))
<!-- #END LANGUAGE_SWITCHER -->

HMCL provides localization support for multiple languages.

This document describes HMCL's support status for these languages and provides a guide for contributors who want to help with HMCL localization.

## Supported languages

Currently, HMCL supports the following languages:

| Language              | Language Tag | Preferred Localization Key | Preferred Localization File Suffix | [Game Language Files](https://minecraft.wiki/w/Language) | Support Status | Volunteers                                |
|-----------------------|--------------|----------------------------|------------------------------------|----------------------------------------------------------|----------------|-------------------------------------------|
| English               | `en`         | `default`                  | (empty)                            | `en_us`                                                  | **Primary**    | [Glavo](https://github.com/Glavo)         |
| English (Upside Down) | `en-Qabs`    | `en-Qabs`                  | `en_Qabs`                          | `en_ud`                                                  | Automatic      |                                           |
| Chinese (Simplified)  | `zh-Hans`    | `zh`                       | `_zh`                              | `zh_cn`                                                  | **Primary**    | [Glavo](https://github.com/Glavo)         |
| Chinese (Traditional) | `zh-Hant`    | `zh-Hant`                  | `_zh_Hant`                         | `zh_tw` <br/> `zh_hk`                                    | **Primary**    | [Glavo](https://github.com/Glavo)         |
| Chinese (Classical)   | `lzh`        | `lzh`                      | `_lzh`                             | `lzh`                                                    | Secondary      |                                           |
| Japanese              | `ja`         | `ja`                       | `_ja`                              | `ja_jp`                                                  | Secondary      |                                           |
| Spanish               | `es`         | `es`                       | `_es`                              | `es_es`                                                  | Secondary      | [3gf8jv4dv](https://github.com/3gf8jv4dv) |
| Russian               | `ru`         | `ru`                       | `_ru`                              | `ru_ru`                                                  | Secondary      | [3gf8jv4dv](https://github.com/3gf8jv4dv) |
| Ukrainian             | `uk`         | `uk`                       | `_uk`                              | `uk_ua`                                                  | Secondary      |                                           |

<details>
<summary>About Language Tags</summary>

HMCL uses language tags that conform to the IETF BCP 47 standard.

When choosing language tags, we follow these principles:

1. For languages defined in the ISO 639 standard, if a tag has already been registered in the [IANA Language Subtag Registry](https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry),
   we always use the registered tag.

   For example, for English, we use `en` instead of `eng` as the language code.

2. For non-standard languages defined by Minecraft, the code defined in the language file's `language.code` should be preferred over the game language file's name.

   This is because Minecraft sometimes uses real-world country/region codes to represent joke languages 
   (for example, Pirate English uses the language file `en_pt`, but `PT` is actually the country code for Portugal).

   For example, for Upside down English, we use `en-Qabs` as the language code instead of `en-UD`.

</details>

<details>
<summary>About Localization Keys and File Suffixes</summary>

Localization file suffixes and keys are used to name [localization resources](#localization-resources).

Generally, the localization key is the language code for the resource, and the localization file suffix is obtained by replacing `-` with `_` in the language code and adding a leading underscore.

As a special case, for default resources, the localization key is `default` and the file suffix is empty.

Due to the existence of the [resource fallback mechanism](#resource-fallback-mechanism),
if there is no resource that exactly matches the current locale, 
HMCL will derive a search list from the current language tag and search for resources in order.

We recommend always providing a default resource (with the `default` key and empty file suffix) when providing localization resources,
to ensure all users can load resources properly.

We also recommend using broader language tags for localization resources whenever possible,
so users are less likely to fall back to the default resource.

For example, if you provide a Simplified Chinese localization resource, 
we recommend using `zh` as the localization key instead of the more specific `zh-Hans`,
so it will apply to all Chinese users and avoid falling back to the default resource for them.

If you want to provide both Simplified and Traditional Chinese resources, 
it is recommended to use the broader `zh` as the key for the resource with more users (as the default Chinese resource),
and use the more specific `zh-Hans`/`zh-Hant` as the key for the resource with fewer users.

</details>

HMCL requires all pull requests that update documentation and localization resources to also update the resources
for all **primary** supported languages.
If the PR author is not familiar with the relevant languages, they can request translation help in the comments,
and maintainers will help translate these texts before merging the PR.

For **secondary** supported languages, we cannot guarantee that these localization resources will always be updated in sync.
We need collaborators who are proficient in these languages to help with maintenance.

We will record volunteers willing to help maintain these language resources in the documentation.
If contributors want to have new localization texts translated into these languages in a timely manner,
they can @ these volunteers in the PR for help.

If you are willing to help maintain localization resources for any language, please open a PR
and add your GitHub username to the volunteer list above.
We greatly appreciate your help.

## Adding Support for a New Language

HMCL welcomes anyone to participate in translation and contribution. 
However, maintaining translations for more languages requires more maintenance effort, so we have some requirements for newly added languages.
Please confirm the following requirements before contributing:

- We prioritize [languages officially supported by Minecraft](https://minecraft.wiki/w/Language).

  Unless there are special reasons, we do not provide support for languages not yet supported by Minecraft.
- We hope to provide long-term maintenance support for all languages.

  Since the maintainers of this project are proficient in only a limited number of languages, 
  to avoid support for new languages quickly becoming outdated due to lack of maintainers,
  we hope to find people proficient in the language to help us maintain the newly added localization files in the long term.
  If there may be a lack of long-term maintainers, we will be more cautious about adding support for that language.

We recommend that contributors submit a [feature request](https://github.com/HMCL-dev/HMCL/issues/new?template=feature.yml) before providing a new language translation,
discuss with other contributors first, and determine the future maintenance plan before starting the translation work.

### Getting Started with Translation

If you want to add support for a new language to HMCL, please start by translating [`I18N.properties`](../HMCL/src/main/resources/assets/lang/I18N.properties).
The vast majority of HMCL's texts are in this file, and translating it will translate the entire interface.

This is a Java Properties file, which is very simple in format.
Before translating, please read the introduction to this format: [Properties file](https://en.wikipedia.org/wiki/.properties).

As the first step of translation, please look up the two- or three-letter language tag for your language in [this table](https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry).
For example, the language tag for English is `en`.

After determining the language tag, please create a file named `I18N_<localization file suffix>.properties` (e.g., `I18N_en.properties`) next to the [`I18N.properties` file](../HMCL/src/main/resources/assets/lang).
Then you can start translating in this file.

The `I18N.properties` file follows the [resource fallback mechanism](#resource-fallback-mechanism) to look up missing translations.
That is, you can translate entry by entry, and any untranslated entries will automatically fall back to English.

After translating part of the file, you can [build HMCL yourself](./Contributing.md#build-hmcl), 
and your translations will be included in the compiled HMCL.
If your computer's default environment is not the target language,
you can set the environment variable `HMCL_LANGUAGE` to the language tag you just found from the table,
and HMCL will automatically switch to that language.

At this point, you can push the file to GitHub and submit a PR to HMCL.
The maintainers of HMCL will complete the remaining steps for you.

## Localization Resources

All documentation and localization resource files follow the naming rule `<resource name><localization file suffix>.<extension>`.

For example, for `README.md`, the localized versions in different languages are named as follows:

- English: `README.md`
- Chinese (Simplified): `README_zh.md`
- Chinese (Traditional): `README_zh_Hant.md`
- Chinese (Classical): `README_lzh.md`

In addition to localized files, HMCL also supports localizing certain text fields in JSON. Localized text in JSON uses the following format:

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

It can be rewritten as localized text:

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

For missing resources in a certain language, HMCL supports a resource fallback mechanism,
which derives a search list based on different language tags and searches for resources in order according to this list.

Before searching, we first refine the language tag through the following steps.

1. Normalize Language Codes

   If the language code subtag in the current language tag is not registered in the IANA Language Subtag Registry, 
   HMCL will try to map it to a registered tag.

   For example, HMCL will replace the language code `eng` with `en`.

2. Map Macrolanguages to Individual Languages

   If the current language code is an [ISO 639 macrolanguage](https://en.wikipedia.org/wiki/ISO_639_macrolanguage), 
   and the macrolanguage usually refers to a specific individual language, HMCL will replace it with that individual language.

   For example, `zh` (Chinese) usually actually refers to `cmn` (Mandarin), so we replace the language code `zh` with `cmn`.

3. Derive Script

   If the current language tag does not specify a script, HMCL will try to derive the script according to the following rules in order:

    1. If the current language tag specifies a variant, and the variant is registered in the IANA Language Subtag Registry, 
       and all its `Prefix` entries in the registry contain the same script, then set the current script to that script.

       For example, if the current variant is `pinyin` (Chinese Pinyin), the script will be set to `Latn` (Latin).

    2. If the current language code is assigned a `Suppress-Script` in the IANA Language Subtag Registry, set the current script to that script.

       For example, if the current language code is `en` (English), the script will be set to `Latn` (Latin); 
       if the code is `ru` (Russian), the script will be set to `Cyrl` (Cyrillic).

    3. If the current language code is `lzh` (Classical Chinese), set the script to `Hant` (Traditional Chinese Characters).

    4. If the current language code is `zh` or a sublanguage of `zh`, check if the current region code is one of `TW`, `HK`, or `MO`. 
       If true, set the script to `Hant` (Traditional Chinese Characters);
       otherwise, set it to `Hans` (Simplified Chinese Characters).

After refining the language code, HMCL will derive a list of language tags based on this language tag.

For example, for the language tag `en-US`, HMCL will refine it to `en-Latn-US` and derive the following search list:

1. `en-Latn-US`
2. `en-Latn`
3. `en-US`
4. `en`
5. `und`

For the language tag `zh-CN`, HMCL will refine it to `cmn-Hans-CN` and derive the following search list:

1. `cmn-Hans-CN`
2. `cmn-Hans`
3. `cmn-CN`
4. `cmn`
5. `zh-Hans-CN`
6. `zh-Hans`
7. `zh-CN`
8. `zh`
9. `und`

For resources that can be merged (such as `.properties` files), 
HMCL will merge resources according to the priority of this list; for resources that are difficult to merge (such as font files), 
HMCL will load the highest-priority resource found in this list.

If the current language uses a three-letter ISO 639 code, but there is also a corresponding two-letter code, 
HMCL will map it to the two-letter code before searching for resources.

For example, if the current environment's language tag is `eng-US`, 
HMCL will map it to `en-US` and then search for localization resources according to the above rules.

### Additional Rules for Chinese

HMCL always adds `zh-CN` to the search list for all Chinese environments, 
and adds `zh-TW` to the search list for all Traditional Chinese environments.

Below are the localization resource search lists for several common Chinese environments.

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
    1. `cmn-Hans-SG`
    2. `cmn-Hans`
    3. `cmn-SG`
    4. `cmn`
    5. `zh-Hans-SG`
    6. `zh-Hans`
    7. `zh-SG`
    8. `zh-CN`
    9. `zh`
    10. `und`
- `zh-TW`:
    1. `zh-Hant-TW`
    2. `zh-Hant`
    3. `zh-TW`
    4. `zh`
    5. `zh-CN`
    6. `und`
- `zh-HK`:
    1. `cmn-Hant-HK`
    2. `cmn-Hant`
    3. `cmn-HK`
    4. `cmn`
    5. `zh-Hant-HK`
    6. `zh-Hant`
    7. `zh-HK`
    8. `zh-TW`
    9. `zh`
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

## Automatic Synchronization of Documentation Content

<!-- #BEGIN BLOCK -->
<!-- #PROPERTY PROCESS_LINK=false -->
To simplify documentation maintenance, HMCL uses a macro mechanism to automatically maintain parts of the documentation content.
Run the following command in the terminal:

```bash
./gradlew updateDocuments
```

This will automatically update all documentation content.

For example, to create links for switching between different language versions of the same document, 
add the following content under the document title:

```markdown
<!-- #BEGIN LANGUAGE_SWITCHER -->
<!-- #END LANGUAGE_SWITCHER -->
```

After running `./gradlew updateDocuments`, these two lines will be automatically replaced with language switcher links like the following:

```markdown
**English** (**Standard**, [uʍoᗡ ǝpᴉsd∩](README_en_Qabs.md)) | 中文 ([简体](README_zh.md), [繁體](README_zh_Hant.md), [文言](README_lzh.md)) | [日本語](README_ja.md) | [español](README_es.md) | [русский](README_ru.md) | [українська](README_uk.md)
```

For more about macros, see [MacroProcessor.java](../buildSrc/src/main/java/org/jackhuang/hmcl/gradle/docs/MacroProcessor.java).
<!-- #END BLOCK -->
