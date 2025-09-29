import org.jackhuang.hmcl.gradle.ci.CheckUpdate
import org.jackhuang.hmcl.gradle.docs.UpdateDocuments

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

org.jackhuang.hmcl.gradle.javafx.JavaFXUtils.register(rootProject)

defaultTasks("clean", "build")


tasks.register<UpdateDocuments>("updateDocuments") {
    documentsDir.set(layout.projectDirectory.dir("docs"))
}

tasks.register<CheckUpdate>("checkUpdateDev") {
    tagPrefix.set("v")
    api.set("https://ci.huangyuhui.net/job/HMCL/lastSuccessfulBuild/api/json")
}

tasks.register<CheckUpdate>("checkUpdateStable") {
    tagPrefix.set("release-")
    api.set("https://ci.huangyuhui.net/job/HMCL-stable/lastSuccessfulBuild/api/json")
}
