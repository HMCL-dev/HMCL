/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.Log4jLevel;
import org.jackhuang.hmcl.util.Pair;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.jackhuang.hmcl.util.Pair.pair;

public class CrashReportAnalyzerTest {
    private List<Pair<String, Log4jLevel>> loadLog(String path) throws IOException {
        List<Pair<String, Log4jLevel>> logs = new ArrayList<>();
        InputStream is = CrashReportAnalyzerTest.class.getResourceAsStream(path);
        if (is == null) {
            throw new IllegalStateException("Resource not found: " + path);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logs.add(pair(line, Log4jLevel.ERROR));
            }
        }
        return logs;
    }

    private CrashReportAnalyzer.Result findResultByRule(List<CrashReportAnalyzer.Result> results, CrashReportAnalyzer.Rule rule) {
        CrashReportAnalyzer.Result r = results.stream().filter(result -> result.getRule() == rule).findFirst().orElse(null);
        Assert.assertNotNull(r);
        return r;
    }

    @Test
    public void tooOldJava() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/logs/too_old_java.txt")),
                CrashReportAnalyzer.Rule.TOO_OLD_JAVA);
        Assert.assertEquals("60", result.getMatcher().group("expected"));
    }

    @Test
    public void tooOldJava1() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/too_old_java.txt")),
                CrashReportAnalyzer.Rule.TOO_OLD_JAVA);
        Assert.assertEquals("52", result.getMatcher().group("expected"));
    }

    @Test
    public void tooOldJava2() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/too_old_java2.txt")),
                CrashReportAnalyzer.Rule.TOO_OLD_JAVA);
        Assert.assertEquals("52", result.getMatcher().group("expected"));
    }

    @Test
    public void securityException() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/security.txt")),
                        CrashReportAnalyzer.Rule.FILE_CHANGED);
        Assert.assertEquals("assets/minecraft/texts/splashes.txt", result.getMatcher().group("file"));
    }

    @Test
    public void noClassDefFoundError1() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/no_class_def_found_error.txt")),
                CrashReportAnalyzer.Rule.NO_CLASS_DEF_FOUND_ERROR);
        Assert.assertEquals("blk", result.getMatcher().group("class"));
    }

    @Test
    public void noClassDefFoundError2() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/no_class_def_found_error2.txt")),
                CrashReportAnalyzer.Rule.NO_CLASS_DEF_FOUND_ERROR);
        Assert.assertEquals("cer", result.getMatcher().group("class"));
    }

    @Test
    public void fileAlreadyExists() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/file_already_exists.txt")),
                CrashReportAnalyzer.Rule.FILE_ALREADY_EXISTS);
        Assert.assertEquals(
                "D:\\Games\\Minecraft\\Minecraft Longtimeusing\\.minecraft\\versions\\1.12.2-forge1.12.2-14.23.5.2775\\config\\pvpsettings.txt",
                result.getMatcher().group("file"));
    }

    @Test
    public void loaderExceptionModCrash() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/loader_exception_mod_crash.txt")),
                CrashReportAnalyzer.Rule.LOADING_CRASHED);
        Assert.assertEquals("Better PvP", result.getMatcher().group("name"));
        Assert.assertEquals("xaerobetterpvp", result.getMatcher().group("id"));
    }

    @Test
    public void loaderExceptionModCrash2() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/loader_exception_mod_crash2.txt")),
                CrashReportAnalyzer.Rule.LOADING_CRASHED);
        Assert.assertEquals("Inventory Sort", result.getMatcher().group("name"));
        Assert.assertEquals("invsort", result.getMatcher().group("id"));
    }

    @Test
    public void loaderExceptionModCrash3() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/loader_exception_mod_crash3.txt")),
                CrashReportAnalyzer.Rule.LOADING_CRASHED);
        Assert.assertEquals("SuperOres", result.getMatcher().group("name"));
        Assert.assertEquals("superores", result.getMatcher().group("id"));
    }

    @Test
    public void loaderExceptionModCrash4() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/loader_exception_mod_crash4.txt")),
                CrashReportAnalyzer.Rule.LOADING_CRASHED);
        Assert.assertEquals("Kathairis", result.getMatcher().group("name"));
        Assert.assertEquals("kathairis", result.getMatcher().group("id"));
    }

    @Test
    public void graphicsDriver() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/graphics_driver.txt")),
                CrashReportAnalyzer.Rule.GRAPHICS_DRIVER);
    }

    @Test
    public void splashScreen() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/splashscreen.txt")),
                CrashReportAnalyzer.Rule.GRAPHICS_DRIVER);
    }
}
