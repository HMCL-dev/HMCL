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
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Identifies known failure signatures and mod-related stack-trace keywords in Minecraft logs.
public final class CrashReportAnalyzer {

    /// Maximum number of decoded characters analyzed in one streaming window.
    private static final int STREAM_WINDOW_SIZE = 4 * 1024 * 1024;

    /// Characters retained between adjacent streaming windows for cross-boundary matches.
    private static final int STREAM_WINDOW_OVERLAP = 512 * 1024;

    /// Maximum characters retained while extracting keywords from one stack-trace line.
    private static final int MAX_STACK_TRACE_LINE_SIZE = 64 * 1024;

    /// Prevents construction of this utility class.
    private CrashReportAnalyzer() {
    }

    /// Known crash signatures and the literal text used to locate possible matches while streaming.
    public enum Rule {
        OPENJ9("(Open J9 is not supported|OpenJ9 is incompatible|\\.J9VMInternals\\.)", triggers("J9")),
        NEED_JDK11("(no such method: sun\\.misc\\.Unsafe\\.defineAnonymousClass\\(Class,byte\\[\\],Object\\[\\]\\)Class/invokeVirtual|java\\.lang\\.UnsupportedClassVersionError: icyllis/modernui/forge/MixinConnector has been compiled by a more recent version of the Java Runtime \\(class file version 55\\.0\\), this version of the Java Runtime only recognizes class file versions up to 52\\.0|java\\.lang\\.IllegalArgumentException: The requested compatibility level JAVA_11 could not be set\\. Level is not supported by the active JRE or ASM version)", triggers("defineAnonymousClass", "class file version 55.0", "compatibility level JAVA_11")),
        TOO_OLD_JAVA("java\\.lang\\.UnsupportedClassVersionError: (.*?) version (?<expected>\\d+)\\.0", triggers("UnsupportedClassVersionError"), "expected"),
        JVM_32BIT("(Could not reserve enough space for (.*?)KB object heap|The specified size exceeds the maximum representable size|Invalid maximum heap size)", triggers("Could not reserve enough space", "maximum representable size", "Invalid maximum heap size")),

        // Some mods/shader packs do incorrect GL operations.
        GL_OPERATION_FAILURE("(1282: Invalid operation|Maybe try a lower resolution resourcepack\\?)", triggers("1282: Invalid operation", "lower resolution resourcepack")),

