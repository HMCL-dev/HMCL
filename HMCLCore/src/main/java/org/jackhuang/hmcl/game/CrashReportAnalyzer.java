package org.jackhuang.hmcl.game;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class CrashReportAnalyzer {

    public enum LogRule {
        OPENJ9("Open J9 is not supported", "OpenJ9 is incompatible"),
        TOO_OLD_JAVA("compiled by a more recent version of the Java Runtime"),
        GRAPHICS_DRIVER("Couldn't set pixel format"),
        JVM_32BIT("Could not reserve enough space for 1048576KB object heap"),

        // Some mods/shader packs do incorrect GL operations.
        GL_OPERATION_FAILURE("1282: Invalid operation");

        public final List<String> keywords;

        LogRule(String... keywords) {
            this.keywords = Collections.unmodifiableList(Arrays.asList(keywords));
        }
    }

    public enum StacktraceRules {
        // We manually write "Pattern.compile" here for IDEA syntax highlighting.



        // Maybe software rendering? Suggest user for using a graphics card.
        OPENGL_NOT_SUPPORTED(Pattern.compile("The driver does not appear to support OpenGL")),
        GRAPHICS_DRIVER(Pattern.compile("Pixel format not accelerated")),
        // Out of memory
        OUT_OF_MEMORY(Pattern.compile("java\\.lang\\.OutOfMemoryError")),
        // game can only run on Java 8. Version of uesr's JVM is too high.
        JDK_9(Pattern.compile("java\\.lang\\.ClassCastException: java\\.base/jdk")),
        // user modifies minecraft primary jar without changing hash file
        FILE_CHANGED(Pattern.compile("java\\.lang\\.SecurityException: SHA1 digest error for (?<file>.*?)"), "file"),
        // mod loader/coremod injection fault, prompt user to reinstall game.
        NO_SUCH_METHOD_ERROR(Pattern.compile("java\\.lang\\.NoSuchMethodError: (?<class>.*?)"), "class"),
        // mod loader/coremod injection fault, prompt user to reinstall game.
        NO_CLASS_DEF_FOUND_ERROR(Pattern.compile("java\\.lang\\.NoClassDefFoundError: (?<class>.*?)"), "class"),
        // coremod wants to access class without "setAccessible"
        ILLEGAL_ACCESS_ERROR(Pattern.compile("java\\.lang\\.IllegalAccessError: tried to access class (.*?) from class (?<class>.*?)"), "class"),
        // Some mods duplicated
        DUPLICATED_MOD(Pattern.compile("DuplicateModsFoundException")),
        MOD_RESOLUTION(Pattern.compile("ModResolutionException: Duplicate")),


        // Mods
        FLANSMODS(Pattern.compile("at co\\.uk\\.flansmods")),
        CUSTOM_NPCS(Pattern.compile("at noppes\\.npcs")),
        SLASH_BLADE(Pattern.compile("at mods\\.flammpfeil\\.slashblade")),
        FORESTRY(Pattern.compile("at forestry\\.")),
        TECH_REBORN(Pattern.compile("at techreborn\\.")),
        APPLIED_ENERGISTICS(Pattern.compile("at appeng\\.")),
        ELEC_CORE(Pattern.compile("at elec332\\.")),
        INDUSTRIAL_CRAFT2(Pattern.compile("at ic2\\.")),
        TWILIGHT_FOREST(Pattern.compile("at twilightforest\\.")),
        OPTIFINE(Pattern.compile("at net\\.optifine\\."));

        private final Pattern pattern;
        private final String[] groupNames;

        StacktraceRules(Pattern pattern, String... groupNames) {
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
}
