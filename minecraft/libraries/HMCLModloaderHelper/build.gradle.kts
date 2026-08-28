version = "1.0"

tasks.compileJava {
    sourceCompatibility = "1.6"
    targetCompatibility = "1.6"
}

tasks.jar {
    manifest {
        attributes(
            "Created-By" to "Copyright(c) 2026 huangyuhui.",
            "Implementation-Version" to project.version,
            "Premain-Class" to "org.jackhuang.hmcl.HMCLModloaderHelper",
            "Can-Redefine-Classes" to true,
            "Can-Retransform-Classes" to true
        )
    }
}

tasks.compileJava {
    javaCompiler.set(javaToolchains.compilerFor {
        languageVersion.set(JavaLanguageVersion.of(8))
    })
}