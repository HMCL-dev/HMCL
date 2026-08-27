version = "1.0"

tasks.compileJava {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

tasks.jar {
    manifest {
        attributes(
            "Created-By" to "Copyright(c) 2013-2025 huangyuhui.",
            "Implementation-Version" to project.version
        )
    }
}
