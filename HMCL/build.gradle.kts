import java.nio.file.FileSystems
import java.nio.file.Files
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile
import java.util.zip.GZIPOutputStream
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.net.URI

import org.glavo.pack200.Pack200
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream

buildscript {
    repositories {
        gradlePluginPortal()
        maven(url = "https://jitpack.io")
    }
    dependencies {
        classpath("org.tukaani:xz:1.8")
        classpath("org.glavo:pack200:0.3.0")
    }
}

plugins {
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

val buildNumber = System.getenv("BUILD_NUMBER")?.toInt().let { number ->
    val offset = System.getenv("BUILD_NUMBER_OFFSET")?.toInt() ?: 0
    if (number != null) {
        (number - offset).toString()
    } else {
        val shortCommit = System.getenv("GITHUB_SHA")?.toLowerCase()?.substring(0, 7)
        if (!shortCommit.isNullOrEmpty()) "dev-$shortCommit" else "SNAPSHOT"
    }
}
val versionRoot = System.getenv("VERSION_ROOT") ?: "3.5"
val versionType = System.getenv("VERSION_TYPE") ?: "nightly"

val microsoftAuthId = System.getenv("MICROSOFT_AUTH_ID") ?: ""
val microsoftAuthSecret = System.getenv("MICROSOFT_AUTH_SECRET") ?: ""
val curseForgeApiKey = System.getenv("CURSEFORGE_API_KEY") ?: ""

version = "$versionRoot.$buildNumber"

dependencies {
    implementation(project(":HMCLCore"))
    implementation("libs:JFoenix")

    implementation("de.javawi.jstun:jstun:0.7.4")
}

fun digest(algorithm: String, bytes: ByteArray) = MessageDigest.getInstance(algorithm).digest(bytes)

fun createChecksum(file: File) {
    val algorithms = linkedMapOf(
        "MD5" to "md5",
        "SHA-1" to "sha1",
        "SHA-256" to "sha256",
        "SHA-512" to "sha512"
    )

    algorithms.forEach { (algorithm, ext) ->
        File(file.parentFile, "${file.name}.$ext").writeText(
            digest(algorithm, file.readBytes()).joinToString(separator = "", postfix = "\n") { "%02x".format(it) }
        )
    }
}

fun attachSignature(jar: File) {
    val keyLocation = System.getenv("HMCL_SIGNATURE_KEY") ?: return
    val privatekey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(File(keyLocation).readBytes()))
    val signer = Signature.getInstance("SHA512withRSA")
    signer.initSign(privatekey)
    ZipFile(jar).use { zip ->
        zip.stream()
            .sorted(Comparator.comparing { it.name })
            .filter { it.name != "META-INF/hmcl_signature" }
            .forEach {
                signer.update(digest("SHA-512", it.name.toByteArray()))
                signer.update(digest("SHA-512", zip.getInputStream(it).readBytes()))
            }
    }
    val signature = signer.sign()
    FileSystems.newFileSystem(URI.create("jar:" + jar.toURI()), emptyMap<String, Any>()).use { zipfs ->
        Files.newOutputStream(zipfs.getPath("META-INF/hmcl_signature")).use { it.write(signature) }
    }
}

val packer = Pack200.newPacker().apply {
    properties()["pack.effort"] = "9"
}

val unpacker = Pack200.newUnpacker()

// Pack200 does not guarantee that unpacked .class file is bit-wise same as the .class file before packing
// because of shrinking. So we should pack .class files and unpack it to make sure that after unpacking
// .class files remain the same.
fun repack(file: File) {
    val packed = ByteArrayOutputStream()
    JarFile(file).use { packer.pack(it, packed) }
    JarOutputStream(file.outputStream()).use { unpacker.unpack(ByteArrayInputStream(packed.toByteArray()), it) }
}

val java11 = sourceSets.create("java11") {
    java {
        srcDir("src/main/java11")
    }
}

tasks.getByName<JavaCompile>(java11.compileJavaTaskName) {
    if (JavaVersion.current() < JavaVersion.VERSION_11) {
        javaCompiler.set(javaToolchains.compilerFor {
            languageVersion.set(JavaLanguageVersion.of(11))
        })
    }
    options.compilerArgs.add("--add-exports=java.base/jdk.internal.loader=ALL-UNNAMED")
    sourceCompatibility = "11"
    targetCompatibility = "11"
}

tasks.jar {
    enabled = false
    dependsOn(tasks["shadowJar"])
}

val jarPath = tasks.jar.get().archiveFile.get().asFile

