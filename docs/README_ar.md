# مُشغّل Hello Minecraft!

<!-- #BEGIN BLOCK -->
<!-- #PROPERTY NAME=BADGES -->
[![Downloads](https://img.shields.io/github/downloads/HMCL-dev/HMCL/total?label=Downloads&style=flat)](https://github.com/HMCL-dev/HMCL/releases)
![Stars](https://img.shields.io/github/stars/HMCL-dev/HMCL?style=flat)
[![Discord](https://img.shields.io/discord/995291757799538688.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/jVvC7HfM6U)
[![QQ Group](https://img.shields.io/badge/QQ-HMCL-bright?label=&logo=qq&logoColor=ffffff&color=1EBAFC&labelColor=1DB0EF&logoSize=auto)](https://docs.hmcl.net/groups.html)
<!-- #END BLOCK -->

<!-- #BEGIN LANGUAGE_SWITCHER -->
**English** (**Standard**, [uʍoᗡ ǝpᴉsd∩](README_en_Qabs.md)) | 中文 ([简体](README_zh.md), [繁體](README_zh_Hant.md), [文言](README_lzh.md)) | [日本語](README_ja.md) | [español](README_es.md) | [русский](README_ru.md) | [українська](README_uk.md)
<!-- #END LANGUAGE_SWITCHER -->

## مقدمة

HMCL هو مُشغّل ماينكرافت مفتوح المصدر ومتعدد المنصات يدعم إدارة التعديلات (Mod)، تخصيص اللعبة، تثبيت محمّلات التعديلات (Forge، NeoForge، Cleanroom، Fabric، Quilt، LiteLoader، و OptiFine)، إنشاء حزم التعديلات، تخصيص واجهة المستخدم، والمزيد.

يتمتع HMCL بقدرات مذهلة عبر المنصات المختلفة. فهو لا يعمل فقط على أنظمة تشغيل مختلفة مثل Windows و Linux و macOS و FreeBSD، بل يدعم أيضًا معماريات المعالج المتنوعة مثل x86 و ARM و RISC-V و MIPS و LoongArch. يمكنك الاستمتاع بماينكرافت بسهولة عبر منصات مختلفة من خلال HMCL.

للاطلاع على الأنظمة ومعماريات المعالج المدعومة من قبل HMCL، يرجى الرجوع إلى [هذا الجدول](PLATFORM.md).

## التحميل

قم بتحميل أحدث إصدار من [الموقع الرسمي](https://hmcl.huangyuhui.net/download).

يمكنك أيضًا العثور على أحدث إصدار من HMCL في [GitHub Releases](https://github.com/HMCL-dev/HMCL/releases).

على الرغم من أن الأمر ليس ضروريًا، يُنصح فقط بتحميل الإصدارات من المواقع الرسمية المذكورة أعلاه.

## الترخيص

يتم توزيع البرنامج بموجب ترخيص [GPLv3](https://www.gnu.org/licenses/gpl-3.0.html) مع الشروط الإضافية التالية:

### شروط إضافية بموجب القسم 7 من GPLv3

1. عند توزيع نسخة معدلة من البرنامج، يجب عليك تغيير اسم البرنامج أو رقم الإصدار بطريقة معقولة من أجل تمييزه عن النسخة الأصلية. (بموجب [GPLv3, 7(c)](https://github.com/HMCL-dev/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L372-L374))

   يمكن تعديل اسم البرنامج ورقم الإصدار [هنا](https://github.com/HMCL-dev/HMCL/blob/javafx/HMCL/src/main/java/org/jackhuang/hmcl/Metadata.java#L33-L35).

2. يجب عدم إزالة إعلان حقوق النشر المعروض في البرنامج. (بموجب [GPLv3, 7(b)](https://github.com/HMCL-dev/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L368-L370))

## المساهمة

إذا كنت ترغب في تقديم طلب دمج (pull request)، إليك بعض المتطلبات:

* بيئة التطوير: IntelliJ IDEA
* المترجم: Java 17+

### الترجمة (Compilation)

راجع صفحة [دليل البناء](./Building.md).

## خيارات JVM (لتصحيح الأخطاء)

| المعامل                                      | الوصف                                                                                         |
| -------------------------------------------- | --------------------------------------------------------------------------------------------- |
| `-Dhmcl.home=<path>`                         | تجاوز دليل HMCL                                                                               |
| `-Dhmcl.self_integrity_check.disable=true`   | تجاوز فحص السلامة الذاتية عند التحقق من التحديثات                                             |
| `-Dhmcl.bmclapi.override=<url>`              | تجاوز جذر API لمزود تحميل BMCLAPI. الافتراضي `https://bmclapi2.bangbang93.com`                |
| `-Dhmcl.font.override=<font family>`         | تجاوز عائلة الخط                                                                              |
| `-Dhmcl.version.override=<version>`          | تجاوز رقم الإصدار                                                                             |
| `-Dhmcl.update_source.override=<url>`        | تجاوز مصدر التحديث لـ HMCL نفسه                                                               |
| `-Dhmcl.authlibinjector.location=<path>`     | استخدام authlib-injector المحدد (بدلاً من تحميل واحد)                                         |
| `-Dhmcl.openjfx.repo=<maven repository url>` | إضافة مستودع Maven مخصص لتحميل OpenJFX                                                         |
| `-Dhmcl.native.encoding=<encoding>`          | تجاوز الترميز الأصلي                                                                          |
| `-Dhmcl.microsoft.auth.id=<App ID>`          | تجاوز معرّف تطبيق Microsoft OAuth                                                             |
| `-Dhmcl.microsoft.auth.secret=<App Secret>`  | تجاوز سر تطبيق Microsoft OAuth                                                                |
| `-Dhmcl.curseforge.apikey=<Api Key>`         | تجاوز مفتاح API الخاص بـ CurseForge                                                           |