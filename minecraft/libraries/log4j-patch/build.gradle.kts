import java.io.RandomAccessFile

version = "1.0"

sourceSets.create("agent") {
    java {
        srcDir("src/main/agent")
    }
}

dependencies {
    compileOnly("org.apache.logging.log4j:log4j-core:2.0-beta9")
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"

    doLast {
        val tree = fileTree(destinationDirectory)
        tree.include("**/*.class")
        tree.exclude("module-info.class")
        tree.forEach {
            RandomAccessFile(it, "rw").use { rf ->
                rf.seek(7)   // major version
                rf.write(50)   // java 6
                rf.close()
            }
        }
    }
}

val agentJar = tasks.create<Jar>("agentJar") {
    dependsOn(tasks.compileJava)

    archiveBaseName.set("log4j-patch-agent")

    manifest {
        attributes("Premain-Class" to "org.glavo.log4j.patch.agent.Log4jAgent")
    }

    from(sourceSets["agent"].output)
    from(sourceSets["main"].output) {
        includeEmptyDirs = false
        eachFile { path = "org/glavo/log4j/patch/agent/$name.bin" }
    }

}

tasks.jar {
    enabled = false
    dependsOn(agentJar)
}
