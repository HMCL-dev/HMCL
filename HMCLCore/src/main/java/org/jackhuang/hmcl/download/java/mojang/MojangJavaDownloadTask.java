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
package org.jackhuang.hmcl.download.java.mojang;

import org.jackhuang.hmcl.download.ArtifactMalformedException;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.DownloadInfo;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.java.JavaInfo;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.ChecksumMismatchException;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.UnsupportedPlatformException;
import org.tukaani.xz.LZMAInputStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class MojangJavaDownloadTask extends Task<MojangJavaDownloadTask.Result> {

    private final DownloadProvider downloadProvider;
    private final Path target;
    private final Task<MojangJavaRemoteFiles> javaDownloadsTask;
    private final List<Task<?>> dependencies = new ArrayList<>();

    private volatile MojangJavaDownloads.JavaDownload download;

    public MojangJavaDownloadTask(DownloadProvider downloadProvider, Path target, GameJavaVersion javaVersion, String platform) {
        this.target = target;
        this.downloadProvider = downloadProvider;
        this.javaDownloadsTask = new GetTask(downloadProvider.injectURLWithCandidates(
                "https://piston-meta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json"))
        .thenComposeAsync(javaDownloadsJson -> {
            MojangJavaDownloads allDownloads = JsonUtils.fromNonNullJson(javaDownloadsJson, MojangJavaDownloads.class);

            Map<String, List<MojangJavaDownloads.JavaDownload>> osDownloads = allDownloads.getDownloads().get(platform);
            if (osDownloads == null || !osDownloads.containsKey(javaVersion.component()))
                throw new UnsupportedPlatformException("Unsupported platform: " + platform);
            List<MojangJavaDownloads.JavaDownload> candidates = osDownloads.get(javaVersion.component());
            for (MojangJavaDownloads.JavaDownload download : candidates) {
                if (JavaInfo.parseVersion(download.getVersion().getName()) >= javaVersion.majorVersion()) {
                    this.download = download;
                    return new GetTask(downloadProvider.injectURLWithCandidates(download.getManifest().getUrl()));
                }
            }
            throw new UnsupportedPlatformException("Candidates: " + JsonUtils.GSON.toJson(candidates));
        })
        .thenApplyAsync(javaDownloadJson -> JsonUtils.fromNonNullJson(javaDownloadJson, MojangJavaRemoteFiles.class));
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return Collections.singleton(javaDownloadsTask);
    }

    @Override
    public void execute() throws Exception {
        for (Map.Entry<String, MojangJavaRemoteFiles.Remote> entry : javaDownloadsTask.getResult().getFiles().entrySet()) {
            Path dest = target.resolve(entry.getKey());
            if (entry.getValue() instanceof MojangJavaRemoteFiles.RemoteFile) {
                MojangJavaRemoteFiles.RemoteFile file = ((MojangJavaRemoteFiles.RemoteFile) entry.getValue());

                // Use local file if it already exists
                try {
                    BasicFileAttributes localFileAttributes = Files.readAttributes(dest, BasicFileAttributes.class);
                    if (localFileAttributes.isRegularFile() && file.getDownloads().containsKey("raw")) {
                        DownloadInfo downloadInfo = file.getDownloads().get("raw");
                        if (localFileAttributes.size() == downloadInfo.getSize()) {
                            ChecksumMismatchException.verifyChecksum(dest, "SHA-1", downloadInfo.getSha1());
                            LOG.info("Skip existing file: " + dest);
                            continue;
                        }
                    }
                } catch (IOException ignored) {
                }

                if (file.getDownloads().containsKey("lzma")) {
                    DownloadInfo download = file.getDownloads().get("lzma");
                    Path tempFile = target.resolve(entry.getKey() + ".lzma");
                    var task = new FileDownloadTask(downloadProvider.injectURLWithCandidates(download.getUrl()), tempFile, new FileDownloadTask.IntegrityCheck("SHA-1", download.getSha1()));
                    task.setName(entry.getKey());
                    dependencies.add(task.thenRunAsync(() -> {
                        Path decompressed = target.resolve(entry.getKey() + ".tmp");
                        try (LZMAInputStream input = new LZMAInputStream(Files.newInputStream(tempFile))) {
                            Files.copy(input, decompressed, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            throw new ArtifactMalformedException("File " + entry.getKey() + " is malformed", e);
                        }

                        try {
                            Files.deleteIfExists(tempFile);
                        } catch (IOException e) {
                            LOG.warning("Failed to delete temporary file: " + tempFile, e);
                        }

                        Files.move(decompressed, dest, StandardCopyOption.REPLACE_EXISTING);
                        if (file.isExecutable()) {
                            FileUtils.setExecutable(dest);
                        }
                    }));
                } else if (file.getDownloads().containsKey("raw")) {
                    DownloadInfo download = file.getDownloads().get("raw");
                    var task = new FileDownloadTask(downloadProvider.injectURLWithCandidates(download.getUrl()), dest, new FileDownloadTask.IntegrityCheck("SHA-1", download.getSha1()));
                    task.setName(entry.getKey());
                    if (file.isExecutable()) {
                        dependencies.add(task.thenRunAsync(() -> FileUtils.setExecutable(dest)));
                    } else {
                        dependencies.add(task);
                    }
                } else {
                    continue;
                }
            } else if (entry.getValue() instanceof MojangJavaRemoteFiles.RemoteDirectory) {
                Files.createDirectories(dest);
            } else if (entry.getValue() instanceof MojangJavaRemoteFiles.RemoteLink) {
                MojangJavaRemoteFiles.RemoteLink link = ((MojangJavaRemoteFiles.RemoteLink) entry.getValue());
                Files.deleteIfExists(dest);
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
        setResult(new Result(download, javaDownloadsTask.getResult()));
    }

    public static final class Result {
        public final MojangJavaDownloads.JavaDownload download;
        public final MojangJavaRemoteFiles remoteFiles;

        public Result(MojangJavaDownloads.JavaDownload download, MojangJavaRemoteFiles remoteFiles) {
            this.download = download;
            this.remoteFiles = remoteFiles;
        }
    }
}
