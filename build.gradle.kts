import org.jackhuang.hmcl.gradle.CheckTranslations

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
        maven(url = "https://libraries.minecraft.net")
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    configure<CheckstyleExtension> {
        sourceSets = setOf()
    }

    dependencies {
        "testImplementation"(rootProject.libs.junit.jupiter)
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging.showStandardStreams = true
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

    tasks.register("checkstyle") {
        dependsOn(tasks["checkstyleMain"], tasks["checkstyleTest"])
    }
}

tasks.register<CheckTranslations>("checkTranslations") {
    val hmclLangDir = layout.projectDirectory.dir("HMCL/src/main/resources/assets/lang")

    englishFile.set(hmclLangDir.file("I18N.properties"))
    simplifiedChineseFile.set(hmclLangDir.file("I18N_zh_CN.properties"))
    traditionalChineseFile.set(hmclLangDir.file("I18N_zh.properties"))
    classicalChineseFile.set(hmclLangDir.file("I18N_lzh.properties"))
}

org.jackhuang.hmcl.gradle.javafx.JavaFXUtils.register(rootProject)

defaultTasks("clean", "build")
