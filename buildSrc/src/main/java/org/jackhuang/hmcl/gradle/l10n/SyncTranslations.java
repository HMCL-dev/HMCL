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
package org.jackhuang.hmcl.gradle.l10n;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;

/// Synchronizes translation files in place with the keys and blank lines of a reference file.
///
/// Existing values, escapes, and continuation lines are retained. Missing keys become
/// `# key=` comments, and obsolete entries are removed. Each translation retains its
/// header; subsequent comments move with the following key, and trailing comments remain
/// at the end. Comments from the reference file are not copied. Output uses UTF-8 and LF.
@NotNullByDefault
@UntrackedTask(because = "Updates translation source files in place")
public abstract class SyncTranslations extends DefaultTask {

    /// Returns the UTF-8 reference file that defines the keys and spacing between entries.
    @InputFile
    public abstract RegularFileProperty getSourceFile();

    /// Returns the UTF-8 translation files to rewrite; the reference file must not be included.
    @InputFiles
    public abstract ConfigurableFileCollection getTranslationFiles();

    /// Reads and validates all inputs, then writes only files whose contents have changed.
    ///
    /// @throws IOException if an input cannot be read or an output cannot be written;
    /// previously written files are not rolled back
    /// @throws GradleException if the reference has duplicate keys or is also a translation
    /// @throws IllegalArgumentException if an input contains a malformed Unicode escape
    @TaskAction
    public void run() throws IOException {
        Path source = getSourceFile().get().getAsFile().toPath();
        Document reference = parse(Files.readString(source));
        var updates = new LinkedHashMap<Path, String>();
        for (var file : getTranslationFiles().getFiles()) {
            Path target = file.toPath();
            if (Files.isSameFile(source, target)) {
                throw new GradleException("The reference file must not be included in translationFiles: " + target);
            }
            String original = Files.readString(target);
            String updated = synchronize(reference, parse(original));
            if (!updated.equals(original)) {
                updates.put(target, updated);
            }
        }
        for (var update : updates.entrySet()) {
            Files.writeString(update.getKey(), update.getValue());
            getLogger().lifecycle("Synchronized {}", update.getKey().getFileName());
        }
    }

    /// Returns a translation arranged by the reference, preserving the last active value
    /// for duplicate translation keys, as [Properties] does.
    ///
    /// @throws GradleException if the reference contains duplicate keys
    static String synchronize(Document reference, Document translation) {
        var values = new HashMap<String, String>();
        var comments = new HashMap<String, String>();
        var pendingComments = new StringBuilder();
        for (Entry entry : translation.entries()) {
            if (entry.key() != null) {
                if (!isComment(entry.text())) {
                    values.put(entry.key(), entry.text());
                }
                comments.merge(entry.key(), pendingComments.toString(), String::concat);
                pendingComments.setLength(0);
            } else if (isComment(entry.text())) {
                pendingComments.append(entry.text());
            }
        }

        var result = new StringBuilder(translation.header());
        var keys = new HashSet<String>();
        for (Entry entry : reference.entries()) {
            @Nullable String key = entry.key();
            if (key == null) {
                if (!isComment(entry.text())) {
                    result.append(entry.text());
                }
                continue;
            }
            if (!keys.add(key)) {
                throw new GradleException("Duplicate reference key: " + key);
            }
            result.append(comments.getOrDefault(key, ""));
            result.append(values.getOrDefault(key, "# " + escapeKey(key) + "=\n"));
        }
        return result.append(pendingComments).toString();
    }

    /// Parses physical lines into entries without re-encoding property values. Canonical
    /// `# key=` placeholders delimit the header just like active entries.
    ///
    /// @throws IOException if the properties reader fails
    /// @throws IllegalArgumentException if a property contains a malformed Unicode escape
    static Document parse(String content) throws IOException {
        @Unmodifiable List<String> lines = content.lines().toList();
        var header = new StringBuilder();
        var entries = new ArrayList<Entry>();
        boolean inHeader = true;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            var text = new StringBuilder(line).append('\n');
            @Nullable String key = null;
            if (isComment(line)) {
                String stripped = stripPropertyWhitespace(line).stripTrailing();
                if (stripped.startsWith("# ") && stripped.endsWith("=")) {
                    Properties placeholder = new Properties();
                    try {
                        placeholder.load(new StringReader(stripped.substring(2)));
                        if (placeholder.size() == 1) {
                            String candidate = placeholder.stringPropertyNames().iterator().next();
                            if (stripped.equals("# " + escapeKey(candidate) + "=")) {
                                key = candidate;
                            }
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Ordinary comments do not have to contain valid property escapes.
                    }
                }
            } else if (!stripPropertyWhitespace(line).isEmpty()) {
                // Only an odd number of trailing backslashes continues a property.
                while (continuesLine(line)) {
                    if (++i == lines.size()) {
                        // Terminate an EOF continuation before moving this entry elsewhere.
                        text.append('\n');
                        break;
                    }
                    line = lines.get(i);
                    text.append(line).append('\n');
                }
                Properties property = new Properties();
                property.load(new StringReader(text.toString()));
                key = property.stringPropertyNames().iterator().next();
            }
            if (key != null) {
                inHeader = false;
            }
            if (inHeader) {
                header.append(text);
            } else {
                entries.add(new Entry(key, text.toString()));
            }
        }
        return new Document(header.toString(), List.copyOf(entries));
    }

    /// Returns whether the first non-whitespace character starts a properties comment.
    private static boolean isComment(String text) {
        String stripped = stripPropertyWhitespace(text);
        return stripped.startsWith("#") || stripped.startsWith("!");
    }

    /// Removes only the leading whitespace recognized by [Properties#load(java.io.Reader)].
    private static String stripPropertyWhitespace(String text) {
        int offset = 0;
        while (offset < text.length()) {
            char ch = text.charAt(offset);
            if (ch != ' ' && ch != '\t' && ch != '\f') {
                break;
            }
            offset++;
        }
        return text.substring(offset);
    }

    /// Returns whether a physical line ends with an unescaped backslash.
    private static boolean continuesLine(String line) {
        int backslashes = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 != 0;
    }

    /// Escapes a decoded key so that removing the placeholder's `# ` prefix yields
    /// a valid property with the original key and an empty value.
    private static String escapeKey(String key) {
        var result = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            switch (ch) {
                case '\t' -> result.append("\\t");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\f' -> result.append("\\f");
                case '\\', ' ', '=', ':', '#', '!' -> result.append('\\').append(ch);
                default -> result.append(ch);
            }
        }
        return result.toString();
    }

    /// Stores an entry's decoded key and its original text with LF line endings.
    ///
    /// @param key the decoded key, or `null` for an ordinary comment or blank line
    /// @param text the complete entry, including continuation lines and its final newline
    @NotNullByDefault
    record Entry(@Nullable String key, String text) {
    }

    /// Stores the leading header and an immutable sequence of entries.
    ///
    /// @param header all lines before the first active entry or canonical placeholder
    /// @param entries the remaining entries in file order
    @NotNullByDefault
    record Document(String header, @Unmodifiable List<Entry> entries) {
    }
}
