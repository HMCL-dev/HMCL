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
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.i18n.LocaleUtils;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OSVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class TerracottaMetadata {
    private TerracottaMetadata() {
    }

    public record Link(@SerializedName("desc") LocalizedText description, String link) {
    }

    private record Config(
            @SerializedName("version_legacy") String legacy,
            @SerializedName("version_recent") List<String> recent,
            @SerializedName("version_latest") String latest,

            @SerializedName("classifiers") Map<String, String> classifiers,
            @SerializedName("downloads") List<String> downloads,
            @SerializedName("downloads_CN") List<String> downloadsCN,
            @SerializedName("links") List<Link> links
    ) {
        private @Nullable TerracottaNative of(String classifier) {
            String hash = this.classifiers.get(classifier);
            if (hash == null)
                return null;

            if (!hash.startsWith("sha256:"))
                throw new IllegalArgumentException(String.format("Invalid hash value %s for classifier %s.", hash, classifier));
            hash = hash.substring("sha256:".length());

            List<URI> links = new ArrayList<>(this.downloads.size() + this.downloadsCN.size());
            for (String download : LocaleUtils.IS_CHINA_MAINLAND
                    ? Lang.merge(this.downloadsCN, this.downloads)
                    : Lang.merge(this.downloads, this.downloadsCN)) {
                links.add(URI.create(download.replace("${version}", this.latest).replace("${classifier}", classifier)));
            }

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
    public static final List<Link> PACKAGE_LINKS;
    public static final String FEEDBACK_LINK = NetworkUtils.withQuery("https://docs.hmcl.net/multiplayer/feedback.html", Map.of(
            "v", "v1",
            "launcher_version", Metadata.VERSION
    ));

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
        PACKAGE_NAME = context != null ? String.format("terracotta-%s-%s-pkg.tar.gz", config.latest, context.branch) : null;

        if (context != null) {
            List<Link> packageLinks = new ArrayList<>(config.links.size());
            for (Link link : config.links) {
                packageLinks.add(new Link(
                        link.description,
                        link.link.replace("${version}", LATEST)
                                .replace("${classifier}", context.branch)
                ));
            }

            Collections.shuffle(packageLinks);
            PACKAGE_LINKS = Collections.unmodifiableList(packageLinks);
        } else {
            PACKAGE_LINKS = null;
        }
    }

    private record ProviderContext(ITerracottaProvider provider, String branch) {
        ProviderContext(ITerracottaProvider provider, String system, String arch) {
            this(provider, system + "-" + arch);
        }
    }

    @Nullable
    private static ProviderContext locateProvider(Config config) {
        String arch = Architecture.SYSTEM_ARCH.getCheckedName();
        return switch (OperatingSystem.CURRENT_OS) {
            case WINDOWS -> {
                if (!OperatingSystem.SYSTEM_VERSION.isAtLeast(OSVersion.WINDOWS_10))
                    yield null;

                TerracottaNative target = config.of("windows-%s.exe".formatted(arch));
                yield target != null
                        ? new ProviderContext(new GeneralProvider(target), "windows", arch)
                        : null;
            }
            case LINUX -> {
                TerracottaNative target = config.of("linux-%s".formatted(arch));
                yield target != null
                        ? new ProviderContext(new GeneralProvider(target), "linux", arch)
                        : null;
            }
            case MACOS -> {
                TerracottaNative installer = config.of("macos-%s.pkg".formatted(arch));
                TerracottaNative binary = config.of("macos-%s".formatted(arch));

                yield installer != null && binary != null
                        ? new ProviderContext(new MacOSProvider(installer, binary), "macos", arch)
                        : null;
            }
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
                    FileUtils.deleteDirectory(path);
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
