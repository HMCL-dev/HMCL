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
package org.jackhuang.hmcl.gradle.docs;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/// @author Glavo
public abstract class UpdateDocuments extends DefaultTask {

    @InputDirectory
    public abstract DirectoryProperty getDocumentsDir();

    // ---

    private void updateDocument(Document document) throws IOException {
        StringBuilder outputBuilder = new StringBuilder(8192);

        for (Document.Item item : document.items()) {
            if (item instanceof Document.Line line) {
                MacroProcessor.processLine(outputBuilder, line.content(), document);
            } else if (item instanceof Document.MacroBlock macro) {
                var processor = MacroProcessor.valueOf(macro.name());
                processor.apply(document, macro, outputBuilder);
            } else
                throw new IllegalArgumentException("Unknown item type: " + item.getClass());
        }

        Files.writeString(document.file(), outputBuilder.toString());
    }

    private void processDocuments(DocumentFileTree tree) throws IOException {
        for (LocalizedDocument localizedDocument : tree.getFiles().values()) {
            for (Document document : localizedDocument.getDocuments().values()) {
                updateDocument(document);
            }
        }

        for (DocumentFileTree subTree : tree.getChildren().values()) {
            processDocuments(subTree);
        }
    }

    @TaskAction
    public void run() throws IOException {
        Path rootDir = getDocumentsDir().get().getAsFile().toPath();
        processDocuments(DocumentFileTree.load(rootDir));
    }
}
