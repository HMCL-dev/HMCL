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
package org.jackhuang.hmcl.ui.directory;

import com.jfoenix.controls.JFXButton;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.SVGContainer;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jetbrains.annotations.NotNullByDefault;

/// Skin for [GameDirectoryListItem].
@NotNullByDefault
public class GameDirectoryListItemSkin extends SkinBase<GameDirectoryListItem> {
    /// Pseudo class applied to the selected item.
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    /// Creates the skin for a game directory list item.
    ///
    /// @param skinnable the list item controlled by this skin
    public GameDirectoryListItemSkin(GameDirectoryListItem skinnable) {
        super(skinnable);

        BorderPane root = new BorderPane();
        root.setPickOnBounds(false);
        RipplerContainer container = new RipplerContainer(root);

        SVGContainer left = SVG.FOLDER.createIcon(20);
        left.setMouseTransparent(true);
        BorderPane.setMargin(left, new Insets(0, 6, 0, 6));
        BorderPane.setAlignment(left, Pos.CENTER_LEFT);
        root.setLeft(left);

        FXUtils.onChangeAndOperate(skinnable.selectedProperty(), active -> {
            skinnable.pseudoClassStateChanged(SELECTED, active);
            
            SVG targetIcon = active ? SVG.FOLDER_FILL : SVG.FOLDER;
            if (left.getIcon() != targetIcon) {
                left.setIcon(targetIcon, Motion.SHORT4);
            }
        });

        FXUtils.onClicked(getSkinnable(), () -> GameDirectoryManager.setSelectedGameDirectory(skinnable.getGameDirectory()));

        TwoLineListItem item = new TwoLineListItem();
        item.setPickOnBounds(false);
        BorderPane.setAlignment(item, Pos.CENTER);
        root.setCenter(item);

        HBox right = new HBox();
        right.setAlignment(Pos.CENTER_RIGHT);

        JFXButton btnRemove = FXUtils.newToggleButton4(SVG.CLOSE, 14);
        btnRemove.setOnAction(e -> skinnable.remove());
        BorderPane.setAlignment(btnRemove, Pos.CENTER);
        right.getChildren().add(btnRemove);
        root.setRight(right);

        item.titleProperty().set(GameDirectoryManager.getGameDirectoryDisplayName(skinnable.getGameDirectory()));
        item.subtitleProperty().set(skinnable.getGameDirectory().getPath().toString());

        getChildren().setAll(container);
    }
}
