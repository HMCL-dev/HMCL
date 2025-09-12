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