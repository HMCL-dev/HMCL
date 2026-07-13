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
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.PromptDialogPane;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;
import java.util.List;

/// Provides actions and selection state for an instance card in the instance list.
@NotNullByDefault
public final class GameListItem extends GameItem implements GameListEntry {
    private final boolean isModpack;
    private final BooleanProperty selected = new SimpleBooleanProperty(this, "selected");

    public GameListItem(HMCLGameRepository repository, String id) {
        super(repository, id);
        this.isModpack = repository.isModpack(id);
        selected.bind(Bindings.createBooleanBinding(
                () -> repository.getGameDirectory() == GameDirectoryManager.getSelectedGameDirectory() && Objects.equals(repository.getSelectedInstance(), id),
                GameDirectoryManager.selectedGameDirectoryProperty(),
                GameDirectoryManager.selectedInstanceProperty()));
    }

    public ReadOnlyBooleanProperty selectedProperty() {
        return selected;
    }

    public void rename() {
        Versions.renameVersion(repository, id).thenAccept(newId -> {
            if (newId != null) {
                org.jackhuang.hmcl.setting.SettingsManager.settings().renameInstanceGroupMember(
                        repository.getGameDirectory().getId(), id, newId);
            }
        });
    }

    public void duplicate() {
        Versions.duplicateVersion(repository, id);
    }

    public void remove() {
        Versions.deleteVersion(repository, id);
    }

    public void export() {
        Versions.exportVersion(repository, id);
    }

    public void browse() {
        Versions.openFolder(repository, id);
    }

    public void testGame() {
        Versions.testGame(repository, id);
    }

    public void launch() {
        Versions.launch(repository, id);
    }

    public void modifyGameSettings() {
        Versions.modifyGameSettings(repository, id);
    }

    /// Opens a modal group chooser and assigns this instance to the selected group.
    public void joinGroup() {
        LauncherSettings settings = SettingsManager.settings();
        List<LauncherSettings.InstanceGroup> groups = settings.getInstanceGroups(getGameDirectory().getId());
        String[] candidates = new String[groups.size() + 1];
        candidates[0] = org.jackhuang.hmcl.util.i18n.I18n.i18n("version.group.ungrouped");
        for (int i = 0; i < groups.size(); i++) {
            candidates[i + 1] = groups.get(i).name();
        }

        String currentGroupId = settings.getInstanceGroup(getGameDirectory().getId(), id);
        int selectedIndex = 0;
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).id().equals(currentGroupId)) {
                selectedIndex = i + 1;
                break;
            }
        }

        PromptDialogPane.Builder.CandidatesQuestion groupQuestion =
                new PromptDialogPane.Builder.CandidatesQuestion("", selectedIndex, candidates);
        Controllers.prompt(new PromptDialogPane.Builder(
                org.jackhuang.hmcl.util.i18n.I18n.i18n("version.group.join"), (questions, handler) -> {
                    int index = groupQuestion.getValue();
                    settings.setInstanceGroup(getGameDirectory().getId(), id,
                            index == 0 ? null : groups.get(index - 1).id());
                    handler.resolve();
                }).addQuestion(groupQuestion));
    }

    public void generateLaunchScript() {
        Versions.generateLaunchScript(repository, id);
    }

    public boolean canUpdate() {
        return isModpack;
    }

    public void update() {
        Versions.updateVersion(repository, id);
    }
}
