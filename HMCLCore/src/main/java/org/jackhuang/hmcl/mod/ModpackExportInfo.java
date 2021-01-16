package org.jackhuang.hmcl.mod;

import org.jackhuang.hmcl.mod.mcbbs.McbbsModpackManifest;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModpackExportInfo {

    private final List<String> whitelist = new ArrayList<>();
    private String name;
    private String author;
    private String version;
    private String description;
    private String url;

    private boolean forceUpdate;
    private boolean packWithLauncher;

    private String fileApi;
    private int minMemory;
    private List<Integer> supportedJavaVersions;
    private String launchArguments;
    private String javaArguments;

    private String authlibInjectorServer;

    private Path output;
    private List<McbbsModpackManifest.Origin> origins = new ArrayList<>();

    public ModpackExportInfo() {}

    public List<String> getWhitelist() {
        return whitelist;
    }

    public ModpackExportInfo setWhitelist(List<String> whitelist) {
        this.whitelist.clear();
        this.whitelist.addAll(whitelist);
        return this;
    }

    /**
     * Name of this modpack.
     */
    public String getName() {
        return name;
    }

    public ModpackExportInfo setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Author of this modpack.
     */
    public String getAuthor() {
        return author;
    }

    public ModpackExportInfo setAuthor(String author) {
        this.author = author;
        return this;
    }

    /**
     * Version of this modpack.
     */
    public String getVersion() {
        return version;
    }

    public ModpackExportInfo setVersion(String version) {
        this.version = version;
        return this;
    }

    /**
     * Description of this modpack.
     *
     * Supports plain HTML text.
     */
    public String getDescription() {
        return description;
    }

    public ModpackExportInfo setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getFileApi() {
        return fileApi;
    }

    public ModpackExportInfo setFileApi(String fileApi) {
        this.fileApi = fileApi;
        return this;
    }

    /**
     * Modpack official introduction webpage link.
     */
    public String getUrl() {
        return url;
    }

    public ModpackExportInfo setUrl(String url) {
        this.url = url;
        return this;
    }

    public boolean isForceUpdate() {
        return forceUpdate;
    }

    public ModpackExportInfo setForceUpdate(boolean forceUpdate) {
        this.forceUpdate = forceUpdate;
        return this;
    }

    public boolean isPackWithLauncher() {
        return packWithLauncher;
    }

    public ModpackExportInfo setPackWithLauncher(boolean packWithLauncher) {
        this.packWithLauncher = packWithLauncher;
        return this;
    }

    public int getMinMemory() {
        return minMemory;
    }

    public ModpackExportInfo setMinMemory(int minMemory) {
        this.minMemory = minMemory;
        return this;
    }

    @Nullable
    public List<Integer> getSupportedJavaVersions() {
        return supportedJavaVersions;
    }

    public ModpackExportInfo setSupportedJavaVersions(List<Integer> supportedJavaVersions) {
        this.supportedJavaVersions = supportedJavaVersions;
        return this;
    }

    public String getLaunchArguments() {
        return launchArguments;
    }

    public ModpackExportInfo setLaunchArguments(String launchArguments) {
        this.launchArguments = launchArguments;
        return this;
    }

    public String getJavaArguments() {
        return javaArguments;
    }

    public ModpackExportInfo setJavaArguments(String javaArguments) {
        this.javaArguments = javaArguments;
        return this;
    }

    public String getAuthlibInjectorServer() {
        return authlibInjectorServer;
    }

    public ModpackExportInfo setAuthlibInjectorServer(String authlibInjectorServer) {
        this.authlibInjectorServer = authlibInjectorServer;
        return this;
    }

    public Path getOutput() {
        return output;
    }

    public ModpackExportInfo setOutput(Path output) {
        this.output = output;
        return this;
    }

    public List<McbbsModpackManifest.Origin> getOrigins() {
        return Collections.unmodifiableList(origins);
    }

    public ModpackExportInfo setOrigins(List<McbbsModpackManifest.Origin> origins) {
        this.origins.clear();
        this.origins.addAll(origins);
        return this;
    }

    public ModpackExportInfo validate() throws NullPointerException {
        if (output == null)
            throw new NullPointerException("ModpackExportInfo.output cannot be null");
        return this;
    }

    public static class Options {
        private boolean requireUrl;
        private boolean requireForceUpdate;
        private boolean requireFileApi;
        private boolean validateFileApi;
        private boolean requireMinMemory;
        private boolean requireAuthlibInjectorServer;
        private boolean requireLaunchArguments;
        private boolean requireJavaArguments;
        private boolean requireOrigins;

        public Options() {
        }

        public boolean isRequireUrl() {
            return requireUrl;
        }

        public boolean isRequireForceUpdate() {
            return requireForceUpdate;
        }

        public boolean isRequireFileApi() {
            return requireFileApi;
        }

        public boolean isValidateFileApi() {
            return validateFileApi;
        }

        public boolean isRequireMinMemory() {
            return requireMinMemory;
        }

        public boolean isRequireAuthlibInjectorServer() {
            return requireAuthlibInjectorServer;
        }

        public boolean isRequireLaunchArguments() {
            return requireLaunchArguments;
        }

        public boolean isRequireJavaArguments() {
            return requireJavaArguments;
        }

        public boolean isRequireOrigins() {
            return requireOrigins;
        }

        public Options requireUrl() {
            requireUrl = true;
            return this;
        }

        public Options requireForceUpdate() {
            requireForceUpdate = true;
            return this;
        }

        public Options requireFileApi(boolean optional) {
            requireFileApi = true;
            validateFileApi = !optional;
            return this;
        }

        public Options requireMinMemory() {
            requireMinMemory = true;
            return this;
        }

        public Options requireAuthlibInjectorServer() {
            requireAuthlibInjectorServer = true;
            return this;
        }

        public Options requireLaunchArguments() {
            requireLaunchArguments = true;
            return this;
        }

        public Options requireJavaArguments() {
            requireJavaArguments = true;
            return this;
        }

        public Options requireOrigins() {
            requireOrigins = true;
            return this;
        }

    }
}
