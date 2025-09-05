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
package org.jackhuang.hmcl.gradle;

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
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
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

        int failedCount;

        public void checkKeyExists(PropertiesFile file, String key) {
            if (!file.properties.containsKey(key)) {
                LOGGER.warn("{} missing key '{}'", file.getFileName(), key);
                onFailure();
            }
        }

        private final Set<Map.Entry<PropertiesFile, String>> reportedMisspellings = new HashSet<>();

        public void checkMisspelled(PropertiesFile file, String key, String value,
                                    String correct, String misspelled) {
            if (value.contains(misspelled)) {
                if (reportedMisspellings.add(Map.entry(file, misspelled))) {
                    LOGGER.warn("The misspelled '{}' in '{}' should be replaced by '{}'",
                            misspelled, file.getFileName(), correct);
                }

                onFailure();
            }
        }

        public void onFailure() {
            failedCount++;
        }

        public void check() {
            if (failedCount > 0)
                throw new GradleException("Failed to check translations, " + failedCount + " found problems.");
        }
    }
}
