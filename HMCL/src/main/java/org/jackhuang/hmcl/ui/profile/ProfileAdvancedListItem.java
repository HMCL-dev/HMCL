/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;

import static org.jackhuang.hmcl.ui.FXUtils.newImage;

public class ProfileAdvancedListItem extends AdvancedListItem {
    private ObjectProperty<Profile> profile = new SimpleObjectProperty<Profile>() {

        @Override
        protected void invalidated() {
            Profile profile = get();
            if (profile == null) {
            } else {
                setTitle(Profiles.getProfileDisplayName(profile));
                setSubtitle(profile.getGameDir().toString());
            }
        }
    };

    public ProfileAdvancedListItem() {
        setImage(newImage("/assets/img/craft_table.png"));
        setRightGraphic(SVG.viewList(Theme.blackFillBinding(), -1, -1));
    }

    public ObjectProperty<Profile> profileProperty() {
        return profile;
    }
}
