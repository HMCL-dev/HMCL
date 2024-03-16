plugins {
    `java-library`
}

val simpleWEBPSha1 = "cf5838cab4710bab9f50be07fa8eabe43cba5975"

dependencies {
    api("com.github.burningtnt.SimpleWEBP:jfx:$simpleWEBPSha1")
    api("com.github.burningtnt.SimpleWEBP:awt:$simpleWEBPSha1")
    api("org.glavo:simple-png-javafx:0.3.0")
    api("com.google.code.gson:gson:2.10.1")
    api("com.moandjiezana.toml:toml4j:0.7.2")
    api("org.tukaani:xz:1.9")
    api("org.hildan.fxgson:fx-gson:5.0.0")
    api("org.jenkins-ci:constant-pool-scanner:1.2")
    api("com.github.steveice10:opennbt:1.5")
    api("org.nanohttpd:nanohttpd:2.3.1")
    api("org.apache.commons:commons-compress:1.25.0")
    compileOnlyApi("org.jetbrains:annotations:24.1.0")

    testImplementation(project(":HMCL"))
}
