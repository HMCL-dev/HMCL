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
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.concurrency.JFXUtilities;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.effects.JFXDepthManager;
import javafx.geometry.Pos;
import javafx.scene.layout.BorderPane;
import org.jackhuang.hmcl.mod.ModInfo;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.StringUtils;

import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class ModItem extends BorderPane {

    public ModItem(ModInfo info, Consumer<ModItem> deleteCallback) {
        JFXCheckBox chkEnabled = new JFXCheckBox();
        BorderPane.setAlignment(chkEnabled, Pos.CENTER);
        setLeft(chkEnabled);

        TwoLineListItem modItem = new TwoLineListItem();
        BorderPane.setAlignment(modItem, Pos.CENTER);
        setCenter(modItem);

        JFXButton btnRemove = new JFXButton();
        JFXUtilities.runInFX(() -> {
            FXUtils.installFastTooltip(btnRemove, i18n("mods.remove"));
        });
        btnRemove.setOnMouseClicked(e -> deleteCallback.accept(this));
        btnRemove.getStyleClass().add("toggle-icon4");
        BorderPane.setAlignment(btnRemove, Pos.CENTER);
        btnRemove.setGraphic(SVG.delete(Theme.blackFillBinding(), 15, 15));
        setRight(btnRemove);

        setStyle("-fx-background-radius: 2; -fx-background-color: white; -fx-padding: 8;");
        JFXDepthManager.setDepth(this, 1);
        modItem.setTitle(info.getFileName());
        StringBuilder message = new StringBuilder(info.getName());
        if (StringUtils.isNotBlank(info.getVersion()))
            message.append(", ").append(i18n("archive.version")).append(": ").append(info.getVersion());
        if (StringUtils.isNotBlank(info.getGameVersion()))
            message.append(", ").append(i18n("archive.game_version")).append(": ").append(info.getGameVersion());
        if (StringUtils.isNotBlank(info.getAuthors()))
            message.append(", ").append(i18n("archive.author")).append(": ").append(info.getAuthors());
        modItem.setSubtitle(message.toString());
        chkEnabled.setSelected(info.isActive());
        chkEnabled.selectedProperty().addListener((a, b, newValue) ->
                info.activeProperty().set(newValue));
    }
}
