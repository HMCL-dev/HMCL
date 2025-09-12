/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.game;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CrashReportAnalyzer {

    private CrashReportAnalyzer() {
    }

    public enum Rule {
        OPENJ9("(Open J9 is not supported|OpenJ9 is incompatible|\\.J9VMInternals\\.)"),
        NEED_JDK11("(no such method: sun\\.misc\\.Unsafe\\.defineAnonymousClass\\(Class,byte\\[\\],Object\\[\\]\\)Class/invokeVirtual|java\\.lang\\.UnsupportedClassVersionError: icyllis/modernui/forge/MixinConnector has been compiled by a more recent version of the Java Runtime \\(class file version 55\\.0\\), this version of the Java Runtime only recognizes class file versions up to 52\\.0|java\\.lang\\.IllegalArgumentException: The requested compatibility level JAVA_11 could not be set\\. Level is not supported by the active JRE or ASM version)"),
        TOO_OLD_JAVA("java\\.lang\\.UnsupportedClassVersionError: (.*?) version (?<expected>\\d+)\\.0", "expected"),
        JVM_32BIT("(Could not reserve enough space for (.*?)KB object heap|The specified size exceeds the maximum representable size|Invalid maximum heap size)"),

        // Some mods/shader packs do incorrect GL operations.
        GL_OPERATION_FAILURE("(1282: Invalid operation|Maybe try a lower resolution resourcepack\\?)"),

        // Maybe software rendering? Suggest user for using a graphics card.
        OPENGL_NOT_SUPPORTED("The driver does not appear to support OpenGL"),
        GRAPHICS_DRIVER("(Pixel format not accelerated|GLX: Failed to create context: GLXBadFBConfig|Couldn't set pixel format|net\\.minecraftforge\\.fml.client\\.SplashProgress|org\\.lwjgl\\.LWJGLException|EXCEPTION_ACCESS_VIOLATION(.|\\n|\\r)+# C {2}\\[(ig|atio|nvoglv))"),
        // macOS initializing OpenGL window issues
        MACOS_FAILED_TO_FIND_SERVICE_PORT_FOR_DISPLAY("java\\.lang\\.IllegalStateException: GLFW error before init: \\[0x10008\\]Cocoa: Failed to find service port for display"),
        // Out of memory
        OUT_OF_MEMORY("(java\\.lang\\.OutOfMemoryError|The system is out of physical RAM or swap space|Out of Memory Error|Error occurred during initialization of VM\\RToo small maximum heap)"),
        // Memory exceeded
        MEMORY_EXCEEDED("There is insufficient memory for the Java Runtime Environment to continue"),
        // Too high resolution
        RESOLUTION_TOO_HIGH("Maybe try a (lower resolution|lowerresolution) (resourcepack|texturepack)\\?"),
        // game can only run on Java 8. Version of uesr's JVM is too high.
        JDK_9("java\\.lang\\.ClassCastException: (java\\.base/jdk|class jdk)"),
        // Forge and OptiFine with crash because the JVM compiled with a new version of Xcode
        // https://github.com/sp614x/optifine/issues/4824
        // https://github.com/MinecraftForge/MinecraftForge/issues/7546
        MAC_JDK_8U261("Terminating app due to uncaught exception 'NSInternalInconsistencyException', reason: 'NSWindow drag regions should only be invalidated on the Main Thread!'"),
        // user modifies minecraft primary jar without changing hash file
        FILE_CHANGED("java\\.lang\\.SecurityException: SHA1 digest error for (?<file>.*)|signer information does not match signer information of other classes in the same package", "file"),
        // mod loader/coremod injection fault, prompt user to reinstall game.
        NO_SUCH_METHOD_ERROR("java\\.lang\\.NoSuchMethodError: (?<class>.*?)", "class"),
        // mod loader/coremod injection fault, prompt user to reinstall game.
        NO_CLASS_DEF_FOUND_ERROR("java\\.lang\\.NoClassDefFoundError: (?<class>.*)", "class"),
        // coremod wants to access class without "setAccessible"
        ILLEGAL_ACCESS_ERROR("java\\.lang\\.IllegalAccessError: tried to access class (.*?) from class (?<class>.*?)", "class"),
        // Some mods duplicated
        DUPLICATED_MOD("Found a duplicate mod (?<name>.*) at (?<path>.*)", "name", "path"),
        // Fabric mod resolution
        MOD_RESOLUTION("ModResolutionException: (?<reason>(.*)[\\n\\r]*( - (.*)[\\n\\r]*)+)", "reason"),
        FORGEMOD_RESOLUTION("Missing or unsupported mandatory dependencies:(?<reason>(.*)[\\n\\r]*(\t(.*)[\\n\\r]*)+)", "reason"),
        FORGE_FOUND_DUPLICATE_MODS("Found duplicate mods:(?<reason>(.*)\\R*(\t(.*)\\R*)+)", "reason"),
        MOD_RESOLUTION_CONFLICT("ModResolutionException: Found conflicting mods: (?<sourcemod>.*) conflicts with (?<destmod>.*)", "sourcemod", "destmod"),
        MOD_RESOLUTION_MISSING("ModResolutionException: Could not find required mod: (?<sourcemod>.*) requires (?<destmod>.*)", "sourcemod", "destmod"),
        MOD_RESOLUTION_MISSING_MINECRAFT("ModResolutionException: Could not find required mod: (?<mod>.*) requires \\{minecraft @ (?<version>.*)}", "mod", "version"),
        MOD_RESOLUTION_COLLECTION("ModResolutionException: Could not resolve valid mod collection \\(at: (?<sourcemod>.*) requires (?<destmod>.*)\\)", "sourcemod", "destmod"),
        // Some mods require a file not existing, asking user to manually delete it
        FILE_ALREADY_EXISTS("java\\.nio\\.file\\.FileAlreadyExistsException: (?<file>.*)", "file"),
        // Forge found some mod crashed in game loading
        LOADING_CRASHED_FORGE("LoaderExceptionModCrash: Caught exception from (?<name>.*?) \\((?<id>.*)\\)", "name", "id"),
        BOOTSTRAP_FAILED("Failed to create mod instance\\. ModID: (?<id>.*?),", "id"),
        // Fabric found some mod crashed in game loading
        LOADING_CRASHED_FABRIC("Could not execute entrypoint stage '(.*?)' due to errors, provided by '(?<id>.*)'!", "id"),
        // Fabric may have breaking changes.
        // https://github.com/FabricMC/fabric-loader/tree/master/src/main/legacyJava deprecated classes may be removed in the future.
        FABRIC_VERSION_0_12("java\\.lang\\.NoClassDefFoundError: org/spongepowered/asm/mixin/transformer/FabricMixinTransformerProxy"),
        // Minecraft 1.16+Forge with crash because JDK-8273826
        // https://github.com/McModLauncher/modlauncher/issues/91
        MODLAUNCHER_8("java\\.lang\\.NoSuchMethodError: ('void sun\\.security\\.util\\.ManifestEntryVerifier\\.<init>\\(java\\.util\\.jar\\.Manifest\\)'|sun\\.security\\.util\\.ManifestEntryVerifier\\.<init>\\(Ljava/util/jar/Manifest;\\)V)"),
        // Manually triggered debug crash
        DEBUG_CRASH("Manually triggered debug crash"),
        CONFIG("Failed loading config file (?<file>.*?) of type (.*?) for modid (?<id>.*)", "id", "file"),
        // Fabric gives some warnings
        FABRIC_WARNINGS("(Warnings were found!|Incompatible mod set!|Incompatible mods found!)(.*?)[\\n\\r]+(?<reason>[^\\[]+)\\[", "reason"),
        // Game crashed when ticking entity
        ENTITY("Entity Type: (?<type>.*)[\\w\\W\\n\\r]*?Entity's Exact location: (?<location>.*)", "type", "location"),
        // Game crashed when tessellating block model
        BLOCK("Block: (?<type>.*)[\\w\\W\\n\\r]*?Block location: (?<location>.*)", "type", "location"),
        // Cannot find native libraries
        UNSATISFIED_LINK_ERROR("java\\.lang\\.UnsatisfiedLinkError: Failed to locate library: (?<name>.*)", "name"),

        //https://github.com/HMCL-dev/HMCL/pull/1813
        OPTIFINE_IS_NOT_COMPATIBLE_WITH_FORGE("(java\\.lang\\.NoSuchMethodError: 'java\\.lang\\.Class sun\\.misc\\.Unsafe\\.defineAnonymousClass\\(java\\.lang\\.Class, byte\\[\\], java\\.lang\\.Object\\[\\]\\)'|java\\.lang\\.NoSuchMethodError: 'void net\\.minecraft\\.client\\.renderer\\.texture\\.SpriteContents\\.\\<init\\>\\(net\\.minecraft\\.resources\\.ResourceLocation, |java\\.lang\\.NoSuchMethodError: 'void net\\.minecraftforge\\.client\\.gui\\.overlay\\.ForgeGui\\.renderSelectedItemName\\(net\\.minecraft\\.client\\.gui\\.GuiGraphics, int\\)'|java\\.lang\\.NoSuchMethodError: 'java\\.lang\\.String com\\.mojang\\.blaze3d\\.systems\\.RenderSystem\\.getBackendDescription\\(\\)'|java\\.lang\\.NoSuchMethodError: 'net\\.minecraft\\.network\\.chat\\.FormattedText net\\.minecraft\\.client\\.gui\\.Font\\.ellipsize\\(net\\.minecraft\\.network\\.chat\\.FormattedText, int\\)'|java\\.lang\\.NoSuchMethodError: 'void net\\.minecraft\\.server\\.level\\.DistanceManager\\.(.*?)\\(net\\.minecraft\\.server\\.level\\.TicketType, net\\.minecraft\\.world\\.level\\.ChunkPos, int, java\\.lang\\.Object, boolean\\)'|java\\.lang\\.NoSuchMethodError: 'void net\\.minecraft\\.client\\.renderer\\.block\\.model\\.BakedQuad\\.\\<init\\>\\(int\\[\\], int, net\\.minecraft\\.core\\.Direction, net\\.minecraft\\.client\\.renderer\\.texture\\.TextureAtlasSprite, boolean, boolean\\)'|TRANSFORMER/net\\.optifine/net\\.optifine\\.reflect\\.Reflector\\.\\<clinit\\>\\(Reflector\\.java)"),
        MOD_FILES_ARE_DECOMPRESSED("(The directories below appear to be extracted jar files\\. Fix this before you continue|Extracted mod jars found, loading will NOT continue)"),//Mod文件被解压
        OPTIFINE_CAUSES_THE_WORLD_TO_FAIL_TO_LOAD("java\\.lang\\.NoSuchMethodError: net\\.minecraft\\.world\\.server\\.ChunkManager$ProxyTicketManager\\.shouldForceTicks\\(J\\)Z"),//OptiFine导致无法加载世界 https://www.minecraftforum.net/forums/support/java-edition-support/3051132-exception-ticking-world
        TOO_MANY_MODS_LEAD_TO_EXCEEDING_THE_ID_LIMIT("maximum id range exceeded"),//Mod过多导致超出ID限制

        // Mod issues
        //https://github.com/HMCL-dev/HMCL/pull/2038
        MODMIXIN_FAILURE("(MixinApplyError|Mixin prepare failed |Mixin apply failed |mixin\\.injection\\.throwables\\.|\\.mixins\\.json\\] FAILED during \\))"),//ModMixin失败
        MIXIN_APPLY_MOD_FAILED("Mixin apply for mod (?<id>.*) failed", "id"),//Mixin应用失败
        FORGE_ERROR("An exception was thrown, the game will display an error screen and halt\\.\\R*(?<reason>.*\\R*(\\s*at .*\\R)+)", "reason"),//Forge报错,Forge可能已经提供了错误信息
        MOD_RESOLUTION0("(\tMod File:|-- MOD |\tFailure message:)"),
        FORGE_REPEAT_INSTALLATION("MultipleArgumentsForOptionException: Found multiple arguments for option (.*?), but you asked for only one"),//https://github.com/HMCL-dev/HMCL/issues/1880
        OPTIFINE_REPEAT_INSTALLATION("ResolutionException: Module optifine reads another module named optifine"),//Optifine 重复安装（及Mod文件夹有，自动安装也有）
        JAVA_VERSION_IS_TOO_HIGH("(Unable to make protected final java\\.lang\\.Class java\\.lang\\.ClassLoader\\.defineClass|java\\.lang\\.NoSuchFieldException: ucp|Unsupported class file major version|because module java\\.base does not export|java\\.lang\\.ClassNotFoundException: jdk\\.nashorn\\.api\\.scripting\\.NashornScriptEngineFactory|java\\.lang\\.ClassNotFoundException: java\\.lang\\.invoke\\.LambdaMetafactory|Exception in thread \"main\" java\\.lang\\.NullPointerException: Cannot read the array length because \"urls\" is null)"),//Java版本过高
        INSTALL_MIXINBOOTSTRAP("java\\.lang\\.ClassNotFoundException: org\\.spongepowered\\.asm\\.launch\\.MixinTweaker"),

        //Forge 默认会把每一个 mod jar 都当做一个 JPMS 的模块（Module）加载。在这个 jar 没有给出 module-info 声明的情况下，JPMS 会采用这样的顺序决定 module 名字：
        //1. META-INF/MANIFEST.MF 里的 Automatic-Module-Name
        //2. 根据文件名生成。文件名里的 .jar 后缀名先去掉，然后检查是否有 -(\\d+(\\.|$)) 的部分，有的话只取 - 前面的部分，- 后面的部分成为 module 的版本号（即尝试判断文件名里是否有版本号，有的话去掉），然后把不是拉丁字母和数字的字符（正则表达式 [^A-Za-z0-9]）都换成点，然后把连续的多个点换成一个点，最后去掉开头和结尾的点。那么
        //按照 2.，如果你的文件名是拔刀剑.jar，那么这么一通流程下来，你得到的 module 名就是空字符串，而这是不允许的。(来自 @Föhn 说明)
        MOD_NAME("Invalid module name: '' is not a Java identifier"),

        //Forge 安装不完整
        INCOMPLETE_FORGE_INSTALLATION("(java\\.io\\.UncheckedIOException: java\\.io\\.IOException: Invalid paths argument, contained no existing paths: \\[(.*?)(forge-(.*?)-client\\.jar|fmlcore-(.*?)\\.jar)\\]|Failed to find Minecraft resource version (.*?) at (.*?)forge-(.*?)-client\\.jar|Cannot find launch target fmlclient, unable to launch|java\\.lang\\.IllegalStateException: Could not find net/minecraft/client/Minecraft\\.class in classloader SecureModuleClassLoader)"),

        NIGHT_CONFIG_FIXES("com\\.electronwill\\.nightconfig\\.core\\.io\\.ParsingException: Not enough data available"),//https://github.com/Fuzss/nightconfigfixes
        //Shaders Mod detected. Please remove it, OptiFine has built-in support for shaders.
        SHADERS_MOD("java\\.lang\\.RuntimeException: Shaders Mod detected\\. Please remove it, OptiFine has built-in support for shaders\\."),

        // 一些模组与 Optifine 不兼容
        MOD_FOREST_OPTIFINE("Error occurred applying transform of coremod META-INF/asm/multipart\\.js function render"),
        // PERFORMANT is not compatible with OptiFine
        PERFORMANT_FOREST_OPTIFINE("org\\.spongepowered\\.asm\\.mixin\\.injection\\.throwables\\.InjectionError: Critical injection failure: Redirector OnisOnLadder\\(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/LivingEntity;\\)Z in performant\\.mixins\\.json:entity\\.LivingEntityMixin failed injection check, \\(0/1\\) succeeded\\. Scanned 1 target\\(s\\)\\. Using refmap performant\\.refmap\\.json"),
        // TwilightForest is not compatible with OptiFine on Minecraft 1.16
        TWILIGHT_FOREST_OPTIFINE("java\\.lang\\.IllegalArgumentException: (.*) outside of image bounds (.*)"),
        // Jade is not compatible with OptiFine on Minecraft 1.20+
        JADE_FOREST_OPTIFINE("Critical injection failure: LVT in net/minecraft/client/renderer/GameRenderer::m_109093_\\(FJZ\\)V has incompatible changes at opcode 760 in callback jade\\.mixins\\.json:GameRendererMixin-\\>@Inject::jade\\$runTick\\(FJZLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;IILcom/mojang/blaze3d/platform/Window;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/gui/GuiGraphics;\\)V\\."),
        // NeoForge 与 OptiFine 不兼容
        NEOFORGE_FOREST_OPTIFINE("cpw\\.mods\\.modlauncher\\.InvalidLauncherSetupException: Invalid Services found OptiFine"),

        // 一些模组与 Sodium 不兼容
        // https://github.com/CaffeineMC/sodium-fabric/wiki/Known-Issues#rtss-incompatible
        RTSS_FOREST_SODIUM("RivaTuner Statistics Server \\(RTSS\\) is not compatible with Sodium");


        private final Pattern pattern;
        private final String[] groupNames;

        Rule(@Language("RegExp") String pattern, String... groupNames) {
            this.pattern = Pattern.compile(pattern);
            this.groupNames = groupNames;
        }

        public Pattern getPattern() {
            return pattern;
        }

        public String[] getGroupNames() {
            return groupNames;
        }
    }

    public static class Result {
        private final Rule rule;
        private final String log;
        private final Matcher matcher;

        public Result(Rule rule, String log, Matcher matcher) {
            this.rule = rule;
            this.log = log;
            this.matcher = matcher;
        }

        public Rule getRule() {
            return rule;
        }

        public String getLog() {
            return log;
        }

        public Matcher getMatcher() {
            return matcher;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Result result = (Result) o;

            if (rule != result.rule) return false;
            if (!log.equals(result.log)) return false;
            return matcher.equals(result.matcher);
        }

        @Override
        public int hashCode() {
            int result = rule.hashCode();
            result = 31 * result + log.hashCode();
            result = 31 * result + matcher.hashCode();
            return result;
        }
    }

    public static Set<Result> analyze(String log) {
        Set<Result> results = new HashSet<>();
        for (Rule rule : Rule.values()) {
            Matcher matcher = rule.pattern.matcher(log);
            if (matcher.find()) {
                results.add(new Result(rule, log, matcher));
            }
        }
        return results;
    }

    private static final Pattern CRASH_REPORT_LOCATION_PATTERN = Pattern.compile("#@!@# Game crashed! Crash report saved to: #@!@# (?<location>.*)");

    @Nullable
    public static String findCrashReport(String log) throws IOException, InvalidPathException {
        Matcher matcher = CRASH_REPORT_LOCATION_PATTERN.matcher(log);
        if (matcher.find()) {
            return Files.readString(Paths.get(matcher.group("location")));
        } else {
            return null;
        }
    }

    public static String extractCrashReport(String rawLog) {
        int begin = rawLog.lastIndexOf("---- Minecraft Crash Report ----");
        int end = rawLog.lastIndexOf("#@!@# Game crashed! Crash report saved to");
        if (begin == -1 || end == -1 || begin >= end) return null;
        return rawLog.substring(begin, end);
    }

    private static final Pattern CRASH_REPORT_STACK_TRACE_PATTERN = Pattern.compile("Description: (.*?)[\\n\\r]+(?<stacktrace>[\\w\\W\\n\\r]+)A detailed walkthrough of the error");
    private static final Pattern STACK_TRACE_LINE_PATTERN = Pattern.compile("at (?<method>.*?)\\((?<sourcefile>.*?)\\)");
    private static final Pattern STACK_TRACE_LINE_MODULE_PATTERN = Pattern.compile("\\{(?<tokens>.*)}");
    private static final Set<String> PACKAGE_KEYWORD_BLACK_LIST = new HashSet<>(Arrays.asList(
            "net", "minecraft", "item", "setup", "block", "assist", "optifine", "player", "unimi", "fastutil", "tileentity", "events", "common", "blockentity", "client", "entity", "mojang", "main", "gui", "world", "server", "dedicated", "map", "dsi", // minecraft
            "renderer", "chunk", "model", "loading", "color", "pipeline", "inventory", "launcher", "physics", "particle", "gen", "registry", "worldgen", "texture", "biomes", "biome",
            "monster", "passive", "ai", "integrated", "tile", "state", "play", "override", "transformers", "structure", "nbt", "pathfinding", "chunk", "audio", "entities", "items", "renderers",
            "storage", "universal", "oshi", "platform",
            "java", "lang", "util", "nio", "io", "sun", "reflect", "zip", "jar", "jdk", "nashorn", "scripts", "runtime", "internal", // java
            "mods", "mod", "impl", "org", "com", "cn", "cc", "jp", // title
            "core", "config", "registries", "lib", "ruby", "mc", "codec", "recipe", "channel", "embedded", "done", "net", "netty", "network", "load", "github", "handler", "content", "feature", // misc
            "file", "machine", "shader", "general", "helper", "init", "library", "api", "integration", "engine", "preload", "preinit",
            "hellominecraft", "jackhuang", // hmcl
            "fml", "minecraftforge", "forge", "cpw", "modlauncher", "launchwrapper", "objectweb", "asm", "event", "eventhandler", "handshake", "modapi", "kcauldron", // forge
            "fabricmc", "loader", "game", "knot", "launch", "mixin" // fabric
    ));

    public static Set<String> findKeywordsFromCrashReport(String crashReport) {
        Matcher matcher = CRASH_REPORT_STACK_TRACE_PATTERN.matcher(crashReport);
        Set<String> result = new HashSet<>();
        if (matcher.find()) {
            for (String line : matcher.group("stacktrace").split("\\n")) {
                Matcher lineMatcher = STACK_TRACE_LINE_PATTERN.matcher(line);
                if (lineMatcher.find()) {
                    String[] method = lineMatcher.group("method").split("\\.");
                    for (int i = 0; i < method.length - 2; i++) {
                        if (PACKAGE_KEYWORD_BLACK_LIST.contains(method[i])) {
                            continue;
                        }
                        result.add(method[i]);
                    }

                    Matcher moduleMatcher = STACK_TRACE_LINE_MODULE_PATTERN.matcher(line);
                    if (moduleMatcher.find()) {
                        for (String module : moduleMatcher.group("tokens").split(",")) {
                            String[] split = module.split(":");
                            if (split.length >= 2 && "xf".equals(split[0])) {
                                if (PACKAGE_KEYWORD_BLACK_LIST.contains(split[1])) {
                                    continue;
                                }

                                result.add(split[1]);
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    public static int getJavaVersionFromMajorVersion(int majorVersion) {
        if (majorVersion >= 46) {
            return majorVersion - 44;
        } else {
            return -1;
        }
    }
}
