/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
import org.jackhuang.hmcl.game.HMCLModpackExportTask;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.game.HMCLModpackManager.MODPACK_PREDICATE;

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
        return new HMCLModpackExportTask(profile.getRepository(), version, (List<String>) settings.get(ModpackFileSelectionPage.MODPACK_FILE_SELECTION),
                new Modpack(
                        (String) settings.get(ModpackInfoPage.MODPACK_NAME),
                        (String) settings.get(ModpackInfoPage.MODPACK_AUTHOR),
                        (String) settings.get(ModpackInfoPage.MODPACK_VERSION),
                        null,
                        (String) settings.get(ModpackInfoPage.MODPACK_DESCRIPTION),
                        null
                ), (File) settings.get(ModpackInfoPage.MODPACK_FILE));
    }

    @Override
    public Node createPage(WizardController controller, int step, Map<String, Object> settings) {
        switch (step) {
            case 0: return new ModpackInfoPage(controller, version);
            case 1: return new ModpackFileSelectionPage(controller, profile, version, MODPACK_PREDICATE);
            default: throw new IllegalArgumentException("step");
        }
    }

    @Override
    public boolean cancel() {
        return true;
    }
}
