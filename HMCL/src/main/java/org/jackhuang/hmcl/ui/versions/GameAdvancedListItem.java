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

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.setting.VersionIconType;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;

import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class GameAdvancedListItem extends AdvancedListItem {
    private final ImageView imageView;
    private final WeakListenerHolder holder = new WeakListenerHolder();
    private Profile profile;
    @SuppressWarnings("unused")
    private Consumer<Event> onVersionIconChangedListener;

    @SuppressWarnings("SuspiciousNameCombination")
    public GameAdvancedListItem() {
        this.imageView = new ImageView();
        FXUtils.limitSize(imageView, LEFT_GRAPHIC_SIZE, LEFT_GRAPHIC_SIZE);
        imageView.setPreserveRatio(true);
        imageView.setImage(null);

        Node imageViewWrapper = FXUtils.limitingSize(imageView, LEFT_GRAPHIC_SIZE, LEFT_GRAPHIC_SIZE);
        imageView.setMouseTransparent(true);
        AdvancedListItem.setAlignment(imageViewWrapper, Pos.CENTER);
        setLeftGraphic(imageViewWrapper);

        holder.add(FXUtils.onWeakChangeAndOperate(Profiles.selectedVersionProperty(), this::loadVersion));

    }

    private void loadVersion(String version) {
        if (Profiles.getSelectedProfile() != profile) {
            profile = Profiles.getSelectedProfile();
            if (profile != null) {
                onVersionIconChangedListener = profile.getRepository().onVersionIconChanged.registerWeak(event -> {
                    this.loadVersion(Profiles.getSelectedVersion());
                });
            }
        }
        if (version != null && Profiles.getSelectedProfile() != null &&
                Profiles.getSelectedProfile().getRepository().hasVersion(version)) {
            setTitle(i18n("version.manage.manage"));
            setSubtitle(version);
            imageView.setImage(Profiles.getSelectedProfile().getRepository().getVersionIconImage(version));
        } else {
            setTitle(i18n("version.empty"));
            setSubtitle(i18n("version.empty.add"));
            imageView.setImage(VersionIconType.DEFAULT.getIcon());
        }
    }
}
