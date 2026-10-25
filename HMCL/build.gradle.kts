/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import org.jackhuang.hmcl.gradle.addon.FormatJsonFile
import org.jackhuang.hmcl.gradle.addon.UpdateAddonProjectInfos
import org.jackhuang.hmcl.gradle.ci.GitHubActionUtils
import org.jackhuang.hmcl.gradle.ci.JenkinsUtils
import org.jackhuang.hmcl.gradle.l10n.*
import org.jackhuang.hmcl.gradle.pack.CreateDeb
import org.jackhuang.hmcl.gradle.pack.ReleaseType
import org.jackhuang.hmcl.gradle.terracotta.AbstractTerracottaTask
import org.jackhuang.hmcl.gradle.terracotta.TerracottaConfigUpgradeTask
import org.jackhuang.hmcl.gradle.terracotta.TerracottaConfigValidateTask
import org.jackhuang.hmcl.gradle.utils.ArtifactUtils.Companion.resolveArtifactFile
import org.jackhuang.hmcl.gradle.utils.ArtifactUtils.Companion.attachSignature
import org.jackhuang.hmcl.gradle.utils.ArtifactUtils.Companion.createChecksum
import org.jackhuang.hmcl.gradle.utils.PropertiesUtils
import org.jackhuang.hmcl.gradle.utils.OfficialBuildValueSource
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.shadow)
}

val projectConfig = PropertiesUtils.load(providers.fileContents(rootProject.layout.projectDirectory.file("config/project.properties")))

val isOfficial = JenkinsUtils.IS_ON_CI || GitHubActionUtils.IS_ON_OFFICIAL_REPO

val versionType = providers.environmentVariable("VERSION_TYPE").getOrElse(if (isOfficial) "nightly" else "unofficial")
val versionRoot = providers.environmentVariable("VERSION_ROOT").getOrElse(projectConfig.getProperty("versionRoot") ?: "3")

val microsoftAuthId = providers.environmentVariable("MICROSOFT_AUTH_ID").getOrElse("")
val curseForgeApiKey = providers.environmentVariable("CURSEFORGE_API_KEY").getOrElse("")

val launcherExe = providers.environmentVariable("HMCL_LAUNCHER_EXE").getOrElse("")

val buildNumber = providers.environmentVariable("BUILD_NUMBER").orNull?.toInt()
if (buildNumber != null) {
    version = if (JenkinsUtils.IS_ON_CI && versionType == "dev") {
        "$versionRoot.0.$buildNumber"
    } else {
        "$versionRoot.$buildNumber"
    }
} else {
    val shortCommit = providers.environmentVariable("GITHUB_SHA").orNull?.lowercase()?.substring(0, 7)
    version = if (shortCommit.isNullOrBlank()) {
        "$versionRoot.SNAPSHOT"
    } else if (isOfficial) {
        "$versionRoot.dev-$shortCommit"
    } else {
        "$versionRoot.unofficial-$shortCommit"
    }
}

val embedResources = configurations.register("embedResources")

