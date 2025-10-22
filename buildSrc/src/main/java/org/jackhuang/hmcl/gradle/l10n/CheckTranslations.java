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
package org.jackhuang.hmcl.gradle.l10n;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;

/// @author Glavo
public abstract class CheckTranslations extends DefaultTask {

    private static final Logger LOGGER = Logging.getLogger(CheckTranslations.class);

    @InputFile
    public abstract RegularFileProperty getEnglishFile();

    @InputFile
    public abstract RegularFileProperty getSimplifiedChineseFile();

    @InputFile
    public abstract RegularFileProperty getTraditionalChineseFile();

    @InputFile
    public abstract RegularFileProperty getClassicalChineseFile();

    @TaskAction
    public void run() throws IOException {
        Checker checker = new Checker();

        var english = new PropertiesFile(getEnglishFile());
        var simplifiedChinese = new PropertiesFile(getSimplifiedChineseFile());
        var traditionalChinese = new PropertiesFile(getTraditionalChineseFile());
        var classicalChinese = new PropertiesFile(getClassicalChineseFile());

        simplifiedChinese.forEach((key, value) -> {
            checker.checkKeyExists(english, key);
            checker.checkKeyExists(traditionalChinese, key);

            checker.checkMisspelled(simplifiedChinese, key, value, "账户", "帐户");
            checker.checkMisspelled(simplifiedChinese, key, value, "其他", "其它");

            checker.checkMisspelled(simplifiedChinese, key, value, "(", "（");
            checker.checkMisspelled(simplifiedChinese, key, value, ")", "）");
        });

        traditionalChinese.forEach((key, value) -> {
            checker.checkMisspelled(traditionalChinese, key, value, "(", "（");
            checker.checkMisspelled(traditionalChinese, key, value, ")", "）");
        });

        classicalChinese.forEach((key, value) -> {
            checker.checkMisspelled(classicalChinese, key, value, "綫", "線");
            checker.checkMisspelled(classicalChinese, key, value, "爲", "為");
            checker.checkMisspelled(classicalChinese, key, value, "啟", "啓");
        });

        checker.check();
    }

    private static final class PropertiesFile {
        final Path path;
        final Properties properties = new Properties();

        PropertiesFile(RegularFileProperty property) throws IOException {
            this(property.getAsFile().get().toPath().toAbsolutePath().normalize());
        }

        PropertiesFile(Path path) throws IOException {
            this.path = path;
            try (var reader = Files.newBufferedReader(path)) {
                properties.load(reader);
            }
        }

        public String getFileName() {
            return path.getFileName().toString();
        }

        public void forEach(BiConsumer<String, String> consumer) {
            properties.forEach((key, value) -> consumer.accept(key.toString(), value.toString()));
        }
    }

    private static final class Checker {

        private final Map<PropertiesFile, Map<Class<?>, Set<Problem>>> problems = new LinkedHashMap<>();
        private int problemsCount;

        public void checkKeyExists(PropertiesFile file, String key) {
            if (!file.properties.containsKey(key)) {
                onFailure(file, new Problem.MissingKey(key));
            }
        }

        public void checkMisspelled(PropertiesFile file, String key, String value,
                                    String correct, String misspelled) {
            if (value.contains(misspelled)) {
                onFailure(file, new Problem.Misspelled(correct, misspelled));
            }
        }

        public void onFailure(PropertiesFile file, Problem problem) {
            problemsCount++;
            problems.computeIfAbsent(file, ignored -> new HashMap<>())
                    .computeIfAbsent(problem.getClass(), ignored -> new LinkedHashSet<>())
                    .add(problem);
        }

        public void check() {
            if (problemsCount > 0) {
                problems.forEach((file, problems) -> {
                    problems.values().stream().flatMap(Collection::stream).forEach(problem ->
                            LOGGER.warn("{}: {}", file.getFileName(), problem.getMessage()));
                });

                throw new GradleException("Failed to check translations, " + problemsCount + " found problems.");
            }
        }
    }

    private static abstract sealed class Problem {
        public abstract String getMessage();

        private static final class MissingKey extends Problem {
            private final String key;

            MissingKey(String key) {
                this.key = key;
            }

            @Override
            public String getMessage() {
                return "missing key '%s'".formatted(key);
            }
        }

        private static final class Misspelled extends Problem {
            private final String correct;
            private final String misspelled;

            Misspelled(String correct, String misspelled) {
                this.correct = correct;
                this.misspelled = misspelled;
            }

            @Override
            public String getMessage() {
                return "misspelled '%s' should be replaced by '%s'".formatted(misspelled, correct);
            }

            @Override
            public int hashCode() {
                return misspelled.hashCode();
            }

            @Override
            public boolean equals(Object obj) {
                return obj instanceof Misspelled that && this.misspelled.equals(that.misspelled);
            }
        }

    }
}
