/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// An immutable path value that uses `/` as its canonical separator on every platform.
///
/// This class performs lexical path operations without consulting the current operating
/// system or the real file system. It can represent relative paths, `/`-rooted paths,
/// drive-rooted paths such as `C:/Users`, and UNC-like paths such as `//server/share/file`.
///
/// The Gson representation is the canonical string returned by [#toString()].
///
/// @author Glavo
@JsonAdapter(PortablePath.Adapter.class)
@JsonSerializable
@NotNullByDefault
public final class PortablePath implements Comparable<PortablePath> {
    /// The canonical separator used by portable paths.
    public static final char SEPARATOR = '/';

    /// The empty relative path.
    public static final PortablePath EMPTY = new PortablePath("");

    /// The canonical portable path string.
    private final String path;

    /// Whether this path has a portable root.
    private final boolean absolute;

    /// Creates a path from one or more path strings.
    ///
    /// @param first the first path string
    /// @param more additional path strings to resolve against `first`
    /// @return the canonical portable path
    public static PortablePath of(String first, String... more) {
        PortablePath result = of(first);
        for (String part : more) {
            result = result.resolve(part);
        }
        return result;
    }

    /// Creates a path from a path string.
    ///
    /// Both `/` and `\` are accepted as separators. Repeated separators are collapsed,
    /// except for the leading `//` of UNC-like roots.
    ///
    /// @param path the path string
    /// @return the canonical portable path
    public static PortablePath of(String path) {
        String canonical = canonicalize(path);
        return canonical.isEmpty() ? EMPTY : new PortablePath(canonical);
    }

    /// Creates a portable path from a path on the default JVM file system.
    ///
    /// @param path the path to convert
    /// @return the portable representation of `path`
    /// @throws IllegalArgumentException if `path` belongs to a non-default file system
    public static PortablePath fromPath(Path path) {
        Objects.requireNonNull(path);
        if (path.getFileSystem() != FileSystems.getDefault()) {
            throw new IllegalArgumentException("Unsupported file system: " + path.getFileSystem());
        }

        return of(path.toString());
    }

    /// Creates a portable path from a canonical path string.
    ///
    /// @param path the canonical path string
    private PortablePath(String path) {
        this.path = path;
        this.absolute = rootLength(path) > 0;
    }

    /// Returns the canonical portable path string.
    ///
    /// @return the canonical portable path string
    public String getPath() {
        return path;
    }

    /// Returns whether this path is the empty relative path.
    ///
    /// @return whether this path is empty
    public boolean isEmpty() {
        return path.isEmpty();
    }

    /// Returns whether this path has a portable root.
    ///
    /// @return whether this path is absolute
    public boolean isAbsolute() {
        return absolute;
    }

    /// Returns whether this path has no portable root.
    ///
    /// @return whether this path is relative
    public boolean isRelative() {
        return !absolute;
    }

    /// Returns whether this path consists only of its root.
    ///
    /// @return whether this path is a root path
    public boolean isRoot() {
        return absolute && path.length() == rootLength(path);
    }

    /// Returns the root of this path.
    ///
    /// @return the root path, or `null` if this path is relative
    public @Nullable PortablePath getRoot() {
        int rootLength = rootLength(path);
        return rootLength == 0 ? null : of(path.substring(0, rootLength));
    }

    /// Returns the parent of this path.
    ///
    /// @return the parent path, or `null` if no parent exists
    public @Nullable PortablePath getParent() {
        if (path.isEmpty() || isRoot()) {
            return null;
        }

        int rootLength = rootLength(path);
        int lastSeparator = path.lastIndexOf(SEPARATOR);
        if (lastSeparator < rootLength) {
            return rootLength == 0 ? null : of(path.substring(0, rootLength));
        }
        if (lastSeparator == rootLength - 1) {
            return of(path.substring(0, rootLength));
        }
        return of(path.substring(0, lastSeparator));
    }

    /// Returns the final name element of this path.
    ///
    /// @return the final name element as a relative path, or `null` if this path has no name
    public @Nullable PortablePath getFileName() {
        if (path.isEmpty() || isRoot()) {
            return null;
        }

        int lastSeparator = path.lastIndexOf(SEPARATOR);
        return of(lastSeparator < rootLength(path) ? path.substring(rootLength(path)) : path.substring(lastSeparator + 1));
    }

