repositories {
    System.getenv("MAVEN_CENTRAL_REPO").let { repo ->
        if (repo.isNullOrBlank())
            mavenCentral()
        else
            maven(url = repo)
    }
}

dependencies {
    implementation(libs.gson)
    implementation(libs.jna)
    implementation(libs.kala.compress.tar)
    implementation(libs.kala.compress.ar)
    implementation(libs.weburl)
    implementation(libs.eclipse.packager.rpm)
    compileOnly(libs.jetbrains.annotations)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    into("org/jackhuang/hmcl/gradle/l10n") {
        from(projectDir.resolve("../HMCLCore/src/main/resources/assets/lang/"))
    }
}
