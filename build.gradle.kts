plugins {
    id("checkstyle")
}

group = "org.jackhuang"
version = "3.0"

subprojects {
    apply {
        plugin("java")
        plugin("idea")
        plugin("maven-publish")
        plugin("checkstyle")
    }

    repositories {
        flatDir {
            name = "libs"
            dirs = setOf(rootProject.file("lib"))
        }
        mavenCentral()
        maven(url = "https://jitpack.io")
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"

        options.encoding = "UTF-8"
    }

    configure<CheckstyleExtension> {
        sourceSets = setOf()
    }

    tasks.withType<Checkstyle> {
        exclude("de/javawi/jstun")
    }

    dependencies {
        "testImplementation"("junit:junit:4.12")
    }

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
            }
        }
        repositories {
            mavenLocal()
        }
    }
}

tasks.create("checkTranslations") {
    doLast {
        val hmclLangDir = file("HMCL/src/main/resources/assets/lang")

        val en = java.util.Properties().apply {
            hmclLangDir.resolve("I18N.properties").bufferedReader().use { load(it) }
        }

        val zh = java.util.Properties().apply {
            hmclLangDir.resolve("I18N_zh.properties").bufferedReader().use { load(it) }
        }

        val zh_CN = java.util.Properties().apply {
            hmclLangDir.resolve("I18N_zh_CN.properties").bufferedReader().use { load(it) }
        }

        var success = true

        zh_CN.forEach {
            if (!en.containsKey(it.key)) {
                project.logger.warn("I18N.properties missing key '${it.key}'")
                success = false
            }
        }

        zh_CN.forEach {
            if (!zh.containsKey(it.key)) {
                project.logger.warn("I18N_zh.properties missing key '${it.key}'")
                success = false
            }
        }

        if (!success) {
            throw GradleException("Part of the translation is missing")
        }
    }
}

apply {
    from("javafx.gradle.kts")
}

defaultTasks("clean", "build")