    /// Returns the number of name elements in this path.
    ///
    /// @return the number of name elements
    public int getNameCount() {
        return names().size();
    }

    /// Returns a name element as a relative path.
    ///
    /// @param index the zero-based name index
    /// @return the name element
    /// @throws IndexOutOfBoundsException if `index` is out of range
    public PortablePath getName(int index) {
        return of(names().get(index));
    }

    /// Returns all name elements in this path.
    ///
    /// @return an immutable list of name elements
    public @Unmodifiable List<String> getNames() {
        return List.copyOf(names());
    }

    /// Resolves another path against this path.
    ///
    /// @param other the other path
    /// @return `other` when it is absolute; otherwise `other` appended to this path
    public PortablePath resolve(PortablePath other) {
        Objects.requireNonNull(other);

        if (other.isAbsolute() || isEmpty()) {
            return other;
        }
        if (other.isEmpty()) {
            return this;
        }
        if (path.endsWith("/")) {
            return of(path + other.path);
        }
        return of(path + SEPARATOR + other.path);
    }

    /// Resolves another path string against this path.
    ///
    /// @param other the other path string
    /// @return the resolved path
    public PortablePath resolve(String other) {
        return resolve(of(other));
    }

    /// Resolves another path against this path's parent.
    ///
    /// @param other the other path
    /// @return the resolved sibling path
    public PortablePath resolveSibling(PortablePath other) {
        Objects.requireNonNull(other);

        @Nullable PortablePath parent = getParent();
        return parent == null ? other : parent.resolve(other);
    }

    /// Resolves another path string against this path's parent.
    ///
    /// @param other the other path string
    /// @return the resolved sibling path
    public PortablePath resolveSibling(String other) {
        return resolveSibling(of(other));
    }

    /// Returns a path with redundant `.` and `..` name elements removed lexically.
    ///
    /// Leading `..` elements are preserved for relative paths. `..` elements that would
    /// escape an absolute root are discarded.
    ///
    /// @return the normalized path
    public PortablePath normalize() {
        if (path.isEmpty()) {
            return this;
        }

        String root = root();
        ArrayList<String> normalized = new ArrayList<>();
        for (String name : names()) {
            if (name.equals(".")) {
                continue;
            }
            if (name.equals("..")) {
                if (!normalized.isEmpty() && !normalized.get(normalized.size() - 1).equals("..")) {
                    normalized.remove(normalized.size() - 1);
                } else if (root.isEmpty()) {
                    normalized.add(name);
                }
            } else {
                normalized.add(name);
            }
        }

        return of(assemble(root, normalized));
    }

    /// Relativizes another path against this path lexically.
    ///
    /// @param other the target path
    /// @return a relative path from this path to `other`
    /// @throws IllegalArgumentException if the two paths have different roots
    public PortablePath relativize(PortablePath other) {
        Objects.requireNonNull(other);

        String root = root();
        if (!root.equals(other.root())) {
            throw new IllegalArgumentException("Cannot relativize paths with different roots: " + this + ", " + other);
        }

        List<String> baseNames = names();
        List<String> otherNames = other.names();
        int common = 0;
        while (common < baseNames.size()
                && common < otherNames.size()
                && baseNames.get(common).equals(otherNames.get(common))) {
            common++;
        }

        ArrayList<String> result = new ArrayList<>();
        for (int i = common; i < baseNames.size(); i++) {
            result.add("..");
        }
        result.addAll(otherNames.subList(common, otherNames.size()));
        return of(assemble("", result));
    }

    /// Returns whether this path starts with another path on a name boundary.
    ///
    /// @param other the path prefix
    /// @return whether this path starts with `other`
    public boolean startsWith(PortablePath other) {
        Objects.requireNonNull(other);
        return matchesPrefix(path, other.path);
    }

    /// Returns whether this path starts with another path string on a name boundary.
    ///
    /// @param other the path prefix string
    /// @return whether this path starts with `other`
    public boolean startsWith(String other) {
        return startsWith(of(other));
    }

    /// Returns whether this path ends with another path on a name boundary.
    ///
    /// @param other the path suffix
    /// @return whether this path ends with `other`
    public boolean endsWith(PortablePath other) {
        Objects.requireNonNull(other);
        if (other.isAbsolute()) {
            return equals(other);
        }
        return matchesSuffix(path, other.path);
    }

