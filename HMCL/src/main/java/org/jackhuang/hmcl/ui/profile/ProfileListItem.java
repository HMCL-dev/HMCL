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
package org.jackhuang.hmcl.ui.profile;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Skin;

import org.jackhuang.hmcl.setting.GameDirectoryProfile;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.ui.Controllers;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ProfileListItem extends RadioButton {
    private final GameDirectoryProfile profile;
    private final StringProperty title = new SimpleStringProperty();
    private final StringProperty subtitle = new SimpleStringProperty();

    public ProfileListItem(GameDirectoryProfile profile) {
        this.profile = profile;
        getStyleClass().setAll("profile-list-item", "navigation-drawer-item");
        setUserData(profile);

        title.set(GameDirectoryManager.getProfileDisplayName(profile));
        subtitle.set(profile.getPath().toString());

        this.selectedProperty().bind(Bindings.equal(profile, GameDirectoryManager.selectedProfileProperty()));
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ProfileListItemSkin(this);
    }

    public void remove() {
        if (!GameDirectoryManager.canRemoveProfile(profile)) {
            Controllers.confirmBackupAndOverwrite(i18n("settings.game_directories.read_only"), () -> {
                GameDirectoryManager.forceOverwriteProfileFiles(profile);
                GameDirectoryManager.removeProfile(profile);
            });
            return;
        }

        GameDirectoryManager.removeProfile(profile);
    }

    public GameDirectoryProfile getProfile() {
        return profile;
    }

    public String getTitle() {
        return title.get();
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public StringProperty titleProperty() {
        return title;
    }

    public String getSubtitle() {
        return subtitle.get();
    }

    public void setSubtitle(String subtitle) {
        this.subtitle.set(subtitle);
    }

    public StringProperty subtitleProperty() {
        return subtitle;
    }
}