        // Maybe software rendering? Suggest user for using a graphics card.
        OPENGL_NOT_SUPPORTED("The driver does not appear to support OpenGL", triggers("does not appear to support OpenGL")),
        GRAPHICS_DRIVER("(Pixel format not accelerated|GLX: Failed to create context: GLXBadFBConfig|Couldn't set pixel format|net\\.minecraftforge\\.fml.client\\.SplashProgress|org\\.lwjgl\\.LWJGLException|EXCEPTION_ACCESS_VIOLATION(.|\\n|\\r)+# C {2}\\[(ig|atio|nvoglv))", triggers("Pixel format not accelerated", "GLXBadFBConfig", "Couldn't set pixel format", "SplashProgress", "LWJGLException", "EXCEPTION_ACCESS_VIOLATION")),
        // macOS initializing OpenGL window issues
        MACOS_FAILED_TO_FIND_SERVICE_PORT_FOR_DISPLAY("java\\.lang\\.IllegalStateException: GLFW error before init: \\[0x10008\\]Cocoa: Failed to find service port for display", triggers("Failed to find service port for display")),
        // Out of memory
        OUT_OF_MEMORY("(java\\.lang\\.OutOfMemoryError|The system is out of physical RAM or swap space|Out of Memory Error|Error occurred during initialization of VM\\RToo small maximum heap)", triggers("OutOfMemoryError", "out of physical RAM or swap space", "Out of Memory Error", "Error occurred during initialization of VM")),
        // Memory exceeded
        MEMORY_EXCEEDED("There is insufficient memory for the Java Runtime Environment to continue", triggers("insufficient memory for the Java Runtime Environment")),
        // Too high resolution
        RESOLUTION_TOO_HIGH("Maybe try a (lower resolution|lowerresolution) (resourcepack|texturepack)\\?", triggers("lower resolution", "lowerresolution")),
        // game can only run on Java 8. Version of uesr's JVM is too high.
        JDK_9("java\\.lang\\.ClassCastException: (java\\.base/jdk|class jdk)", triggers("java.lang.ClassCastException:")),
        // Forge and OptiFine with crash because the JVM compiled with a new version of Xcode
        // https://github.com/sp614x/optifine/issues/4824
        // https://github.com/MinecraftForge/MinecraftForge/issues/7546
        MAC_JDK_8U261("Terminating app due to uncaught exception 'NSInternalInconsistencyException', reason: 'NSWindow drag regions should only be invalidated on the Main Thread!'", triggers("NSWindow drag regions should only be invalidated")),
        // user modifies minecraft primary jar without changing hash file
        FILE_CHANGED("java\\.lang\\.SecurityException: SHA1 digest error for (?<file>.*)|signer information does not match signer information of other classes in the same package", triggers("SHA1 digest error for", "signer information does not match"), "file"),
        // mod loader/coremod injection fault, prompt user to reinstall game.
        NO_SUCH_METHOD_ERROR("java\\.lang\\.NoSuchMethodError: (?<class>.*?)", triggers("java.lang.NoSuchMethodError:"), "class"),
        // mod loader/coremod injection fault, prompt user to reinstall game.
        NO_CLASS_DEF_FOUND_ERROR("java\\.lang\\.NoClassDefFoundError: (?<class>.*)", triggers("java.lang.NoClassDefFoundError:"), "class"),
        // coremod wants to access class without "setAccessible"
        ILLEGAL_ACCESS_ERROR("java\\.lang\\.IllegalAccessError: tried to access class (.*?) from class (?<class>.*?)", triggers("java.lang.IllegalAccessError: tried to access class"), "class"),
        // Some mods duplicated
        DUPLICATED_MOD("Found a duplicate mod (?<name>.*) at (?<path>.*)", triggers("Found a duplicate mod"), "name", "path"),
        // Fabric mod resolution
        MOD_RESOLUTION("ModResolutionException: (?<reason>(.*)[\\n\\r]*( - (.*)[\\n\\r]*)+)", triggers("ModResolutionException:"), "reason"),
        FORGEMOD_RESOLUTION("Missing or unsupported mandatory dependencies:(?<reason>(.*)[\\n\\r]*(\t(.*)[\\n\\r]*)+)", triggers("Missing or unsupported mandatory dependencies:"), "reason"),
        FORGE_FOUND_DUPLICATE_MODS("Found duplicate mods:(?<reason>(.*)\\R*(\t(.*)\\R*)+)", triggers("Found duplicate mods:"), "reason"),
        MOD_RESOLUTION_CONFLICT("ModResolutionException: Found conflicting mods: (?<sourcemod>.*) conflicts with (?<destmod>.*)", triggers("ModResolutionException: Found conflicting mods:"), "sourcemod", "destmod"),
        MOD_RESOLUTION_MISSING("ModResolutionException: Could not find required mod: (?<sourcemod>.*) requires (?<destmod>.*)", triggers("ModResolutionException: Could not find required mod:"), "sourcemod", "destmod"),
        MOD_RESOLUTION_MISSING_MINECRAFT("ModResolutionException: Could not find required mod: (?<mod>.*) requires \\{minecraft @ (?<version>.*)}", triggers("ModResolutionException: Could not find required mod:"), "mod", "version"),
        MOD_RESOLUTION_COLLECTION("ModResolutionException: Could not resolve valid mod collection \\(at: (?<sourcemod>.*) requires (?<destmod>.*)\\)", triggers("ModResolutionException: Could not resolve valid mod collection"), "sourcemod", "destmod"),
        // Some mods require a file not existing, asking user to manually delete it
        FILE_ALREADY_EXISTS("java\\.nio\\.file\\.FileAlreadyExistsException: (?<file>.*)", triggers("java.nio.file.FileAlreadyExistsException:"), "file"),
        // Forge found some mod crashed in game loading
        LOADING_CRASHED_FORGE("LoaderExceptionModCrash: Caught exception from (?<name>.*?) \\((?<id>.*)\\)", triggers("LoaderExceptionModCrash: Caught exception from"), "name", "id"),
        BOOTSTRAP_FAILED("Failed to create mod instance\\. ModID: (?<id>.*?),", triggers("Failed to create mod instance. ModID:"), "id"),
        // Fabric found some mod crashed in game loading
        LOADING_CRASHED_FABRIC("Could not execute entrypoint stage '(.*?)' due to errors, provided by '(?<id>.*)'!", triggers("Could not execute entrypoint stage"), "id"),
        // Fabric may have breaking changes.
        // https://github.com/FabricMC/fabric-loader/tree/master/src/main/legacyJava deprecated classes may be removed in the future.
        FABRIC_VERSION_0_12("java\\.lang\\.NoClassDefFoundError: org/spongepowered/asm/mixin/transformer/FabricMixinTransformerProxy", triggers("FabricMixinTransformerProxy")),
        // Minecraft 1.16+Forge with crash because JDK-8273826
        // https://github.com/McModLauncher/modlauncher/issues/91
        MODLAUNCHER_8("java\\.lang\\.NoSuchMethodError: ('void sun\\.security\\.util\\.ManifestEntryVerifier\\.<init>\\(java\\.util\\.jar\\.Manifest\\)'|sun\\.security\\.util\\.ManifestEntryVerifier\\.<init>\\(Ljava/util/jar/Manifest;\\)V)", triggers("ManifestEntryVerifier")),
        // Manually triggered debug crash
        DEBUG_CRASH("Manually triggered debug crash", triggers("Manually triggered debug crash")),
        CONFIG("Failed loading config file (?<file>.*?) of type (.*?) for modid (?<id>.*)", triggers("Failed loading config file"), "id", "file"),
        // Fabric gives some warnings
        FABRIC_WARNINGS("(Warnings were found!|Incompatible mod set!|Incompatible mods found!)(.*?)[\\n\\r]+(?<reason>[^\\[]+)\\[", triggers("Warnings were found!", "Incompatible mod set!", "Incompatible mods found!"), "reason"),
        // Game crashed when ticking entity
        ENTITY("Entity Type: (?<type>.*)[\\w\\W\\n\\r]*?Entity's Exact location: (?<location>.*)", triggers("Entity Type:"), "type", "location"),
        // Game crashed when tessellating block model
        BLOCK("Block: (?<type>.*)[\\w\\W\\n\\r]*?Block location: (?<location>.*)", triggers("Block:"), "type", "location"),
        // Cannot find native libraries
        UNSATISFIED_LINK_ERROR("java\\.lang\\.UnsatisfiedLinkError: Failed to locate library: (?<name>.*)", triggers("java.lang.UnsatisfiedLinkError: Failed to locate library:"), "name"),

