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
package org.jackhuang.hmcl.gradle.addon;

import com.google.gson.*;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/// Rewrites a JSON source file in its canonical compact representation.
@NotNullByDefault
@UntrackedTask(because = "Formats a JSON source file in place")
public abstract class FormatJsonFile extends DefaultTask {

    /// Returns the JSON source file to rewrite.
    @InputFile
    public abstract RegularFileProperty getFile();

    /// Parses and rewrites the configured JSON file if its representation changes.
    ///
    /// @throws IOException if the file cannot be read or written
    /// @throws JsonParseException if the file does not contain valid JSON
    @TaskAction
    public void run() throws IOException {
        Path path = getFile().get().getAsFile().toPath();
        String original = Files.readString(path);

        JsonElement json = JsonParser.parseString(original);
        String formatted = new GsonBuilder()
                .setPrettyPrinting()
                .create()
                .toJson(json);

        if (!formatted.equals(original)) {
            Files.writeString(path, formatted);
        }
    }
}
