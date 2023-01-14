plugins {
    `java-library`
}

dependencies {
    api("org.glavo:simple-png:0.1.1")
    api("com.google.code.gson:gson:2.8.1")
    api("com.moandjiezana.toml:toml4j:0.7.2")
    api("org.tukaani:xz:1.8")
    api("org.hildan.fxgson:fx-gson:3.1.0") {
        exclude(group = "org.jetbrains", module = "annotations")
    }
    api("org.jenkins-ci:constant-pool-scanner:1.2")
    api("com.github.steveice10:opennbt:1.1")
    api("com.nqzero:permit-reflect:0.3")
    api("org.nanohttpd:nanohttpd:2.3.1")
    api("org.apache.commons:commons-compress:1.21")
    api("org.apache.commons:commons-lang3:3.12.0")
    compileOnlyApi("org.jetbrains:annotations:16.0.3")
}
