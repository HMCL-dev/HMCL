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
package org.jackhuang.hmcl.ui.profile;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.ToggleGroup;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.wizard.DecoratorPage;
import org.jackhuang.hmcl.util.MappedObservableList;

import static org.jackhuang.hmcl.ui.FXUtils.onInvalidating;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ProfileList extends Control implements DecoratorPage {
    private final StringProperty title = new SimpleStringProperty(i18n("profile.manage"));
    private final ListProperty<ProfileListItem> items = new SimpleListProperty<>(FXCollections.observableArrayList());
    private ObjectProperty<Profile> selectedProfile = new SimpleObjectProperty<Profile>() {
        {
            items.addListener(onInvalidating(this::invalidated));
        }

        @Override
        protected void invalidated() {
            Profile selected = get();
            items.forEach(item -> item.selectedProperty().set(item.getProfile() == selected));
        }
    };

    private ToggleGroup toggleGroup;

    public ProfileList() {
        toggleGroup = new ToggleGroup();

        items.bindContent(MappedObservableList.create(
                Profiles.profilesProperty(),
                profile -> new ProfileListItem(toggleGroup, profile)));

        selectedProfile.bindBidirectional(Profiles.selectedProfileProperty());
        toggleGroup.selectedToggleProperty().addListener((o, a, toggle) -> {
            if (toggle == null || toggle.getUserData() == null) return;
            selectedProfile.set(((ProfileListItem) toggle.getUserData()).getProfile());
        });
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ProfileListSkin(this);
    }

    public void addNewProfile() {
        Controllers.navigate(new ProfilePage(null));
    }

    public ListProperty<ProfileListItem> itemsProperty() {
        return items;
    }

    @Override
    public StringProperty titleProperty() {
        return title;
    }
}
