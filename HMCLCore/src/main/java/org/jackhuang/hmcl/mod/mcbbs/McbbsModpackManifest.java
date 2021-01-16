/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.mod.mcbbs;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.util.gson.*;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.MINECRAFT;

public class McbbsModpackManifest implements Validation {
    public static final String MANIFEST_TYPE = "minecraftModpack";

    private final String manifestType;
    private final int manifestVersion;
    private final String name;
    private final String version;
    private final String author;
    private final String description;

    @Nullable
    private final String fileApi;
    private final String url;
    private final boolean forceUpdate;
    @SerializedName("origin")
    private final List<Origin> origins;
    private final List<Addon> addons;
    private final List<Library> libraries;
    private final List<File> files;
    private final Settings settings;
    private final LaunchInfo launchInfo;
    // sandbox and antiCheating are both not supported.

    public McbbsModpackManifest() {
        this(MANIFEST_TYPE, 1, "", "", "", "", null, "", false, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), new Settings(), new LaunchInfo());
    }

    public McbbsModpackManifest(String manifestType, int manifestVersion, String name, String version, String author, String description, @Nullable String fileApi, String url, boolean forceUpdate, List<Origin> origins, List<Addon> addons, List<Library> libraries, List<File> files, Settings settings, LaunchInfo launchInfo) {
        this.manifestType = manifestType;
        this.manifestVersion = manifestVersion;
        this.name = name;
        this.version = version;
        this.author = author;
        this.description = description;
        this.fileApi = fileApi;
        this.url = url;
        this.forceUpdate = forceUpdate;
        this.origins = origins;
        this.addons = addons;
        this.libraries = libraries;
        this.files = files;
        this.settings = settings;
        this.launchInfo = launchInfo;
    }

    public String getManifestType() {
        return manifestType;
    }

    public int getManifestVersion() {
        return manifestVersion;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    public String getFileApi() {
        return fileApi;
    }

    public String getUrl() {
        return url;
    }

    public boolean isForceUpdate() {
        return forceUpdate;
    }

    public List<Origin> getOrigins() {
        return origins;
    }

    public List<Addon> getAddons() {
        return addons;
    }

    public List<Library> getLibraries() {
        return libraries;
    }

    public List<File> getFiles() {
        return files;
    }

    public Settings getSettings() {
        return settings;
    }

    public LaunchInfo getLaunchInfo() {
        return launchInfo;
    }

    @Override
    public void validate() throws JsonParseException, TolerableValidationException {
        if (!MANIFEST_TYPE.equals(manifestType))
            throw new JsonParseException("McbbsModpackManifest.manifestType must be 'minecraftModpack'");
//        if (manifestVersion > 1)
//            throw new JsonParseException("Only supports version 1 of McbbsModpackManifest");
        if (files == null)
            throw new JsonParseException("McbbsModpackManifest.files cannot be null");
    }

    public static final class Origin {
        private final String type;
        private final int id;

        public Origin() {
            this("", 0);
        }

        public Origin(String type, int id) {
            this.type = type;
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public int getId() {
            return id;
        }
    }

    public static final class Addon {
        private final String id;
        private final String version;

        public Addon() {
            this("", "");
        }

        public Addon(String id, String version) {
            this.id = id;
            this.version = version;
        }

        public String getId() {
            return id;
        }

        public String getVersion() {
            return version;
        }
    }

    public static final class Settings {
        @SerializedName("install_modes")
        private final boolean installMods;

        @SerializedName("install_resourcepack")
        private final boolean installResourcepack;

        public Settings() {
            this(true, true);
        }

        public Settings(boolean installMods, boolean installResourcepack) {
            this.installMods = installMods;
            this.installResourcepack = installResourcepack;
        }

        public boolean isInstallMods() {
            return installMods;
        }

        public boolean isInstallResourcepack() {
            return installResourcepack;
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
        private final boolean force;

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
            this.path = path;
            this.hash = hash;
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

        public String getFileName() {
            return fileName;
        }

        public URL getUrl() {
            return url == null ? NetworkUtils.toURL("https://www.curseforge.com/minecraft/mc-mods/" + projectID + "/download/" + fileID + "/file")
                    : NetworkUtils.toURL(NetworkUtils.encodeLocation(url));
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

    public static final class LaunchInfo {
        private final int minMemory;
        private final List<Integer> supportJava;
        @SerializedName("launchArgument")
        private final List<String> launchArguments;
        @SerializedName("javaArgument")
        private final List<String> javaArguments;

        public LaunchInfo() {
            this(0, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }

        public LaunchInfo(int minMemory, List<Integer> supportJava, List<String> launchArguments, List<String> javaArguments) {
            this.minMemory = minMemory;
            this.supportJava = supportJava;
            this.launchArguments = launchArguments;
            this.javaArguments = javaArguments;
        }

        public int getMinMemory() {
            return minMemory;
        }

        @Nullable
        public List<Integer> getSupportJava() {
            return supportJava;
        }

        public List<String> getLaunchArguments() {
            return Optional.ofNullable(launchArguments).orElseGet(Collections::emptyList);
        }

        public List<String> getJavaArguments() {
            return Optional.ofNullable(javaArguments).orElseGet(Collections::emptyList);
        }
    }

    public static class ServerInfo {
        private final String authlibInjectorServer;

        public ServerInfo() {
            this(null);
        }

        public ServerInfo(String authlibInjectorServer) {
            this.authlibInjectorServer = authlibInjectorServer;
        }

        @Nullable
        public String getAuthlibInjectorServer() {
            return authlibInjectorServer;
        }
    }

    public Modpack toModpack(Charset encoding) throws IOException {
        String gameVersion = addons.stream().filter(x -> MINECRAFT.getPatchId().equals(x.id)).findAny()
                .orElseThrow(() -> new IOException("Cannot find game version")).getVersion();
        return new Modpack(name, author, version, gameVersion, description, encoding, this);
    }

    public void injectLaunchOptions(LaunchOptions.Builder launchOptions) {
        launchOptions.getGameArguments().addAll(launchInfo.getLaunchArguments());
        launchOptions.getJavaArguments().addAll(launchInfo.getJavaArguments());
    }

    /**
     * @param zip the CurseForge modpack file.
     * @throws IOException if the file is not a valid zip file.
     * @throws JsonParseException if the server-manifest.json is missing or malformed.
     * @return the manifest.
     */
    public static Modpack readManifest(Path zip, Charset encoding) throws IOException, JsonParseException {
        String json = CompressingUtils.readTextZipEntry(zip, "manifest.json", encoding);
        McbbsModpackManifest manifest = JsonUtils.fromNonNullJson(json, McbbsModpackManifest.class);
        return manifest.toModpack(encoding);
    }
}
