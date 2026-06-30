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
package org.jackhuang.hmcl.modpack.mcbbs;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.modpack.ModpackManifest;
import org.jackhuang.hmcl.modpack.ModpackProvider;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonSubtype;
import org.jackhuang.hmcl.util.gson.JsonType;
import org.jackhuang.hmcl.util.gson.TolerableValidationException;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.MINECRAFT;

public record McbbsModpackManifest(String manifestType, int manifestVersion, String name, String version, String author,
                                   String description, @Nullable String fileApi, String url, boolean forceUpdate,
                                   @SerializedName("origin") List<Origin> origins, List<Addon> addons,
                                   List<Library> libraries, List<File> files, Settings settings,
                                   LaunchInfo launchInfo) implements ModpackManifest, Validation {
    public static final String MANIFEST_TYPE = "minecraftModpack";

    // sandbox and antiCheating are both not supported.

    public McbbsModpackManifest() {
        this(MANIFEST_TYPE, 1, "", "", "", "", null, "", false, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), new Settings(), new LaunchInfo());
    }

    public McbbsModpackManifest setFiles(List<File> files) {
        return new McbbsModpackManifest(manifestType, manifestVersion, name, version, author, description, fileApi, url, forceUpdate, origins, addons, libraries, files, settings, launchInfo);
    }

    @Override
    public ModpackProvider getProvider() {
        return McbbsModpackProvider.INSTANCE;
    }

    @Override
    public void validate() throws JsonParseException, TolerableValidationException {
        if (!MANIFEST_TYPE.equals(manifestType))
            throw new JsonParseException("McbbsModpackManifest.manifestType must be 'minecraftModpack'");
//        if (manifestVersion > 1)
//            throw new JsonParseException("Only supports version 1 of McbbsModpackManifest");
        if (files == null)
            throw new JsonParseException("McbbsModpackManifest.files cannot be null");
        if (addons == null)
            throw new JsonParseException("McbbsModpackManifest.addons cannot be null");
    }

    public record Origin(String type, int id) {
        public Origin() {
            this("", 0);
        }

    }

    public record Addon(String id, String version) {
        public Addon() {
            this("", "");
        }

    }

    public record Settings(@SerializedName("install_mods") boolean installMods,
                           @SerializedName("install_resourcepack") boolean installResourcepack) {
        public Settings() {
            this(true, true);
        }
    }

    @JsonType(
            property = "type",
            subtypes = {
                    @JsonSubtype(clazz = AddonFile.class, name = "addon"),
                    @JsonSubtype(clazz = CurseFile.class, name = "curse")
            }
    )
    public static abstract class File implements Validation {
        protected final boolean force;

        public File(boolean force) {
            this.force = force;
        }

        @Override
        public void validate() throws JsonParseException, TolerableValidationException {
        }

        public boolean isForce() {
            return force;
        }
    }

    public static final class AddonFile extends File {
        private final String path;
        private final String hash;

        public AddonFile(boolean force, String path, String hash) {
            super(force);
            this.path = Objects.requireNonNull(path);
            this.hash = Objects.requireNonNull(hash);
        }

        public String getPath() {
            return path;
        }

        public String getHash() {
            return hash;
        }

        @Override
        public void validate() throws JsonParseException, TolerableValidationException {
            super.validate();

            Validation.requireNonNull(path, "AddonFile.path cannot be null");
            Validation.requireNonNull(hash, "AddonFile.hash cannot be null");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AddonFile addonFile = (AddonFile) o;
            return path.equals(addonFile.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path);
        }
    }

    public static final class CurseFile extends File {
        private final int projectID;
        private final int fileID;
        private final String fileName;
        private final String url;

        public CurseFile() {
            this(false, 0, 0, "", "");
        }

        public CurseFile(boolean force, int projectID, int fileID, String fileName, String url) {
            super(force);
            this.projectID = projectID;
            this.fileID = fileID;
            this.fileName = fileName;
            this.url = url;
        }

        public int getProjectID() {
            return projectID;
        }

        public int getFileID() {
            return fileID;
        }

        @Nullable
        public String getFileName() {
            return fileName;
        }

        public String getUrl() {
            return url == null
                    ? "https://www.curseforge.com/minecraft/mc-mods/" + projectID + "/download/" + fileID + "/file"
                    : url;
        }

        public CurseFile withFileName(String fileName) {
            return new CurseFile(force, projectID, fileID, fileName, url);
        }

        public CurseFile withURL(String url) {
            return new CurseFile(force, projectID, fileID, fileName, url);
        }

        @Override
        public void validate() throws JsonParseException, TolerableValidationException {
            super.validate();

            if (projectID == 0 || fileID == 0) {
                throw new JsonParseException("CurseFile.{projectID|fileID} cannot be empty.");
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CurseFile curseFile = (CurseFile) o;
            return projectID == curseFile.projectID && fileID == curseFile.fileID;
        }

        @Override
        public int hashCode() {
            return Objects.hash(projectID, fileID);
        }
    }

    public record LaunchInfo(int minMemory, @Nullable List<Integer> supportJava,
                             @SerializedName("launchArgument") List<String> launchArguments,
                             @SerializedName("javaArgument") List<String> javaArguments) {
        public LaunchInfo() {
            this(0, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }

        @Override
        public List<String> launchArguments() {
            return Optional.ofNullable(launchArguments).orElseGet(Collections::emptyList);
        }

        @Override
        public List<String> javaArguments() {
            return Optional.ofNullable(javaArguments).orElseGet(Collections::emptyList);
        }
    }

    public record ServerInfo(@Nullable String authlibInjectorServer) {
        public ServerInfo() {
            this(null);
        }
    }

    public Modpack toModpack(Charset encoding) throws IOException {
        String gameVersion = addons.stream().filter(x -> MINECRAFT.getPatchId().equals(x.id)).findAny()
                .orElseThrow(() -> new IOException("Cannot find game version")).version();
        return new Modpack(name, author, version, gameVersion, description, encoding, this) {
            @Override
            public Task<?> getInstallTask(DefaultDependencyManager dependencyManager, Path zipFile, String name, String iconUrl) {
                return new McbbsModpackLocalInstallTask(dependencyManager, zipFile, this, McbbsModpackManifest.this, name);
            }
        };
    }

    public void injectLaunchOptions(LaunchOptions.Builder launchOptions) {
        launchOptions.getGameArguments().addAll(launchInfo.launchArguments());
        launchOptions.getJavaArguments().addAll(launchInfo.javaArguments());
    }

}
