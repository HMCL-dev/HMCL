# دليل البناء
<!-- #BEGIN LANGUAGE_SWITCHER -->
**English** | [中文](Building_zh.md)
<!-- #END LANGUAGE_SWITCHER -->
## المتطلبات
لبناء مُشغّل HMCL، تحتاج إلى تثبيت JDK 17 (أو أحدث). يمكنك تحميله من هنا: [Download Liberica JDK](https://bell-sw.com/pages/downloads/#jdk-25-lts).
بعد تثبيت JDK، تأكد من أن متغير البيئة `JAVA_HOME` يشير إلى دليل JDK المطلوب.
يمكنك التحقق من إصدار JDK الذي يشير إليه `JAVA_HOME` بهذه الطريقة:
<details>
<summary>Windows</summary>
PowerShell:
```
PS > & "$env:JAVA_HOME/bin/java.exe" -version
openjdk version "25" 2025-09-16 LTS
OpenJDK Runtime Environment (build 25+37-LTS)
OpenJDK 64-Bit Server VM (build 25+37-LTS, mixed mode, sharing)
```
</details>
<details>
<summary>Linux/macOS/FreeBSD</summary>
```
> $JAVA_HOME/bin/java -version
openjdk version "25" 2025-09-16 LTS
OpenJDK Runtime Environment (build 25+37-LTS)
OpenJDK 64-Bit Server VM (build 25+37-LTS, mixed mode, sharing)
```
</details>
## الحصول على الكود المصدري لـ HMCL
- يمكنك الحصول على أحدث كود مصدري عبر [Git](https://git-scm.com/downloads):
```shell
  git clone https://github.com/HMCL-dev/HMCL.git
  cd HMCL
```
- يمكنك تحميل إصدار محدد من الكود المصدري يدويًا من [صفحة إصدارات GitHub](https://github.com/HMCL-dev/HMCL/releases).
## بناء HMCL
لبناء HMCL، انتقل إلى الدليل الجذر لمشروع HMCL ونفّذ الأمر التالي:
```shell
./gradlew clean makeExecutables
```
ملفات برنامج HMCL المبنية موجودة في الدليل الفرعي `HMCL/build/libs` ضمن جذر المشروع.