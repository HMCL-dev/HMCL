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
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.concurrency.JFXUtilities;
import javafx.beans.InvalidationListener;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;

import java.io.File;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class GameAdvancedListItem extends AdvancedListItem {

    public GameAdvancedListItem() {
        Profiles.selectedVersionProperty().addListener((o, a, version) -> {
            File iconFile = Profiles.getSelectedProfile().getRepository().getVersionIcon(version);
            if (iconFile.exists())
                imageProperty().set(new Image("file:" + iconFile.getAbsolutePath()));
            else
                imageProperty().set(new Image("/assets/img/grass.png"));

            if (version != null) {
                titleProperty().set(version);
                subtitleProperty().set(null);
            } else {
                titleProperty().set(i18n("version.empty"));
                subtitleProperty().set(i18n("version.empty.add"));
            }
        });
    }
}
