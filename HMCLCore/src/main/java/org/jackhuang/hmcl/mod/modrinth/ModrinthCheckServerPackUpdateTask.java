/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.mod.modrinth;

import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * Checks whether a Modrinth server modpack has a newer version available on the remote server.
 *
 * The task downloads {@code server.mrpack} from the remote API, extracts the manifest, and compares
 * the remote version ID with the local one using {@link VersionNumber}. The result is {@code true} if
 * an update is available, {@code false} otherwise.
 *
 * When an update is found, the downloaded {@code server.mrpack} path is stored in a static field
 * and can be retrieved via {@link #getUpdateFile()}. If no update is available or an error occurs,
 * the temporary file is deleted.
 *
 * @see ModrinthManifest
 * @see FileDownloadTask
 */
public class ModrinthCheckServerPackUpdateTask extends Task<Boolean> {

    /**
     * The path to the downloaded {@code server.mrpack} file, or {@code null} if no update file is available.
     */
    private static @Nullable Path updateFilePath = null;

    /**
     * The current local version ID of the modpack.
     */
    private final String versionId;

    /**
     * The task that downloads {@code server.mrpack} from the remote API, or {@code null} if no download URL is available.
     */
    private final @Nullable FileDownloadTask downloadTask;

    /**
     * Creates a new update check task.
     *
     * @param versionId the current local version ID of the modpack.
     * @param fileApi   the base URL for downloading the server modpack; if blank, no download will be attempted.
     */
    public ModrinthCheckServerPackUpdateTask(String versionId, String fileApi) {
        this.versionId = versionId;

        if (StringUtils.isNotBlank(fileApi)) {
            try {
                updateFilePath = Files.createTempFile("hmcl-server-auto-update", ".mrpack");
                downloadTask = new FileDownloadTask(fileApi + "/server.mrpack", updateFilePath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            downloadTask = null;
        }
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return downloadTask != null ? Collections.singleton(downloadTask) : Collections.emptySet();
    }

    @Override
    public boolean isRelyingOnDependents() {
        return false;
    }

    /**
     * Returns the path to the downloaded {@code server.mrpack} file, or {@code null} if no update file is available.
     *
     * @return the path to the downloaded update file, or {@code null}
     */
    public static @Nullable Path getUpdateFile() {
        return updateFilePath;
    }

    @Override
    public void execute() throws Exception {
        if (updateFilePath == null || downloadTask == null || downloadTask.getException() != null) {
            setResult(false);
            return;
        }

        try {
            ModrinthManifest remoteManifest;
            try (var zip = CompressingUtils.openZipFile(updateFilePath)) {
                remoteManifest = JsonUtils.fromNonNullJson(
                        CompressingUtils.readTextZipEntry(zip, "modrinth.index.json"),
                        ModrinthManifest.class);
            }

            if (VersionNumber.compare(remoteManifest.getVersionId(), versionId) > 0) {
                setResult(true);
            } else {
                setResult(false);
            }
        } catch (Exception e) {
            LOG.warning("Failed to check for modpack updates", e);
            setResult(false);
        } finally {
            if (!getResult()) {
                try {
                    Files.deleteIfExists(updateFilePath);
                } catch (IOException ignored) {
                }
            }
        }
    }
}
