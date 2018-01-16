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
package org.jackhuang.hmcl.ui.download;

import javafx.scene.Node;
import org.jackhuang.hmcl.download.BMCLAPIDownloadProvider;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;

import java.util.Map;

public final class InstallerWizardProvider implements WizardProvider {
    private final Profile profile;
    private final String gameVersion;
    private final Version version;
    private final String forge;
    private final String liteLoader;
    private final String optiFine;

    public InstallerWizardProvider(Profile profile, String gameVersion, Version version) {
        this(profile, gameVersion, version, null, null, null);
    }

    public InstallerWizardProvider(Profile profile, String gameVersion, Version version, String forge, String liteLoader, String optiFine) {
        this.profile = profile;
        this.gameVersion = gameVersion;
        this.version = version;
        this.forge = forge;
        this.liteLoader = liteLoader;
        this.optiFine = optiFine;
    }

    public Profile getProfile() {
        return profile;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public Version getVersion() {
        return version;
    }

    public String getForge() {
        return forge;
    }

    public String getLiteLoader() {
        return liteLoader;
    }

    public String getOptiFine() {
        return optiFine;
    }

    @Override
    public void start(Map<String, Object> settings) {
    }

    @Override
    public Object finish(Map<String, Object> settings) {
        Task ret = Task.empty();

        if (settings.containsKey("forge"))
            ret = ret.with(profile.getDependency().installLibraryAsync(gameVersion, version, "forge", (String) settings.get("forge")));

        if (settings.containsKey("liteloader"))
            ret = ret.with(profile.getDependency().installLibraryAsync(gameVersion, version, "liteloader", (String) settings.get("liteloader")));

        if (settings.containsKey("optifine"))
            ret = ret.with(profile.getDependency().installLibraryAsync(gameVersion, version, "optifine", (String) settings.get("optifine")));

        return ret.with(Task.of(profile.getRepository()::refreshVersions));
    }

    @Override
    public Node createPage(WizardController controller, int step, Map<String, Object> settings) {
        switch (step) {
            case 0:
                return new AdditionalInstallersPage(this, controller, profile.getRepository(), BMCLAPIDownloadProvider.INSTANCE);
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public boolean cancel() {
        return true;
    }

}
