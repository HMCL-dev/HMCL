import org.jackhuang.hmcl.gradle.ci.CheckUpdate
import org.jackhuang.hmcl.gradle.docs.UpdateDocuments
import org.jackhuang.hmcl.gradle.l10n.ParseLanguageSubtagRegistry

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

tasks.register<ParseLanguageSubtagRegistry>("parseLanguageSubtagRegistry") {
    languageSubtagRegistryFile.set(layout.projectDirectory.file("language-subtag-registry"))

    sublanguagesFile.set(layout.projectDirectory.file("HMCLCore/src/main/resources/assets/lang/sublanguages.csv"))
    defaultScriptFile.set(layout.projectDirectory.file("HMCLCore/src/main/resources/assets/lang/default_script.csv"))
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