        //https://github.com/HMCL-dev/HMCL/pull/1813
        OPTIFINE_IS_NOT_COMPATIBLE_WITH_FORGE("(java\\.lang\\.NoSuchMethodError: 'java\\.lang\\.Class sun\\.misc\\.Unsafe\\.defineAnonymousClass\\(java\\.lang\\.Class, byte\\[\\], java\\.lang\\.Object\\[\\]\\)'|java\\.lang\\.NoSuchMethodError: 'void net\\.minecraft\\.client\\.renderer\\.texture\\.SpriteContents\\.\\<init\\>\\(net\\.minecraft\\.resources\\.ResourceLocation, |java\\.lang\\.NoSuchMethodError: 'void net\\.minecraftforge\\.client\\.gui\\.overlay\\.ForgeGui\\.renderSelectedItemName\\(net\\.minecraft\\.client\\.gui\\.GuiGraphics, int\\)'|java\\.lang\\.NoSuchMethodError: 'java\\.lang\\.String com\\.mojang\\.blaze3d\\.systems\\.RenderSystem\\.getBackendDescription\\(\\)'|java\\.lang\\.NoSuchMethodError: 'net\\.minecraft\\.network\\.chat\\.FormattedText net\\.minecraft\\.client\\.gui\\.Font\\.ellipsize\\(net\\.minecraft\\.network\\.chat\\.FormattedText, int\\)'|java\\.lang\\.NoSuchMethodError: 'void net\\.minecraft\\.server\\.level\\.DistanceManager\\.(.*?)\\(net\\.minecraft\\.server\\.level\\.TicketType, net\\.minecraft\\.world\\.level\\.ChunkPos, int, java\\.lang\\.Object, boolean\\)'|java\\.lang\\.NoSuchMethodError: 'void net\\.minecraft\\.client\\.renderer\\.block\\.model\\.BakedQuad\\.\\<init\\>\\(int\\[\\], int, net\\.minecraft\\.core\\.Direction, net\\.minecraft\\.client\\.renderer\\.texture\\.TextureAtlasSprite, boolean, boolean\\)'|TRANSFORMER/net\\.optifine/net\\.optifine\\.reflect\\.Reflector\\.\\<clinit\\>\\(Reflector\\.java)", triggers("java.lang.NoSuchMethodError:", "TRANSFORMER/net.optifine")),
        MOD_FILES_ARE_DECOMPRESSED("(The directories below appear to be extracted jar files\\. Fix this before you continue|Extracted mod jars found, loading will NOT continue)", triggers("extracted jar files", "Extracted mod jars found")),//Mod文件被解压
        OPTIFINE_CAUSES_THE_WORLD_TO_FAIL_TO_LOAD("java\\.lang\\.NoSuchMethodError: net\\.minecraft\\.world\\.server\\.ChunkManager$ProxyTicketManager\\.shouldForceTicks\\(J\\)Z", triggers("ChunkManager$ProxyTicketManager.shouldForceTicks")),//OptiFine导致无法加载世界 https://www.minecraftforum.net/forums/support/java-edition-support/3051132-exception-ticking-world
        TOO_MANY_MODS_LEAD_TO_EXCEEDING_THE_ID_LIMIT("maximum id range exceeded", triggers("maximum id range exceeded")),//Mod过多导致超出ID限制

