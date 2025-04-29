repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.gson)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}