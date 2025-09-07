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
import java.util.*;
import java.util.regex.Pattern;

/// @author Glavo
public enum MacroProcessor {
    BLOCK {
        @Override
        public void apply(Document document, Document.MacroBlock macroBlock, StringBuilder outputBuilder) throws IOException {
            MacroProcessor.writeBegin(outputBuilder, macroBlock);
            MacroProcessor.writeProperties(outputBuilder, macroBlock);
            for (String line : macroBlock.contentLines()) {
                outputBuilder.append(line).append('\n');
            }
            MacroProcessor.writeEnd(outputBuilder, macroBlock);
        }
    },
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
                    if (targetDoc != null) {
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

                outputBuilder.append('\n');
            }
            MacroProcessor.writeEnd(outputBuilder, macroBlock);
        }
    },
    COPY {
        private record Replace(Pattern pattern, String replacement) {
        }

        private static IllegalArgumentException illegalReplace(String value) {
            return new IllegalArgumentException("Illegal replacement pattern: " + value);
        }

        private static Replace parseReplace(String value) {
            List<String> list = MacroProcessor.parseStringList(value);
            if (list.size() != 2)
                throw illegalReplace(value);

            return new Replace(Pattern.compile(list.get(0)), list.get(1));
        }

        @Override
        public void apply(Document document, Document.MacroBlock macroBlock, StringBuilder outputBuilder) throws IOException {
            var mutableProperties = new LinkedHashMap<>(macroBlock.properties());

            String name = MacroProcessor.removeSingleProperty(mutableProperties, "NAME");
            if (name == null)
                throw new IllegalArgumentException("Missing property: name");

            List<Replace> replaces = Objects.requireNonNullElse(mutableProperties.remove("REPLACE"), List.<String>of())
                    .stream()
                    .map(it -> parseReplace(it))
                    .toList();

            if (!mutableProperties.isEmpty())
                throw new IllegalArgumentException("Unsupported properties: " + mutableProperties.keySet());


            LocalizedDocument localizedDocument = document.directory().getFiles().get(document.name());
            Document fromDocument;
            if (localizedDocument == null || (fromDocument = localizedDocument.getDocuments().get(DocumentLocale.ENGLISH)) == null)
                throw new IOException("Document " + document.name() + " for english does not exist");

            List<String> nameList = List.of(name);

            var fromBlock = (Document.MacroBlock) fromDocument.items().stream()
                    .filter(it -> it instanceof Document.MacroBlock macro
                            && macro.name().equals(BLOCK.name())
                            && nameList.equals(macro.properties().get("NAME"))
                    )
                    .findFirst()
                    .orElseThrow(() -> new IOException("Cannot find the block \"" + name + "\" in " + fromDocument.file()));

            MacroProcessor.writeBegin(outputBuilder, macroBlock);
            MacroProcessor.writeProperties(outputBuilder, macroBlock);
            for (String line : fromBlock.contentLines()) {
                for (Replace replace : replaces) {
                    line = replace.pattern.matcher(line).replaceAll(replace.replacement());
                }
                outputBuilder.append(line).append('\n');
            }
            MacroProcessor.writeEnd(outputBuilder, macroBlock);
        }
    },
    ;

    private static String removeSingleProperty(Map<String, List<String>> properties, String name) {
        List<String> values = properties.remove(name);
        if (values == null || properties.isEmpty())
            return null;

        if (values.size() != 1)
            throw new IllegalArgumentException("Unexpected number of property " + name + ": " + values.size());

        return values.get(0);
    }

    private static List<String> parseStringList(String str) {
        if (str.isBlank()) {
            return new ArrayList<>();
        }

        // Split the string with ' and space cleverly.
        ArrayList<String> parts = new ArrayList<>(2);

        boolean hasValue = false;
        StringBuilder current = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); ) {
            char c = str.charAt(i);
            if (c == '\'' || c == '"') {
                hasValue = true;
                int end = str.indexOf(c, i + 1);
                if (end < 0) {
                    end = str.length();
                }
                current.append(str, i + 1, end);
                i = end + 1;

            } else if (c == ' ' || c == '\t') {
                if (hasValue) {
                    parts.add(current.toString());
                    current.setLength(0);
                    hasValue = false;
                }
                i++;
            } else {
                hasValue = true;
                current.append(c);
                i++;
            }
        }
        if (hasValue)
            parts.add(current.toString());

        return parts;
    }

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

    private static void writeProperties(StringBuilder builder, Document.MacroBlock macroBlock) throws IOException {
        macroBlock.properties().forEach((key, values) -> {
            for (String value : values) {
                builder.append("<!-- #PROPERTY ").append(key).append('=');
                Document.writePropertyValue(builder, value);
                builder.append(" -->\n");
            }
        });
    }

    public abstract void apply(Document document,
                               Document.MacroBlock macroBlock,
                               StringBuilder outputBuilder) throws IOException;
}
