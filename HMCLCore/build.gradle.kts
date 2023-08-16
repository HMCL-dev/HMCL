plugins {
    `java-library`
}

dependencies {
    api("com.github.burningtnt.SimpleWEBP:jfx:0bdd60c8de7aa8a66a9f3d7d549d841efe26cb13")
    api("org.glavo:simple-png-javafx:0.3.0")
    api("com.google.code.gson:gson:2.10.1")
    api("com.moandjiezana.toml:toml4j:0.7.2")
    api("org.tukaani:xz:1.9")
    api("org.hildan.fxgson:fx-gson:5.0.0")
    api("org.jenkins-ci:constant-pool-scanner:1.2")
    api("com.github.steveice10:opennbt:1.5")
    api("org.nanohttpd:nanohttpd:2.3.1")
    api("org.apache.commons:commons-compress:1.23.0")
    compileOnlyApi("org.jetbrains:annotations:24.0.1")

    testImplementation(project(":HMCL"))
}
