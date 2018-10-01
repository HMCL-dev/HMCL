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
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.ListPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.SelectedItemProperties.createSelectedItemPropertyFor;

public class ProfileList extends ListPage<ProfileListItem> implements DecoratorPage {
    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper(i18n("profile.manage"));
    private final ListProperty<Profile> profiles = new SimpleListProperty<>(FXCollections.observableArrayList());
    private ObjectProperty<Profile> selectedProfile;

    public ProfileList() {
        setItems(MappedObservableList.create(profilesProperty(), ProfileListItem::new));
        selectedProfile = createSelectedItemPropertyFor(getItems(), Profile.class);
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
