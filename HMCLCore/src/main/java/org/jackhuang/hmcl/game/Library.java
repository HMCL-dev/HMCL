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

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.Constants;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.gson.TolerableValidationException;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A class that describes a Minecraft dependency.
 *
 * @author huangyuhui
 */
@Immutable
public class Library implements Comparable<Library>, Validation {
    /**
     * <p>A possible native descriptors can be: [variant-]os[-key]</p>
     *
     * <p>
     * Variant can be empty string, 'native', or 'natives'.
     * Key can be empty string, system arch, or system arch bit count.
     * </p>
     */
    private static final String[] POSSIBLE_NATIVE_DESCRIPTORS;

    static {
        String[] keys = {
                "",
                Architecture.SYSTEM_ARCH.name().toLowerCase(Locale.ROOT),
                Architecture.SYSTEM_ARCH.getBits().getBit()
        };
        String[] variants = {"", "native", "natives"};

        POSSIBLE_NATIVE_DESCRIPTORS = new String[keys.length * variants.length];
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            for (int j = 0; j < variants.length; j++) {
                if (!variants[j].isEmpty()) {
                    builder.append(variants[j]).append('-');
                }
                builder.append(OperatingSystem.CURRENT_OS.getMojangName());
                if (!keys[i].isEmpty()) {
                    builder.append('-').append(keys[i]);
                }

                POSSIBLE_NATIVE_DESCRIPTORS[i * variants.length + j] = builder.toString();
                builder.setLength(0);
            }
        }
    }

    @SerializedName("name")
    private final Artifact artifact;
    private final String url;
    private final LibrariesDownloadInfo downloads;
    private final ExtractRules extract;
    private final Map<String, String> natives;
    private final List<CompatibilityRule> rules;
    private final List<String> checksums;

    @SerializedName(value = "hint", alternate = {"MMC-hint"})
    private final String hint;

    @SerializedName(value = "filename", alternate = {"MMC-filename"})
    private final String fileName;

    public Library(Artifact artifact) {
        this(artifact, null, null);
    }

    public Library(Artifact artifact, String url, LibrariesDownloadInfo downloads) {
        this(artifact, url, downloads, null, null, null, null, null, null);
    }

    public Library(Artifact artifact, String url, LibrariesDownloadInfo downloads, List<String> checksums, ExtractRules extract, Map<String, String> natives, List<CompatibilityRule> rules, String hint, String filename) {
        this.artifact = artifact;
        this.url = url;
        this.downloads = downloads;
        this.extract = extract;
        this.natives = natives;
        this.rules = rules;
        this.checksums = checksums;
        this.hint = hint;
        this.fileName = filename;
    }

    public String getGroupId() {
        return artifact.getGroup();
    }

    public String getArtifactId() {
        return artifact.getName();
    }

    public String getName() {
        return artifact.toString();
    }

    public String getVersion() {
        return artifact.getVersion();
    }

    public String getClassifier() {
        if (artifact.getClassifier() == null) {
            if (natives != null) {
                for (String nativeDescriptor : POSSIBLE_NATIVE_DESCRIPTORS) {
                    String nd = natives.get(nativeDescriptor);
                    if (nd != null) {
                        return nd.replace("${arch}", Architecture.SYSTEM_ARCH.getBits().getBit());
                    }
                }
            } else if (downloads != null && downloads.getClassifiers() != null) {
                for (String nativeDescriptor : POSSIBLE_NATIVE_DESCRIPTORS) {
                    LibraryDownloadInfo info = downloads.getClassifiers().get(nativeDescriptor);
                    if (info != null) {
                        return nativeDescriptor;
                    }
                }
            }

            return null;
        } else {
            return artifact.getClassifier();
        }
    }

    public ExtractRules getExtract() {
        return extract == null ? ExtractRules.EMPTY : extract;
    }

    public boolean appliesToCurrentEnvironment() {
        return CompatibilityRule.appliesToCurrentEnvironment(rules);
    }

    public boolean isNative() {
        if (!appliesToCurrentEnvironment()) {
            return false;
        }
        if (natives != null) {
            return true;
        }

        return downloads != null && downloads.getClassifiers().keySet().stream().anyMatch(s -> s.startsWith("native"));
    }

    public LibraryDownloadInfo getRawDownloadInfo() {
        if (downloads != null) {
            if (isNative())
                return downloads.getClassifiers().get(getClassifier());
            else
                return downloads.getArtifact();
        } else {
            return null;
        }
    }

    public Artifact getArtifact() {
        return artifact;
    }

    public String getPath() {
        LibraryDownloadInfo temp = getRawDownloadInfo();
        if (temp != null && temp.getPath() != null)
            return temp.getPath();
        else
            return artifact.setClassifier(getClassifier()).getPath();
    }

    public LibraryDownloadInfo getDownload() {
        LibraryDownloadInfo temp = getRawDownloadInfo();
        String path = getPath();
        return new LibraryDownloadInfo(path,
                computePath(temp, path),
                temp != null ? temp.getSha1() : null,
                temp != null ? temp.getSize() : 0
        );
    }

    private String computePath(LibraryDownloadInfo raw, String path) {
        if (raw != null) {
            String url = raw.getUrl();
            if (url != null) {
                return url;
            }
        }

        String repo = Lang.requireNonNullElse(url, Constants.DEFAULT_LIBRARY_URL);
        if (!repo.endsWith("/")) {
            repo += '/';
        }

        return repo + path;
    }

    public boolean hasDownloadURL() {
        LibraryDownloadInfo temp = getRawDownloadInfo();
        if (temp != null) return temp.getUrl() != null;
        else return url != null;
    }

    public List<String> getChecksums() {
        return checksums;
    }

    public List<CompatibilityRule> getRules() {
        return rules;
    }

    /**
     * Hint for how to locate the library file.
     *
     * @return null for default, "local" for location in version/&lt;version&gt;/libraries/filename
     */
    @Nullable
    public String getHint() {
        return hint;
    }

    public Library withoutCommunityFields() {
        return new Library(artifact, url, downloads, checksums, extract, natives, rules, null, null);
    }

    /**
     * Available when hint is "local"
     *
     * @return the filename of the local library in version/&lt;version&gt;/libraries/$filename
     */
    @Nullable
    public String getFileName() {
        return fileName;
    }

    public boolean is(String groupId, String artifactId) {
        return getGroupId().equals(groupId) && getArtifactId().equals(artifactId);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("name", getName()).toString();
    }

    @Override
    public int compareTo(Library o) {
        if (getName().compareTo(o.getName()) == 0)
            return Boolean.compare(isNative(), o.isNative());
        else
            return getName().compareTo(o.getName());
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Library))
            return false;

        Library other = (Library) obj;
        return getName().equals(other.getName()) && (isNative() == other.isNative());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), isNative());
    }

    public Library setClassifier(String classifier) {
        return new Library(artifact.setClassifier(classifier), url, downloads, checksums, extract, natives, rules, hint, fileName);
    }

    @Override
    public void validate() throws JsonParseException, TolerableValidationException {
        if (artifact == null)
            throw new JsonParseException("Library.name cannot be null");
    }
}
