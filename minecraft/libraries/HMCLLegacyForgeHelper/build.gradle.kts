version = "1.0"

tasks.compileJava {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

tasks.jar {
    manifest {
        attributes(
            "Created-By" to "Copyright(c) 2026 huangyuhui.",
            "Implementation-Version" to project.version,
            "Premain-Class" to "org.jackhuang.hmcl.HMCLLegacyForgeHelper",
            "Can-Redefine-Classes" to true,
            "Can-Retransform-Classes" to true
        )
    }
}
