// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
        this.isModpack = gameInstance.isModpack();
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
