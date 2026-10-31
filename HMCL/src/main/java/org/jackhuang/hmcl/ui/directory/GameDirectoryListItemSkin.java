// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.directory;

import com.jfoenix.controls.JFXButton;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.control.SkinBase;
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

        TwoLineListItem item = new TwoLineListItem();
        item.setSideSpacing(0);
        RipplerContainer container = new RipplerContainer(item);

        SVGContainer left = (skinnable.isSelected() ? SVG.FOLDER_FILL : SVG.FOLDER).createIcon(20);
        left.setMouseTransparent(true);
        TwoLineListItem.setMargin(left, new Insets(0, 6, 0, 6));
        item.setLeading(left);

        FXUtils.onChangeAndOperate(skinnable.selectedProperty(), active -> {
            skinnable.pseudoClassStateChanged(SELECTED, active);

            SVG targetIcon = active ? SVG.FOLDER_FILL : SVG.FOLDER;
            if (left.getIcon() != targetIcon) {
                left.setIcon(targetIcon, Motion.SHORT4);
            }
        });

        FXUtils.onClicked(getSkinnable(), () -> GameDirectoryManager.setSelectedGameDirectory(skinnable.getGameDirectory()));

        JFXButton btnRemove = FXUtils.newToggleButton4(SVG.CLOSE, 14);
        btnRemove.setOnAction(e -> skinnable.remove());
        item.setTrailing(btnRemove);

        item.titleProperty().set(GameDirectoryManager.getGameDirectoryDisplayName(skinnable.getGameDirectory()));
        item.subtitleProperty().set(skinnable.getGameDirectory().getPath().toString());

        getChildren().setAll(container);
    }
}
