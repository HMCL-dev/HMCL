package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.Log4jLevel;
import org.jackhuang.hmcl.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CrashReportAnalyzer {
    public enum Rule {
        // We manually write "Pattern.compile" here for IDEA syntax highlighting.

        OPENJ9(Pattern.compile("(Open J9 is not supported|OpenJ9 is incompatible)")),
        TOO_OLD_JAVA(Pattern.compile("java\\.lang\\.UnsupportedClassVersionError: (.*?) version (?<expected>\\d+)\\.0"), "expected"),
        JVM_32BIT(Pattern.compile("Could not reserve enough space for 1048576KB object heap")),

        // Some mods/shader packs do incorrect GL operations.
        GL_OPERATION_FAILURE(Pattern.compile("1282: Invalid operation")),

        // Maybe software rendering? Suggest user for using a graphics card.
        OPENGL_NOT_SUPPORTED(Pattern.compile("The driver does not appear to support OpenGL")),
        GRAPHICS_DRIVER(Pattern.compile("(Pixel format not accelerated|Couldn't set pixel format|net\\.minecraftforge\\.fml.client\\.SplashProgress|org\\.lwjgl\\.LWJGLException)")),
        // Out of memory
        OUT_OF_MEMORY(Pattern.compile("java\\.lang\\.OutOfMemoryError")),
        // game can only run on Java 8. Version of uesr's JVM is too high.
        JDK_9(Pattern.compile("java\\.lang\\.ClassCastException: java\\.base/jdk")),
        // user modifies minecraft primary jar without changing hash file
        FILE_CHANGED(Pattern.compile("java\\.lang\\.SecurityException: SHA1 digest error for (?<file>.*)"), "file"),
        // mod loader/coremod injection fault, prompt user to reinstall game.
        NO_SUCH_METHOD_ERROR(Pattern.compile("java\\.lang\\.NoSuchMethodError: (?<class>.*?)"), "class"),
        // mod loader/coremod injection fault, prompt user to reinstall game.
        NO_CLASS_DEF_FOUND_ERROR(Pattern.compile("java\\.lang\\.NoClassDefFoundError: (?<class>.*)"), "class"),
        // coremod wants to access class without "setAccessible"
        ILLEGAL_ACCESS_ERROR(Pattern.compile("java\\.lang\\.IllegalAccessError: tried to access class (.*?) from class (?<class>.*?)"), "class"),
        // Some mods duplicated
        DUPLICATED_MOD(Pattern.compile("DuplicateModsFoundException")),
        MOD_RESOLUTION(Pattern.compile("ModResolutionException: Duplicate")),
        // Some mods require a file not existing, asking user to manually delete it
        FILE_ALREADY_EXISTS(Pattern.compile("java\\.nio\\.file\\.FileAlreadyExistsException: (?<file>.*)"), "file"),
        // Forge found some mod crashed in game loading
        LOADING_CRASHED(Pattern.compile("LoaderExceptionModCrash: Caught exception from (?<name>.*?) \\((?<id>.*)\\)"), "name", "id");

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

    public static List<Result> anaylze(List<Pair<String, Log4jLevel>> logs) {
        List<Result> results = new ArrayList<>();
        for (Pair<String, Log4jLevel> log : logs) {
            for (Rule rule : Rule.values()) {
                Matcher matcher = rule.pattern.matcher(log.getKey());
                if (matcher.find()) {
                    results.add(new Result(rule, log.getKey(), matcher));
                }
            }
        }
        return results;
    }

    public static final int getJavaVersionFromMajorVersion(int majorVersion) {
        if (majorVersion >= 46) {
            return majorVersion - 44;
        } else {
            return -1;
        }
    }
}
