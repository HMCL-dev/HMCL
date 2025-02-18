plugins {
    `java-library`
}

dependencies {
    val kalaCompressVersion = "1.27.1-1"

    api("org.glavo.kala:kala-compress-archivers-zip:$kalaCompressVersion")
    api("org.glavo.kala:kala-compress-archivers-tar:$kalaCompressVersion")
    api("org.glavo:simple-png-javafx:0.3.0")
    api("com.google.code.gson:gson:2.11.0")
    api("com.moandjiezana.toml:toml4j:0.7.2")
    api("org.tukaani:xz:1.10")
    api("org.hildan.fxgson:fx-gson:5.0.0")
    api("org.jenkins-ci:constant-pool-scanner:1.2")
    api("com.github.steveice10:opennbt:1.5")
    api("org.nanohttpd:nanohttpd:2.3.1")
    api("org.jsoup:jsoup:1.18.1")
    compileOnlyApi("org.jetbrains:annotations:26.0.1")

    if (JavaVersion.current().isJava8) {
        org.gradle.internal.jvm.Jvm.current().toolsJar?.let {
            compileOnly(files(it))
        }
    }
}
