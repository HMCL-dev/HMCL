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
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/// @author Glavo
public abstract class UpdateDocuments extends DefaultTask {

    @InputDirectory
    public abstract DirectoryProperty getDocumentsDir();

    // ---

    private static final Pattern LINK_PATTERN = Pattern.compile(
            "(?<=]\\()[a-zA-Z0-9_\\-./]+\\.md(?=\\))"
    );

    private void processLine(StringBuilder outputBuilder, String line, Document document) {
        outputBuilder.append(LINK_PATTERN.matcher(line).replaceAll(matchResult -> {
            String rawLink = matchResult.group();
            String[] splitPath = rawLink.split("/");

            if (splitPath.length == 0)
                return rawLink;

            String fileName = splitPath[splitPath.length - 1];
            if (!fileName.endsWith(".md"))
                return rawLink;

            DocumentFileTree current = document.directory();
            for (int i = 0; i < splitPath.length - 1; i++) {
                String name = splitPath[i];
                switch (name) {
                    case "" -> {
                        return rawLink;
                    }
                    case "." -> {
                        continue;
                    }
                    case ".." -> {
                        current = current.getParent();
                        if (current == null)
                            return rawLink;
                    }
                    default -> {
                        current = current.getChildren().get(name);
                        if (current == null)
                            return rawLink;
                    }
                }
            }

            DocumentLocale.LocaleAndName currentLocaleAndName = DocumentLocale.parseFileName(fileName.substring(0, fileName.length() - ".md".length()));
            LocalizedDocument localizedDocument = current.getFiles().get(currentLocaleAndName.name());
            if (localizedDocument != null) {
                List<DocumentLocale> candidateLocales = document.locale().getCandidates();
                for (DocumentLocale candidateLocale : candidateLocales) {
                    if (candidateLocale == currentLocaleAndName.locale())
                        return rawLink;

                    Document targetDoc = localizedDocument.getDocuments().get(candidateLocale);
                    if (targetDoc != null) {
                        splitPath[splitPath.length - 1] = targetDoc.file().getFileName().toString();
                        return String.join("/", splitPath);
                    }
                }
            }

            return rawLink;
        })).append('\n');
    }

    private void updateDocument(Document document) throws IOException {
        StringBuilder outputBuilder = new StringBuilder(8192);

        for (Document.Item item : document.items()) {
            if (item instanceof Document.Line line) {
                processLine(outputBuilder, line.content(), document);
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
