rootProject.name = "HMCL3"
include(
    "HMCL",
    "HMCLCore",
    "HMCLTransformerDiscoveryService",
    "log4j-patch"
)

val minecraftLibraries = listOf("log4j-patch", "HMCLTransformerDiscoveryService")

for (library in minecraftLibraries) {
    project(":$library").projectDir = file("minecraft/libraries/$library")
}
