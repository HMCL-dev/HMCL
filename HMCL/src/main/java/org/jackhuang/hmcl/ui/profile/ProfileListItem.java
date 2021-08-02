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

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Skin;

import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;

public class ProfileListItem extends RadioButton {
    private final Profile profile;
    private final StringProperty title = new SimpleStringProperty();
    private final StringProperty subtitle = new SimpleStringProperty();

    public ProfileListItem(Profile profile) {
        this.profile = profile;
        getStyleClass().setAll("profile-list-item", "navigation-drawer-item");
        setUserData(profile);

        title.set(Profiles.getProfileDisplayName(profile));
        subtitle.set(profile.getGameDir().toString());
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ProfileListItemSkin(this);
    }

    public void remove() {
        Profiles.getProfiles().remove(profile);
    }

    public Profile getProfile() {
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
