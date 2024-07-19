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

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Glavo
 */
public abstract class GameVersionNumber implements Comparable<GameVersionNumber> {

    public static String[] getDefaultGameVersions() {
        return Versions.DEFAULT_GAME_VERSIONS;
    }

    public static GameVersionNumber asGameVersion(String version) {
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

                if (version.startsWith("1."))
                    return Release.parse(version);

                if (version.length() == 6 && version.charAt(2) == 'w')
                    return Snapshot.parse(version);
            }
        } catch (IllegalArgumentException ignore) {
        }

        Special special = Versions.SPECIALS.get(version);
        if (special == null) {
            special = new Special(version);
        }
        return special;
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

    GameVersionNumber(String value) {
        this.value = value;
    }

    enum Type {
        PRE_CLASSIC, CLASSIC, INFDEV, ALPHA, BETA, NEW
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

    @Override
    public String toString() {
        return value;
    }

    static final class Old extends GameVersionNumber {
        static Old parse(String value) {
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
                    if (!value.startsWith("inf-")) {
                        throw new IllegalArgumentException(value);
                    }
                    type = Type.INFDEV;
                    prefixLength = "inf-".length();
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
            super(value);
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
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Old)) return false;
            Old other = (Old) o;
            return type == other.type && this.versionNumber.compareTo(other.versionNumber) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, versionNumber.hashCode());
        }
    }

    static final class Release extends GameVersionNumber {

        private static final Pattern PATTERN = Pattern.compile("1\\.(?<minor>[0-9]+)(\\.(?<patch>[0-9]+))?((?<eaType>(-[a-zA-Z]+| Pre-Release ))(?<eaVersion>.+))?");

        static final int TYPE_GA = Integer.MAX_VALUE;

        static final int TYPE_UNKNOWN = 0;
        static final int TYPE_EXP = 1;
        static final int TYPE_PRE = 2;
        static final int TYPE_RC = 3;

        static final Release ZERO = new Release("0.0", 0, 0, 0, TYPE_GA, VersionNumber.ZERO);

        static Release parse(String value) {
            Matcher matcher = PATTERN.matcher(value);
            if (!matcher.matches()) {
                throw new IllegalArgumentException(value);
            }

            int minor = Integer.parseInt(matcher.group("minor"));

            String patchString = matcher.group("patch");
            int patch = patchString != null ? Integer.parseInt(patchString) : 0;

            String eaTypeString = matcher.group("eaType");
            int eaType;
            if (eaTypeString == null) {
                eaType = TYPE_GA;
            } else if ("-pre".equals(eaTypeString) || " Pre-Release ".equals(eaTypeString)) {
                eaType = TYPE_PRE;
            } else if ("-rc".equals(eaTypeString)) {
                eaType = TYPE_RC;
            } else if ("-exp".equals(eaTypeString)) {
                eaType = TYPE_EXP;
            } else {
                eaType = TYPE_UNKNOWN;
            }

            String eaVersionString = matcher.group("eaVersion");
            VersionNumber eaVersion = eaVersionString != null ? VersionNumber.asVersion(eaVersionString) : VersionNumber.ZERO;

            return new Release(value, 1, minor, patch, eaType, eaVersion);
        }

        private final int major;
        private final int minor;
        private final int patch;

        private final int eaType;
        private final VersionNumber eaVersion;

        Release(String value, int major, int minor, int patch, int eaType, VersionNumber eaVersion) {
            super(value);
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.eaType = eaType;
            this.eaVersion = eaVersion;
        }

        @Override
        Type getType() {
            return Type.NEW;
        }

        int compareToRelease(Release other) {
            int c = Integer.compare(this.major, other.major);
            if (c != 0) {
                return c;
            }

            c = Integer.compare(this.minor, other.minor);
            if (c != 0) {
                return c;
            }

            c = Integer.compare(this.patch, other.patch);
            if (c != 0) {
                return c;
            }

            c = Integer.compare(this.eaType, other.eaType);
            if (c != 0) {
                return c;
            }

            return this.eaVersion.compareTo(other.eaVersion);
        }

        int compareToSnapshot(Snapshot other) {
            int idx = Arrays.binarySearch(Versions.SNAPSHOT_INTS, other.intValue);
            if (idx >= 0)
                return this.compareToRelease(Versions.SNAPSHOT_PREV[idx]) <= 0 ? -1 : 1;

            idx = -(idx + 1);
            if (idx == Versions.SNAPSHOT_INTS.length)
                return -1;

            return this.compareToRelease(Versions.SNAPSHOT_PREV[idx]) <= 0 ? -1 : 1;
        }

        @Override
        int compareToImpl(@NotNull GameVersionNumber other) {
            if (other instanceof Release)
                return compareToRelease((Release) other);

            if (other instanceof Snapshot)
                return compareToSnapshot((Snapshot) other);

            if (other instanceof Special)
                return -((Special) other).compareToReleaseOrSnapshot(this);

            throw new AssertionError(other.getClass());
        }

        @Override
        public int hashCode() {
            return Objects.hash(major, minor, patch, eaType, eaVersion);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Release other = (Release) o;
            return major == other.major && minor == other.minor && patch == other.patch && eaType == other.eaType && eaVersion.equals(other.eaVersion);
        }
    }

    static final class Snapshot extends GameVersionNumber {
        static Snapshot parse(String value) {
            if (value.length() != 6 || value.charAt(2) != 'w')
                throw new IllegalArgumentException(value);

            int year;
            int week;
            try {
                year = Integer.parseInt(value.substring(0, 2));
                week = Integer.parseInt(value.substring(3, 5));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(value);
            }

            char suffix = value.charAt(5);
            if ((suffix < 'a' || suffix > 'z') && suffix != '~')
                throw new IllegalArgumentException(value);

            return new Snapshot(value, year, week, suffix);
        }

        static int toInt(int year, int week, char suffix) {
            return (year << 16) | (week << 8) | suffix;
        }

        final int intValue;

        Snapshot(String value, int year, int week, char suffix) {
            super(value);
            this.intValue = toInt(year, week, suffix);
        }

        @Override
        Type getType() {
            return Type.NEW;
        }

        @Override
        int compareToImpl(@NotNull GameVersionNumber other) {
            if (other instanceof Release)
                return -((Release) other).compareToSnapshot(this);

            if (other instanceof Snapshot)
                return Integer.compare(this.intValue, ((Snapshot) other).intValue);

            if (other instanceof Special)
                return -((Special) other).compareToReleaseOrSnapshot(this);

            throw new AssertionError(other.getClass());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Snapshot other = (Snapshot) o;
            return this.intValue == other.intValue;
        }

        @Override
        public int hashCode() {
            return intValue;
        }
    }

    static final class Special extends GameVersionNumber {
        private VersionNumber versionNumber;

        private GameVersionNumber prev;

        Special(String value) {
            super(value);
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

            return versionNumber = VersionNumber.asVersion(value);
        }

        GameVersionNumber getPrevNormalVersion() {
            GameVersionNumber v = prev;
            while (v instanceof Special) {
                v = ((Special) v).prev;
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

            if (this.value.equals(other.value))
                return 0;

            int c = this.getPrevNormalVersion().compareTo(other.getPrevNormalVersion());
            if (c != 0)
                return c;

            GameVersionNumber v = prev;
            while (v instanceof Special) {
                if (v == other)
                    return 1;

                v = ((Special) v).prev;
            }

            return -1;
        }

        @Override
        int compareToImpl(@NotNull GameVersionNumber o) {
            if (o instanceof Release)
                return compareToReleaseOrSnapshot(o);

            if (o instanceof Snapshot)
                return compareToReleaseOrSnapshot(o);

            if (o instanceof Special)
                return compareToSpecial((Special) o);

            throw new AssertionError(o.getClass());
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Special other = (Special) o;
            return Objects.equals(this.value, other.value);
        }
    }

    static final class Versions {
        static final HashMap<String, Special> SPECIALS = new HashMap<>();
        static final String[] DEFAULT_GAME_VERSIONS;

        static final int[] SNAPSHOT_INTS;
        static final Release[] SNAPSHOT_PREV;

        static {
            ArrayDeque<String> defaultGameVersions = new ArrayDeque<>(64);

            List<Snapshot> snapshots = new ArrayList<>(1024);
            List<Release> snapshotPrev = new ArrayList<>(1024);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(GameVersionNumber.class.getResourceAsStream("/assets/game/versions.txt"), StandardCharsets.US_ASCII))) {
                Release currentRelease = null;
                GameVersionNumber prev = null;

                for (String line; (line = reader.readLine()) != null; ) {
                    if (line.isEmpty())
                        continue;

                    GameVersionNumber version = GameVersionNumber.asGameVersion(line);

                    if (currentRelease == null)
                        currentRelease = (Release) version;

                    if (version instanceof Snapshot) {
                        Snapshot snapshot = (Snapshot) version;
                        snapshots.add(snapshot);
                        snapshotPrev.add(currentRelease);
                    } else if (version instanceof Release) {
                        currentRelease = (Release) version;

                        if (currentRelease.eaType == Release.TYPE_GA) {
                            defaultGameVersions.addFirst(currentRelease.value);
                        }
                    } else if (version instanceof Special) {
                        Special special = (Special) version;
                        special.prev = prev;
                        SPECIALS.put(special.value, special);
                    } else
                        throw new AssertionError("version: " + version);

                    prev = version;
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