    /// Returns whether this path ends with another path string on a name boundary.
    ///
    /// @param other the path suffix string
    /// @return whether this path ends with `other`
    public boolean endsWith(String other) {
        return endsWith(of(other));
    }

    /// Returns this path using the current platform's file separator.
    ///
    /// @return the platform-specific path string
    public String toNativeString() {
        return File.separatorChar == SEPARATOR ? path : path.replace(SEPARATOR, File.separatorChar);
    }

    /// Converts this path to a [Path] on the default JVM file system.
    ///
    /// @return the converted path
    public Path toPath() {
        return Path.of(toNativeString());
    }

    /// Resolves this portable path against a base path when it is relative.
    ///
    /// @param base the base path on the default JVM file system
    /// @return this path converted to [Path], or `base.resolve(toPath())` when this path is relative
    public Path resolveAgainst(Path base) {
        Objects.requireNonNull(base);
        return isAbsolute() ? toPath() : base.resolve(toPath());
    }

    /// Compares this path with another path by canonical string.
    ///
    /// @param other the other path
    /// @return the comparison result
    @Override
    public int compareTo(PortablePath other) {
        return path.compareTo(other.path);
    }

    /// Returns whether this path equals another object.
    ///
    /// @param object the object to compare
    /// @return whether the object is an equal portable path
    @Override
    public boolean equals(@Nullable Object object) {
        return object instanceof PortablePath other && path.equals(other.path);
    }

    /// Returns the hash code of this path.
    ///
    /// @return the hash code
    @Override
    public int hashCode() {
        return path.hashCode();
    }

    /// Returns the canonical portable path string.
    ///
    /// @return the canonical portable path string
    @Override
    public String toString() {
        return path;
    }

    /// Returns the root string of this path.
    private String root() {
        return path.substring(0, rootLength(path));
    }

    /// Returns the name elements of this path.
    private List<String> names() {
        int rootLength = rootLength(path);
        if (rootLength == path.length()) {
            return List.of();
        }

        String names = path.substring(rootLength);
        if (names.isEmpty()) {
            return List.of();
        }

        ArrayList<String> result = new ArrayList<>();
        int start = 0;
        while (start <= names.length()) {
            int end = names.indexOf(SEPARATOR, start);
            if (end < 0) {
                result.add(names.substring(start));
                break;
            }
            result.add(names.substring(start, end));
            start = end + 1;
        }
        return result;
    }

    /// Canonicalizes a path string.
    private static String canonicalize(String path) {
        Objects.requireNonNull(path);
        if (path.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Path contains NUL character: " + path);
        }
        if (path.isEmpty()) {
            return "";
        }

        String value = path.replace('\\', SEPARATOR);
        StringBuilder builder = new StringBuilder(value.length() + 1);
        int index = appendRoot(builder, value);
        boolean previousSeparator = builder.length() > 0 && builder.charAt(builder.length() - 1) == SEPARATOR;

        while (index < value.length()) {
            char ch = value.charAt(index++);
            if (ch == SEPARATOR) {
                if (!previousSeparator) {
                    builder.append(SEPARATOR);
                    previousSeparator = true;
                }
            } else {
                builder.append(ch);
                previousSeparator = false;
            }
        }

        trimTrailingSeparators(builder);
        ensureCanonicalRoot(builder);
        return builder.toString();
    }

    /// Appends the root of `value` and returns the first unconsumed character index.
    private static int appendRoot(StringBuilder builder, String value) {
        if (hasDrivePrefix(value)) {
            builder.append(value.charAt(0)).append(':');
            if (value.length() == 2) {
                builder.append(SEPARATOR);
                return 2;
            }

            builder.append(SEPARATOR);
            int index = value.charAt(2) == SEPARATOR ? 3 : 2;
            while (index < value.length() && value.charAt(index) == SEPARATOR) {
                index++;
            }
            return index;
        }

        if (value.startsWith("//") && value.length() > 2 && value.charAt(2) != SEPARATOR) {
            builder.append("//");
            int index = 2;
            while (index < value.length() && value.charAt(index) == SEPARATOR) {
                index++;
            }
            return index;
        }

        if (value.charAt(0) == SEPARATOR) {
            builder.append(SEPARATOR);
            int index = 1;
            while (index < value.length() && value.charAt(index) == SEPARATOR) {
                index++;
            }
            return index;
        }

        return 0;
    }

