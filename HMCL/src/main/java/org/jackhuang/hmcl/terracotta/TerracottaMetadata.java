package org.jackhuang.hmcl.terracotta;

import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.terracotta.provider.GeneralProvider;
import org.jackhuang.hmcl.terracotta.provider.ITerracottaProvider;
import org.jackhuang.hmcl.terracotta.provider.MacOSProvider;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class TerracottaMetadata {
    private TerracottaMetadata() {
    }

    public static final TerracottaNative WINDOWS_X86_64;
    public static final TerracottaNative WINDOWS_ARM64;

    public static final TerracottaNative LINUX_X86_64;
    public static final TerracottaNative LINUX_ARM64;

    public static final TerracottaNative MACOS_INSTALLER_X86_64;
    public static final TerracottaNative MACOS_INSTALLER_ARM64;
    public static final TerracottaNative MACOS_BIN_X86_64;
    public static final TerracottaNative MACOS_BIN_ARM64;

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

        private TerracottaNative of(String classifier) {
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

            return new TerracottaNative(
                    Collections.unmodifiableList(links),
                    Metadata.DEPENDENCIES_DIRECTORY.resolve(
                            String.format("terracota/%s/terracotta-%s", this.version, classifier)
                    ).toAbsolutePath(),
                    Collections.unmodifiableList(legacyPath),
                    new FileDownloadTask.IntegrityCheck("SHA-256", hash)
            );
        }
    }

    private static final List<Path> LEGACY_PATH;

    static {
        Config config;
        try (InputStream is = TerracottaMetadata.class.getResourceAsStream("/assets/terracotta.json")) {
            config = JsonUtils.fromNonNullJsonFully(is, Config.class);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }

        WINDOWS_X86_64 = config.of("windows-x86_64.exe");
        WINDOWS_ARM64 = config.of("windows-arm64.exe");
        LINUX_X86_64 = config.of("linux-x86_64");
        LINUX_ARM64 = config.of("linux-arm64");
        MACOS_INSTALLER_X86_64 = config.of("macos-x86_64.pkg");
        MACOS_INSTALLER_ARM64 = config.of("macos-arm64.pkg");
        MACOS_BIN_X86_64 = config.of("macos-x86_64");
        MACOS_BIN_ARM64 = config.of("macos-arm64");

        int LEGACY_BUT_NOT_OUT_OF_DATE_COUNT = 3;
        if (config.legacy.size() < LEGACY_BUT_NOT_OUT_OF_DATE_COUNT) {
            LEGACY_PATH = List.of();
        } else {
            int count = config.legacy.size() - LEGACY_BUT_NOT_OUT_OF_DATE_COUNT;

            List<Path> legacyPath = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                legacyPath.add(Metadata.DEPENDENCIES_DIRECTORY.resolve(String.format("terracota/%s", config.legacy.get(i))).toAbsolutePath());
            }
            LEGACY_PATH = legacyPath;
        }
    }

    public static void removeLegacy() {
        for (Path path : LEGACY_PATH) {
            if (!Files.exists(path)) {
                continue;
            }

            try {
                Files.walkFileTree(path, new SimpleFileVisitor<>() {
                    @Override
                    public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return super.visitFile(file, attrs);
                    }

                    @Override
                    public @NotNull FileVisitResult postVisitDirectory(@NotNull Path dir, @Nullable IOException exc) throws IOException {
                        Files.delete(dir);
                        return super.postVisitDirectory(dir, exc);
                    }
                });
            } catch (IOException e) {
                LOG.warning(String.format("Unable to delete legacy version: %s", path), e);
            }
        }
    }

    public static final ITerracottaProvider PROVIDER = locateProvider();

    private static ITerracottaProvider locateProvider() {
        if (Architecture.SYSTEM_ARCH != Architecture.X86_64 && Architecture.SYSTEM_ARCH != Architecture.ARM64) {
            return null;
        }

        switch (OperatingSystem.CURRENT_OS) {
            case WINDOWS: {
                if (OperatingSystem.isWindows81OrLater()) {
                    return new GeneralProvider();
                }
                return null;
            }
            case LINUX: {
                return new GeneralProvider();
            }
            case MACOS: {
                return new MacOSProvider();
            }
            default: {
                return null;
            }
        }
    }
}
