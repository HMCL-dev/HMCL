import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.0.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
    id("org.jetbrains.compose") version "1.6.11"
}

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
            noJdk = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":HMCL"))
        }
    }
}

val mVersion = Unit.let {
    val VERSION_ROOT = System.getenv("VERSION_ROOT") ?: "3.5"
    val BUILD_NUMBER_OFFSET = System.getenv("BUILD_NUMBER_OFFSET")?.toIntOrNull() ?: 0
    val BUILD_NUMBER = System.getenv("BUILD_NUMBER")?.toIntOrNull()?.let BUILD_NUMBER@{
        return@BUILD_NUMBER it - BUILD_NUMBER_OFFSET
    } ?: 0
    return@let "$VERSION_ROOT.$BUILD_NUMBER"
}

compose.desktop {
    application {
        mainClass = "org.jackhuang.hmcl.Main"

        jvmArgs(
            "--add-exports=java.base/jdk.internal.loader=ALL-UNNAMED",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "-XX:MinHeapFreeRatio=5",
            "-XX:MaxHeapFreeRatio=15",
        )

        buildTypes.release {
            proguard {
                isEnabled = false
            }
        }

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi, TargetFormat.Exe,
                TargetFormat.Deb,
            )

            modules(
                "jdk.unsupported",
                "jdk.zipfs",
                "jdk.management",
                "jdk.jsobject",
                "jdk.xml.dom",
                "java.net.http",
            )

            packageName = rootProject.name
            packageVersion = mVersion
            copyright = "Copyright(c) 2013-2024 huangyuhui."

            val iconBasePath = "../HMCL/image"
            macOS {
                bundleID = "org.jackhuang.hmcl"
                appCategory = "public.app-category.games"
                iconFile = file("$iconBasePath/hmcl.icns")
            }
            windows {
                dirChooser = true
                perUserInstall = true
                menuGroup = rootProject.name
                upgradeUuid = "e8917524-75d2-a935-3314-8167bf4a9209"
                iconFile = file("$iconBasePath/hmcl.ico")
            }
            linux {
                iconFile = file("$iconBasePath/hmcl.png")
            }
        }
    }
}
