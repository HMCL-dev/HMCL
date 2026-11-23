// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.java.mojang;

import org.jackhuang.hmcl.download.ArtifactMalformedException;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.DownloadInfo;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.java.JavaInfo;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.task.TaskName;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.ChecksumMismatchException;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.UnsupportedPlatformException;
import org.jetbrains.annotations.Nullable;
import org.tukaani.xz.LZMAInputStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class MojangJavaDownloadTask extends Task<MojangJavaDownloadTask.Result> {

    private static final String JAVA_LIST_URL = "https://piston-meta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

    private final DownloadProvider downloadProvider;
    private final Path target;
    private final Path tempDir;
    /// Runtime component and minimum Java major version requested by the manifest.
    private final GameJavaVersion javaVersion;
    /// Platform key used by the Mojang runtime catalog.
    private final String platform;

    /// Creates a runtime download that stages files in the supplied temporary directory.
    public MojangJavaDownloadTask(DownloadProvider downloadProvider, Path target, Path tempDir, GameJavaVersion javaVersion, String platform) {
        this.target = target;
        this.tempDir = tempDir;
        this.downloadProvider = downloadProvider;
        this.javaVersion = javaVersion;
        this.platform = platform;
    }

    /// Returns the first compatible runtime advertised for the requested platform and component.
    private MojangJavaDownloads.JavaDownload selectDownload(MojangJavaDownloads catalog) throws UnsupportedPlatformException {
        @Nullable Map<String, List<MojangJavaDownloads.JavaDownload>> components = catalog.downloads().get(platform);
        @Nullable List<MojangJavaDownloads.JavaDownload> candidates = components == null ? null : components.get(javaVersion.component());
        if (candidates == null) {
            throw new UnsupportedPlatformException("Unsupported platform: " + platform);
        }
        for (MojangJavaDownloads.JavaDownload candidate : candidates) {
            if (JavaInfo.parseVersion(candidate.version().name()) >= javaVersion.majorVersion()) {
                return candidate;
            }
        }
        throw new UnsupportedPlatformException("Candidates: " + JsonUtils.GSON.toJson(candidates));
    }

    /// Downloads the selected runtime into the staging directory and installs it after its files are complete.
    @Override
    public Result execute(TaskExecutionContext context) throws Exception {
        String catalog = context.run(new GetTask(downloadProvider.getDownloadCandidates(JAVA_LIST_URL)));
        MojangJavaDownloads.JavaDownload runtime = selectDownload(JsonUtils.fromNonNullJson(catalog, MojangJavaDownloads.class));
        String manifest = context.run(new GetTask(downloadProvider.getDownloadCandidates(runtime.manifest().getUrl())));
        MojangJavaRemoteFiles files = JsonUtils.fromNonNullJson(manifest, MojangJavaRemoteFiles.class);
        List<Task<?>> dependencies = new ArrayList<>();
        for (Map.Entry<String, MojangJavaRemoteFiles.Remote> entry : files.files().entrySet()) {
            Path dest = tempDir.resolve(entry.getKey());
            if (entry.getValue() instanceof MojangJavaRemoteFiles.RemoteFile file) {
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
                    @Nullable DownloadInfo raw = file.getDownloads().get("raw");

                    @Nullable String rawSha1;
                    if (raw != null && raw.getSha1() != null) {
                        rawSha1 = raw.getSha1();
                    } else {
                        rawSha1 = null;
                    }

                    Path tempFile = tempDir.resolve(entry.getKey() + ".lzma");
                    var task = new FileDownloadTask(downloadProvider.getDownloadCandidates(download.getUrl()), tempFile,
                            new FileDownloadTask.IntegrityCheck("SHA-1", download.getSha1()));
                    task.setName(new TaskName.Simple(entry.getKey()));
                    dependencies.add(task.thenRunAsync(() -> {
                        Path decompressed = tempDir.resolve(entry.getKey() + ".tmp");
                        var digest = MessageDigest.getInstance("SHA-1");
                        try (var input = new DigestInputStream(new LZMAInputStream(Files.newInputStream(tempFile)), digest)) {
                            Files.copy(input, decompressed, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            throw new ArtifactMalformedException("File " + entry.getKey() + " is malformed", e);
                        }

                        String actualSha1 = HexFormat.of().formatHex(digest.digest());

                        if (rawSha1 != null && !actualSha1.equalsIgnoreCase(rawSha1)) {
                            throw new ArtifactMalformedException("File " + entry.getKey() + " has incorrect SHA-1 hash: expected " + rawSha1 + ", got " + actualSha1);
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
                    var task = new FileDownloadTask(downloadProvider.getDownloadCandidates(download.getUrl()), dest, new FileDownloadTask.IntegrityCheck("SHA-1", download.getSha1()));
                    task.setName(new TaskName.Simple(entry.getKey()));
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
            } else if (entry.getValue() instanceof MojangJavaRemoteFiles.RemoteLink link) {
                Files.deleteIfExists(dest);
                Files.createSymbolicLink(dest, Paths.get(link.getTarget()));
            }
        }
        context.runAll(dependencies, FetchTask.getDownloadExecutorConcurrency());

        FileUtils.cleanDirectory(target);

        if (Files.getFileStore(target).equals(Files.getFileStore(tempDir))) {
            Files.move(tempDir, target, StandardCopyOption.REPLACE_EXISTING);
        } else {
            FileUtils.copyDirectory(tempDir, target);
            FileUtils.deleteDirectory(tempDir);
        }
        return new Result(runtime, files);
    }

    public record Result(MojangJavaDownloads.JavaDownload download, MojangJavaRemoteFiles remoteFiles) {
    }
}
