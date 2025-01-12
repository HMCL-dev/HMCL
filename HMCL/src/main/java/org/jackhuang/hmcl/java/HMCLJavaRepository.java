/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.java;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.java.mojang.MojangJavaDownloadTask;
import org.jackhuang.hmcl.download.java.mojang.MojangJavaRemoteFiles;
import org.jackhuang.hmcl.game.DownloadInfo;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class HMCLJavaRepository implements JavaRepository {
    public static final String MOJANG_JAVA_PREFIX = "mojang-";

    private final Path root;

    public HMCLJavaRepository(Path root) {
        this.root = root;
    }

    public Path getPlatformRoot(Platform platform) {
        return root.resolve(platform.toString());
    }

    @Override
    public Path getJavaDir(Platform platform, String name) {
        return getPlatformRoot(platform).resolve(name);
    }

    public Path getJavaDir(Platform platform, GameJavaVersion gameJavaVersion) {
        return getJavaDir(platform, MOJANG_JAVA_PREFIX + gameJavaVersion.getComponent());
    }

    @Override
    public Path getManifestFile(Platform platform, String name) {
        return getPlatformRoot(platform).resolve(name + ".json");
    }

    public Path getManifestFile(Platform platform, GameJavaVersion gameJavaVersion) {
        return getManifestFile(platform, MOJANG_JAVA_PREFIX + gameJavaVersion.getComponent());
    }

    public boolean isInstalled(Platform platform, String name) {
        return Files.exists(getManifestFile(platform, name));
    }

    public boolean isInstalled(Platform platform, GameJavaVersion gameJavaVersion) {
        return isInstalled(platform, MOJANG_JAVA_PREFIX + gameJavaVersion.getComponent());
    }

    public @Nullable Path getJavaExecutable(Platform platform, String name) {
        Path javaDir = getJavaDir(platform, name);
        try {
            return JavaManager.getExecutable(javaDir).toRealPath();
        } catch (IOException ignored) {
            if (platform.getOperatingSystem() == OperatingSystem.OSX) {
                try {
                    return JavaManager.getMacExecutable(javaDir).toRealPath();
                } catch (IOException ignored1) {
                }
            }
        }

        return null;
    }

    public @Nullable Path getJavaExecutable(Platform platform, GameJavaVersion gameJavaVersion) {
        return getJavaExecutable(platform, MOJANG_JAVA_PREFIX + gameJavaVersion.getComponent());
    }

    @Override
    public Collection<JavaRuntime> getAllJava(Platform platform) {
        Path root = getPlatformRoot(platform);
        if (!Files.isDirectory(root))
            return Collections.emptyList();

        ArrayList<JavaRuntime> list = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path file : stream) {
                try {
                    String name = file.getFileName().toString();
                    if (name.endsWith(".json") && Files.isRegularFile(file)) {
                        Path javaDir = file.resolveSibling(name.substring(0, name.length() - ".json".length()));
                        Path executable;
                        try {
                            executable = JavaManager.getExecutable(javaDir).toRealPath();
                        } catch (IOException e) {
                            if (platform.getOperatingSystem() == OperatingSystem.OSX)
                                executable = JavaManager.getMacExecutable(javaDir).toRealPath();
                            else
                                throw e;
                        }

                        if (Files.isDirectory(javaDir)) {
                            JavaManifest manifest;
                            try (InputStream input = Files.newInputStream(file)) {
                                manifest = JsonUtils.fromJsonFully(input, JavaManifest.class);
                            }

                            list.add(JavaRuntime.of(executable, manifest.getInfo(), true));
                        }
                    }
                } catch (Throwable e) {
                    LOG.warning("Failed to parse " + file, e);
                }
            }

        } catch (IOException ignored) {
        }
        return list;
    }

    @Override
    public Task<JavaRuntime> getDownloadJavaTask(DownloadProvider downloadProvider, Platform platform, GameJavaVersion gameJavaVersion) {
        Path javaDir = getJavaDir(platform, gameJavaVersion);

        return new MojangJavaDownloadTask(downloadProvider, javaDir, gameJavaVersion, JavaManager.getMojangJavaPlatform(platform)).thenApplyAsync(result -> {
            Path executable;
            try {
                executable = JavaManager.getExecutable(javaDir).toRealPath();
            } catch (IOException e) {
                if (platform.getOperatingSystem() == OperatingSystem.OSX)
                    executable = JavaManager.getMacExecutable(javaDir).toRealPath();
                else
                    throw e;
            }

            JavaInfo info;
            if (JavaManager.isCompatible(platform))
                info = JavaInfo.fromExecutable(executable, false);
            else
                info = new JavaInfo(platform, result.download.getVersion().getName(), null);

            Map<String, Object> update = new LinkedHashMap<>();
            update.put("provider", "mojang");
            update.put("component", gameJavaVersion.getComponent());

            Map<String, JavaLocalFiles.Local> files = new LinkedHashMap<>();
            result.remoteFiles.getFiles().forEach((path, file) -> {
                if (file instanceof MojangJavaRemoteFiles.RemoteFile) {
                    DownloadInfo downloadInfo = ((MojangJavaRemoteFiles.RemoteFile) file).getDownloads().get("raw");
                    if (downloadInfo != null) {
                        files.put(path, new JavaLocalFiles.LocalFile(downloadInfo.getSha1(), downloadInfo.getSize()));
                    }
                } else if (file instanceof MojangJavaRemoteFiles.RemoteDirectory) {
                    files.put(path, new JavaLocalFiles.LocalDirectory());
                } else if (file instanceof MojangJavaRemoteFiles.RemoteLink) {
                    files.put(path, new JavaLocalFiles.LocalLink(((MojangJavaRemoteFiles.RemoteLink) file).getTarget()));
                }
            });

            JavaManifest manifest = new JavaManifest(info, update, files);
            FileUtils.writeText(getManifestFile(platform, gameJavaVersion), JsonUtils.GSON.toJson(manifest));
            return JavaRuntime.of(executable, info, true);
        });
    }

    public Task<JavaRuntime> getInstallJavaTask(Platform platform, String name, Map<String, Object> update, Path archiveFile) {
        Path javaDir = getJavaDir(platform, name);
        return new JavaInstallTask(javaDir, update, archiveFile).thenApplyAsync(result -> {
            if (!result.getInfo().getPlatform().equals(platform))
                throw new IOException("Platform is mismatch: expected " + platform + " but got " + result.getInfo().getPlatform());

            Path executable = javaDir.resolve("bin").resolve(platform.getOperatingSystem().getJavaExecutable()).toRealPath();
            FileUtils.writeText(getManifestFile(platform, name), JsonUtils.GSON.toJson(result));
            return JavaRuntime.of(executable, result.getInfo(), true);
        });
    }

    @Override
    public Task<Void> getUninstallJavaTask(Platform platform, String name) {
        return Task.runAsync(() -> {
            Files.deleteIfExists(getManifestFile(platform, name));
            FileUtils.deleteDirectory(getJavaDir(platform, name).toFile());
        });
    }

    @Override
    public Task<Void> getUninstallJavaTask(JavaRuntime java) {
        return Task.runAsync(() -> {
            Path root = getPlatformRoot(java.getPlatform());
            Path relativized = root.relativize(java.getBinary());

            if (relativized.getNameCount() > 1) {
                String name = relativized.getName(0).toString();
                Files.deleteIfExists(getManifestFile(java.getPlatform(), name));
                FileUtils.deleteDirectory(getJavaDir(java.getPlatform(), name).toFile());
            }
        });
    }
}
