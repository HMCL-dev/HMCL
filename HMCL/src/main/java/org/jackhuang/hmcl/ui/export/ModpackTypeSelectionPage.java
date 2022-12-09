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
package org.jackhuang.hmcl.ui.export;

import com.jfoenix.controls.JFXButton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import org.jackhuang.hmcl.mod.ModpackExportInfo;
import org.jackhuang.hmcl.mod.mcbbs.McbbsModpackExportTask;
import org.jackhuang.hmcl.mod.multimc.MultiMCModpackExportTask;
import org.jackhuang.hmcl.mod.server.ServerModpackExportTask;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.Lang;

import java.util.Map;

import static org.jackhuang.hmcl.ui.export.ModpackInfoPage.MODPACK_INFO_OPTION;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class ModpackTypeSelectionPage extends VBox implements WizardPage {
    private final WizardController controller;

    public ModpackTypeSelectionPage(WizardController controller) {
        this.controller = controller;

        this.getStyleClass().add("jfx-list-view");
        this.setMaxSize(300, 150);
        this.getChildren().setAll(
                Lang.apply(new Label(i18n("modpack.export.as")), title -> title.setPadding(new Insets(8))),
                createButton("modpack.type.mcbbs", "modpack.type.mcbbs.export", MODPACK_TYPE_MCBBS, McbbsModpackExportTask.OPTION),
                createButton("modpack.type.multimc", "modpack.type.multimc.export", MODPACK_TYPE_MULTIMC, MultiMCModpackExportTask.OPTION),
                createButton("modpack.type.server", "modpack.type.server.export", MODPACK_TYPE_SERVER, ServerModpackExportTask.OPTION)
        );
    }

    private JFXButton createButton(String titleKey, String detailKey, String type, ModpackExportInfo.Options option) {
        JFXButton button = new JFXButton();
        button.setOnAction(e -> {
            controller.getSettings().put(MODPACK_TYPE, type);
            controller.getSettings().put(MODPACK_INFO_OPTION, option);
            controller.onNext();
        });

        button.prefWidthProperty().bind(this.widthProperty());

        BorderPane graphic = new BorderPane();
        graphic.setMouseTransparent(true);
        graphic.setLeft(new TwoLineListItem(i18n(titleKey), i18n(detailKey)));

        SVGPath arrow = new SVGPath();
        arrow.setContent(SVG.ARROW_RIGHT);
        BorderPane.setAlignment(arrow, Pos.CENTER);
        graphic.setRight(arrow);

        button.setGraphic(graphic);

        return button;
    }

    @Override
    public void cleanup(Map<String, Object> settings) {
    }

    @Override
    public String getTitle() {
        return i18n("modpack.wizard.step.3.title");
    }

    public static final String MODPACK_TYPE = "modpack.type";

    public static final String MODPACK_TYPE_MCBBS = "mcbbs";
    public static final String MODPACK_TYPE_MULTIMC = "multimc";
    public static final String MODPACK_TYPE_SERVER = "server";
}