        // Mod issues
        //https://github.com/HMCL-dev/HMCL/pull/2038
        MODMIXIN_FAILURE("(MixinApplyError|Mixin prepare failed |Mixin apply failed |mixin\\.injection\\.throwables\\.|\\.mixins\\.json\\] FAILED during \\))", triggers("MixinApplyError", "Mixin prepare failed ", "Mixin apply failed ", "mixin.injection.throwables.", ".mixins.json] FAILED during )")),//ModMixin失败
        MIXIN_APPLY_MOD_FAILED("Mixin apply for mod (?<id>.*) failed", triggers("Mixin apply for mod"), "id"),//Mixin应用失败
        FORGE_ERROR("An exception was thrown, the game will display an error screen and halt\\.\\R*(?<reason>.*\\R*(\\s*at .*\\R)+)", triggers("An exception was thrown, the game will display an error screen and halt."), "reason"),//Forge报错,Forge可能已经提供了错误信息
        MOD_RESOLUTION0("(\tMod File:|-- MOD |\tFailure message:)", triggers("\tMod File:", "-- MOD ", "\tFailure message:")),
        FORGE_REPEAT_INSTALLATION("MultipleArgumentsForOptionException: Found multiple arguments for option (.*?), but you asked for only one", triggers("MultipleArgumentsForOptionException: Found multiple arguments")),//https://github.com/HMCL-dev/HMCL/issues/1880
        OPTIFINE_REPEAT_INSTALLATION("ResolutionException: Module optifine reads another module named optifine", triggers("Module optifine reads another module named optifine")),//Optifine 重复安装（及Mod文件夹有，自动安装也有）
        JAVA_VERSION_IS_TOO_HIGH("(Unable to make protected final java\\.lang\\.Class java\\.lang\\.ClassLoader\\.defineClass|java\\.lang\\.NoSuchFieldException: ucp|Unsupported class file major version|because module java\\.base does not export|java\\.lang\\.ClassNotFoundException: jdk\\.nashorn\\.api\\.scripting\\.NashornScriptEngineFactory|java\\.lang\\.ClassNotFoundException: java\\.lang\\.invoke\\.LambdaMetafactory|Exception in thread \"main\" java\\.lang\\.NullPointerException: Cannot read the array length because \"urls\" is null)", triggers("ClassLoader.defineClass", "NoSuchFieldException: ucp", "Unsupported class file major version", "module java.base does not export", "NashornScriptEngineFactory", "java.lang.invoke.LambdaMetafactory", "Cannot read the array length because \"urls\" is null")),//Java版本过高
        INSTALL_MIXINBOOTSTRAP("java\\.lang\\.ClassNotFoundException: org\\.spongepowered\\.asm\\.launch\\.MixinTweaker", triggers("org.spongepowered.asm.launch.MixinTweaker")),

