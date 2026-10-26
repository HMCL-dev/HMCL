// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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

        SVGContainer left = (skinnable.isSelected() ? SVG.FOLDER_FILL : SVG.FOLDER).createIcon(20);
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
