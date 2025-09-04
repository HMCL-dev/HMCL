/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.gradle.properties;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;

/// @author Glavo
public abstract class PropertiesFormatTask extends DefaultTask {

    @InputFile
    public abstract RegularFileProperty getReferencedFile();

    @OutputFile
    public abstract RegularFileProperty getTargetFile();

    @TaskAction
    public void run() throws IOException {
        PropertiesFile referencedFile = PropertiesFile.load(getReferencedFile().getAsFile().get().toPath());
        PropertiesFile targetFile = PropertiesFile.load(getTargetFile().getAsFile().get().toPath());
        var targetProperties = new LinkedHashMap<>(targetFile.items());

        ByteArrayOutputStream buffer = new ByteArrayOutputStream(256 * 1024);
        try (OutputStreamWriter writer = new OutputStreamWriter(buffer)) {
            for (String key : referencedFile.items().keySet()) {
                PropertiesFile.Item item = targetProperties.remove(key);
                if (item != null) {
                    for (String line : item.lines()) {
                        writer.write(line);
                        writer.write('\n');
                    }
                }
            }

            if (!targetProperties.isEmpty()) {
                writer.write("# TODO: Unknown Properties");
                for (PropertiesFile.Item item : targetProperties.values()) {
                    for (String line : item.lines()) {
                        writer.write(line);
                        writer.write('\n');
                    }
                }
            }
        }

        try (var output = Files.newOutputStream(getTargetFile().getAsFile().get().toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            buffer.writeTo(output);
        }
    }
}