        //Forge 默认会把每一个 mod jar 都当做一个 JPMS 的模块（Module）加载。在这个 jar 没有给出 module-info 声明的情况下，JPMS 会采用这样的顺序决定 module 名字：
        //1. META-INF/MANIFEST.MF 里的 Automatic-Module-Name
        //2. 根据文件名生成。文件名里的 .jar 后缀名先去掉，然后检查是否有 -(\\d+(\\.|$)) 的部分，有的话只取 - 前面的部分，- 后面的部分成为 module 的版本号（即尝试判断文件名里是否有版本号，有的话去掉），然后把不是拉丁字母和数字的字符（正则表达式 [^A-Za-z0-9]）都换成点，然后把连续的多个点换成一个点，最后去掉开头和结尾的点。那么
        //按照 2.，如果你的文件名是拔刀剑.jar，那么这么一通流程下来，你得到的 module 名就是空字符串，而这是不允许的。(来自 @Föhn 说明)
        MOD_NAME("Invalid module name: '' is not a Java identifier", triggers("Invalid module name: '' is not a Java identifier")),

        //Forge 安装不完整
        INCOMPLETE_FORGE_INSTALLATION("(java\\.io\\.UncheckedIOException: java\\.io\\.IOException: Invalid paths argument, contained no existing paths: \\[(.*?)(forge-(.*?)-client\\.jar|fmlcore-(.*?)\\.jar)\\]|Failed to find Minecraft resource version (.*?) at (.*?)forge-(.*?)-client\\.jar|Cannot find launch target fmlclient, unable to launch|java\\.lang\\.IllegalStateException: Could not find net/minecraft/client/Minecraft\\.class in classloader SecureModuleClassLoader)", triggers("Invalid paths argument, contained no existing paths:", "Failed to find Minecraft resource version", "Cannot find launch target fmlclient", "Could not find net/minecraft/client/Minecraft.class")),

        NIGHT_CONFIG_FIXES("com\\.electronwill\\.nightconfig\\.core\\.io\\.ParsingException: Not enough data available", triggers("nightconfig.core.io.ParsingException: Not enough data available")),//https://github.com/Fuzss/nightconfigfixes
        //Shaders Mod detected. Please remove it, OptiFine has built-in support for shaders.
        SHADERS_MOD("java\\.lang\\.RuntimeException: Shaders Mod detected\\. Please remove it, OptiFine has built-in support for shaders\\.", triggers("Shaders Mod detected. Please remove it")),

        // 一些模组与 Optifine 不兼容
        MOD_FOREST_OPTIFINE("Error occurred applying transform of coremod META-INF/asm/multipart\\.js function render", triggers("Error occurred applying transform of coremod META-INF/asm/multipart.js function render")),
        // PERFORMANT is not compatible with OptiFine
        PERFORMANT_FOREST_OPTIFINE("org\\.spongepowered\\.asm\\.mixin\\.injection\\.throwables\\.InjectionError: Critical injection failure: Redirector OnisOnLadder\\(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/LivingEntity;\\)Z in performant\\.mixins\\.json:entity\\.LivingEntityMixin failed injection check, \\(0/1\\) succeeded\\. Scanned 1 target\\(s\\)\\. Using refmap performant\\.refmap\\.json", triggers("Redirector OnisOnLadder")),
        // TwilightForest is not compatible with OptiFine on Minecraft 1.16
        TWILIGHT_FOREST_OPTIFINE("java\\.lang\\.IllegalArgumentException: (.*) outside of image bounds (.*)", triggers("outside of image bounds")),
        // Jade is not compatible with OptiFine on Minecraft 1.20+
        JADE_FOREST_OPTIFINE("Critical injection failure: LVT in net/minecraft/client/renderer/GameRenderer::m_109093_\\(FJZ\\)V has incompatible changes at opcode 760 in callback jade\\.mixins\\.json:GameRendererMixin-\\>@Inject::jade\\$runTick\\(FJZLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;IILcom/mojang/blaze3d/platform/Window;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/gui/GuiGraphics;\\)V\\.", triggers("callback jade.mixins.json:GameRendererMixin")),
        // NeoForge 与 OptiFine 不兼容
        NEOFORGE_FOREST_OPTIFINE("cpw\\.mods\\.modlauncher\\.InvalidLauncherSetupException: Invalid Services found OptiFine", triggers("Invalid Services found OptiFine")),

