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
import java.util.*;
import java.util.stream.Collectors;

public class JavaDownloadTask extends Task<Void> {
    private final String javaName;
    private final Path rootDir;
    private final String platform;
    private final Task<RemoteFiles> javaDownloadsTask;
    private JavaDownloads.JavaDownload download;
    private final List<Task<?>> dependencies = new ArrayList<>();

    public JavaDownloadTask(String javaName, String javaVersion, Path rootDir, DownloadProvider downloadProvider) throws UnsupportedPlatformException {
        this.javaName = javaName;
        this.rootDir = rootDir;
        this.platform = getCurrentJavaPlatform().orElseThrow(UnsupportedPlatformException::new);
        this.javaDownloadsTask = new GetTask(NetworkUtils.toURL(downloadProvider.injectURL(
                "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json")))
        .thenComposeAsync(javaDownloadsJson -> {
            JavaDownloads allDownloads = JsonUtils.fromNonNullJson(javaDownloadsJson, JavaDownloads.class);
            if (!allDownloads.getDownloads().containsKey(platform)) throw new UnsupportedPlatformException();
            Map<String, List<JavaDownloads.JavaDownload>> osDownloads = allDownloads.getDownloads().get(platform);
            if (!osDownloads.containsKey(javaName)) throw new UnsupportedPlatformException();
            List<JavaDownloads.JavaDownload> candidates = osDownloads.get(javaName);
            for (JavaDownloads.JavaDownload download : candidates) {
                if (VersionNumber.VERSION_COMPARATOR.compare(download.getVersion().getName(), javaVersion) >= 0) {
                    this.download = download;
                    return new GetTask(NetworkUtils.toURL(download.getManifest().getUrl()));
                }
            }
            throw new UnsupportedPlatformException();
        })
        .thenApplyAsync(javaDownloadJson -> JsonUtils.fromNonNullJson(javaDownloadJson, RemoteFiles.class));
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return Collections.singleton(javaDownloadsTask);
    }

    @Override
    public void execute() throws Exception {
        Path jvmDir = rootDir.resolve(javaName).resolve(platform).resolve(javaName);
        for (Map.Entry<String, RemoteFiles.RemoteFile> file : javaDownloadsTask.getResult().getFiles().entrySet()) {
            Path dest = jvmDir.resolve(file.getKey());
            if (file.getValue().getDownloads().containsKey("lzma")) {
                DownloadInfo download = file.getValue().getDownloads().get("lzma");
                File tempFile = Files.createTempFile("hmcl", "tmp").toFile();
                FileDownloadTask task = new FileDownloadTask(NetworkUtils.toURL(download.getUrl()), tempFile, new FileDownloadTask.IntegrityCheck("SHA-1", download.getSha1()));
                dependencies.add(task.thenRunAsync(() -> {
                    try {
                        Files.copy(new LZMAInputStream(new FileInputStream(tempFile)), dest);
                    } catch (IOException e) {
                        throw new ArtifactMalformedException("File " + file.getKey() + " is malformed");
                    }
                }));
            } else if (file.getValue().getDownloads().containsKey("raw")) {
                DownloadInfo download = file.getValue().getDownloads().get("raw");
                FileDownloadTask task = new FileDownloadTask(NetworkUtils.toURL(download.getUrl()), dest.toFile(), new FileDownloadTask.IntegrityCheck("SHA-1", download.getSha1()));
                dependencies.add(task);
            } else {
                throw new UnsupportedOperationException();
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
        FileUtils.writeText(rootDir.resolve(javaName).resolve(platform).resolve(".version").toFile(), download.getVersion().getName());
        FileUtils.writeText(rootDir.resolve(javaName).resolve(platform).resolve(javaName + ".sha1").toFile(),
                javaDownloadsTask.getResult().getFiles().entrySet().stream()
                        .map(entry -> entry.getKey() + " /#// " + entry.getValue().getDownloads().get("raw").getSha1() + " " + entry.getValue().getDownloads().get("raw").getSize())
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
