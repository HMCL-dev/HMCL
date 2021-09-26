package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.Range;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.download.LibraryAnalyzer.LAUNCH_WRAPPER_MAIN;

public enum HMCLJavaVersion {

    // Minecraft>=1.17 requires Java 16
    VANILLA_JAVA_16(HMCLJavaVersion.RULE_MANDATORY, versionRange("1.17", HMCLJavaVersion.MAX), versionRange("16", HMCLJavaVersion.MAX)),
    // Minecraft>=1.13 requires Java 8
    VANILLA_JAVA_8(HMCLJavaVersion.RULE_MANDATORY, versionRange("1.13", HMCLJavaVersion.MAX), versionRange("8", HMCLJavaVersion.MAX)),
    // Minecraft>=1.7.10+Forge accepts Java 8
    SUGGEST_JAVA_8(HMCLJavaVersion.RULE_SUGGESTED, versionRange("1.7.10", HMCLJavaVersion.MAX), versionRange("8", HMCLJavaVersion.MAX)),
    // LaunchWrapper<=1.12 will crash because of assuming the system class loader is an instance of URLClassLoader (Java 8)
    LAUNCH_WRAPPER(HMCLJavaVersion.RULE_MANDATORY, versionRange("0", "1.12"), versionRange("0", "8")) {
        @Override
        public boolean test(Version version) {
            return LAUNCH_WRAPPER_MAIN.equals(version.getMainClass()) &&
                    version.getLibraries().stream()
                            .filter(library -> "launchwrapper".equals(library.getArtifactId()))
                            .anyMatch(library -> VersionNumber.asVersion(library.getVersion()).compareTo(VersionNumber.asVersion("1.13")) < 0);
        }
    },
    // Minecraft>=1.13 may crash when generating world on Java [1.8,1.8.0_51)
    VANILLA_JAVA_8_51(HMCLJavaVersion.RULE_SUGGESTED, versionRange("1.13", HMCLJavaVersion.MAX), versionRange("1.8.0_51", HMCLJavaVersion.MAX)),

    ;

    private final int type;
    private final Range<VersionNumber> gameVersion;
    private final Range<VersionNumber> javaVersion;

    HMCLJavaVersion(int type, Range<VersionNumber> gameVersion, Range<VersionNumber> javaVersion) {
        this.type = type;
        this.gameVersion = gameVersion;
        this.javaVersion = javaVersion;
    }

    public boolean test(Version version) {
        return true;
    }

    @Nullable
    public static JavaVersion findSuitableJavaVersion(VersionNumber gameVersion, Version version) throws InterruptedException {
        Range<VersionNumber> mandatoryJavaRange = versionRange(MIN, MAX);
        Range<VersionNumber> suggestedJavaRange = versionRange(MIN, MAX);
        for (HMCLJavaVersion java : values()) {
            if (java.gameVersion.contains(gameVersion) && java.test(version)) {
                if (java.type == RULE_MANDATORY) {
                    mandatoryJavaRange = mandatoryJavaRange.intersectionWith(java.javaVersion);
                    suggestedJavaRange = suggestedJavaRange.intersectionWith(java.javaVersion);
                } else if (java.type == RULE_SUGGESTED) {
                    suggestedJavaRange = suggestedJavaRange.intersectionWith(java.javaVersion);
                }
            }
        }

        JavaVersion mandatory = null;
        JavaVersion suggested = null;
        for (JavaVersion javaVersion : JavaVersion.getJavas()) {
            // select the latest java version that this version accepts.
            if (mandatoryJavaRange.contains(javaVersion.getVersionNumber())) {
                if (mandatory == null) mandatory = javaVersion;
                else if (javaVersion.getVersionNumber().compareTo(mandatory.getVersionNumber()) > 0) {
                    mandatory = javaVersion;
                }
            }
            if (suggestedJavaRange.contains(javaVersion.getVersionNumber())) {
                if (suggested == null) suggested = javaVersion;
                else if (javaVersion.getVersionNumber().compareTo(suggested.getVersionNumber()) > 0) {
                    suggested = javaVersion;
                }
            }
        }

        if (suggested != null) return suggested;
        else return mandatory;
    }

    public static final int RULE_MANDATORY = 1;
    public static final int RULE_SUGGESTED = 2;

    public static final String MIN = "0";
    public static final String MAX = "10000";

    private static Range<VersionNumber> versionRange(String fromInclusive, String toExclusive) {
        return Range.between(VersionNumber.asVersion(fromInclusive), VersionNumber.asVersion(toExclusive));
    }
}
