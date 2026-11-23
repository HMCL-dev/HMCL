// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.export;

import javafx.scene.Node;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.modpack.ModAdviser;
import org.jackhuang.hmcl.modpack.ModpackExportInfo;
import org.jackhuang.hmcl.modpack.mcbbs.McbbsModpackExportTask;
import org.jackhuang.hmcl.modpack.modrinth.ModrinthModpackExportTask;
import org.jackhuang.hmcl.modpack.server.ServerModpackExportTask;
import org.jackhuang.hmcl.setting.AuthlibInjectorServerList;
import org.jackhuang.hmcl.setting.FontManager;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.task.TaskSignificance;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.io.Zipper;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class ExportWizardProvider implements WizardProvider {
    private final HMCLGameInstance gameInstance;

    public ExportWizardProvider(HMCLGameInstance gameInstance) {
        this.gameInstance = gameInstance;
    }

    @Override
    public void start(SettingsMap settings) {
    }

    @Override
    public Object finish(SettingsMap settings) {
        List<String> whitelist = settings.get(ModpackFileSelectionPage.MODPACK_FILE_SELECTION);
        Path modpackFile = settings.get(ModpackFileSelectionPage.MODPACK_FILE);
        ModpackExportInfo exportInfo = Objects.requireNonNull(settings.get(ModpackInfoPage.MODPACK_INFO));
        exportInfo.setWhitelist(whitelist);
        String modpackType = settings.get(ModpackTypeSelectionPage.MODPACK_TYPE);

        settings.put("title", i18n("install.modpack.export", exportInfo.getName()));
        return exportWithLauncher(modpackType, exportInfo, modpackFile);
    }

    private Task<?> exportWithLauncher(String modpackType, ModpackExportInfo exportInfo, Path modpackFile) {
        @Nullable Path launcherJar = JarUtils.thisJarPath();
        boolean packWithLauncher = exportInfo.isPackWithLauncher() && launcherJar != null;
        return new Task<@Nullable Void>() {
            {
                setSignificance(TaskSignificance.MODERATE);
            }

            /// Exports the modpack and optionally wraps it with the launcher.
            @Override
            public @Nullable Void execute(TaskExecutionContext context) throws Exception {
                Path destination = packWithLauncher ? Files.createTempFile("hmcl", ".zip") : modpackFile;
                try {
                    HMCLGameInstance instance = resolveCurrentGameInstance();
                    Task<?> export = switch (modpackType) {
                        case ModpackTypeSelectionPage.MODPACK_TYPE_MCBBS -> new McbbsModpackExportTask(instance, exportInfo, destination);
                        case ModpackTypeSelectionPage.MODPACK_TYPE_SERVER -> new ServerModpackExportTask(instance, exportInfo, destination);
                        case ModpackTypeSelectionPage.MODPACK_TYPE_MODRINTH -> new ModrinthModpackExportTask(instance, exportInfo, destination);
                        default -> throw new IllegalStateException("Unrecognized modpack type " + modpackType);
                    };
                    context.run(export);
                    if (packWithLauncher) {
                        bundleLauncher(modpackType, modpackFile, destination, Objects.requireNonNull(launcherJar));
                    }
                    return null;
                } finally {
                    if (packWithLauncher) {
                        try {
                            Files.deleteIfExists(destination);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        };
    }

    /// Bundles the exported modpack, launcher and user settings into a ZIP file.
    private void bundleLauncher(String modpackType, Path modpackFile, Path tempModpack, Path launcherJar) throws IOException {
        try (Zipper zip = new Zipper(modpackFile)) {
            LauncherSettings exported = new LauncherSettings();
            LauncherSettings current = settings();

            exported.versionListSourceProperty().set(current.versionListSourceProperty().get());
            exported.fileDownloadSourceProperty().set(current.fileDownloadSourceProperty().get());
            exported.preferredLoginTypeProperty().set(current.preferredLoginTypeProperty().get());

            zip.putTextFile(exported.toJson(), ".hmcl/config/launcher-settings.json");
            AuthlibInjectorServerList exportedServers = new AuthlibInjectorServerList();
            exportedServers.getServers().setAll(SettingsManager.getAuthlibInjectorServers());
            zip.putTextFile(
                    JsonUtils.GSON.toJson(exportedServers, AuthlibInjectorServerList.class),
                    ".hmcl/config/authlib-injector-servers.json");

            // Bundled package under .hmcl/modpack/ (not the process workdir).
            String packageName = ModpackTypeSelectionPage.MODPACK_TYPE_MODRINTH.equals(modpackType)
                    ? "modpack.mrpack"
                    : "modpack.zip";
            zip.putFile(tempModpack, ".hmcl/" + Metadata.BUNDLED_MODPACK_DIRECTORY_NAME + "/" + packageName);

            for (String extension : FontManager.FONT_EXTENSIONS) {
                String fileName = "font." + extension;
                Path font = Metadata.HMCL_LOCAL_HOME.resolve(fileName);
                if (!Files.isRegularFile(font))
                    font = Metadata.CURRENT_DIRECTORY.resolve(fileName);
                if (Files.isRegularFile(font))
                    zip.putFile(font, ".hmcl/" + fileName);
            }

            zip.putFile(launcherJar, launcherJar.getFileName().toString());
        }
    }

    /// Returns the current registered snapshot for the instance selected by this wizard.
    ///
    /// @return the current registered instance
    private HMCLGameInstance resolveCurrentGameInstance() {
        return gameInstance.getRepository().getInstance(gameInstance.getId());
    }

    @Override
    public Node createPage(WizardController controller, int step, SettingsMap settings) {
        return switch (step) {
            case 0 -> new ModpackTypeSelectionPage(controller);
            case 1 -> new ModpackInfoPage(controller, gameInstance);
            case 2 -> new ModpackFileSelectionPage(controller, gameInstance, ModAdviser::suggestMod);
            default -> throw new IllegalArgumentException("step");
        };
    }

    @Override
    public boolean cancel() {
        return true;
    }
}
