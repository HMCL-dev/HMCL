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
package org.jackhuang.hmcl.ui.versions;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.jackhuang.hmcl.setting.Profile;

public class GameListItem extends GameItem {
    private final boolean isModpack;
    private final BooleanProperty selected = new SimpleBooleanProperty(this, "selected");

    public GameListItem(Profile profile, String id) {
        super(profile, id);
        this.isModpack = profile.getRepository().isModpack(id);
        selected.bind(profile.selectedVersionProperty().isEqualTo(id));
    }

    public ReadOnlyBooleanProperty selectedProperty() {
        return selected;
    }

    public void rename() {
        Versions.renameVersion(profile, id);
    }

    public void duplicate() {
        Versions.duplicateVersion(profile, id);
    }

    public void remove() {
        Versions.deleteVersion(profile, id);
    }

    public void export() {
        Versions.exportVersion(profile, id);
    }

    public void browse() {
        Versions.openFolder(profile, id);
    }

    public void testGame() {
        Versions.testGame(profile, id);
    }

    public void launch() {
        Versions.launch(profile, id);
    }

    public void modifyGameSettings() {
        Versions.modifyGameSettings(profile, id);
    }

    public void generateLaunchScript() {
        Versions.generateLaunchScript(profile, id);
    }

    public boolean canUpdate() {
        return isModpack;
    }

    public void update() {
        Versions.updateVersion(profile, id);
    }
}
