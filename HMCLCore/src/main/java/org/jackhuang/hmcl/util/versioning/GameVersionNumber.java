/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.versioning;

import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public abstract sealed class GameVersionNumber implements Comparable<GameVersionNumber> {

    public static String[] getDefaultGameVersions() {
        return Versions.DEFAULT_GAME_VERSIONS;
    }

    public static GameVersionNumber asGameVersion(String version) {
        GameVersionNumber versionNumber = Versions.SPECIALS.get(version);
        if (versionNumber != null)
            return versionNumber;

        try {
            if (!version.isEmpty()) {
                char ch = version.charAt(0);
                switch (ch) {
                    case 'r':
                    case 'a':
                    case 'b':
                    case 'c':
                    case 'i':
                        return Old.parse(version);
                }

                if (version.equals("0.0"))
                    return Release.ZERO;

                if (version.length() >= 6 && version.charAt(2) == 'w')
                    return LegacySnapshot.parse(version);

                return Release.parse(version);
            }
        } catch (Throwable ignore) {
        }

        return new Special(version, version);
    }

    public static GameVersionNumber asGameVersion(Optional<String> version) {
        return version.isPresent() ? asGameVersion(version.get()) : unknown();
    }

    public static GameVersionNumber unknown() {
        return Release.ZERO;
    }

    public static int compare(String version1, String version2) {
        return asGameVersion(version1).compareTo(asGameVersion(version2));
    }

    public static VersionRange<GameVersionNumber> between(String minimum, String maximum) {
        return VersionRange.between(asGameVersion(minimum), asGameVersion(maximum));
    }

    public static VersionRange<GameVersionNumber> atLeast(String minimum) {
        return VersionRange.atLeast(asGameVersion(minimum));
    }

    public static VersionRange<GameVersionNumber> atMost(String maximum) {
        return VersionRange.atMost(asGameVersion(maximum));
    }

    final String value;
    final String normalized;

    GameVersionNumber(String value, String normalized) {
        this.value = value;
        this.normalized = normalized;
    }

    public boolean isAprilFools() {
        if (this instanceof Special) {
            String normalizedVersion = this.toNormalizedString();
            return !normalizedVersion.startsWith("1.") && !normalizedVersion.equals("13w12~")
                    || normalizedVersion.equals("1.RV-Pre1");
        }

        if (this instanceof LegacySnapshot snapshot) {
            return snapshot.intValue == LegacySnapshot.toInt(15, 14, 'a', false);
        }

        return false;
    }

    enum Type {
        PRE_CLASSIC, CLASSIC, INDEV, INFDEV, ALPHA, BETA, NEW
    }

    abstract Type getType();

    abstract int compareToImpl(@NotNull GameVersionNumber other);

    public int compareTo(@NotNull String other) {
        return this.compareTo(asGameVersion(other));
    }

    @Override
    public int compareTo(@NotNull GameVersionNumber other) {
        if (this.getType() != other.getType())
            return Integer.compare(this.getType().ordinal(), other.getType().ordinal());

        return compareToImpl(other);
    }

    /// @see #isAtLeast(String, String, boolean)
    public boolean isAtLeast(@NotNull String releaseVersion, @NotNull String snapshotVersion) {
        return isAtLeast(releaseVersion, snapshotVersion, false);
    }

    /// When comparing between Release Version and Snapshot Version, it is necessary to load `/assets/game/versions.txt` and perform a lookup, which is less efficient.
    /// Therefore, when checking whether a version contains a certain feature, you should use this method and provide both the first release version and the exact snapshot version that introduced the feature,
    /// so that the comparison can be performed quickly without a lookup.
    ///
    /// For example, the datapack feature was introduced in Minecraft 1.13, and more specifically in snapshot `17w43a`.
    /// So you can test whether a game version supports datapacks like this:
    ///
    /// ```java
    /// GameVersionNumber.asVersion("...").isAtLeast("1.13", "17w43a");
    ///```
    ///
    /// @param strictReleaseVersion When `strictReleaseVersion` is `false`, `releaseVersion` is considered less than
    ///                             its corresponding pre/rc versions.
    public boolean isAtLeast(@NotNull String releaseVersion, @NotNull String snapshotVersion, boolean strictReleaseVersion) {
        if (this instanceof Release self) {
            Release other;
            if (strictReleaseVersion) {
                other = Release.parse(releaseVersion);
            } else {
                other = Release.parseSimple(releaseVersion);
            }

            return self.compareToRelease(other) >= 0;
        } else {
            return this.compareTo(LegacySnapshot.parse(snapshotVersion)) >= 0;
        }
    }

    public String toNormalizedString() {
        return normalized;
    }

    @Override
    public String toString() {
        return value;
    }

    protected ToStringBuilder buildDebugString() {
        return new ToStringBuilder(this)
                .append("value", value)
                .append("normalized", normalized)
                .append("type", getType());
    }

    public final String toDebugString() {
        return buildDebugString().toString();
    }

    public static final class Old extends GameVersionNumber {
        static Old parse(String value) {
            if (value.isEmpty())
                throw new IllegalArgumentException("Empty old version number");

            Type type;
            int prefixLength = 1;
            switch (value.charAt(0)) {
                case 'r':
                    if (!value.startsWith("rd-")) {
                        throw new IllegalArgumentException(value);
                    }

                    type = Type.PRE_CLASSIC;
                    prefixLength = "rd-".length();
                    break;
                case 'i':
                    if (value.startsWith("inf-")) {
                        type = Type.INFDEV;
                        prefixLength = "inf-".length();
                    } else if (value.startsWith("in-")) {
                        type = Type.INDEV;
                        prefixLength = "in-".length();
                    } else {
                        throw new IllegalArgumentException(value);
                    }
                    break;
                case 'a':
                    type = Type.ALPHA;
                    break;
                case 'b':
                    type = Type.BETA;
                    break;
                case 'c':
                    type = Type.CLASSIC;
                    break;
                default:
                    throw new IllegalArgumentException(value);
            }

            if (value.length() < prefixLength + 1 || !Character.isDigit(value.charAt(prefixLength)))
                throw new IllegalArgumentException(value);

            return new Old(value, type, VersionNumber.asVersion(value.substring(prefixLength)));
        }

        final Type type;
        final VersionNumber versionNumber;

        private Old(String value, Type type, VersionNumber versionNumber) {
            super(value, value);
            this.type = type;
            this.versionNumber = versionNumber;
        }

        @Override
        Type getType() {
            return type;
        }

        @Override
        int compareToImpl(@NotNull GameVersionNumber other) {
            return this.versionNumber.compareTo(((Old) other).versionNumber);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, versionNumber);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Old that
                    && this.type == that.type
                    && this.versionNumber.equals(that.versionNumber);
        }
    }

    public static final class Release extends GameVersionNumber {
        private static final int MINIMUM_YEAR_MAJOR_VERSION = 25;

        public enum ReleaseType {
            UNKNOWN(""),
            SNAPSHOT("-snapshot-"),
            PRE_RELEASE("-pre"),
            RELEASE_CANDIDATE("-rc"),
            GA("");
            private final String infix;

            ReleaseType(String infix) {
                this.infix = infix;
            }
        }

        public enum Additional {
            NONE(""), UNOBFUSCATED("_unobfuscated");
            private final String suffix;

            Additional(String suffix) {
                this.suffix = suffix;
            }
        }

        static final Release ZERO = new Release(
                "0.0", "0.0",
                0, 0, 0,
                ReleaseType.UNKNOWN, VersionNumber.ZERO, Additional.NONE
        );

        private static final Pattern VERSION_PATTERN = Pattern.compile("(?<prefix>(?<major>1|[1-9]\\d+)\\.(?<minor>\\d+)(\\.(?<patch>[0-9]+))?)(?<suffix>.*)");

        static Release parse(String value) {
            Matcher matcher = VERSION_PATTERN.matcher(value);
            if (!matcher.matches()) {
                throw new IllegalArgumentException(value);
            }

            int major = Integer.parseInt(matcher.group("major"));
            if (major != 1 && major < MINIMUM_YEAR_MAJOR_VERSION)
                throw new IllegalArgumentException(value);

            int minor = Integer.parseInt(matcher.group("minor"));

            String patchString = matcher.group("patch");
            int patch = patchString != null ? Integer.parseInt(patchString) : 0;

            String suffix = matcher.group("suffix");

            ReleaseType releaseType;
            VersionNumber eaVersion;
            Additional additional = Additional.NONE;
            boolean needNormalize = false;

            if (suffix.endsWith("_unobfuscated")) {
                suffix = suffix.substring(0, suffix.length() - "_unobfuscated".length());
                additional = Additional.UNOBFUSCATED;
            } else if (suffix.endsWith(" Unobfuscated")) {
                needNormalize = true;
                suffix = suffix.substring(0, suffix.length() - " Unobfuscated".length());
                additional = Additional.UNOBFUSCATED;
            }

            if (suffix.isEmpty()) {
                releaseType = ReleaseType.GA;
                eaVersion = VersionNumber.ZERO;
            } else if (suffix.startsWith("-snapshot-")) {
                releaseType = ReleaseType.SNAPSHOT;
                eaVersion = VersionNumber.asVersion(suffix.substring("-snapshot-".length()));
            } else if (suffix.startsWith(" Snapshot ")) {
                needNormalize = true;
                releaseType = ReleaseType.SNAPSHOT;
                eaVersion = VersionNumber.asVersion(suffix.substring(" Snapshot ".length()));
            } else if (suffix.startsWith("-pre")) {
                releaseType = ReleaseType.PRE_RELEASE;
                eaVersion = VersionNumber.asVersion(suffix.substring("-pre".length()));
            } else if (suffix.startsWith(" Pre-Release ")) {
                needNormalize = true;
                releaseType = ReleaseType.PRE_RELEASE;
                eaVersion = VersionNumber.asVersion(suffix.substring(" Pre-Release ".length()));
            } else if (suffix.startsWith(" Pre-release ")) {
                // https://github.com/HMCL-dev/HMCL/issues/5476
                needNormalize = true;
                releaseType = ReleaseType.PRE_RELEASE;
                eaVersion = VersionNumber.asVersion(suffix.substring(" Pre-release ".length()));
            } else if (suffix.startsWith("-rc")) {
                releaseType = ReleaseType.RELEASE_CANDIDATE;
                eaVersion = VersionNumber.asVersion(suffix.substring("-rc".length()));
            } else if (suffix.startsWith(" Release Candidate ")) {
                needNormalize = true;
                releaseType = ReleaseType.RELEASE_CANDIDATE;
                eaVersion = VersionNumber.asVersion(suffix.substring(" Release Candidate ".length()));
            } else {
                throw new IllegalArgumentException(value);
            }

            String normalized;
            if (needNormalize) {
                StringBuilder builder = new StringBuilder(value.length());
                builder.append(matcher.group("prefix"));
                if (releaseType != ReleaseType.GA) {
                    builder.append(releaseType.infix);
                    builder.append(eaVersion);
                }
                builder.append(additional.suffix);
                normalized = builder.toString();
            } else {
                normalized = value;
            }

            return new Release(value, normalized, major, minor, patch, releaseType, eaVersion, additional);
        }

        /// Quickly parses a simple format (`[1-9][0-9]+\.[0-9]+(\.[0-9]+)?`) release version.
        /// The returned [#eaType] will be set to [ReleaseType#UNKNOWN], meaning it will be less than all pre/rc and official versions of this version.
        ///
        /// @see GameVersionNumber#isAtLeast(String, String)
        static Release parseSimple(String value) {
            int majorLength = getNumberLength(value, 0);
            if (majorLength == 0 || value.length() < majorLength + 2 || value.charAt(majorLength) != '.')
                throw new IllegalArgumentException(value);

            int major = Integer.parseInt(value.substring(0, majorLength));
            if (major != 1 && major < MINIMUM_YEAR_MAJOR_VERSION)
                throw new IllegalArgumentException(value);

            final int minorOffset = majorLength + 1;

            int minorLength = getNumberLength(value, minorOffset);
            if (minorLength == 0)
                throw new IllegalArgumentException(value);

            try {
                int minor = Integer.parseInt(value.substring(minorOffset, minorOffset + minorLength));
                int patch = 0;

                if (minorOffset + minorLength < value.length()) {
                    int patchOffset = minorOffset + minorLength + 1;

                    if (patchOffset >= value.length() || value.charAt(patchOffset - 1) != '.')
                        throw new IllegalArgumentException(value);

                    patch = Integer.parseInt(value.substring(patchOffset));
                }

                return new Release(value, value, major, minor, patch, ReleaseType.UNKNOWN, VersionNumber.ZERO, Additional.NONE);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(value);
            }
        }

        private static int getNumberLength(String value, int offset) {
            int current = offset;
            while (current < value.length()) {
                char ch = value.charAt(current);
                if (ch < '0' || ch > '9')
                    break;

                current++;
            }

            return current - offset;
        }

        private final int major;
        private final int minor;
        private final int patch;

        private final ReleaseType eaType;
        private final VersionNumber eaVersion;
        private final Additional additional;

        Release(String value, String normalized, int major, int minor, int patch, ReleaseType eaType, VersionNumber eaVersion, Additional additional) {
            super(value, normalized);
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.eaType = eaType;
            this.eaVersion = eaVersion;
            this.additional = additional;
        }

        @Override
        Type getType() {
            return Type.NEW;
        }

        int compareToRelease(Release other) {
            int c = Integer.compare(this.major, other.major);
            if (c != 0)
                return c;

            c = Integer.compare(this.minor, other.minor);
            if (c != 0)
                return c;

            c = Integer.compare(this.patch, other.patch);
            if (c != 0)
                return c;

            c = this.eaType.compareTo(other.eaType);
            if (c != 0)
                return c;

            c = this.eaVersion.compareTo(other.eaVersion);
            if (c != 0)
                return c;

            return this.additional.compareTo(other.additional);
        }

        int compareToSnapshot(LegacySnapshot other) {
            if (major == 0) {
                return -1;
            } else if (major == 1) {
                int idx = Arrays.binarySearch(Versions.SNAPSHOT_INTS, other.intValue);
                if (idx >= 0)
                    return this.compareToRelease(Versions.SNAPSHOT_PREV[idx]) <= 0 ? -1 : 1;

                idx = -(idx + 1);
                if (idx == Versions.SNAPSHOT_INTS.length)
                    return -1;

                return this.compareToRelease(Versions.SNAPSHOT_PREV[idx]) <= 0 ? -1 : 1;
            } else {
                return 1;
            }
        }

        @Override
        int compareToImpl(@NotNull GameVersionNumber other) {
            if (other instanceof Release release)
                return compareToRelease(release);

            if (other instanceof LegacySnapshot snapshot)
                return compareToSnapshot(snapshot);

            if (other instanceof Special special)
                return -special.compareToReleaseOrSnapshot(this);

            throw new AssertionError(other.getClass());
        }

        public int getMajor() {
            return major;
        }

        public int getMinor() {
            return minor;
        }

        public int getPatch() {
            return patch;
        }

        public ReleaseType getEaType() {
            return eaType;
        }

        public VersionNumber getEaVersion() {
            return eaVersion;
        }

        public Additional getAdditional() {
            return additional;
        }

        @Override
        public int hashCode() {
            return Objects.hash(major, minor, patch, eaType, eaVersion, additional);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Release that
                    && this.major == that.major
                    && this.minor == that.minor
                    && this.patch == that.patch
                    && this.eaType == that.eaType
                    && this.eaVersion.equals(that.eaVersion)
                    && this.additional == that.additional;
        }

        @Override
        protected ToStringBuilder buildDebugString() {
            return super.buildDebugString()
                    .append("major", major)
                    .append("minor", minor)
                    .append("patch", patch)
                    .append("eaType", eaType)
                    .append("eaVersion", eaVersion)
                    .append("additional", additional);
        }
    }

    /// Legacy snapshot version numbers like `25w46a`.
    public static final class LegacySnapshot extends GameVersionNumber {
        static LegacySnapshot parse(String value) {
            if (value.length() < 6 || value.charAt(2) != 'w')
                throw new IllegalArgumentException(value);

            int prefixLength;
            boolean unobfuscated;
            String normalized;
            if (value.endsWith("_unobfuscated")) {
                prefixLength = value.length() - "_unobfuscated".length();
                unobfuscated = true;
                normalized = value;
            } else if (value.endsWith(" Unobfuscated")) {
                prefixLength = value.length() - " Unobfuscated".length();
                unobfuscated = true;
                normalized = value.substring(0, prefixLength) + "_unobfuscated";
            } else {
                prefixLength = value.length();
                unobfuscated = false;
                normalized = value;
            }

            if (prefixLength != 6) {
                throw new IllegalArgumentException(value);
            }

            int year;
            int week;
            try {
                year = Integer.parseInt(value.substring(0, 2));
                week = Integer.parseInt(value.substring(3, 5));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(value);
            }

            char suffix = value.charAt(5);
            if (suffix < 'a' || suffix > 'z')
                throw new IllegalArgumentException(value);

            return new LegacySnapshot(value, normalized, year, week, suffix, unobfuscated);
        }

        static int toInt(int year, int week, char suffix, boolean unobfuscated) {
            return (year << 24) | (week << 16) | (suffix << 8) | (unobfuscated ? 1 : 0);
        }

        final int intValue;

        LegacySnapshot(String value, String normalized, int year, int week, char suffix, boolean unobfuscated) {
            super(value, normalized);
            this.intValue = toInt(year, week, suffix, unobfuscated);
        }

        @Override
        Type getType() {
            return Type.NEW;
        }

        @Override
        int compareToImpl(@NotNull GameVersionNumber other) {
            if (other instanceof Release otherRelease)
                return -otherRelease.compareToSnapshot(this);

            if (other instanceof LegacySnapshot otherSnapshot)
                return Integer.compare(this.intValue, otherSnapshot.intValue);

            if (other instanceof Special otherSpecial)
                return -otherSpecial.compareToReleaseOrSnapshot(this);

            throw new AssertionError(other.getClass());
        }

        public int getYear() {
            return (intValue >> 24) & 0xff;
        }

        public int getWeek() {
            return (intValue >> 16) & 0xff;
        }

        public char getSuffix() {
            return (char) ((intValue >> 8) & 0xff);
        }

        public boolean isUnobfuscated() {
            return (intValue & 0b00000001) != 0;
        }

        @Override
        public int hashCode() {
            return intValue;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof LegacySnapshot that && this.intValue == that.intValue;
        }

        @Override
        protected ToStringBuilder buildDebugString() {
            return super.buildDebugString()
                    .append("year", getYear())
                    .append("week", getWeek())
                    .append("suffix", getSuffix())
                    .append("unobfuscated", isUnobfuscated());
        }
    }

    public static final class Special extends GameVersionNumber {
        private VersionNumber versionNumber;

        private GameVersionNumber prev;

        Special(String value, String normalized) {
            super(value, normalized);
        }

        @Override
        Type getType() {
            return Type.NEW;
        }

        boolean isUnknown() {
            return prev == null;
        }

        VersionNumber asVersionNumber() {
            if (versionNumber != null)
                return versionNumber;

            return versionNumber = VersionNumber.asVersion(normalized);
        }

        GameVersionNumber getPrevNormalVersion() {
            GameVersionNumber v = prev;
            while (v instanceof Special special) {
                v = special.prev;
            }

            if (v == null) throw new AssertionError("version: " + value);

            return v;
        }

        int compareToReleaseOrSnapshot(GameVersionNumber other) {
            if (isUnknown()) {
                return 1;
            }

            if (getPrevNormalVersion().compareTo(other) >= 0) {
                return 1;
            }

            return -1;
        }

        int compareToSpecial(Special other) {
            if (this.isUnknown())
                return other.isUnknown() ? this.asVersionNumber().compareTo(other.asVersionNumber()) : 1;

            if (other.isUnknown())
                return -1;

            if (this.normalized.equals(other.normalized))
                return 0;

            int c = this.getPrevNormalVersion().compareTo(other.getPrevNormalVersion());
            if (c != 0)
                return c;

            GameVersionNumber v = prev;
            while (v instanceof Special special) {
                if (v == other)
                    return 1;

                v = special.prev;
            }

            return -1;
        }

        @Override
        int compareToImpl(@NotNull GameVersionNumber o) {
            if (o instanceof Release || o instanceof LegacySnapshot)
                return compareToReleaseOrSnapshot(o);

            if (o instanceof Special special)
                return compareToSpecial(special);

            throw new AssertionError(o.getClass());
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Special that && this.normalized.equals(that.normalized);
        }

        @Override
        public int hashCode() {
            return normalized.hashCode();
        }
    }

    static final class Versions {
        static final HashMap<String, Special> SPECIALS = new HashMap<>();
        static final String[] DEFAULT_GAME_VERSIONS;

        static final int[] SNAPSHOT_INTS;
        static final Release[] SNAPSHOT_PREV;

        static {
            ArrayDeque<String> defaultGameVersions = new ArrayDeque<>(64);

            List<LegacySnapshot> snapshots = new ArrayList<>(1024);
            List<Release> snapshotPrev = new ArrayList<>(1024);

            //noinspection DataFlowIssue
            try (var reader = new BufferedReader(new InputStreamReader(GameVersionNumber.class.getResourceAsStream("/assets/game/versions.txt"), StandardCharsets.US_ASCII))) {
                Release currentRelease = null;
                GameVersionNumber prev = null;

                for (String line; (line = reader.readLine()) != null; ) {
                    if (line.isEmpty())
                        continue;

                    GameVersionNumber version = GameVersionNumber.asGameVersion(line);

                    if (currentRelease == null)
                        currentRelease = (Release) version;

                    if (version instanceof LegacySnapshot snapshot) {
                        snapshots.add(snapshot);
                        snapshotPrev.add(currentRelease);
                    } else if (version instanceof Release release) {
                        currentRelease = release;

                        if (currentRelease.eaType == Release.ReleaseType.GA
                                && currentRelease.additional == Release.Additional.NONE) {
                            defaultGameVersions.addFirst(currentRelease.value);
                        }
                    } else if (version instanceof Special special) {
                        special.prev = prev;
                        SPECIALS.put(special.value, special);
                    } else
                        throw new AssertionError("version: " + version);

                    prev = version;
                }
            } catch (IOException e) {
                throw new AssertionError(e);
            }

            //noinspection DataFlowIssue
            try (var reader = new BufferedReader(new InputStreamReader(GameVersionNumber.class.getResourceAsStream("/assets/game/version-alias.csv"), StandardCharsets.US_ASCII))) {
                for (String line; (line = reader.readLine()) != null; ) {
                    if (line.isEmpty())
                        continue;

                    String[] parts = line.split(",");
                    if (parts.length < 2) {
                        LOG.warning("Invalid line: " + line);
                        continue;
                    }

                    String normalized = parts[0];
                    Special normalizedVersion = SPECIALS.get(normalized);
                    if (normalizedVersion == null) {
                        LOG.warning("Unknown special version: " + normalized);
                        continue;
                    }

                    for (int i = 1; i < parts.length; i++) {
                        String version = parts[i];
                        Special versionNumber = new Special(version, normalized);
                        versionNumber.prev = normalizedVersion.prev;
                        SPECIALS.put(version, versionNumber);
                    }
                }
            } catch (IOException e) {
                throw new AssertionError(e);
            }

            DEFAULT_GAME_VERSIONS = defaultGameVersions.toArray(new String[0]);

            SNAPSHOT_INTS = new int[snapshots.size()];
            for (int i = 0; i < snapshots.size(); i++) {
                SNAPSHOT_INTS[i] = snapshots.get(i).intValue;
            }

            SNAPSHOT_PREV = snapshotPrev.toArray(new Release[SNAPSHOT_INTS.length]);
        }
    }
}
