/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.terracotta;

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.terracotta.provider.GeneralProvider;
import org.jackhuang.hmcl.terracotta.provider.ITerracottaProvider;
import org.jackhuang.hmcl.terracotta.provider.MacOSProvider;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OSVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.DirectoryStream;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class TerracottaMetadata {
    private TerracottaMetadata() {
    }

    private record Config(
            @SerializedName("version_legacy") String legacy,
            @SerializedName("version_recent") List<String> recent,
            @SerializedName("version_latest") String latest,

            @SerializedName("classifiers") Map<String, String> classifiers,
            @SerializedName("downloads") List<String> downloads,
            @SerializedName("links") List<String> links
    ) {
        private TerracottaNative of(String classifier) {
            List<URI> links = new ArrayList<>(this.downloads.size());
            for (String download : this.downloads) {
                links.add(URI.create(download.replace("${version}", this.latest).replace("${classifier}", classifier)));
            }

            String hash = Objects.requireNonNull(this.classifiers.get(classifier), String.format("Classifier %s doesn't exist.", classifier));
            if (!hash.startsWith("sha256:")) {
                throw new IllegalArgumentException(String.format("Invalid hash value %s for classifier %s.", hash, classifier));
            }
            hash = hash.substring("sha256:".length());

            return new TerracottaNative(
                    Collections.unmodifiableList(links),
                    Metadata.DEPENDENCIES_DIRECTORY.resolve(
                            String.format("terracotta/%s/terracotta-%s-%s", this.latest, this.latest, classifier)
                    ).toAbsolutePath(),
                    new FileDownloadTask.IntegrityCheck("SHA-256", hash)
            );
        }
    }

    public static final ITerracottaProvider PROVIDER;
    public static final String PACKAGE_NAME;
    private static final List<String> PACKAGE_LINKS;

    private static final Pattern LEGACY;
    private static final List<String> RECENT;
    private static final String LATEST;

    static {
        Config config;
        try (InputStream is = TerracottaMetadata.class.getResourceAsStream("/assets/terracotta.json")) {
            config = JsonUtils.fromNonNullJsonFully(is, Config.class);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }

        LEGACY = Pattern.compile(config.legacy);
        RECENT = config.recent;
        LATEST = config.latest;

        ProviderContext context = locateProvider(config);
        PROVIDER = context != null ? context.provider() : null;
        PACKAGE_NAME = context != null ? String.format("terracotta-%s-%s.tar.gz", context.system, context.arch) : null;

        if (context != null) {
            List<String> packageLinks = new ArrayList<>(config.links.size());
            for (String link : config.links) {
                packageLinks.add(link.replace("${version}", LATEST)
                        .replace("${system}", context.system)
                        .replace("${arch}", context.arch)
                );
            }

            PACKAGE_LINKS = Collections.unmodifiableList(packageLinks);
        } else {
            PACKAGE_LINKS = null;
        }
    }

    private record ProviderContext(ITerracottaProvider provider, String system, String arch) {
    }

    public static String getPackageLink() {
        return PACKAGE_LINKS.get(ThreadLocalRandom.current().nextInt(0, PACKAGE_LINKS.size()));
    }

    @Nullable
    private static ProviderContext locateProvider(Config config) {
        String architecture = switch (Architecture.SYSTEM_ARCH) {
            case X86_64 -> "x86_64";
            case ARM64 -> "arm64";
            default -> null;
        };
        if (architecture == null) {
            return null;
        }

        return switch (OperatingSystem.CURRENT_OS) {
            case WINDOWS -> {
                if (OperatingSystem.SYSTEM_VERSION.isAtLeast(OSVersion.WINDOWS_8_1)) {
                    yield new ProviderContext(
                            new GeneralProvider(config.of(String.format("windows-%s.exe", architecture))),
                            "windows", architecture
                    );
                }
                yield null;
            }
            case LINUX -> new ProviderContext(
                    new GeneralProvider(config.of(String.format("linux-%s", architecture))),
                    "linux", architecture
            );
            case MACOS -> new ProviderContext(
                    new MacOSProvider(
                            config.of(String.format("macos-%s.pkg", architecture)),
                            config.of(String.format("macos-%s", architecture))
                    ),
                    "macos", architecture
            );
            default -> null;
        };
    }

    public static void removeLegacyVersionFiles() throws IOException {
        try (DirectoryStream<Path> terracotta = Files.newDirectoryStream(Metadata.DEPENDENCIES_DIRECTORY.resolve("terracotta").toAbsolutePath())) {
            for (Path path : terracotta) {
                String name = FileUtils.getName(path);
                if (LATEST.equals(name) || RECENT.contains(name) || !LEGACY.matcher(name).matches()) {
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
                    LOG.warning(String.format("Unable to remove legacy terracotta files: %s", path), e);
                }
            }
        }
    }

    public static boolean hasLegacyVersionFiles() throws IOException {
        try (DirectoryStream<Path> terracotta = Files.newDirectoryStream(Metadata.DEPENDENCIES_DIRECTORY.resolve("terracotta").toAbsolutePath())) {
            for (Path path : terracotta) {
                String name = FileUtils.getName(path);
                if (!LATEST.equals(name) && (RECENT.contains(name) || LEGACY.matcher(name).matches())) {
                    return true;
                }
            }
        }

        return false;
    }
}
