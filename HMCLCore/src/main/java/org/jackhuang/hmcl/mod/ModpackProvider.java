/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2022  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.mod;

import com.google.gson.JsonParseException;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.task.Task;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

public interface ModpackProvider {

    String getName();

    Task<?> createCompletionTask(DefaultDependencyManager dependencyManager, String version);

    Task<?> createUpdateTask(DefaultDependencyManager dependencyManager, String name, File zipFile, Modpack modpack) throws MismatchedModpackTypeException;

    /**
     * @param zipFile the opened modpack zip file.
     * @param file the modpack zip file path.
     * @param encoding encoding of zip file.
     * @throws IOException if the file is not a valid zip file.
     * @throws JsonParseException if the manifest.json is missing or malformed.
     * @return the manifest.
     */
    Modpack readManifest(ZipFile zipFile, Path file, Charset encoding) throws IOException, JsonParseException;

    default void injectLaunchOptions(String modpackConfigurationJson, LaunchOptions.Builder builder) {
    }
}