tasks.getByName<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set(null as String?)

    minimize {
        exclude(dependency("com.google.code.gson:.*:.*"))
        exclude(dependency("com.github.steveice10:.*:.*"))
        exclude(dependency("libs:JFoenix:.*"))
    }

    manifest {
        attributes(
            "Created-By" to "Copyright(c) 2013-2021 huangyuhui.",
            "Main-Class" to "org.jackhuang.hmcl.Main",
            "Multi-Release" to "true",
            "Implementation-Version" to project.version,
            "Microsoft-Auth-Id" to microsoftAuthId,
            "Microsoft-Auth-Secret" to microsoftAuthSecret,
            "CurseForge-Api-Key" to curseForgeApiKey,
            "Build-Channel" to versionType,
            "Class-Path" to "pack200.jar",
            "Add-Opens" to listOf(
                "java.base/java.lang",
                "java.base/java.lang.reflect",
                "javafx.graphics/javafx.css",
                "javafx.base/com.sun.javafx.runtime",
                "javafx.controls/com.sun.javafx.scene.control.behavior",
                "javafx.controls/javafx.scene.control.skin",
                "javafx.controls/com.sun.javafx.scene.control",
                "javafx.base/com.sun.javafx.binding",
                "javafx.base/com.sun.javafx.event",
                "javafx.graphics/com.sun.javafx.stage"
            ).joinToString(" "),
            "Add-Exports" to listOf(
                "java.base/jdk.internal.loader",
                "javafx.controls/com.sun.javafx.scene.control.behavior",
                "javafx.controls/javafx.scene.control.skin",
                "javafx.controls/com.sun.javafx.scene.control",
                "javafx.base/com.sun.javafx.binding",
                "javafx.graphics/com.sun.javafx.stage",
                "javafx.base/com.sun.javafx.event"
            ).joinToString(" ")
        )
    }

    doLast {
        repack(jarPath) // see repack()
        attachSignature(jarPath)
        createChecksum(jarPath)
    }
}

fun createExecutable(suffix: String, header: String) {
    val output = File(jarPath.parentFile, jarPath.nameWithoutExtension + '.' + suffix)

    output.outputStream().use {
        it.write(File(project.projectDir, header).readBytes())
        it.write(jarPath.readBytes())
    }

    createChecksum(output)
}

tasks.processResources {
    fun convertToBSS(resource: String) {
        doFirst {
            val cssFile = File(projectDir, "src/main/resources/$resource")
            val bssFile = File(projectDir, "build/compiled-resources/${resource.substring(0, resource.length - 4)}.bss")
            bssFile.parentFile.mkdirs()
            javaexec {
                classpath = sourceSets["main"].compileClasspath
                mainClass.set("com.sun.javafx.css.parser.Css2Bin")
                args(cssFile, bssFile)
            }
        }
    }

    from("build/compiled-resources")

    convertToBSS("assets/css/root.css")
    convertToBSS("assets/css/blue.css")

    into("META-INF/versions/11") {
        from(sourceSets["java11"].output)
    }
    dependsOn(tasks["java11Classes"])

    into("assets") {
        from(project.buildDir.resolve("openjfx-dependencies.json"))
    }
    dependsOn(rootProject.tasks["generateOpenJFXDependencies"])
}

val packFile = File(jarPath.parentFile, jarPath.nameWithoutExtension + ".pack")

val makePack = tasks.create("makePack") {
    dependsOn(tasks.jar)

    doLast {
        packFile.outputStream().use { out ->
            JarFile(jarPath).use { jarFile -> packer.pack(jarFile, out) }
        }
        createChecksum(packFile)
    }
}

val makePackXz = tasks.create("makePackXz") {
    dependsOn(makePack)

    val packXz = File(packFile.parentFile, packFile.name + ".xz")

    doLast {
        // Our CI server does not have enough memory space to compress file at highest level.
        XZOutputStream(packXz.outputStream(), LZMA2Options(5))
            .use { it.write(packFile.readBytes()) }
        createChecksum(packXz)
    }
}

val makePackGz = tasks.create("makePackGz") {
    dependsOn(makePack)

    val packGz = File(packFile.parentFile, packFile.name + ".gz")

    doLast {
        GZIPOutputStream(packGz.outputStream()).use { it.write(packFile.readBytes()) }
        createChecksum(packGz)
    }
}

val makeExecutables = tasks.create("makeExecutables") {
    dependsOn(makePack)

    doLast {
        createExecutable("exe", "src/main/resources/assets/HMCLauncher.exe")
        createExecutable("sh", "src/main/resources/assets/HMCLauncher.sh")
    }
}

tasks.build {
    dependsOn(makePackXz, makePackGz, makeExecutables)
}

tasks.create<JavaExec>("run") {
    dependsOn(tasks.jar)

    group = "application"

    classpath = files(jarPath)
    workingDir = rootProject.rootDir
}
