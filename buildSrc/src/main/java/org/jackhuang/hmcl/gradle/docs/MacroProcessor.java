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

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/// @author Glavo
public enum MacroProcessor {
    LANGUAGE_SWITCHER {
        private static <T> boolean containsIdentity(List<T> list, T element) {
            for (T t : list) {
                if (t == element)
                    return true;
            }

            return false;
        }

        @Override
        public void apply(Document document,
                          Document.MacroBlock macroBlock,
                          StringBuilder outputBuilder) throws IOException {
            LocalizedDocument localized = document.directory().getFiles().get(document.name());
            if (localized == null || localized.getDocuments().isEmpty())
                throw new AssertionError("Document " + document.name() + " does not exist");

            MacroProcessor.writeBegin(outputBuilder, macroBlock);
            if (localized.getDocuments().size() > 1) {
                var languageToDocs = new LinkedHashMap<String, List<Document>>();
                for (DocumentLocale locale : DocumentLocale.values()) {
                    Document targetDoc = localized.getDocuments().get(locale);
                    if (localized.getDocuments().containsKey(locale)) {
                        languageToDocs.computeIfAbsent(locale.getLanguageDisplayName(), name -> new ArrayList<>(1))
                                .add(targetDoc);
                    }
                }

                boolean firstLanguage = true;

                for (var entry : languageToDocs.entrySet()) {
                    if (firstLanguage)
                        firstLanguage = false;
                    else
                        outputBuilder.append(" | ");

                    String languageName = entry.getKey();
                    List<Document> targetDocs = entry.getValue();

                    boolean containsCurrent = containsIdentity(targetDocs, document);
                    if (targetDocs.size() == 1) {
                        if (containsCurrent)
                            outputBuilder.append("**").append(languageName).append("**");
                        else
                            outputBuilder.append("[").append(languageName).append("](").append(targetDocs.get(0).file().getFileName()).append(")");
                    } else {
                        if (containsCurrent)
                            outputBuilder.append("**").append(languageName).append("**");
                        else
                            outputBuilder.append(languageName);

                        outputBuilder.append(" (");

                        boolean isFirst = true;
                        for (Document targetDoc : targetDocs) {
                            if (isFirst)
                                isFirst = false;
                            else
                                outputBuilder.append(", ");

                            String subLanguage = targetDoc.locale().getSubLanguageDisplayName();

                            if (targetDoc == document) {
                                outputBuilder.append("**").append(subLanguage).append("**");
                            } else {
                                outputBuilder.append('[').append(subLanguage).append("](").append(targetDoc.file().getFileName()).append(")");
                            }
                        }

                        outputBuilder.append(")");
                    }
                }
            }
            MacroProcessor.writeEnd(outputBuilder, macroBlock);
        }
    },
    KEEP {
        @Override
        public void apply(Document document, Document.MacroBlock macroBlock, StringBuilder outputBuilder) throws IOException {
            if (!macroBlock.properties().isEmpty())
                throw new IllegalArgumentException("The KEEP macro must not have properties.");

            MacroProcessor.writeBegin(outputBuilder, macroBlock);
            for (String line : macroBlock.contentLines()) {
                outputBuilder.append(line).append('\n');
            }
            MacroProcessor.writeEnd(outputBuilder, macroBlock);

        }
    };

    private static void writeBegin(StringBuilder builder, Document.MacroBlock macroBlock) throws IOException {
        builder.append("<!-- #BEGIN ");
        builder.append(macroBlock.name());
        builder.append(" -->\n");
    }

    private static void writeEnd(StringBuilder builder, Document.MacroBlock macroBlock) throws IOException {
        builder.append("<!-- #END ");
        builder.append(macroBlock.name());
        builder.append(" -->\n");
    }

    public abstract void apply(Document document,
                               Document.MacroBlock macroBlock,
                               StringBuilder outputBuilder) throws IOException;
}
