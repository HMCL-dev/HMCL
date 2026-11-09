plugins {
    `java-library`
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

tasks.compileJava {
    options.compilerArgs.add("--add-exports=jdk.attach/sun.tools.attach=ALL-UNNAMED")
}

dependencies {
    api(libs.kala.compress.zip)
    api(libs.kala.compress.tar)
    api(libs.kala.encoding.detctor)
    api(libs.simple.png.javafx)
    api(libs.gson)
    api(libs.tomlj)
    api(libs.xz)
    api(libs.lz4)
    api(libs.fx.gson)
    api(libs.constant.pool.scanner)
    api(libs.nanohttpd)
    api(libs.jsoup)
    api(libs.jna)
    api(libs.pci.ids)
    api(libs.hello.nbt)
    api(libs.weburl)
    api(libs.uuid.tools)
    api(libs.commonmark)
    api(libs.commonmark.autolink)
    api(libs.commonmark.underline)
    api(libs.commonmark.strikethrough)
    api(libs.commonmark.table)

    compileOnlyApi(libs.jetbrains.annotations)

    testImplementation(libs.jna.platform)
    testImplementation(libs.jimfs)
}

tasks.processResources {
    mustRunAfter("updateClientSha1")

    listOf(
        "HMCLTransformerDiscoveryService",
        "HMCLMultiMCBootstrap"
    ).map { project(":$it").tasks["jar"] as Jar }.forEach { task ->
        dependsOn(task)

        into("assets/game") {
            from(task.outputs.files)
        }
    }
}

tasks.register("updateClientSha1", org.jackhuang.hmcl.gradle.component.UpdateClientSha1::class) {
    description = "Update the embedded Minecraft client SHA-1 table"

    unlistedVersionsFile.set(layout.projectDirectory.file("src/main/resources/assets/game/unlisted-versions.json"))
    outputFile.set(layout.projectDirectory.file("src/main/resources/assets/game/client-sha1.csv"))
}

tasks.register("updateLiteLoaderVersionList", org.jackhuang.hmcl.gradle.component.UpdateLiteLoaderVersionList::class) {
    description = "Update LiteLoader version list"

    outputFile.set(projectDir.resolve("src/main/resources/assets/liteloader/versions.json"))
}

tasks.register("updateAmdGpuIDs") {
    description = "Update AMD GPU IDs"

    val commit = "3b5e41a44cd1842297e17447c3984c85c2a6d837"

    val sourceUrl = uri("https://gitlab.freedesktop.org/mesa/libdrm/-/raw/$commit/data/amdgpu.ids")
    val outputFile = projectDir.resolve("src/main/resources/assets/platform/amdgpu.ids")
    outputs.file(outputFile)

    doLast {
        outputFile.parentFile.mkdirs()

        val rawContent = sourceUrl.toURL().readText()

        outputFile.bufferedWriter().use { writer ->
            writer.write("# https://gitlab.freedesktop.org/mesa/libdrm/-/blob/$commit/data/amdgpu.ids\n")
            writer.write("#\n")

            val linePattern = """(?<deviceId>[0-9A-F]{4}),\s+(?<revisionId>[0-9A-Z]{2}),\s+(?<productName>.+)""".toPattern()

            val lines = rawContent.lines()
            lines.forEachIndexed { index, line ->
                val normalized = line
                    // Replace non-breaking space with normal space
                    .replace('\u00A0', ' ')
                    // Replace narrow non-breaking space with normal space
                    .replace('\u202f', ' ')
                    .trim()
                    .let {
                        val matcher = linePattern.matcher(it)
                        if (matcher.matches()) {
                            val deviceId = matcher.group("deviceId")
                            val revisionId = matcher.group("revisionId")
                            val productName = matcher.group("productName")

                            "$deviceId,\t$revisionId,\t$productName"
                        } else {
                            it
                        }
                    }

                if (normalized.isNotEmpty() || index != lines.lastIndex) {
                    writer.write(normalized)
                    writer.write("\n")
                }
            }
        }
    }
}
