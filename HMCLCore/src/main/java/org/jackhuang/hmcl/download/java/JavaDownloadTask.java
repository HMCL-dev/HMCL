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
package org.jackhuang.hmcl.download.java;

import org.jackhuang.hmcl.download.ArtifactMalformedException;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.DownloadInfo;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.tukaani.xz.LZMAInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class JavaDownloadTask extends Task<Void> {
    private final org.jackhuang.hmcl.game.JavaVersion javaVersion;
    private final Path rootDir;
    private String platform;
    private final Task<RemoteFiles> javaDownloadsTask;
    private JavaDownloads.JavaDownload download;
    private final List<Task<?>> dependencies = new ArrayList<>();

    public JavaDownloadTask(org.jackhuang.hmcl.game.JavaVersion javaVersion, Path rootDir, DownloadProvider downloadProvider) {
        this.javaVersion = javaVersion;
        this.rootDir = rootDir;
        this.javaDownloadsTask = new GetTask(NetworkUtils.toURL(
                "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json"))
        .thenComposeAsync(javaDownloadsJson -> {
            JavaDownloads allDownloads = JsonUtils.fromNonNullJson(javaDownloadsJson, JavaDownloads.class);
            if (!allDownloads.getDownloads().containsKey(platform)) throw new UnsupportedPlatformException();
            Map<String, List<JavaDownloads.JavaDownload>> osDownloads = allDownloads.getDownloads().get(platform);
            if (!osDownloads.containsKey(javaVersion.getComponent())) throw new UnsupportedPlatformException();
            List<JavaDownloads.JavaDownload> candidates = osDownloads.get(javaVersion.getComponent());
            for (JavaDownloads.JavaDownload download : candidates) {
                if (VersionNumber.VERSION_COMPARATOR.compare(download.getVersion().getName(), Integer.toString(javaVersion.getMajorVersion())) >= 0) {
                    this.download = download;
                    return new GetTask(NetworkUtils.toURL(download.getManifest().getUrl()));
                }
            }
            throw new UnsupportedPlatformException();
        })
        .thenApplyAsync(javaDownloadJson -> JsonUtils.fromNonNullJson(javaDownloadJson, RemoteFiles.class));
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
        this.platform = getCurrentJavaPlatform().orElseThrow(UnsupportedPlatformException::new);
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return Collections.singleton(javaDownloadsTask);
    }

    @Override
    public void execute() throws Exception {
        Path jvmDir = rootDir.resolve(javaVersion.getComponent()).resolve(platform).resolve(javaVersion.getComponent());
        for (Map.Entry<String, RemoteFiles.Remote> entry : javaDownloadsTask.getResult().getFiles().entrySet()) {
            Path dest = jvmDir.resolve(entry.getKey());
            if (entry.getValue() instanceof RemoteFiles.RemoteFile) {
                RemoteFiles.RemoteFile file = ((RemoteFiles.RemoteFile) entry.getValue());
                if (file.getDownloads().containsKey("lzma")) {
                    DownloadInfo download = file.getDownloads().get("lzma");
                    File tempFile = Files.createTempFile("hmcl", "tmp").toFile();
                    FileDownloadTask task = new FileDownloadTask(NetworkUtils.toURL(download.getUrl()), tempFile, new FileDownloadTask.IntegrityCheck("SHA-1", download.getSha1()));
                    dependencies.add(task.thenRunAsync(() -> {
                        try {
                            Files.copy(new LZMAInputStream(new FileInputStream(tempFile)), dest);
                        } catch (IOException e) {
                            throw new ArtifactMalformedException("File " + entry.getKey() + " is malformed");
                        }
                    }));
                } else if (file.getDownloads().containsKey("raw")) {
                    DownloadInfo download = file.getDownloads().get("raw");
                    FileDownloadTask task = new FileDownloadTask(NetworkUtils.toURL(download.getUrl()), dest.toFile(), new FileDownloadTask.IntegrityCheck("SHA-1", download.getSha1()));
                    dependencies.add(task);
                } else {
                    continue;
                }
            } else if (entry.getValue() instanceof RemoteFiles.RemoteDirectory) {
                Files.createDirectories(dest);
            } else if (entry.getValue() instanceof RemoteFiles.RemoteLink) {
                RemoteFiles.RemoteLink link = ((RemoteFiles.RemoteLink) entry.getValue());
                Files.createSymbolicLink(dest, Paths.get(link.getTarget()));
            }
        }
    }

    @Override
    public List<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean doPostExecute() {
        return true;
    }

    @Override
    public void postExecute() throws Exception {
        FileUtils.writeText(rootDir.resolve(javaVersion.getComponent()).resolve(platform).resolve(".version").toFile(), download.getVersion().getName());
        FileUtils.writeText(rootDir.resolve(javaVersion.getComponent()).resolve(platform).resolve(javaVersion.getComponent() + ".sha1").toFile(),
                javaDownloadsTask.getResult().getFiles().entrySet().stream()
                        .filter(entry -> entry.getValue() instanceof RemoteFiles.RemoteFile)
                        .map(entry -> {
                            RemoteFiles.RemoteFile file = (RemoteFiles.RemoteFile) entry.getValue();
                            return entry.getKey() + " /#// " + file.getDownloads().get("raw").getSha1() + " " + file.getDownloads().get("raw").getSize();
                        })
                        .collect(Collectors.joining(OperatingSystem.LINE_SEPARATOR)));
    }

    public static Optional<String> getCurrentJavaPlatform() {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX) {
            if (Architecture.CURRENT == Architecture.X86) {
                return Optional.of("linux-i386");
            } else if (Architecture.CURRENT == Architecture.X86_64) {
                return Optional.of("linux");
            }
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
            if (Architecture.CURRENT == Architecture.X86_64) {
                return Optional.of("mac-os");
            }
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            if (Architecture.CURRENT == Architecture.X86) {
                return Optional.of("windows-x86");
            } else if (Architecture.CURRENT == Architecture.X86_64) {
                return Optional.of("windows-x64");
            }
        }
        return Optional.empty();
    }

    public static class UnsupportedPlatformException extends Exception {}
}
