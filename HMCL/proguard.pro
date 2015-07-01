-libraryjars <java.home>/lib/rt.jar
-libraryjars <java.home>/lib/jce.jar
-libraryjars <java.home>/lib/jsse.jar

-dontoptimize
-dontshrink
-dontwarn java.lang.invoke.*

-overloadaggressively
-repackageclasses 'org.jackhuang.hellominecraft.launcher'
-allowaccessmodification

-renamesourcefileattribute SourceFile

-keepattributes *Annotation*,SourceFile,LineNumberTable,Signature

-keep class com.** { *; }
-keep class org.jackhuang.hellominecraft.lookandfeel.* { *; }
-keep class org.jackhuang.hellominecraft.lookandfeel.painters.* { *; }
-keep class org.jackhuang.hellominecraft.lookandfeel.ui.* { *; }

-keepclassmembers class org.jackhuang.mojang.authlib.Agent { *; }
-keepclassmembers class org.jackhuang.mojang.authlib.UserType { *; }

-keepclassmembers class org.jackhuang.mojang.authlib.properties.Property { *; }

-keepclassmembers class org.jackhuang.mojang.authlib.minecraft.MinecraftProfileTexture$Type { *; }
-keepclassmembers class org.jackhuang.mojang.authlib.minecraft.MinecraftProfileTexture { *; }

-keepclassmembers class org.jackhuang.mojang.authlib.yggdrasil.request.AuthenticationRequest { *; }
-keepclassmembers class org.jackhuang.mojang.authlib.yggdrasil.request.InvalidateRequest { *; }
-keepclassmembers class org.jackhuang.mojang.authlib.yggdrasil.request.JoinMinecraftServerRequest { *; }
-keepclassmembers class org.jackhuang.mojang.authlib.yggdrasil.request.RefreshRequest { *; }

-keepclassmembers class org.jackhuang.mojang.authlib.yggdrasil.response.AuthenticationResponse { *; }
-keepclassmembers class org.jackhuang.mojang.authlib.yggdrasil.response.HasJoinedMinecraftServerResponse { *; }
-keepclassmembers class org.jackhuang.mojang.authlib.yggdrasil.response.MinecraftProfilePropertiesResponse { *; }
-keepclassmembers class org.jackhuang.mojang.authlib.yggdrasil.response.MinecraftTexturesPayload { *; }
-keepclassmembers class org.jackhuang.mojang.authlib.yggdrasil.response.ProfileSearchResultsResponse { *; }
-keepclassmembers class org.jackhuang.mojang.authlib.yggdrasil.response.RefreshResponse { *; }
-keepclassmembers class org.jackhuang.mojang.authlib.yggdrasil.response.Response { *; }
-keepclassmembers class org.jackhuang.mojang.authlib.yggdrasil.response.User { *; }

-keep class org.jackhuang.hellominecraft.launcher.Main { public static void main(java.lang.String[]); }
-keep class org.jackhuang.hellominecraft.launcher.Launcher { public static void main(java.lang.String[]); }

-keepclassmembers class org.jackhuang.hellominecraft.launcher.settings.Profile { private <fields>; public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.launcher.settings.Config { private <fields>; }

-keepclassmembers class org.jackhuang.hellominecraft.launcher.version.MinecraftLibrary { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.launcher.version.IMinecraftLibrary { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.launcher.version.Natives { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.launcher.version.OS { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.launcher.version.Rules { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.launcher.version.MinecraftVersion { public <fields>; }

-keepclassmembers class org.jackhuang.hellominecraft.launcher.utils.assets.AssetsObject { <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.launcher.utils.assets.AssetsIndex { <fields>; }

-keepclassmembers class org.jackhuang.hellominecraft.launcher.utils.installers.forge.InstallProfile { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.launcher.utils.installers.forge.Install { public <fields>; }

-keepclassmembers class org.jackhuang.hellominecraft.launcher.utils.installers.forge.vanilla.MinecraftForgeVersionRoot { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.launcher.utils.installers.forge.vanilla.MinecraftForgeVersion { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.launcher.utils.installers.forge.bmcl.ForgeVersion { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.launcher.utils.installers.forge.bmcl.Downloads { public <fields>; }

-keepclassmembers class org.jackhuang.hellominecraft.launcher.utils.installers.optifine.OptiFineVersion { public <fields>; }

-keepclassmembers class org.jackhuang.hellominecraft.launcher.utils.installers.liteloader.LiteLoaderVersionsRoot { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.launcher.utils.installers.liteloader.LiteLoaderMCVersions { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.launcher.utils.installers.liteloader.LiteLoaderVersion { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.launcher.utils.installers.liteloader.LiteLoaderVersionsMeta { public <fields>; }

-keepclassmembers class org.jackhuang.hellominecraft.utils.system.JdkVersion { private <fields>; }

-keepclassmembers class org.jackhuang.hellominecraft.version.MinecraftRemoteLatestVersion { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.version.MinecraftRemoteVersion { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.version.MinecraftRemoteVersions { public <fields>; }
