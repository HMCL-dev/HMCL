plugins {
    `java-library`
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

dependencies {
    api(project(":HMCLCore"))

    compileOnlyApi(libs.jetbrains.annotations)
}
