plugins {
    `java-library`
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

tasks.compileJava {
    options.compilerArgs.add("--add-exports=jdk.attach/sun.tools.attach=ALL-UNNAMED")
}

dependencies {
    api(libs.kala.compress.zip)
    api(libs.kala.compress.tar)
    api(libs.simple.png.javafx)
    api(libs.gson)
    api(libs.toml)
    api(libs.xz)
    api(libs.fx.gson)
    api(libs.constant.pool.scanner)
    api(libs.opennbt)
    api(libs.nanohttpd)
    api(libs.jsoup)
    api(libs.chardet)
    api(libs.jna)
    api(libs.pci.ids)

    compileOnlyApi(libs.jetbrains.annotations)

    testImplementation(libs.jna.platform)
    testImplementation(libs.jimfs)
}

tasks.processResources {
    listOf(
        "HMCLTransformerDiscoveryService",
        "HMCLMultiMCBootstrap"
    ).map { project(":$it").tasks["jar"] as Jar }.forEach { task ->
        dependsOn(task)

        into("assets/game") {
            from(task.outputs.files)
        }
    }
}
