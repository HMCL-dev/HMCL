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
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.game.AccountHelper;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.task.Schedulers;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ProfileListItem extends Control {
    private final Profile profile;
    private final ToggleGroup toggleGroup;
    private final StringProperty title = new SimpleStringProperty();
    private final StringProperty subtitle = new SimpleStringProperty();
    private final BooleanProperty selected = new SimpleBooleanProperty();

    public ProfileListItem(ToggleGroup toggleGroup, Profile profile) {
        this.profile = profile;
        this.toggleGroup = toggleGroup;

        title.set(Profiles.getProfileDisplayName(profile));
        subtitle.set(profile.getGameDir().toString());
        selected.set(Profiles.selectedProfileProperty().get() == profile);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ProfileListItemSkin(this);
    }

    public ToggleGroup getToggleGroup() {
        return toggleGroup;
    }

    public Profile getProfile() {
        return profile;
    }

    public StringProperty titleProperty() {
        return title;
    }

    public StringProperty subtitleProperty() {
        return subtitle;
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public void remove() {
        Profiles.getProfiles().remove(profile);
    }
}
