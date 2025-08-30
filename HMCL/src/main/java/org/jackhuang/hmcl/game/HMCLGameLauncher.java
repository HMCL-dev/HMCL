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

import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.launch.DefaultLauncher;
import org.jackhuang.hmcl.launch.ProcessListener;
import org.jackhuang.hmcl.util.i18n.Locales;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author huangyuhui
 */
public final class HMCLGameLauncher extends DefaultLauncher {

    public HMCLGameLauncher(GameRepository repository, Version version, AuthInfo authInfo, LaunchOptions options) {
        this(repository, version, authInfo, options, null);
    }

    public HMCLGameLauncher(GameRepository repository, Version version, AuthInfo authInfo, LaunchOptions options, ProcessListener listener) {
        this(repository, version, authInfo, options, listener, true);
    }

    public HMCLGameLauncher(GameRepository repository, Version version, AuthInfo authInfo, LaunchOptions options, ProcessListener listener, boolean daemon) {
        super(repository, version, authInfo, options, listener, daemon);
    }

    @Override
    protected Map<String, String> getConfigurations() {
        Map<String, String> res = super.getConfigurations();
        res.put("${launcher_name}", Metadata.NAME);
        res.put("${launcher_version}", Metadata.VERSION);
        return res;
    }

    private void generateOptionsTxt() {
        if (config().isDisableAutoGameOptions())
            return;

        Path runDir = repository.getRunDirectory(version.getId()).toPath();
        Path optionsFile = runDir.resolve("options.txt");
        Path configFolder = runDir.resolve("config");

        if (Files.exists(optionsFile))
            return;

        if (Files.isDirectory(configFolder)) {
            try (Stream<Path> stream = Files.walk(configFolder, 2, FileVisitOption.FOLLOW_LINKS)) {
                if (stream.anyMatch(file ->
                        Files.isRegularFile(file) && "options.txt".equals(file.getFileName().toString())))
                    return;
            } catch (IOException e) {
                LOG.warning("Failed to visit config folder", e);
            }
        }

        Locale locale = Locale.getDefault();
        if (Locales.isEnglish(locale))
            return;

        /*
         *  1.0         : No language option, do not set for these versions
         *  1.1  ~ 1.5  : zh_CN works fine, zh_cn will crash (the last two letters must be uppercase, otherwise it will cause an NPE crash)
         *  1.6  ~ 1.10 : zh_CN works fine, zh_cn will automatically switch to English
         *  1.11 ~ 1.12 : zh_cn works fine, zh_CN will display Chinese but the language setting will incorrectly show English as selected
         *  1.13+       : zh_cn works fine, zh_CN will automatically switch to English
         */
        GameVersionNumber gameVersion = GameVersionNumber.asGameVersion(repository.getGameVersion(version));
        if (gameVersion.compareTo("1.1") < 0)
            return;

        String lang = normalizedLanguageTag(locale, gameVersion);
        if (lang.isEmpty())
            return;

        if (gameVersion.compareTo("1.11") >= 0)
            lang = lang.toLowerCase(Locale.ROOT);

        try {
            Files.createDirectories(optionsFile.getParent());
            Files.writeString(optionsFile, String.format("lang:%s\n", lang));
        } catch (IOException e) {
            LOG.warning("Unable to generate options.txt", e);
        }
    }

    private static String normalizedLanguageTag(Locale locale, GameVersionNumber gameVersion) {
        String language = locale.getLanguage();
        String region = locale.getCountry();
        String script = locale.getScript();

        switch (language) {
            case "zh":
                if (!Locales.isSimplifiedChinese(locale)) {
                    if (region.equals("HK") || region.equals("MO"))
                        return gameVersion.compareTo("1.16") >= 0
                                ? "zh_HK"
                                : "zh_TW";
                    if (region.equals("TW") || script.equals("Hant"))
                        return "zh_TW";
                }
                return "zh_CN";
            case "ru":
                return "ru_RU";
            case "uk":
                return "uk_UA";
            case "es":
                return "es_ES";
            case "ja":
                return "ja_JP";
            case "lzh":
                return "lzh";
            default:
                return "";
        }
    }

    @Override
    public ManagedProcess launch() throws IOException, InterruptedException {
        generateOptionsTxt();
        return super.launch();
    }

    @Override
    public void makeLaunchScript(File scriptFile) throws IOException {
        generateOptionsTxt();
        super.makeLaunchScript(scriptFile);
    }
}
