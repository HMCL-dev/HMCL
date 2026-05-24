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

/// Checks whether a Modrinth modpack with `fileApi` has a newer version available on the remote server.
///
/// The result is `true` if an update is available, `false` otherwise. When an update is found,
/// the downloaded `server.mrpack` path and remote version id are stored in static fields and can
/// be retrieved via [getPendingUpdateFile] and [getPendingUpdateVersionId].
/// Call [consumePendingUpdate] to retrieve and clear the pending update info.
public class ModrinthCheckServerPackUpdateTask extends Task<Boolean> {

    private static @Nullable Path updateFilePath = null;

    private final String versionId;
    private final FileDownloadTask downloadTask;

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
