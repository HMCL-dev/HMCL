repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.gson)
    implementation(libs.jna)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.processResources {
    into("org/jackhuang/hmcl/gradle/l10n") {
        from(file("../HMCLCore/src/main/resources/assets/lang/sublanguages.json"))
    }
}
