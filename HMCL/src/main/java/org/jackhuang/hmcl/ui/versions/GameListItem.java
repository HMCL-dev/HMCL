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

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.GameDirectoryManager;

import java.util.Objects;

public class GameListItem extends GameItem {
    private final boolean isModpack;
    private final BooleanProperty selected = new SimpleBooleanProperty(this, "selected");

    public GameListItem(HMCLGameRepository repository, GameInstanceID instanceId) {
        super(repository, instanceId);
        this.isModpack = repository.isModpack(instanceId);
        selected.bind(Bindings.createBooleanBinding(
                () -> repository.getGameDirectory() == GameDirectoryManager.getSelectedGameDirectory() && Objects.equals(repository.getSelectedInstance2(), instanceId),
                GameDirectoryManager.selectedGameDirectoryProperty(),
                GameDirectoryManager.selectedInstanceProperty()));
    }

    public ReadOnlyBooleanProperty selectedProperty() {
        return selected;
    }

    public void rename() {
        Instances.renameVersion(repository, id);
    }

    public void duplicate() {
        Instances.duplicateInstance(repository, id);
    }

    public void remove() {
        Instances.deleteVersion(repository, id);
    }

    public void export() {
        Instances.exportVersion(repository, id);
    }

    public void browse() {
        Instances.openFolder(repository, id);
    }

    public void testGame() {
        Instances.testGame(repository, id);
    }

    public void launch() {
        Instances.launch(repository, id);
    }

    public void modifyGameSettings() {
        Instances.modifyGameSettings(repository, id);
    }

    public void generateLaunchScript() {
        Instances.generateLaunchScript(repository, id);
    }

    public boolean canUpdate() {
        return isModpack;
    }

    public void update() {
        Instances.updateVersion(repository, id);
    }
}
