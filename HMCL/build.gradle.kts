import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.zip.ZipFile

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("com.google.code.gson:gson:2.10.1")
    }
}

plugins {
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

val isOfficial = System.getenv("HMCL_SIGNATURE_KEY") != null
        || (System.getenv("GITHUB_REPOSITORY_OWNER") == "HMCL-dev" && System.getenv("GITHUB_BASE_REF")
    .isNullOrEmpty())

val buildNumber = System.getenv("BUILD_NUMBER")?.toInt().let { number ->
    val offset = System.getenv("BUILD_NUMBER_OFFSET")?.toInt() ?: 0
    if (number != null) {
        (number - offset).toString()
    } else {
        val shortCommit = System.getenv("GITHUB_SHA")?.lowercase()?.substring(0, 7)
        val prefix = if (isOfficial) "dev" else "unofficial"
        if (!shortCommit.isNullOrEmpty()) "$prefix-$shortCommit" else "SNAPSHOT"
    }
}
val versionRoot = System.getenv("VERSION_ROOT") ?: "3.5"
val versionType = System.getenv("VERSION_TYPE") ?: if (isOfficial) "nightly" else "unofficial"

val microsoftAuthId = System.getenv("MICROSOFT_AUTH_ID") ?: ""
val microsoftAuthSecret = System.getenv("MICROSOFT_AUTH_SECRET") ?: ""
val curseForgeApiKey = System.getenv("CURSEFORGE_API_KEY") ?: ""

version = "$versionRoot.$buildNumber"

dependencies {
    implementation(project(":HMCLCore"))
    implementation("libs:JFoenix")
}

fun digest(algorithm: String, bytes: ByteArray): ByteArray = MessageDigest.getInstance(algorithm).digest(bytes)

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
    val keyLocation = System.getenv("HMCL_SIGNATURE_KEY")
    if (keyLocation == null) {
        logger.warn("Missing signature key")
        return
    }

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

tasks.getByName<JavaCompile>("compileJava") {
    dependsOn(tasks.create("computeDynamicResources") {
        this@create.inputs.file(rootProject.rootDir.toPath().resolve("data-json/dynamic-remote-resources-raw.json"))
        this@create.outputs.file(rootProject.rootDir.toPath().resolve("data-json/dynamic-remote-resources.json"))

        doLast {
            Gson().also { gsonInstance ->
                Files.newBufferedReader(
                    rootProject.rootDir.toPath().resolve("data-json/dynamic-remote-resources-raw.json"),
                    Charsets.UTF_8
                ).use { br ->
                    (gsonInstance.fromJson(br, JsonElement::class.java) as JsonObject)
                }.also { data ->
                    data.asMap().forEach { (namespace, namespaceData) ->
                        (namespaceData as JsonObject).asMap().forEach { (name, nameData) ->
                            (nameData as JsonObject).asMap().forEach { (version, versionData) ->
                                require(versionData is JsonObject)
                                val localPath =
                                    (versionData.get("local_path") as com.google.gson.JsonPrimitive).asString
                                val sha1 = (versionData.get("sha1") as com.google.gson.JsonPrimitive).asString

                                val currentSha1 = digest(
                                    "SHA-1",
                                    Files.readAllBytes(rootProject.rootDir.toPath().resolve(localPath))
                                ).joinToString(separator = "") { "%02x".format(it) }

                                if (!sha1.equals(currentSha1, ignoreCase = true)) {
                                    throw IllegalStateException("Mismatched SHA-1 in $.${namespace}.${name}.${version} of dynamic remote resources detected. Require ${currentSha1}, but found $sha1")
                                }
                            }
                        }
                    }

                    rootProject.rootDir.toPath().resolve("data-json/dynamic-remote-resources.json").also { zippedPath ->
                        gsonInstance.toJson(data).also { expectedData ->
                            if (Files.exists(zippedPath)) {
                                Files.readString(zippedPath, Charsets.UTF_8).also { rawData ->
                                    if (!rawData.equals(expectedData)) {
                                        if (System.getenv("GITHUB_SHA") == null) {
                                            Files.writeString(zippedPath, expectedData, Charsets.UTF_8)
                                        } else {
                                            throw IllegalStateException("Mismatched zipped dynamic-remote-resources json file!")
                                        }
                                    }
                                }
                            } else {
                                Files.writeString(zippedPath, expectedData, Charsets.UTF_8)
                            }
                        }
                    }
                }
            }
        }
    })
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

    exclude("**/package-info.class")
    exclude("META-INF/maven/**")

    minimize {
        exclude(dependency("com.google.code.gson:.*:.*"))
        exclude(dependency("libs:JFoenix:.*"))
    }

    manifest {
        attributes(
            "Created-By" to "Copyright(c) 2013-2023 huangyuhui.",
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
                "java.base/jdk.internal.loader",
                "javafx.base/com.sun.javafx.binding",
                "javafx.base/com.sun.javafx.event",
                "javafx.base/com.sun.javafx.runtime",
                "javafx.graphics/javafx.css",
                "javafx.graphics/com.sun.javafx.stage",
                "javafx.graphics/com.sun.prism",
                "javafx.controls/com.sun.javafx.scene.control",
                "javafx.controls/com.sun.javafx.scene.control.behavior",
                "javafx.controls/javafx.scene.control.skin",
                "jdk.attach/sun.tools.attach"
            ).joinToString(" ")
        )

        System.getenv("GITHUB_SHA")?.also {
            attributes("GitHub-SHA" to it)
        }
    }

    doLast {
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
        from(project.layout.buildDirectory.file("openjfx-dependencies.json"))
    }
    dependsOn(rootProject.tasks["generateOpenJFXDependencies"])
}

val makeExecutables = tasks.create("makeExecutables") {
    dependsOn(tasks.jar)
    doLast {
        createExecutable("exe", "src/main/resources/assets/HMCLauncher.exe")
        createExecutable("sh", "src/main/resources/assets/HMCLauncher.sh")
    }
}

tasks.build {
    dependsOn(makeExecutables)
}

tasks.create<JavaExec>("run") {
    dependsOn(tasks.jar)

    group = "application"

    classpath = files(jarPath)
    workingDir = rootProject.rootDir
}
