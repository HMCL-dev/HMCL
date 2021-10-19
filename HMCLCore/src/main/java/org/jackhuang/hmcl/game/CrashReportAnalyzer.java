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

import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CrashReportAnalyzer {

    private CrashReportAnalyzer() {
    }

    public enum Rule {
        // We manually write "Pattern.compile" here for IDEA syntax highlighting.

        OPENJ9(Pattern.compile("(Open J9 is not supported|OpenJ9 is incompatible)")),
        TOO_OLD_JAVA(Pattern.compile("java\\.lang\\.UnsupportedClassVersionError: (.*?) version (?<expected>\\d+)\\.0"), "expected"),
        JVM_32BIT(Pattern.compile("(Could not reserve enough space for (.*?) object heap|The specified size exceeds the maximum representable size)")),

        // Some mods/shader packs do incorrect GL operations.
        GL_OPERATION_FAILURE(Pattern.compile("1282: Invalid operation")),

        // Maybe software rendering? Suggest user for using a graphics card.
        OPENGL_NOT_SUPPORTED(Pattern.compile("The driver does not appear to support OpenGL")),
        GRAPHICS_DRIVER(Pattern.compile("(Pixel format not accelerated|Couldn't set pixel format|net\\.minecraftforge\\.fml.client\\.SplashProgress|org\\.lwjgl\\.LWJGLException|EXCEPTION_ACCESS_VIOLATION(.|\\n|\\r)+# C {2}\\[(ig|atio|nvoglv))")),
        // Out of memory
        OUT_OF_MEMORY(Pattern.compile("(java\\.lang\\.OutOfMemoryError|The system is out of physical RAM or swap space)")),
        // Memory exceeded
        MEMORY_EXCEEDED(Pattern.compile("There is insufficient memory for the Java Runtime Environment to continue")),
        // Too high resolution
        RESOLUTION_TOO_HIGH(Pattern.compile("Maybe try a (lower resolution|lowerresolution) (resourcepack|texturepack)\\?")),
        // game can only run on Java 8. Version of uesr's JVM is too high.
        JDK_9(Pattern.compile("java\\.lang\\.ClassCastException: (java\\.base/jdk|class jdk)")),
        // user modifies minecraft primary jar without changing hash file
        FILE_CHANGED(Pattern.compile("java\\.lang\\.SecurityException: SHA1 digest error for (?<file>.*)"), "file"),
        // mod loader/coremod injection fault, prompt user to reinstall game.
        NO_SUCH_METHOD_ERROR(Pattern.compile("java\\.lang\\.NoSuchMethodError: (?<class>.*?)"), "class"),
        // mod loader/coremod injection fault, prompt user to reinstall game.
        NO_CLASS_DEF_FOUND_ERROR(Pattern.compile("java\\.lang\\.NoClassDefFoundError: (?<class>.*)"), "class"),
        // coremod wants to access class without "setAccessible"
        ILLEGAL_ACCESS_ERROR(Pattern.compile("java\\.lang\\.IllegalAccessError: tried to access class (.*?) from class (?<class>.*?)"), "class"),
        // Some mods duplicated
        DUPLICATED_MOD(Pattern.compile("Found a duplicate mod (?<name>.*) at (?<path>.*)"), "name", "path"),
        // Fabric mod resolution
        MOD_RESOLUTION(Pattern.compile("ModResolutionException: (?<reason>(.*)[\\n\\r]*( - (.*)[\\n\\r]*)+)"), "reason"),
        MOD_RESOLUTION_CONFLICT(Pattern.compile("ModResolutionException: Found conflicting mods: (?<sourcemod>.*) conflicts with (?<destmod>.*)"), "sourcemod", "destmod"),
        MOD_RESOLUTION_MISSING(Pattern.compile("ModResolutionException: Could not find required mod: (?<sourcemod>.*) requires (?<destmod>.*)"), "sourcemod", "destmod"),
        MOD_RESOLUTION_MISSING_MINECRAFT(Pattern.compile("ModResolutionException: Could not find required mod: (?<mod>.*) requires \\{minecraft @ (?<version>.*)}"), "mod", "version"),
        MOD_RESOLUTION_COLLECTION(Pattern.compile("ModResolutionException: Could not resolve valid mod collection \\(at: (?<sourcemod>.*) requires (?<destmod>.*)\\)"), "sourcemod", "destmod"),
        // Some mods require a file not existing, asking user to manually delete it
        FILE_ALREADY_EXISTS(Pattern.compile("java\\.nio\\.file\\.FileAlreadyExistsException: (?<file>.*)"), "file"),
        // Forge found some mod crashed in game loading
        LOADING_CRASHED_FORGE(Pattern.compile("LoaderExceptionModCrash: Caught exception from (?<name>.*?) \\((?<id>.*)\\)"), "name", "id"),
        BOOTSTRAP_FAILED(Pattern.compile("Failed to create mod instance. ModID: (?<id>.*?),"), "id"),
        // Fabric found some mod crashed in game loading
        LOADING_CRASHED_FABRIC(Pattern.compile("Could not execute entrypoint stage '(.*?)' due to errors, provided by '(?<id>.*)'!"), "id"),
        // Fabric may have breaking changes.
        // https://github.com/FabricMC/fabric-loader/tree/master/src/main/legacyJava deprecated classes may be removed in the future.
        FABRIC_VERSION_0_12(Pattern.compile("java\\.lang\\.NoClassDefFoundError: org/spongepowered/asm/mixin/transformer/FabricMixinTransformerProxy")),
        // Manually triggerd debug crash
        DEBUG_CRASH(Pattern.compile("Manually triggered debug crash")),
        CONFIG(Pattern.compile("Failed loading config file (?<file>.*?) of type SERVER for modid (?<id>.*)"), "id", "file"),
        // Fabric gives some warnings
        FABRIC_WARNINGS(Pattern.compile("Warnings were found!(.*?)[\\n\\r]+(?<reason>[^\\[]+)\\["), "reason"),
        // Game crashed when ticking entity
        ENTITY(Pattern.compile("Entity Type: (?<type>.*)[\\w\\W\\n\\r]*?Entity's Exact location: (?<location>.*)"), "type", "location"),
        // Game crashed when tesselating block model
        BLOCK(Pattern.compile("Block: (?<type>.*)[\\w\\W\\n\\r]*?Block location: (?<location>.*)"), "type", "location"),
        // Cannot find native libraries
        UNSATISFIED_LINK_ERROR(Pattern.compile("java.lang.UnsatisfiedLinkError: Failed to locate library: (?<name>.*)"), "name"),



        // Mod issues
        // TwilightForest is not compatible with OptiFine on Minecraft 1.16.
        TWILIGHT_FOREST_OPTIFINE(Pattern.compile("java.lang.IllegalArgumentException: (.*) outside of image bounds (.*)"));

        private final Pattern pattern;
        private final String[] groupNames;

        Rule(Pattern pattern, String... groupNames) {
            this.pattern = pattern;
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
    }

    public static List<Result> anaylze(String log) {
        List<Result> results = new ArrayList<>();
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
            return FileUtils.readText(Paths.get(matcher.group("location")));
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
            "net", "minecraft", "item", "block", "player", "tileentity", "events", "common", "client", "entity", "mojang", "main", "gui", "world", "server", "dedicated", // minecraft
            "renderer", "chunk", "model", "loading", "color", "pipeline", "inventory", "launcher", "physics", "particle", "gen", "registry", "worldgen", "texture", "biomes", "biome",
            "monster", "passive", "ai", "integrated", "tile", "state", "play", "structure", "nbt", "pathfinding", "chunk", "audio", "entities", "items", "renderers",
            "storage",
            "java", "lang", "util", "nio", "io", "sun", "reflect", "zip", "jdk", "nashorn", "scripts", "runtime", "internal", // java
            "mods", "mod", "impl", "org", "com", "cn", "cc", "jp", // title
            "core", "config", "registries", "lib", "ruby", "mc", "codec", "channel", "embedded", "netty", "network", "handler", "feature", // misc
            "file", "machine", "shader", "general", "helper", "init", "library", "api", "integration", "engine", "preload", "preinit",
            "hellominecraft", "jackhuang", // hmcl
            "fml", "minecraftforge", "forge", "cpw", "modlauncher", "launchwrapper", "objectweb", "asm", "event", "eventhandler", "handshake", "kcauldron", // forge
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