    /// Removes trailing separators while preserving the root.
    private static void trimTrailingSeparators(StringBuilder builder) {
        while (builder.length() > rootLength(builder) && builder.charAt(builder.length() - 1) == SEPARATOR) {
            builder.setLength(builder.length() - 1);
        }
    }

    /// Ensures drive and UNC roots use their canonical spelling.
    private static void ensureCanonicalRoot(StringBuilder builder) {
        if (builder.length() == 2 && hasDrivePrefix(builder)) {
            builder.append(SEPARATOR);
            return;
        }

        if (startsWith(builder, "//")) {
            int serverEnd = indexOf(builder, SEPARATOR, 2);
            if (serverEnd < 0 || serverEnd == builder.length() - 1) {
                return;
            }

            int shareEnd = indexOf(builder, SEPARATOR, serverEnd + 1);
            if (shareEnd < 0) {
                builder.append(SEPARATOR);
            }
        }
    }

    /// Assembles a path from a root and name elements.
    private static String assemble(String root, List<String> names) {
        if (names.isEmpty()) {
            return root;
        }

        StringBuilder builder = new StringBuilder(root);
        if (!root.isEmpty() && builder.charAt(builder.length() - 1) != SEPARATOR) {
            builder.append(SEPARATOR);
        }

        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                builder.append(SEPARATOR);
            }
            builder.append(names.get(i));
        }
        return builder.toString();
    }

    /// Returns whether a string starts with a drive prefix.
    private static boolean hasDrivePrefix(CharSequence path) {
        return path.length() >= 2
                && path.charAt(1) == ':'
                && isAsciiLetter(path.charAt(0));
    }

    /// Returns whether a character is an ASCII letter.
    private static boolean isAsciiLetter(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
    }

    /// Returns the root length of a canonical path string.
    private static int rootLength(CharSequence path) {
        if (hasDrivePrefix(path)) {
            return path.length() >= 3 && path.charAt(2) == SEPARATOR ? 3 : 2;
        }

        if (startsWith(path, "//")) {
            int serverEnd = indexOf(path, SEPARATOR, 2);
            if (serverEnd < 0) {
                return path.length();
            }

            int shareEnd = indexOf(path, SEPARATOR, serverEnd + 1);
            return shareEnd < 0 ? path.length() : shareEnd + 1;
        }

        return path.length() > 0 && path.charAt(0) == SEPARATOR ? 1 : 0;
    }

    /// Returns whether `value` starts with `prefix`.
    private static boolean startsWith(CharSequence value, String prefix) {
        if (value.length() < prefix.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (value.charAt(i) != prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /// Finds a character in a character sequence.
    private static int indexOf(CharSequence value, char ch, int start) {
        for (int i = start; i < value.length(); i++) {
            if (value.charAt(i) == ch) {
                return i;
            }
        }
        return -1;
    }

    /// Returns whether `path` starts with `prefix` on a name boundary.
    private static boolean matchesPrefix(String path, String prefix) {
        if (prefix.isEmpty()) {
            return path.isEmpty();
        }
        return path.equals(prefix)
                || path.startsWith(prefix)
                && (prefix.endsWith("/") || path.charAt(prefix.length()) == SEPARATOR);
    }

    /// Returns whether `path` ends with `suffix` on a name boundary.
    private static boolean matchesSuffix(String path, String suffix) {
        if (suffix.isEmpty()) {
            return path.isEmpty();
        }
        return path.equals(suffix)
                || path.endsWith(suffix)
                && path.charAt(path.length() - suffix.length() - 1) == SEPARATOR;
    }

    /// Gson adapter that serializes portable paths as canonical strings.
    @NotNullByDefault
    public static final class Adapter extends TypeAdapter<@Nullable PortablePath> {
        /// Writes a portable path as its canonical string, or JSON null when the value is null.
        @Override
        public void write(JsonWriter out, @Nullable PortablePath value) throws IOException {
            out.value(value != null ? value.path : null);
        }

        /// Reads a portable path from a canonical string or JSON null.
        @Override
        public @Nullable PortablePath read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            if (in.peek() != JsonToken.STRING) {
                throw new JsonParseException("Portable path must be a string: " + in.peek());
            }

            try {
                return PortablePath.of(in.nextString());
            } catch (IllegalArgumentException e) {
                throw new JsonParseException("Invalid portable path", e);
            }
        }
    }
}
