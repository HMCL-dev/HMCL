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
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.modpack.ModpackManifest;
import org.jackhuang.hmcl.modpack.ModpackProvider;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.TolerableValidationException;
import org.jackhuang.hmcl.util.gson.Validation;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.MINECRAFT;

public record ServerModpackManifest(String name, String author, String version, String description, String fileApi,
                                    List<ModpackConfiguration.FileInformation> files,
                                    List<Addon> addons) implements ModpackManifest, Validation {

    public ServerModpackManifest() {
        this("", "", "", "", "", Collections.emptyList(), Collections.emptyList());
    }

    @Override
    public ModpackProvider getProvider() {
        return ServerModpackProvider.INSTANCE;
    }

    @Override
    public void validate() throws JsonParseException, TolerableValidationException {
        if (fileApi == null)
            throw new JsonParseException("ServerModpackManifest.fileApi cannot be blank");
        if (files == null)
            throw new JsonParseException("ServerModpackManifest.files cannot be null");
    }

    public record Addon(String id, String version) {
        public Addon() {
            this("", "");
        }
    }

    public Modpack toModpack(Charset encoding) throws IOException {
        String gameVersion = addons.stream().filter(x -> MINECRAFT.getPatchId().equals(x.id)).findAny()
                .orElseThrow(() -> new IOException("Cannot find game version")).version();
        return new Modpack(name, author, version, gameVersion, description, encoding, this) {
            @Override
            public Task<?> getInstallTask(DefaultDependencyManager dependencyManager, Path zipFile, String name, String iconUrl) {
                return new ServerModpackLocalInstallTask(dependencyManager, zipFile, this, ServerModpackManifest.this, name);
            }
        };
    }

}
