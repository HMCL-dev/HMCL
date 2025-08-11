package org.jackhuang.hmcl.terracotta;

import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.terracotta.provider.GeneralProvider;
import org.jackhuang.hmcl.terracotta.provider.ITerracottaProvider;
import org.jackhuang.hmcl.terracotta.provider.MacOSProvider;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TerracottaMetadata {
    private TerracottaMetadata() {
    }

    public static final TerracottaConfig WINDOWS_X86_64;
    public static final TerracottaConfig WINDOWS_ARM64;

    public static final TerracottaConfig LINUX_X86_64;
    public static final TerracottaConfig LINUX_ARM64;

    public static final TerracottaConfig MACOS_INSTALLER_X86_64;
    public static final TerracottaConfig MACOS_INSTALLER_ARM64;
    public static final TerracottaConfig MACOS_BIN_X86_64;
    public static final TerracottaConfig MACOS_BIN_ARM64;

    private static final class Config {
        private final List<String> legacy;
        private final String version;
        private final Map<String, String> classifiers;
        private final List<String> downloads;

        private Config(List<String> legacy, String version, Map<String, String> classifiers, List<String> downloads) {
            this.legacy = legacy;
            this.version = version;
            this.classifiers = classifiers;
            this.downloads = downloads;
        }

        private TerracottaConfig create(String classifier) {
            List<URI> links = new ArrayList<>(this.downloads.size());
            for (String download : this.downloads) {
                links.add(URI.create(download.replace("${version}", this.version).replace("${classifier}", classifier)));
            }

            String hash = Objects.requireNonNull(this.classifiers.get(classifier), String.format("Classifier %s doesn't exist.", classifier));
            if (!hash.startsWith("sha256:")) {
                throw new IllegalArgumentException(String.format("Invalid hash value %s for classifier %s.", hash, classifier));
            }
            hash = hash.substring("sha256:".length());

            List<Path> legacyPath = new ArrayList<>(this.legacy.size());
            for (String legacy : this.legacy) {
                legacyPath.add(Metadata.DEPENDENCIES_DIRECTORY.resolve(
                        String.format("terracota/%s/terracotta-%s", legacy, classifier)
                ).toAbsolutePath());
            }

            return new TerracottaConfig(
                    Collections.unmodifiableList(links),
                    Metadata.DEPENDENCIES_DIRECTORY.resolve(
                            String.format("terracota/%s/terracotta-%s", this.version, classifier)
                    ).toAbsolutePath(),
                    Collections.unmodifiableList(legacyPath),
                    new FileDownloadTask.IntegrityCheck("SHA-256", hash)
            );
        }
    }

    static {
        Config config;
        try (InputStream is = TerracottaMetadata.class.getResourceAsStream("/assets/terracotta.json")) {
            config = JsonUtils.fromNonNullJsonFully(is, Config.class);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }

        WINDOWS_X86_64 = config.create("windows-x86_64.exe");
        WINDOWS_ARM64 = config.create("windows-arm64.exe");
        LINUX_X86_64 = config.create("linux-x86_64");
        LINUX_ARM64 = config.create("linux-arm64");
        MACOS_INSTALLER_X86_64 = config.create("macos-x86_64.pkg");
        MACOS_INSTALLER_ARM64 = config.create("macos-arm64.pkg");
        MACOS_BIN_X86_64 = config.create("macos-x86_64");
        MACOS_BIN_ARM64 = config.create("macos-arm64");
    }

    public static final ITerracottaProvider PROVIDER = locateProvider();

    private static ITerracottaProvider locateProvider() {
        if (GeneralProvider.TARGET != null) {
            return new GeneralProvider();
        } else if (MacOSProvider.INSTALLER != null) {
            return new MacOSProvider();
        } else {
            return null;
        }
    }
}
