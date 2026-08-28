rootProject.name = "HMCL3"
include(
    "HMCL",
    "HMCLCore",
    "HMCLBoot"
)

val minecraftLibraries =
    listOf("HMCLTransformerDiscoveryService", "HMCLMultiMCBootstrap", "HMCLLegacyForgeHelper", "HMCLModLoaderHelper")
include(minecraftLibraries)

for (library in minecraftLibraries) {
    project(":$library").projectDir = file("minecraft/libraries/$library")
}
