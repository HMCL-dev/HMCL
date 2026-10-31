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

import org.jackhuang.hmcl.gradle.ci.CheckUpdate
import org.jackhuang.hmcl.gradle.docs.UpdateDocuments
import org.jackhuang.hmcl.gradle.l10n.UpdateLanguageSubtagRegistry
import org.jackhuang.hmcl.gradle.l10n.UpdateTimeZoneRegions

plugins {
    id("checkstyle")
    id("org.glavo.gradle-wrapper-neo") version "0.2.0"
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

        System.getenv("MAVEN_CENTRAL_REPO").let { repo ->
            if (repo.isNullOrBlank())
                mavenCentral()
            else
                maven(url = repo)
        }

        maven(url = "https://jitpack.io")
        maven(url = "https://libraries.minecraft.net")
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    @Suppress("UnstableApiUsage")
    tasks.withType<Checkstyle> {
        maxHeapSize.set("2g")

        setConfigProperties("licenseHeaderFile" to rootProject.rootDir.resolve("config/checkstyle/license-header.txt"))
    }

    configure<CheckstyleExtension> {
        sourceSets = setOf()
    }

    dependencies {
        "testImplementation"(rootProject.libs.junit.jupiter)
        "testImplementation"(rootProject.libs.assertj.core)
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

org.jackhuang.hmcl.gradle.javafx.JavaFXUtils.register(rootProject)

defaultTasks("clean", "build")

tasks.register<UpdateLanguageSubtagRegistry>("updateLanguageSubtagRegistry") {
    description = "Update the checked-in IANA sublanguage and default-script mappings"

    sublanguagesFile.set(layout.projectDirectory.file("HMCLCore/src/main/resources/assets/lang/sublanguages.csv"))
    defaultScriptFile.set(layout.projectDirectory.file("HMCLCore/src/main/resources/assets/lang/default_script.csv"))
}

tasks.register<UpdateTimeZoneRegions>("updateTimeZoneRegions") {
    description = "Update the checked-in IANA time zone to region mapping"
    tzdbVersion.set("2026e")
    outputFile.set(layout.projectDirectory.file("HMCL/src/main/resources/assets/timezone_regions.csv"))
}

tasks.register<UpdateDocuments>("updateDocuments") {
    documentsDir.set(layout.projectDirectory.dir("docs"))
}

tasks.register<CheckUpdate>("checkUpdateDev") {
    uri.set("https://ci.huangyuhui.net/job/HMCL-nightly")
}

tasks.register<CheckUpdate>("checkUpdateStable") {
    uri.set("https://ci.huangyuhui.net/job/HMCL-stable")
}
