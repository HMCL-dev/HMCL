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
package org.jackhuang.hmcl.modpack.server;

import com.google.gson.JsonParseException;
import kala.compress.archivers.zip.ZipArchiveReader;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.modpack.MismatchedModpackTypeException;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.modpack.ModpackProvider;
import org.jackhuang.hmcl.modpack.ModpackUpdateTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

public final class ServerModpackProvider implements ModpackProvider {
    public static final ServerModpackProvider INSTANCE = new ServerModpackProvider();

    @Override
    public String getName() {
        return "Server";
    }

    @Override
    public Task<?> createCompletionTask(DefaultDependencyManager dependencyManager, GameInstanceID instanceId) {
        return new ServerModpackCompletionTask(dependencyManager, instanceId);
    }

    @Override
    public Task<?> createUpdateTask(DefaultDependencyManager dependencyManager, GameInstanceID instanceId, Path zipFile, Modpack modpack) throws MismatchedModpackTypeException {
        if (!(modpack.getManifest() instanceof ServerModpackManifest serverModpackManifest))
            throw new MismatchedModpackTypeException(getName(), modpack.getManifest().getProvider().getName());

        return new ModpackUpdateTask(dependencyManager.getGameRepository(), instanceId, new ServerModpackLocalInstallTask(dependencyManager, zipFile, modpack, serverModpackManifest, instanceId));
    }

    @Override
    public Modpack readManifest(ZipArchiveReader zip, Path file, Charset encoding) throws IOException, JsonParseException {
        String json = CompressingUtils.readTextZipEntry(zip, "server-manifest.json");
        ServerModpackManifest manifest = JsonUtils.fromNonNullJson(json, ServerModpackManifest.class);
        return manifest.toModpack(encoding);
    }
}
