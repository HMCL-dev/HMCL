// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