        // 一些模组与 Sodium 不兼容
        // https://github.com/CaffeineMC/sodium-fabric/wiki/Known-Issues#rtss-incompatible
        RTSS_FOREST_SODIUM("RivaTuner Statistics Server \\(RTSS\\) is not compatible with Sodium", triggers("RivaTuner Statistics Server (RTSS)"));


        /// Compiled crash signature.
        private final Pattern pattern;

        /// Literal strings that every matching alternative is represented by.
        private final @Unmodifiable List<String> triggers;

        /// Named groups interpolated into the crash explanation.
        private final String @Unmodifiable [] groupNames;

        /// Creates a crash rule.
        ///
        /// @param pattern complete crash signature
        /// @param triggers literal strings representing all alternatives of the signature
        /// @param groupNames named groups interpolated into the crash explanation
        Rule(@Language("RegExp") String pattern, @Unmodifiable List<String> triggers, String... groupNames) {
            this.pattern = Pattern.compile(pattern);
            this.triggers = List.copyOf(triggers);
            this.groupNames = groupNames.clone();
        }

        /// Returns the compiled crash signature.
        ///
        /// @return compiled crash signature
        public Pattern getPattern() {
            return pattern;
        }

        /// Returns the names of groups interpolated into the crash explanation.
        ///
        /// @return immutable view of group names
        public String @Unmodifiable [] getGroupNames() {
            return groupNames.clone();
        }
    }

    /// A detected crash rule and a matcher detached from the analyzed input.
    public record Result(Rule rule, String log, Matcher matcher) {
    }

    /// Results produced while streaming a complete log.
    public record Analysis(@Unmodifiable Set<Result> results, @Unmodifiable Set<String> keywords) {
        /// Creates an immutable analysis result.
        public Analysis {
            results = Set.copyOf(results);
            keywords = Set.copyOf(keywords);
        }
    }

    /// Analyzes an in-memory log with all crash rules.
    ///
    /// @param log complete log text
    /// @return the first match for each crash rule
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

    /// Analyzes a complete log without retaining all decoded characters in memory.
    ///
    /// @param reader decoded log reader; ownership remains with the caller
    /// @return detected crash rules and crash-report keywords
    /// @throws IOException if the log cannot be read
    public static Analysis analyze(Reader reader) throws IOException {
        Analyzer analyzer = new Analyzer();
        CrashReportKeywordCollector keywordCollector = new CrashReportKeywordCollector();
        StringBuilder window = new StringBuilder(STREAM_WINDOW_SIZE);
        char[] buffer = new char[32 * 1024];

        int length;
        while ((length = reader.read(buffer)) >= 0) {
            if (length == 0) {
                continue;
            }

            keywordCollector.accept(buffer, length);
            window.append(buffer, 0, length);
            if (window.length() >= STREAM_WINDOW_SIZE) {
                analyzer.accept(window);
                window.delete(0, window.length() - STREAM_WINDOW_OVERLAP);
            }
        }

        if (!window.isEmpty()) {
            analyzer.accept(window);
        }
        keywordCollector.finish();
        return new Analysis(analyzer.results(), keywordCollector.results());
    }

    /// Copies a match onto its matched substring so it cannot retain a streaming window.
    ///
    /// @param rule matched rule
    /// @param matcher matcher positioned at the match
    /// @return detached match result
    private static Result detach(Rule rule, Matcher matcher) {
        String matchedLog = matcher.group();
        Matcher detachedMatcher = rule.pattern.matcher(matchedLog);
        if (!detachedMatcher.find()) {
            throw new AssertionError("Failed to detach crash analysis result for " + rule);
        }
        return new Result(rule, matchedLog, detachedMatcher);
    }

