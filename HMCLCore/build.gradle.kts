plugins {
    `java-library`
}

dependencies {
    api(libs.kala.compress.zip)
    api(libs.kala.compress.tar)
    api(libs.simple.png.javafx)
    api(libs.gson)
    api(libs.toml)
    api(libs.xz)
    api(libs.fx.gson)
    api(libs.constant.pool.scanner)
    api(libs.opennbt)
    api(libs.nanohttpd)
    api(libs.jsoup)
    api(libs.chardet)
    api(libs.jna)
    api(libs.pci.ids)
    api(libs.zipfs)

    compileOnlyApi(libs.jetbrains.annotations)

    testImplementation(libs.jna.platform)
}
