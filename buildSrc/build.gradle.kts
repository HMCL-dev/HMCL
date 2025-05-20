repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.gson)
    implementation(libs.jna)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}