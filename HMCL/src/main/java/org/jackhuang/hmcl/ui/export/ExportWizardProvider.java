/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui.export;

import javafx.scene.Node;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.game.HMCLModpackExportTask;
import org.jackhuang.hmcl.game.HMCLModpackManager;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.setting.Config;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;
import org.jackhuang.hmcl.util.ZipEngine;

import java.io.File;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ExportWizardProvider implements WizardProvider {
    private final Profile profile;
    private final String version;

    public ExportWizardProvider(Profile profile, String version) {
        this.profile = profile;
        this.version = version;
    }

    @Override
    public void start(Map<String, Object> settings) {
    }

    @Override
    public Object finish(Map<String, Object> settings) {
        @SuppressWarnings("unchecked")
        List<String> whitelist = (List<String>) settings.get(ModpackFileSelectionPage.MODPACK_FILE_SELECTION);
        List<File> launcherJar = Launcher.getCurrentJarFiles();
        boolean includeLauncher = (Boolean) settings.get(ModpackInfoPage.MODPACK_INCLUDE_LAUNCHER) && launcherJar != null;

        return new Task() {
            Task dependency = null;

            @Override
            public void execute() throws Exception {
                File modpackFile = (File) settings.get(ModpackInfoPage.MODPACK_FILE);
                File tempModpack = includeLauncher ? Files.createTempFile("hmcl", ".zip").toFile() : modpackFile;

                dependency = new HMCLModpackExportTask(profile.getRepository(), version, whitelist,
                        new Modpack(
                                (String) settings.get(ModpackInfoPage.MODPACK_NAME),
                                (String) settings.get(ModpackInfoPage.MODPACK_AUTHOR),
                                (String) settings.get(ModpackInfoPage.MODPACK_VERSION),
                                null,
                                (String) settings.get(ModpackInfoPage.MODPACK_DESCRIPTION),
                                null
                        ), tempModpack);

                if (includeLauncher) {
                    dependency = dependency.then(Task.of(() -> {
                        boolean flag = true;

                        try (ZipEngine zip = new ZipEngine(modpackFile)) {
                            Config config = Settings.INSTANCE.getRawConfig();

                            config.hasProxy.set(false);
                            config.selectedProfile.set("");
                            config.commonDirectory.set(null);
                            config.fontFamily.set("Consolas");
                            config.fontSize.set(12);
                            config.localization.set(null);
                            config.accounts.clear();
                            config.selectedAccount.set("");
                            config.logLines.set(100);
                            config.configurations.clear();

                            zip.putTextFile(config.toJson(), Settings.SETTINGS_FILE_NAME);
                            zip.putFile(tempModpack, "modpack.zip");

                            File bg = new File("bg").getAbsoluteFile();
                            if (bg.isDirectory())
                                zip.putDirectory(bg);

                            File background_png = new File("background.png").getAbsoluteFile();
                            if (background_png.isFile())
                                zip.putFile(background_png, "background.png");

                            File background_jpg = new File("background.jpg").getAbsoluteFile();
                            if (background_jpg.isFile())
                                zip.putFile(background_jpg, "background.jpg");

                            for (File jar : launcherJar)
                                zip.putFile(jar, jar.getName());
                        }
                    }));
                }
            }

            @Override
            public Collection<? extends Task> getDependencies() {
                return Collections.singleton(dependency);
            }
        };
    }

    @Override
    public Node createPage(WizardController controller, int step, Map<String, Object> settings) {
        switch (step) {
            case 0: return new ModpackInfoPage(controller, version);
            case 1: return new ModpackFileSelectionPage(controller, profile, version, HMCLModpackManager::suggestMod);
            default: throw new IllegalArgumentException("step");
        }
    }

    @Override
    public boolean cancel() {
        return true;
    }
}
