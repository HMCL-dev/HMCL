rootProject.name = "HMCL3"
include(
    "HMCL",
    "HMCLCore",
    "HMCLBoot",
    "HMCLTransformerDiscoveryService"
)

val minecraftLibraries = listOf("HMCLTransformerDiscoveryService")

for (library in minecraftLibraries) {
    project(":$library").projectDir = file("minecraft/libraries/$library")
}
