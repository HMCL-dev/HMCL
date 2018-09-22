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
import javafx.collections.ObservableList;
import javafx.scene.control.ToggleGroup;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.ListPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import static org.jackhuang.hmcl.ui.FXUtils.onInvalidating;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ProfileList extends ListPage<ProfileListItem> implements DecoratorPage {
    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper(i18n("profile.manage"));
    private ObjectProperty<Profile> selectedProfile = new SimpleObjectProperty<Profile>() {
        {
            itemsProperty().addListener(onInvalidating(this::invalidated));
        }

        @Override
        protected void invalidated() {
            Profile selected = get();
            itemsProperty().forEach(item -> item.selectedProperty().set(item.getProfile() == selected));
        }
    };
    private final ListProperty<Profile> profiles = new SimpleListProperty<>(FXCollections.observableArrayList());

    private ToggleGroup toggleGroup;
    private final ObservableList<ProfileListItem> profileItems;

    public ProfileList() {
        toggleGroup = new ToggleGroup();

        profileItems = MappedObservableList.create(
                profilesProperty(),
                profile -> new ProfileListItem(toggleGroup, profile));

        itemsProperty().bindContent(profileItems);

        toggleGroup.selectedToggleProperty().addListener((o, a, toggle) -> {
            if (toggle == null || toggle.getUserData() == null) return;
            selectedProfile.set(((ProfileListItem) toggle.getUserData()).getProfile());
        });
    }

    public ObjectProperty<Profile> selectedProfileProperty() {
        return selectedProfile;
    }

    public ListProperty<Profile> profilesProperty() {
        return profiles;
    }

    @Override
    public void add() {
        Controllers.navigate(new ProfilePage(null));
    }

    @Override
    public ReadOnlyStringProperty titleProperty() {
        return title.getReadOnlyProperty();
    }
}
