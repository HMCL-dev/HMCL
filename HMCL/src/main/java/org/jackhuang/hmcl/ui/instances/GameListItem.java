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
package org.jackhuang.hmcl.ui.instances;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jetbrains.annotations.Nullable;

public class GameListItem extends GameItem {
    private final boolean isModpack;
    private final BooleanProperty selected = new SimpleBooleanProperty(this, "selected");

    public GameListItem(HMCLGameInstance gameInstance) {
        super(gameInstance);
        HMCLGameRepository repository = gameInstance.getRepository();
        GameInstanceID instanceId = gameInstance.getId();
        this.isModpack = repository.isModpack(instanceId);
        selected.bind(Bindings.createBooleanBinding(
                () -> {
                    if (repository.getGameDirectory() != GameDirectoryManager.getSelectedGameDirectory()) return false;
                    @Nullable HMCLGameInstance selectedInstance = repository.getSelectedInstance();
                    return selectedInstance != null && selectedInstance.getId().equals(instanceId);
                },
                GameDirectoryManager.selectedGameDirectoryProperty(),
                GameDirectoryManager.selectedInstanceProperty()));
    }

    public ReadOnlyBooleanProperty selectedProperty() {
        return selected;
    }

    public void rename() {
        Instances.renameInstance(gameInstance);
    }

    public void duplicate() {
        Instances.duplicateInstance(gameInstance);
    }

    public void remove() {
        Instances.deleteInstance(gameInstance);
    }

    public void export() {
        Instances.exportInstance(gameInstance);
    }

    public void browse() {
        Instances.openFolder(gameInstance);
    }

    public void testGame() {
        Instances.testGame(gameInstance);
    }

    public void launch() {
        Instances.launch(gameInstance);
    }

    public void modifyGameSettings() {
        Instances.modifyGameSettings(gameInstance);
    }

    public void generateLaunchScript() {
        Instances.generateLaunchScript(gameInstance);
    }

    public boolean canUpdate() {
        return isModpack;
    }

    public void update() {
        Instances.updateInstance(gameInstance);
    }
}
