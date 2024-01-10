import kotlin.streams.toList

plugins {
    `java-library`
}

dependencies {
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
    compileOnlyApi("com.github.burningtnt:BytecodeImplGenerator:b45b6638eeaeb903aa22ea947d37c45e5716a18c")
}

tasks.getByName<JavaCompile>("compileJava") {
    val bytecodeClasses = listOf(
        "org/jackhuang/hmcl/util/platform/ManagedProcess"
    )

    doLast {
        javaexec {
            classpath(project.sourceSets["main"].compileClasspath)
            mainClass.set("net.burningtnt.bcigenerator.BytecodeImplGenerator")
            System.getProperty("bci.debug.address")?.let { address -> jvmArgs("-agentlib:jdwp=transport=dt_socket,server=n,address=$address,suspend=y") }
            args(bytecodeClasses.stream().map { s -> project.layout.buildDirectory.file("classes/java/main/$s.class").get().asFile.path }.toList())
        }
    }
}
