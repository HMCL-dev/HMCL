/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.patchpack;

import javafx.scene.Node;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.patchpack.PatchpackHelper;
import org.jackhuang.hmcl.patchpack.PatchpackInfo;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

@NotNullByDefault
public final class PatchpackInstallWizardProvider implements WizardProvider {
    public static final SettingsMap.Key<GameInstanceID> INSTANCE_ID = new SettingsMap.Key<>("PATCH_PACK_INSTANCE_ID");
    public static final SettingsMap.Key<HMCLGameRepository> REPOSITORY = new SettingsMap.Key<>("PATCH_PACK_REPOSITORY");
    public static final SettingsMap.Key<Path> PATCH_PACK_FILE = new SettingsMap.Key<>("PATCH_PACK_FILE");
    public static final SettingsMap.Key<PatchpackInfo> PATCH_PACK_INFO = new SettingsMap.Key<>("PATCH_PACK_INFO");

    private final HMCLGameRepository repository;
    private final GameInstanceID instanceId;
    private final @Nullable Path file;

    public PatchpackInstallWizardProvider(HMCLGameInstance gameInstance) {
        this(gameInstance.getRepository(), gameInstance.getId(), null);
    }

    public PatchpackInstallWizardProvider(HMCLGameInstance gameInstance, Path file) {
        this(gameInstance.getRepository(), gameInstance.getId(), file);
    }

    private PatchpackInstallWizardProvider(HMCLGameRepository repository, GameInstanceID instanceId, @Nullable Path file) {
        this.repository = repository;
        this.instanceId = instanceId;
        this.file = file;
    }

    @Override
    public void start(SettingsMap settings) {
        settings.put(INSTANCE_ID, instanceId);
        settings.put(REPOSITORY, repository);
        if (file != null)
            settings.put(PATCH_PACK_FILE, file);
    }

    @Override
    public Node createPage(WizardController controller, int step, SettingsMap settings) {
        if (file != null) {
            if (step != 0)
                throw new IllegalStateException("error step " + step + ", settings: " + settings + ", pages: " + controller.getPages());
            return new LocalPatchPackPage(controller);
        }

        return switch (step) {
            case 0 -> new PatchPackSelectionPage(controller);
            case 1 -> new LocalPatchPackPage(controller);
            default ->
                    throw new IllegalStateException("error step " + step + ", settings: " + settings + ", pages: " + controller.getPages());
        };
    }

    @Nullable
    @Override
    public Object finish(SettingsMap settings) {
        Path selected = settings.get(PATCH_PACK_FILE);
        PatchpackInfo info = settings.get(PATCH_PACK_INFO);
        @Nullable Charset charset = settings.get(LocalPatchPackPage.PATCH_PACK_CHARSET);
        if (selected == null || info == null || charset == null)
            return null;

        @Nullable HMCLGameInstance instance = repository.findInstance(instanceId);
        if (instance == null) {
            Controllers.dialog(i18n("instance.empty"), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            return null;
        }

        settings.put("title", i18n("patchpack.installing"));
        settings.put("success_message", i18n("install.success"));
        settings.put(FailureCallback.KEY, (ignored, exception, next) ->
                Controllers.dialog(StringUtils.getStackTrace(exception), i18n("install.failed"), MessageDialogPane.MessageType.ERROR, next));

        Path runDirectory = instance.getRunDirectory();
        return PatchpackHelper.getInstallTask(selected, charset, info, runDirectory)
                .setName(i18n("install.patchpack"))
                .thenComposeAsync(repository.refreshAsync());
    }

    @Override
    public boolean cancel() {
        return true;
    }

    public static void install(HMCLGameInstance gameInstance) {
        Controllers.getDecorator().startWizard(new PatchpackInstallWizardProvider(gameInstance), i18n("patchpack.task.install"));
    }

    public static void install(HMCLGameInstance gameInstance, Path file) {
        Controllers.getDecorator().startWizard(new PatchpackInstallWizardProvider(gameInstance, file), i18n("patchpack.task.install"));
    }
}
