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
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.mod.ModpackExportInfo;
import org.jackhuang.hmcl.mod.mcbbs.McbbsModpackExportTask;
import org.jackhuang.hmcl.mod.multimc.MultiMCModpackExportTask;
import org.jackhuang.hmcl.mod.server.ServerModpackExportTask;
import org.jackhuang.hmcl.mod.modrinth.ModrinthModpackExportTask;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.SettingsMap;

import static org.jackhuang.hmcl.ui.export.ModpackInfoPage.MODPACK_INFO_OPTION;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class ModpackTypeSelectionPage extends VBox implements WizardPage {
    private final WizardController controller;

    public ModpackTypeSelectionPage(WizardController controller) {
        this.controller = controller;
        this.setPadding(new Insets(10));

        Label title = new Label(i18n("modpack.export.as"));
        VBox.setMargin(title, new Insets(8, 0, 8, 12));

        this.getStyleClass().add("jfx-list-view");
        this.setMaxSize(400, 150);
        this.setSpacing(8);

        this.getChildren().setAll(
                title,
                createButton(MODPACK_TYPE_MCBBS, McbbsModpackExportTask.OPTION),
                createButton(MODPACK_TYPE_MULTIMC, MultiMCModpackExportTask.OPTION),
                createButton(MODPACK_TYPE_SERVER, ServerModpackExportTask.OPTION),
                createButton(MODPACK_TYPE_MODRINTH, ModrinthModpackExportTask.OPTION)
        );
    }

    private JFXButton createButton(String type, ModpackExportInfo.Options option) {
        JFXButton button = new JFXButton();

        button.getStyleClass().add("card");
        button.setOnAction(e -> {
            controller.getSettings().put(MODPACK_TYPE, type);
            controller.getSettings().put(MODPACK_INFO_OPTION, option);
            controller.onNext();
        });

        button.prefWidthProperty().bind(this.widthProperty());

        BorderPane graphic = new BorderPane();
        graphic.setMouseTransparent(true);
        graphic.setLeft(new TwoLineListItem(i18n("modpack.type." + type), i18n("modpack.type." + type + ".export")));

        Node arrow = SVG.ARROW_FORWARD.createIcon();
        BorderPane.setAlignment(arrow, Pos.CENTER);
        graphic.setRight(arrow);

        button.setGraphic(graphic);

        return button;
    }

    @Override
    public void cleanup(SettingsMap settings) {
    }

    @Override
    public String getTitle() {
        return i18n("modpack.wizard.step.3.title");
    }

    public static final SettingsMap.Key<String> MODPACK_TYPE = new SettingsMap.Key<>("modpack.type");

    public static final String MODPACK_TYPE_MCBBS = "mcbbs";
    public static final String MODPACK_TYPE_MULTIMC = "multimc";
    public static final String MODPACK_TYPE_SERVER = "server";
    public static final String MODPACK_TYPE_MODRINTH = "modrinth";
}
