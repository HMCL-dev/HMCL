/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.construct;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.StringUtils;

/// @author Glavo
public abstract class LineButtonBaseSkin extends SkinBase<LineButtonBase> {

    protected final BorderPane root;
    protected final RipplerContainer container;

    protected LineButtonBaseSkin(LineButtonBase control) {
        super(control);

        this.root = new BorderPane();
        root.setPadding(new Insets(8, 8, 8, 16));

        this.container = new RipplerContainer(root);
        this.getChildren().setAll(container);

        // Left

        var left = new VBox();
        root.setCenter(left);
        left.setMouseTransparent(true);
        left.setAlignment(Pos.CENTER_LEFT);

        var titleLabel = new Label();
        titleLabel.textProperty().bind(control.titleProperty());
        titleLabel.getStyleClass().add("title");

        var subtitleLabel = new Label();
        subtitleLabel.setWrapText(true);
        subtitleLabel.setMinHeight(Region.USE_PREF_SIZE);
        subtitleLabel.getStyleClass().add("subtitle");
        subtitleLabel.textProperty().bind(control.subtitleProperty());

        FXUtils.onChangeAndOperate(control.subtitleProperty(), subtitle -> {
            if (StringUtils.isBlank(subtitle))
                left.getChildren().setAll(titleLabel);
            else
                left.getChildren().setAll(titleLabel, subtitleLabel);
        });

    }
}
