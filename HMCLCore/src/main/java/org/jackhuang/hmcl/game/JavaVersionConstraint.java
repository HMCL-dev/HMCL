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

import org.apache.commons.lang3.Range;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static org.jackhuang.hmcl.download.LibraryAnalyzer.LAUNCH_WRAPPER_MAIN;

public enum JavaVersionConstraint {

    // Minecraft>=1.17 requires Java 16
    VANILLA_JAVA_16(JavaVersionConstraint.RULE_MANDATORY, versionRange("1.17", JavaVersionConstraint.MAX), versionRange("16", JavaVersionConstraint.MAX)),
    VANILLA_JAVA_17(JavaVersionConstraint.RULE_MANDATORY, versionRange("1.18", JavaVersionConstraint.MAX), versionRange("17", JavaVersionConstraint.MAX)),
    // Minecraft<=1.17.1+Forge[37.0.0,37.0.60) not compatible with Java 17
    MODDED_JAVA_16(JavaVersionConstraint.RULE_SUGGESTED, versionIs("1.17.1"), versionRange("16", "16.999")) {
        @Override
        protected boolean appliesToVersionImpl(VersionNumber gameVersionNumber, @Nullable Version version,
                                               @Nullable JavaVersion javaVersion) {
            if (version == null) return false;
            VersionNumber forgePatchVersion = LibraryAnalyzer.analyze(version)
                    .getVersion(LibraryAnalyzer.LibraryType.FORGE)
                    .map(LibraryAnalyzer.LibraryType.FORGE::patchVersion)
                    .map(VersionNumber::asVersion)
                    .orElse(null);
            return forgePatchVersion != null && forgePatchVersion.compareTo(VersionNumber.asVersion("37.0.60")) < 0;
        }
    },
    // Minecraft>=1.13 requires Java 8
    VANILLA_JAVA_8(JavaVersionConstraint.RULE_MANDATORY, versionRange("1.13", JavaVersionConstraint.MAX), versionRange("1.8", JavaVersionConstraint.MAX)),
    // Minecraft>=1.7.10+Forge accepts Java 8
    MODDED_JAVA_8(JavaVersionConstraint.RULE_SUGGESTED, versionRange("1.7.10", JavaVersionConstraint.MAX), versionRange("1.8", JavaVersionConstraint.MAX)),
    // Minecraft<=1.7.2+Forge requires Java<=7, But LegacyModFixer may fix that problem. So only suggest user using Java 7.
    MODDED_JAVA_7(JavaVersionConstraint.RULE_SUGGESTED, versionRange(JavaVersionConstraint.MIN, "1.7.2"), versionRange(JavaVersionConstraint.MIN, "1.7.999")) {
        @Override
        protected boolean appliesToVersionImpl(VersionNumber gameVersionNumber, @Nullable Version version,
                                               @Nullable JavaVersion javaVersion) {
            if (version == null) return false;
            LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(version);
            return analyzer.has(LibraryAnalyzer.LibraryType.FORGE);
        }
    },
    // LaunchWrapper<=1.12 will crash because LaunchWrapper assumes the system class loader is an instance of URLClassLoader (Java 8)
    LAUNCH_WRAPPER(JavaVersionConstraint.RULE_MANDATORY, versionRange("0", "1.12.999"), versionRange("0", "1.8.999")) {
        @Override
        protected boolean appliesToVersionImpl(VersionNumber gameVersionNumber, @Nullable Version version,
                                               @Nullable JavaVersion javaVersion) {
            if (version == null) return false;
            return LAUNCH_WRAPPER_MAIN.equals(version.getMainClass()) &&
                    version.getLibraries().stream()
                            .filter(library -> "launchwrapper".equals(library.getArtifactId()))
                            .anyMatch(library -> VersionNumber.asVersion(library.getVersion()).compareTo(VersionNumber.asVersion("1.13")) < 0);
        }
    },
    // Minecraft>=1.13 may crash when generating world on Java [1.8,1.8.0_51)
    VANILLA_JAVA_8_51(JavaVersionConstraint.RULE_SUGGESTED, versionRange("1.13", JavaVersionConstraint.MAX), versionRange("1.8.0_51", JavaVersionConstraint.MAX)),
    // Minecraft with suggested java version recorded in game json is restrictedly constrained.
    GAME_JSON(JavaVersionConstraint.RULE_MANDATORY, versionRange(JavaVersionConstraint.MIN, JavaVersionConstraint.MAX), versionRange(JavaVersionConstraint.MIN, JavaVersionConstraint.MAX)) {
        @Override
        protected boolean appliesToVersionImpl(VersionNumber gameVersionNumber, @Nullable Version version,
                                               @Nullable JavaVersion javaVersion) {
            if (version == null) return false;
            // We only checks for 1.7.10 and above, since 1.7.2 with Forge can only run on Java 7, but it is recorded Java 8 in game json, which is not correct.
            return gameVersionNumber.compareTo(VersionNumber.asVersion("1.7.10")) >= 0 && version.getJavaVersion() != null;
        }

        @Override
        public Range<VersionNumber> getJavaVersionRange(Version version) {
            String javaVersion;
            if (Objects.requireNonNull(version.getJavaVersion()).getMajorVersion() >= 9) {
                javaVersion = "" + version.getJavaVersion().getMajorVersion();
            } else {
                javaVersion = "1." + version.getJavaVersion().getMajorVersion();
            }
            return JavaVersionConstraint.versionRange(javaVersion, JavaVersionConstraint.MAX);
        }
    },
    // On Linux, JDK 9+ cannot launch Minecraft<=1.12.2, since JDK 9+ does not accept loading native library built in different arch.
    // For example, JDK 9+ 64-bit cannot load 32-bit lwjgl native library.
    VANILLA_LINUX_JAVA_8(JavaVersionConstraint.RULE_MANDATORY, versionRange("0", "1.12.999"), versionRange(JavaVersionConstraint.MIN, "1.8.999")) {
        @Override
        protected boolean appliesToVersionImpl(VersionNumber gameVersionNumber, @Nullable Version version,
                                               @Nullable JavaVersion javaVersion) {
            return OperatingSystem.CURRENT_OS == OperatingSystem.LINUX
                    && Architecture.SYSTEM_ARCH == Architecture.X86_64
                    && (javaVersion == null || javaVersion.getArchitecture() == Architecture.X86_64);
        }

        @Override
        public boolean checkJava(VersionNumber gameVersionNumber, Version version, JavaVersion javaVersion) {
            return javaVersion.getArchitecture() != Architecture.X86_64 || super.checkJava(gameVersionNumber, version, javaVersion);
        }
    },
    // Minecraft currently does not provide official support for architectures other than x86 and x86-64.
    VANILLA_X86(JavaVersionConstraint.RULE_SUGGESTED, versionRange(JavaVersionConstraint.MIN, JavaVersionConstraint.MAX), versionRange(JavaVersionConstraint.MIN, JavaVersionConstraint.MAX)) {
        @Override
        protected boolean appliesToVersionImpl(VersionNumber gameVersionNumber, @Nullable Version version,
                                               @Nullable JavaVersion javaVersion) {
            if (javaVersion == null || javaVersion.getArchitecture() != Architecture.ARM64)
                return false;

            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS || OperatingSystem.CURRENT_OS == OperatingSystem.OSX)
                return gameVersionNumber.compareTo(VersionNumber.asVersion("1.6")) < 0;

            return false;
        }

