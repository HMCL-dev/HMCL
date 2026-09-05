import java.io.RandomAccessFile

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

tasks.compileJava {
    doLast {
        val outputDir = destinationDirectory.get().asFile
        outputDir.walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .forEach { file ->
                RandomAccessFile(file, "rw").use { raf ->
                    if (raf.length() >= 8) {
                        val magic = raf.readInt()
                        if (magic == 0xCAFEBABE.toInt()) {
                            raf.seek(6)
                            raf.writeShort(50)
                        }
                    }
                }
            }
    }
}