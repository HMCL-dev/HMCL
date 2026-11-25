// SPDX-FileCopyrightText: Copyright (C) 2024 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.java;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.java.mojang.MojangJavaDownloadTask;
import org.jackhuang.hmcl.download.java.mojang.MojangJavaRemoteFiles;
import org.jackhuang.hmcl.game.DownloadInfo;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.NativePlatform;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public Path getPlatformRoot(NativePlatform platform) {
        return root.resolve(platform.toString());
    }

    @Override
    public Path getJavaDir(NativePlatform platform, String name) {
        return getPlatformRoot(platform).resolve(name);
    }

    public Path getJavaDir(NativePlatform platform, GameJavaVersion gameJavaVersion) {
        return getJavaDir(platform, MOJANG_JAVA_PREFIX + gameJavaVersion.component());
    }

    @Override
    public Path getManifestFile(NativePlatform platform, String name) {
        return getPlatformRoot(platform).resolve(name + ".json");
    }

    public Path getManifestFile(NativePlatform platform, GameJavaVersion gameJavaVersion) {
        return getManifestFile(platform, MOJANG_JAVA_PREFIX + gameJavaVersion.component());
    }

    public boolean isInstalled(NativePlatform platform, String name) {
        return Files.exists(getManifestFile(platform, name));
    }

    public boolean isInstalled(NativePlatform platform, GameJavaVersion gameJavaVersion) {
        return isInstalled(platform, MOJANG_JAVA_PREFIX + gameJavaVersion.component());
    }

    public @Nullable Path getJavaExecutable(NativePlatform platform, String name) {
        Path javaDir = getJavaDir(platform, name);
        try {
            return JavaManager.getExecutable(javaDir).toRealPath();
        } catch (IOException ignored) {
            if (platform.os() == OperatingSystem.MACOS) {
                try {
                    return JavaManager.getMacExecutable(javaDir).toRealPath();
                } catch (IOException ignored1) {
                }
            }
        }

        return null;
    }

    public @Nullable Path getJavaExecutable(NativePlatform platform, GameJavaVersion gameJavaVersion) {
        return getJavaExecutable(platform, MOJANG_JAVA_PREFIX + gameJavaVersion.component());
    }

    private static void getAllJava(List<Path> list, NativePlatform platform, Path platformRoot) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(platformRoot)) {
            for (Path file : stream) {
                try {
                    String name = file.getFileName().toString();
                    if (name.endsWith(".json") && Files.isRegularFile(file)) {
                        Path javaDir = file.resolveSibling(name.substring(0, name.length() - ".json".length()));
                        Path executable;
                        try {
                            executable = JavaManager.getExecutable(javaDir).toRealPath();
                        } catch (IOException e) {
                            if (platform.os() == OperatingSystem.MACOS)
                                executable = JavaManager.getMacExecutable(javaDir).toRealPath();
                            else
                                throw e;
                        }

                        if (Files.isDirectory(javaDir)) {
                            list.add(executable);
                        }
                    }
                } catch (Throwable e) {
                    LOG.warning("Failed to parse " + file, e);
                }
            }

        } catch (IOException ignored) {
        }
    }

    @Override
    public Collection<Path> getAllJava(NativePlatform platform) {
        Path platformRoot = getPlatformRoot(platform);
        if (!Files.isDirectory(platformRoot))
            return Collections.emptyList();

        ArrayList<Path> list = new ArrayList<>();

        getAllJava(list, platform, platformRoot);
        return list;
    }

    @Override
    public Task<JavaRuntime> getDownloadJavaTask(DownloadProvider downloadProvider, NativePlatform platform, GameJavaVersion gameJavaVersion) {
        Path javaDir = getJavaDir(platform, gameJavaVersion);
        Path tempDir = getPlatformRoot(platform).resolve(".tmp").resolve(javaDir.getFileName());

        return new MojangJavaDownloadTask(downloadProvider, javaDir, tempDir, gameJavaVersion, JavaManager.getMojangJavaPlatform(platform)).thenApplyAsync(result -> {
            Path executable;
            try {
                executable = JavaManager.getExecutable(javaDir).toRealPath();
            } catch (IOException e) {
                if (platform.os() == OperatingSystem.MACOS)
                    executable = JavaManager.getMacExecutable(javaDir).toRealPath();
                else
                    throw e;
            }

            JavaInfo info;
            if (JavaManager.isCompatible(platform))
                info = JavaInfoUtils.fromExecutable(executable);
            else
                info = new JavaInfo(platform, result.download().version().name(), null);

            Map<String, Object> update = new LinkedHashMap<>();
            update.put("provider", "mojang");
            update.put("component", gameJavaVersion.component());

            Map<String, JavaLocalFiles.Local> files = new LinkedHashMap<>();
            result.remoteFiles().files().forEach((path, file) -> {
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
            JsonUtils.writeToJsonFile(getManifestFile(platform, gameJavaVersion), manifest);
            return JavaRuntime.of(executable, info, true);
        });
    }

    public Task<JavaRuntime> getInstallJavaTask(NativePlatform platform, String name, Map<String, Object> update, Path archiveFile) {
        Path javaDir = getJavaDir(platform, name);
        return new JavaInstallTask(javaDir, update, archiveFile).thenApplyAsync(result -> {
            if (!result.info().getPlatform().equals(platform))
                throw new IOException("Platform is mismatch: expected " + platform + " but got " + result.info().getPlatform());

            Path executable = javaDir.resolve("bin").resolve(platform.os().getJavaExecutable()).toRealPath();
            JsonUtils.writeToJsonFile(getManifestFile(platform, name), result);
            return JavaRuntime.of(executable, result.info(), true);
        });
    }

    /// Deletes the installed files and their manifest.
    private void uninstallJava(NativePlatform platform, String name) throws IOException {
        FileUtils.deleteDirectory(getJavaDir(platform, name));
        Files.deleteIfExists(getManifestFile(platform, name));
    }

    /// Removes the runtime if its executable belongs to an installation in this repository.
    boolean uninstallJava(JavaRuntime java) throws IOException {
        NativePlatform platform = java.getPlatform();
        Path root;
        try {
            root = getPlatformRoot(platform).toRealPath();
        } catch (IOException | SecurityException ignored) {
            return false;
        }
        Path binary = java.getBinary().normalize();
        if (!binary.startsWith(root))
            return false;
        Path relative = root.relativize(binary);
        if (relative.getNameCount() < 2)
            return false;

        uninstallJava(platform, relative.getName(0).toString());
        return true;
    }

    /// Removes the named installation when the task runs.
    @Override
    public Task<@Nullable Void> getUninstallJavaTask(NativePlatform platform, String name) {
        return Task.runAsync(() -> uninstallJava(platform, name));
    }

    /// Removes a runtime belonging to this repository when the task runs.
    @Override
    public Task<@Nullable Void> getUninstallJavaTask(JavaRuntime java) {
        return Task.runAsync(() -> uninstallJava(java));
    }
}