    /// Selects possible rules by literal text before applying their complete patterns.
    private static final class Analyzer {
        /// Rules that have not matched an earlier window.
        private final EnumSet<Rule> remainingRules = EnumSet.allOf(Rule.class);

        /// First detached match for every detected rule.
        private final EnumMap<Rule, Result> results = new EnumMap<>(Rule.class);

        /// Literal trigger index shared by all rules.
        private final TriggerIndex triggerIndex = new TriggerIndex();

        /// Applies all unresolved rules to a diagnostic window.
        ///
        /// @param log diagnostic text window
        private void accept(CharSequence log) {
            for (Rule rule : triggerIndex.findCandidates(log, remainingRules)) {
                Matcher matcher = rule.pattern.matcher(log);
                if (matcher.find()) {
                    results.put(rule, detach(rule, matcher));
                    remainingRules.remove(rule);
                }
            }
        }

        /// Returns the detached matches collected so far.
        ///
        /// @return detected crash rules
        private Set<Result> results() {
            return new HashSet<>(results.values());
        }
    }

    /// Indexes rule triggers by their first character for a single linear scan of each window.
    private static final class TriggerIndex {
        /// Trigger buckets indexed by their first ASCII character.
        private final List<Trigger> @Unmodifiable [] buckets;

        /// Builds an immutable index of all declared rule triggers.
        @SuppressWarnings("unchecked")
        private TriggerIndex() {
            List<Trigger>[] mutableBuckets = (List<Trigger>[]) new List<?>[128];
            Arrays.fill(mutableBuckets, List.of());
            for (Rule rule : Rule.values()) {
                for (String trigger : rule.triggers) {
                    if (trigger.isEmpty() || trigger.charAt(0) >= mutableBuckets.length) {
                        throw new IllegalArgumentException("Rule triggers must start with an ASCII character");
                    }
                    int bucket = trigger.charAt(0);
                    if (mutableBuckets[bucket].isEmpty()) {
                        mutableBuckets[bucket] = new ArrayList<>();
                    }
                    mutableBuckets[bucket].add(new Trigger(rule, trigger));
                }
            }

            for (int i = 0; i < mutableBuckets.length; i++) {
                if (!mutableBuckets[i].isEmpty()) {
                    mutableBuckets[i] = List.copyOf(mutableBuckets[i]);
                }
            }
            buckets = mutableBuckets.clone();
        }

        /// Finds unresolved rules whose trigger occurs in the supplied window.
        ///
        /// @param text diagnostic text window
        /// @param remainingRules rules that have not matched an earlier window
        /// @return rules that may match this window
        private EnumSet<Rule> findCandidates(CharSequence text, Set<Rule> remainingRules) {
            EnumSet<Rule> candidates = EnumSet.noneOf(Rule.class);
            for (int i = 0; i < text.length(); i++) {
                char first = text.charAt(i);
                if (first >= buckets.length || buckets[first].isEmpty()) {
                    continue;
                }

                for (Trigger trigger : buckets[first]) {
                    if (remainingRules.contains(trigger.rule())
                            && !candidates.contains(trigger.rule())
                            && startsWith(text, i, trigger.text())) {
                        candidates.add(trigger.rule());
                    }
                }
            }
            return candidates;
        }

        /// Tests whether text has a literal prefix at an offset without allocating a substring.
        ///
        /// @param text source text
        /// @param offset candidate start offset
        /// @param prefix required prefix
        /// @return whether the prefix occurs at the offset
        private static boolean startsWith(CharSequence text, int offset, String prefix) {
            if (offset + prefix.length() > text.length()) {
                return false;
            }
            for (int i = 1; i < prefix.length(); i++) {
                if (text.charAt(offset + i) != prefix.charAt(i)) {
                    return false;
                }
            }
            return true;
        }
    }

    /// Associates one literal trigger with its crash rule.
    ///
    /// @param rule owning crash rule
    /// @param text literal trigger text
    private record Trigger(Rule rule, String text) {
    }

