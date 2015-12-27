-libraryjars <java.home>/lib/rt.jar
-libraryjars <java.home>/lib/jce.jar
-libraryjars <java.home>/lib/jsse.jar

-dontoptimize
-dontwarn
-dontshrink

-overloadaggressively
-repackageclasses 'org.jackhuang.hellominecraft.svrmgr'
-allowaccessmodification

-renamesourcefileattribute SourceFile

-keepattributes *Annotation*,SourceFile,LineNumberTable,Signature

-keep class com.** { *; }
-keep class org.jackhuang.metro.** { *; }
-keep class yaml.** { *; }
-keep class org.ho.** { *; }
-keep class org.jsoup.** { *; }

-keep class org.jackhuang.hellominecraft.svrmgr.Main { public static void main(java.lang.String[]); }

-keepclassmembers class org.jackhuang.hellominecraft.svrmgr.settings.Settings { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.svrmgr.settings.BannedPlayers { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.svrmgr.settings.Op { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.svrmgr.settings.PlayerList { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.svrmgr.settings.Schedule { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.svrmgr.settings.WhiteList { public <fields>; }

-keepclassmembers class org.jackhuang.hellominecraft.svrmgr.cbplugins.Category { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.svrmgr.cbplugins.BukkitPlugin { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.svrmgr.cbplugins.PluginInfo { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.svrmgr.cbplugins.PluginInformation { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.svrmgr.cbplugins.PluginVersion { public <fields>; }

-keepclassmembers class org.jackhuang.hellominecraft.svrmgr.installer.cauldron.InstallProfile { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.svrmgr.installer.cauldron.Install { public <fields>; }

-keepclassmembers class org.jackhuang.hellominecraft.version.Latest { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.version.MinecraftVersion { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.version.MinecraftLibrary { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.version.IMinecraftLibrary { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.version.MinecraftVersions { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.version.Natives { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.version.OS { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.version.Rules { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.version.AssetsObject { public <fields>; }
-keepclassmembers class org.jackhuang.hellominecraft.version.AssetsIndex { public <fields>; }
