version = "1.0"

dependencies {
    compileOnly(project.files("lib/modlauncher-4.1.0.jar"))
}

tasks.compileJava {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

tasks.jar {
    manifest {
        attributes(
            "Created-By" to "Copyright(c) 2013-2020 huangyuhui.",
            "Implementation-Version" to project.version
        )
    }
}
