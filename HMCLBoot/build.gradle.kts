plugins {
    id("java-library")
}

tasks.compileJava {
    options.release.set(8)
}

tasks.compileTestJava {
    options.release.set(17)
}
