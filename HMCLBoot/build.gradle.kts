plugins {
    id("java-library")
}

tasks.withType<JavaCompile> {
    options.release.set(8)
}