dependencies {
    implementation(project(":HMCLCore"))
    implementation(project(":HMCLBoot"))
    implementation("libs:JFoenix")
    implementation(libs.jwebp)
    implementation(libs.fxsvgimage)
    implementation(libs.java.info)
    implementation(libs.monet.fx)
    implementation(libs.nayuki.qrcodegen)
    implementation(libs.uuid.tools)

    testImplementation(libs.jimfs)

    if (launcherExe.isBlank()) {
        implementation(libs.hmclauncher)
    }

    embedResources(libs.authlib.injector)
    embedResources(libs.lwjgl.unsafe.agent)
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

tasks.checkstyleMain {
    // Third-party code is not checked
    exclude("**/org/jackhuang/hmcl/ui/image/apng/**")
}

val addOpens = listOf(
    "java.base/java.lang",
    "java.base/java.lang.reflect",
    "java.base/jdk.internal.loader",
    "javafx.base/com.sun.javafx.binding",
    "javafx.base/com.sun.javafx.event",
    "javafx.base/com.sun.javafx.runtime",
    "javafx.base/javafx.beans.property",
    "javafx.graphics/javafx.css",
    "javafx.graphics/javafx.stage",
    "javafx.graphics/javafx.scene",
    "javafx.graphics/com.sun.glass.ui",
    "javafx.graphics/com.sun.javafx.stage",
    "javafx.graphics/com.sun.javafx.util",
    "javafx.graphics/com.sun.prism",
    "javafx.controls/com.sun.javafx.scene.control",
    "javafx.controls/com.sun.javafx.scene.control.behavior",
    "javafx.graphics/com.sun.javafx.tk.quantum",
    "javafx.controls/javafx.scene.control",
    "javafx.controls/javafx.scene.control.skin",
    "jdk.attach/sun.tools.attach",
)

tasks.compileJava {
    options.compilerArgs.addAll(addOpens.map { "--add-exports=$it=ALL-UNNAMED" })
}

val createPropertiesFile = tasks.register<WriteProperties>("createPropertiesFile") {
    destinationFile.set(layout.buildDirectory.file("hmcl.properties"))
    encoding = "UTF-8"

    property("hmcl.version", project.version.toString())
    property("hmcl.add-opens", addOpens.joinToString(" "))
    providers.environmentVariable("GITHUB_SHA").orNull?.let { property("hmcl.version.hash", it) }
    property("hmcl.version.type", versionType)
    property("hmcl.microsoft.auth.id", microsoftAuthId)
    property("hmcl.curseforge.apikey", curseForgeApiKey)
    property("hmcl.authlib-injector.version", libs.authlib.injector.get().version!!)
    property("hmcl.lwjgl-unsafe-agent.version", libs.lwjgl.unsafe.agent.get().version!!)
    property("hmcl.official", providers.of(OfficialBuildValueSource::class) {
        parameters.publicKeyFile.set(layout.projectDirectory.file("src/main/resources/assets/hmcl_signature_publickey.der"))
        parameters.privateKeyLocation.set(providers.environmentVariable("HMCL_SIGNATURE_KEY"))
        // Pin the official public key independently of the replaceable resource file.
        // Under HMCL’s license agreement, you **MUST** change the software name or the version number in
        // a reasonable way to distinguish it from the original version.
        // You may modify this hash if and only if you have changed the software name or version number
        // in accordance with the license requirements.
        parameters.expectedSha256.set("36aadcfde8626b9d4c7a74facb3fdfda47e703c205a673fbe6a56b1d419f8b8e")
    })
}

tasks.jar {
    enabled = false
    dependsOn(tasks["shadowJar"])
}

val jarPath = tasks.jar.get().archiveFile.get().asFile

tasks.shadowJar {
    dependsOn(createPropertiesFile)

    archiveClassifier.set(null as String?)

    exclude("**/package-info.class")
    exclude("META-INF/maven/**")

    exclude("META-INF/services/javax.imageio.spi.ImageReaderSpi")
    exclude("META-INF/services/javax.imageio.spi.ImageInputStreamSpi")

    listOf(
        "aix-*", "sunos-*", "openbsd-*", "dragonflybsd-*", "freebsd-*", "linux-*",
        "*-ppc", "*-ppc64le", "*-s390x", "*-armel",
    ).forEach { exclude("com/sun/jna/$it/**") }

    minimize {
        exclude(dependency("com.google.code.gson:.*:.*"))
        exclude(dependency("net.java.dev.jna:jna:.*"))
        exclude(dependency("libs:JFoenix:.*"))
        exclude(project(":HMCLBoot"))
    }

    manifest.attributes(
        "Created-By" to "Copyright(c) 2013-2025 huangyuhui.",
        "Implementation-Version" to project.version.toString(),
        "Main-Class" to "org.jackhuang.hmcl.Main",
        "Multi-Release" to "true",
        "Add-Opens" to addOpens.joinToString(" "),
        "Enable-Native-Access" to "ALL-UNNAMED",
        "Enable-Final-Field-Mutation" to "ALL-UNNAMED",
    )

    if (launcherExe.isNotBlank()) {
        into("assets") {
            from(file(launcherExe))
        }
    }

    val jarFile = jarPath
    val keyLocation = providers.environmentVariable("HMCL_SIGNATURE_KEY").orNull

    doLast {
        if (keyLocation != null) {
            attachSignature(jarFile, keyLocation)
        } else {
            logger.warn("Missing signature key")
        }
        createChecksum(jarFile)
    }
}

tasks.processResources {
    dependsOn(upsideDownTranslate)
    dependsOn(createLocaleNamesResourceBundle)
    dependsOn(createLanguageList)
    dependsOn(validateTerracottaConfig)

    into("assets/") {
        from(createPropertiesFile.flatMap { it.destinationFile })
        from(embedResources)
    }

    into("assets/lang") {
        from(createLanguageList.map { it.outputFile })
        from(upsideDownTranslate.map { it.outputFile })
        from(createLocaleNamesResourceBundle.map { it.outputDirectory })
    }
}

val makeExecutables = tasks.register("makeExecutables") {
    val extensions = listOf("exe", "sh")

    dependsOn(tasks.jar)

    inputs.file(jarPath)
    outputs.files(extensions.map { resolveArtifactFile(jarPath, it) })

    val jarFile = jarPath

    doLast {
        val jarContent = jarFile.readBytes()

        ZipFile(jarFile).use { zipFile ->
            for (extension in extensions) {
                val output = resolveArtifactFile(jarFile, extension)
                val entry = zipFile.getEntry("assets/HMCLauncher.$extension")
                    ?: throw GradleException("HMCLauncher.$extension not found")

                output.outputStream().use { outputStream ->
                    zipFile.getInputStream(entry).use { it.copyTo(outputStream) }
                    outputStream.write(jarContent)
                }

                createChecksum(output)
            }
        }
    }
}

val makeDeb = tasks.register("makeDeb", CreateDeb::class) {
    dependsOn(makeExecutables)

    val debFile = layout.file(provider { resolveArtifactFile(jarPath, "deb") })

    val debChannel = when (versionType) {
        "stable" -> ReleaseType.STABLE
        "dev" -> ReleaseType.DEVELOPMENT
        else -> ReleaseType.NIGHTLY
    }

    version.set(project.version.toString())
    releaseType.set(debChannel)
    launcherClassName.set("org.jackhuang.hmcl.Launcher")
    appShFile.set(layout.file(provider { resolveArtifactFile(jarPath, "sh") }))
    iconFile.set(layout.projectDirectory.file("image/hmcl.png"))
    outputFile.set(debFile)

    doLast {
        createChecksum(debFile.get().asFile)
    }
}

tasks.build {
    dependsOn(makeExecutables)
    dependsOn(makeDeb)
}

fun parseToolOptions(options: String?): MutableList<String> {
    if (options == null)
        return mutableListOf()

    val builder = StringBuilder()
    val result = mutableListOf<String>()

    var offset = 0

    loop@ while (offset < options.length) {
        val ch = options[offset]
        if (Character.isWhitespace(ch)) {
            if (builder.isNotEmpty()) {
                result += builder.toString()
                builder.clear()
            }

            while (offset < options.length && Character.isWhitespace(options[offset])) {
                offset++
            }

            continue@loop
        }

        if (ch == '\'' || ch == '"') {
            offset++

            while (offset < options.length) {
                val ch2 = options[offset++]
                if (ch2 != ch) {
                    builder.append(ch2)
                } else {
                    continue@loop
                }
            }

            throw GradleException("Unmatched quote in $options")
        }

        builder.append(ch)
        offset++
    }

    if (builder.isNotEmpty()) {
        result += builder.toString()
    }

    return result
}

// For IntelliJ IDEA
tasks.withType<JavaExec> {
    if (name != "run") {
        jvmArgs(addOpens.map { "--add-opens=$it=ALL-UNNAMED" })
//        if (javaVersion >= JavaVersion.VERSION_24) {
//            jvmArgs("--enable-native-access=ALL-UNNAMED")
//        }
    }
}

tasks.register<JavaExec>("run") {
    dependsOn(tasks.jar)

    group = "application"

    classpath = files(jarPath)
    workingDir = rootProject.rootDir

    val vmOptions = parseToolOptions(providers.environmentVariable("HMCL_JAVA_OPTS").getOrElse("-Xmx1g"))
    if (vmOptions.none { it.startsWith("-Dhmcl.offline.auth.restricted=") })
        vmOptions += "-Dhmcl.offline.auth.restricted=false"

    jvmArgs(vmOptions)

    val hmclJavaHome = providers.environmentVariable("HMCL_JAVA_HOME").orNull
    if (hmclJavaHome != null) {
        this.executable(
            file(hmclJavaHome).resolve("bin")
                .resolve(if (providers.systemProperty("os.name").orNull?.lowercase()?.startsWith("windows") ?: false) "java.exe" else "java")
        )
    }

    doFirst {
        logger.quiet("HMCL_JAVA_OPTS: {}", vmOptions)
        logger.quiet("HMCL_JAVA_HOME: {}", hmclJavaHome ?: providers.systemProperty("java.home").orNull)
    }
}

// terracotta

val terracottaConfigFile = layout.projectDirectory.file("src/main/resources/assets/terracotta.json")

val upgradeTerracottaConfig = tasks.register<TerracottaConfigUpgradeTask>("upgradeTerracottaConfig") {
    configFile = terracottaConfigFile

    classifiers = listOf(
        "windows-x86_64", "windows-arm64",
        "macos-x86_64", "macos-arm64",
        "linux-x86_64", "linux-arm64", "linux-loongarch64", "linux-riscv64",
        "freebsd-x86_64"
    )

    downloadURL = $$"https://github.com/burningtnt/Terracotta/releases/download/v${version}/terracotta-${version}-${classifier}-pkg.tar.gz"

    templateFile = layout.projectDirectory.file("terracotta-template.json")
}

val validateTerracottaConfig = tasks.register<TerracottaConfigValidateTask>("validateTerracottaConfig") {
    configFile = terracottaConfigFile
    upgradeTaskPath = upgradeTerracottaConfig.get().path
}

tasks.withType<AbstractTerracottaTask> {
    version = libs.versions.terracotta
}

// Check Translations

tasks.register<CheckTranslations>("checkTranslations") {
    val dir = layout.projectDirectory.dir("src/main/resources/assets/lang")

    englishFile.set(dir.file("I18N.properties"))
    simplifiedChineseFile.set(dir.file("I18N_zh_Hans.properties"))
    traditionalChineseFile.set(dir.file("I18N_zh_Hant.properties"))
    classicalChineseFile.set(dir.file("I18N_lzh.properties"))
}

// l10n

tasks.register<SyncTranslations>("syncTranslations") {
    group = "localization"
    description = "Synchronizes translation keys and blank lines with I18N.properties."

    val dir = layout.projectDirectory.dir("src/main/resources/assets/lang")
    sourceFile.set(dir.file("I18N.properties"))
    translationFiles.from(fileTree(dir) { include("I18N_*.properties") })
}

val generatedDir = layout.buildDirectory.dir("generated")

val upsideDownTranslate = tasks.register<UpsideDownTranslate>("upsideDownTranslate") {
    inputFile.set(layout.projectDirectory.file("src/main/resources/assets/lang/I18N.properties"))
    outputFile.set(generatedDir.map { it.file("generated/i18n/I18N_en_Qabs.properties") })
}

val createLanguageList = tasks.register<CreateLanguageList>("createLanguageList") {
    resourceBundleDir.set(layout.projectDirectory.dir("src/main/resources/assets/lang"))
    resourceBundleBaseName.set("I18N")
    additionalLanguages.set(listOf("en-Qabs"))
    outputFile.set(generatedDir.map { it.file("languages.json") })
}

val createLocaleNamesResourceBundle =
    tasks.register<CreateLocaleNamesResourceBundle>("createLocaleNamesResourceBundle") {
        dependsOn(createLanguageList)

        languagesFile.set(createLanguageList.flatMap { it.outputFile })
        outputDirectory.set(generatedDir.map { it.dir("generated/LocaleNames") })
    }

// mcmod data

tasks.register<FormatJsonFile>("formatModJson") {
    file.set(layout.projectDirectory.file("mod.json"))
}

tasks.register<FormatJsonFile>("formatModpackJson") {
    file.set(layout.projectDirectory.file("modpack.json"))
}

tasks.register<UpdateAddonProjectInfos>("updateModProjectInfos") {
    inputFile.set(layout.projectDirectory.file("mod.json"))
    outputFile.set(layout.projectDirectory.file("src/main/resources/assets/addon/mod.csv"))
    projectType.set(UpdateAddonProjectInfos.AddonProjectType.MOD)
}

tasks.register<UpdateAddonProjectInfos>("updateModpackProjectInfos") {
    inputFile.set(layout.projectDirectory.file("modpack.json"))
    outputFile.set(layout.projectDirectory.file("src/main/resources/assets/addon/modpack.csv"))
    projectType.set(UpdateAddonProjectInfos.AddonProjectType.MODPACK)
}