        @Override
        public boolean checkJava(VersionNumber gameVersionNumber, Version version, JavaVersion javaVersion) {
            return javaVersion.getArchitecture().isX86();
        }
    },
    // Minecraft 1.16+Forge with crash because JDK-8273826
    MODLAUNCHER_8(JavaVersionConstraint.RULE_SUGGESTED, versionRange("1.16.3", "1.17.1"), versionRange(JavaVersionConstraint.MIN, JavaVersionConstraint.MAX)) {
        @Override
        protected boolean appliesToVersionImpl(VersionNumber gameVersionNumber, @Nullable Version version,
                                               @Nullable JavaVersion javaVersion) {
            if (version == null || javaVersion == null) return false;
            VersionNumber forgePatchVersion = LibraryAnalyzer.analyze(version)
                    .getVersion(LibraryAnalyzer.LibraryType.FORGE)
                    .map(LibraryAnalyzer.LibraryType.FORGE::patchVersion)
                    .map(VersionNumber::asVersion)
                    .orElse(null);
            if (forgePatchVersion == null) {
                return false;
            }
            switch (gameVersionNumber.toString()) {
                case "1.16.3":
                    return forgePatchVersion.compareTo(VersionNumber.asVersion("34.1.27")) >= 0;
                case "1.16.4":
                    return true;
                case "1.16.5":
                    return forgePatchVersion.compareTo(VersionNumber.asVersion("36.2.23")) <= 0;
                case "1.17.1":
                    return versionRange("37.0.60", "37.0.75").contains(forgePatchVersion);
                default:
                    return false;
            }
        }

        @Override
        public boolean checkJava(VersionNumber gameVersionNumber, Version version, JavaVersion javaVersion) {
            int parsedJavaVersion = javaVersion.getParsedVersion();
            if (parsedJavaVersion > 17) {
                return false;
            } else if (parsedJavaVersion == 8) {
                return javaVersion.getVersionNumber().compareTo(VersionNumber.asVersion("1.8.0_321")) < 0;
            } else if (parsedJavaVersion == 11) {
                return javaVersion.getVersionNumber().compareTo(VersionNumber.asVersion("11.0.14")) < 0;
            } else if (parsedJavaVersion == 15) {
                return javaVersion.getVersionNumber().compareTo(VersionNumber.asVersion("15.0.6")) < 0;
            } else if (parsedJavaVersion == 17) {
                return javaVersion.getVersionNumber().compareTo(VersionNumber.asVersion("17.0.2")) < 0;
            } else {
                return true;
            }
        }
    };;

    private final int type;
    private final Range<VersionNumber> gameVersionRange;
    private final Range<VersionNumber> javaVersionRange;

    JavaVersionConstraint(int type, Range<VersionNumber> gameVersionRange, Range<VersionNumber> javaVersionRange) {
        this.type = type;
        this.gameVersionRange = gameVersionRange;
        this.javaVersionRange = javaVersionRange;
    }

    public int getType() {
        return type;
    }

    public Range<VersionNumber> getGameVersionRange() {
        return gameVersionRange;
    }

    public Range<VersionNumber> getJavaVersionRange(Version version) {
        return javaVersionRange;
    }

    public final boolean appliesToVersion(@Nullable VersionNumber gameVersionNumber, @Nullable Version version,
                                          @Nullable JavaVersion javaVersion) {
        return gameVersionRange.contains(gameVersionNumber)
                && appliesToVersionImpl(gameVersionNumber, version, javaVersion);
    }

    protected boolean appliesToVersionImpl(VersionNumber gameVersionNumber, @Nullable Version version,
                                           @Nullable JavaVersion javaVersion) {
        return true;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean checkJava(VersionNumber gameVersionNumber, Version version, JavaVersion javaVersion) {
        return getJavaVersionRange(version).contains(javaVersion.getVersionNumber());
    }

    public static final List<JavaVersionConstraint> ALL = Lang.immutableListOf(values());

    public static VersionRanges findSuitableJavaVersionRange(VersionNumber gameVersion, Version version) {
        Range<VersionNumber> mandatoryJavaRange = versionRange(MIN, MAX);
        Range<VersionNumber> suggestedJavaRange = versionRange(MIN, MAX);
        for (JavaVersionConstraint java : ALL) {
            if (java.appliesToVersion(gameVersion, version, null)) {
                Range<VersionNumber> javaVersionRange = java.getJavaVersionRange(version);
                if (java.type == RULE_MANDATORY) {
                    mandatoryJavaRange = mandatoryJavaRange.intersectionWith(javaVersionRange);
                    suggestedJavaRange = suggestedJavaRange.intersectionWith(javaVersionRange);
                } else if (java.type == RULE_SUGGESTED) {
                    suggestedJavaRange = suggestedJavaRange.intersectionWith(javaVersionRange);
                }
            }
        }
        return new VersionRanges(mandatoryJavaRange, suggestedJavaRange);
    }

    @Nullable
    public static JavaVersion findSuitableJavaVersion(VersionNumber gameVersion, Version version) throws InterruptedException {
        VersionRanges range = findSuitableJavaVersionRange(gameVersion, version);

        boolean forceX86 = Architecture.SYSTEM_ARCH == Architecture.ARM64
                && (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS || OperatingSystem.CURRENT_OS == OperatingSystem.OSX)
                && gameVersion.compareTo(VersionNumber.asVersion("1.6")) < 0;

        JavaVersion mandatory = null;
        JavaVersion suggested = null;
        for (JavaVersion javaVersion : JavaVersion.getJavas()) {
            // select the latest x86 java that this version accepts.
            if (forceX86 && !javaVersion.getArchitecture().isX86())
                continue;

            VersionNumber javaVersionNumber = javaVersion.getVersionNumber();
            if (range.getMandatory().contains(javaVersionNumber)) {
                if (mandatory == null) mandatory = javaVersion;
                else if (compareJavaVersion(javaVersion, mandatory) > 0) {
                    mandatory = javaVersion;
                }
            }
            if (range.getSuggested().contains(javaVersionNumber)) {
                if (suggested == null) suggested = javaVersion;
                else if (compareJavaVersion(javaVersion, suggested) > 0) {
                    suggested = javaVersion;
                }
            }
        }

        if (suggested != null) return suggested;
        else return mandatory;
    }

    private static int compareJavaVersion(JavaVersion javaVersion1, JavaVersion javaVersion2) {
        Architecture arch1 = javaVersion1.getArchitecture();
        Architecture arch2 = javaVersion2.getArchitecture();

        if (arch1 != arch2) {
            if (arch1 == Architecture.X86_64) {
                return 1;
            }
            if (arch2 == Architecture.X86_64) {
                return -1;
            }
        }
        return javaVersion1.getVersionNumber().compareTo(javaVersion2.getVersionNumber());
    }

    public static final int RULE_MANDATORY = 1;
    public static final int RULE_SUGGESTED = 2;

    public static final String MIN = "0";
    public static final String MAX = "10000";

    private static Range<VersionNumber> versionRange(String fromInclusive, String toExclusive) {
        return Range.between(VersionNumber.asVersion(fromInclusive), VersionNumber.asVersion(toExclusive));
    }

    static Range<VersionNumber> versionIs(String version) {
        return Range.is(VersionNumber.asVersion(version));
    }

    public static class VersionRanges {
        private final Range<VersionNumber> mandatory;
        private final Range<VersionNumber> suggested;

        public VersionRanges(Range<VersionNumber> mandatory, Range<VersionNumber> suggested) {
            this.mandatory = mandatory;
            this.suggested = suggested;
        }

        public Range<VersionNumber> getMandatory() {
            return mandatory;
        }

        public Range<VersionNumber> getSuggested() {
            return suggested;
        }
    }
}
