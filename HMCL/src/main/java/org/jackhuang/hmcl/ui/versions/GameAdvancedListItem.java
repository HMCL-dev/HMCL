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

import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.util.Pair;

import static org.jackhuang.hmcl.ui.FXUtils.newImage;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class GameAdvancedListItem extends AdvancedListItem {
    private final Tooltip tooltip;
    private final ImageView imageView;

    public GameAdvancedListItem() {
        tooltip = new Tooltip();

        Pair<Node, ImageView> view = createImageView(null);
        setLeftGraphic(view.getKey());
        imageView = view.getValue();

        FXUtils.onChangeAndOperate(Profiles.selectedVersionProperty(), version -> {
            if (version != null && Profiles.getSelectedProfile() != null &&
                    Profiles.getSelectedProfile().getRepository().hasVersion(version)) {
                FXUtils.installFastTooltip(this, tooltip);
                setTitle(version);
                setSubtitle(null);
                imageView.setImage(Profiles.getSelectedProfile().getRepository().getVersionIconImage(version));
                tooltip.setText(version);
            } else {
                Tooltip.uninstall(this,tooltip);
                setTitle(i18n("version.empty"));
                setSubtitle(i18n("version.empty.add"));
                imageView.setImage(newImage("/assets/img/grass.png"));
                tooltip.setText("");
            }
        });

        setActionButtonVisible(false);
//        setRightGraphic(SVG.gear(Theme.blackFillBinding(), -1, -1));
    }
}