    /// Creates an immutable list of literal rule triggers.
    ///
    /// @param triggers literal strings representing all alternatives of a rule
    /// @return immutable trigger list
    private static @Unmodifiable List<String> triggers(String... triggers) {
        return List.of(triggers);
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

    /// Finds possible mod identifiers in a complete crash report.
    ///
    /// @param crashReport crash-report text
    /// @return possible mod identifiers from its stack trace
    public static Set<String> findKeywordsFromCrashReport(String crashReport) {
        CrashReportKeywordCollector collector = new CrashReportKeywordCollector();
        collector.accept(crashReport);
        collector.finish();
        return collector.results();
    }

    /// Extracts package keywords while a crash report is streamed line by line.
    private static final class CrashReportKeywordCollector {
        /// Keywords extracted from stack-trace lines.
        private final Set<String> results = new HashSet<>();

        /// Current line, bounded to avoid retaining an arbitrarily long line.
        private final StringBuilder line = new StringBuilder();

        /// Whether the current position is inside the crash-report stack trace.
        private boolean inStackTrace;

        /// Whether the first complete crash report has already been processed.
        private boolean finished;

        /// Keywords held until the current crash report reaches its end marker.
        private final Set<String> pendingResults = new HashSet<>();

        /// Accepts another decoded character block.
        ///
        /// @param chars source buffer
        /// @param length number of valid characters in the buffer
        private void accept(char[] chars, int length) {
            for (int i = 0; i < length; i++) {
                char character = chars[i];
                if (character == '\n' || character == '\r') {
                    finishLine();
                } else if (line.length() < MAX_STACK_TRACE_LINE_SIZE) {
                    line.append(character);
                }
            }
        }

        /// Accepts an in-memory character sequence without copying it first.
        ///
        /// @param chars source text
        private void accept(CharSequence chars) {
            for (int i = 0; i < chars.length(); i++) {
                accept(chars.charAt(i));
            }
        }

        /// Accepts one decoded character.
        ///
        /// @param character decoded character
        private void accept(char character) {
            if (character == '\n' || character == '\r') {
                finishLine();
            } else if (line.length() < MAX_STACK_TRACE_LINE_SIZE) {
                line.append(character);
            }
        }

        /// Processes the final unterminated line, if present.
        private void finish() {
            if (!line.isEmpty()) {
                finishLine();
            }
        }

        /// Updates crash-report state and extracts keywords from the current line.
        private void finishLine() {
            if (!finished && line.indexOf("Description:") >= 0) {
                inStackTrace = true;
            }
            if (!inStackTrace) {
                line.setLength(0);
                return;
            }
            if (line.indexOf("A detailed walkthrough of the error") >= 0) {
                inStackTrace = false;
                finished = true;
                results.addAll(pendingResults);
                pendingResults.clear();
                line.setLength(0);
                return;
            }

            findKeywordsFromStackTraceLine(line, pendingResults);
            line.setLength(0);
        }

        /// Returns the extracted keywords.
        ///
        /// @return possible mod identifiers
        private Set<String> results() {
            return new HashSet<>(results);
        }
    }

    /// Extracts package keywords from one stack-trace line.
    ///
    /// @param line stack-trace line
    /// @param result destination keyword set
    private static void findKeywordsFromStackTraceLine(CharSequence line, Set<String> result) {
        Matcher lineMatcher = STACK_TRACE_LINE_PATTERN.matcher(line);
        if (!lineMatcher.find()) {
            return;
        }

        String[] method = lineMatcher.group("method").split("\\.");
        for (int i = 0; i < method.length - 2; i++) {
            if (!PACKAGE_KEYWORD_BLACK_LIST.contains(method[i])) {
                result.add(method[i]);
            }
        }

        Matcher moduleMatcher = STACK_TRACE_LINE_MODULE_PATTERN.matcher(line);
        if (moduleMatcher.find()) {
            for (String module : moduleMatcher.group("tokens").split(",")) {
                String[] split = module.split(":");
                if (split.length >= 2 && "xf".equals(split[0]) && !PACKAGE_KEYWORD_BLACK_LIST.contains(split[1])) {
                    result.add(split[1]);
                }
            }
        }
    }

    public static int getJavaVersionFromMajorVersion(int majorVersion) {
        if (majorVersion >= 46) {
            return majorVersion - 44;
        } else {
            return -1;
        }
    }
}
