-target 1.8
-dontoptimize
-dontobfuscate

# JFoenix
-keep class com.jfoenix.** {
    <fields>;
    <methods>;
}

# HMCL
-keep class org.jackhuang.** {
    <fields>;
    <methods>;
}