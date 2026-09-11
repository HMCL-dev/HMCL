/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.setting.GameInstanceIconType;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class GameInstanceIconDialog extends JFXDialogLayout {
    private final HMCLGameInstance gameInstance;
    private final Runnable onFinish;
    private final GameSettings.@Nullable Instance setting;

    public GameInstanceIconDialog(HMCLGameInstance gameInstance, Runnable onFinish) {
        this.gameInstance = gameInstance;
        this.onFinish = onFinish;
        this.setting = gameInstance.getSettingsOrCreate();

        setHeading(new Label(i18n("settings.icon")));

        FlowPane pane = new FlowPane();
        pane.getChildren().setAll(
                createCustomIcon(),
                createIcon(GameInstanceIconType.GRASS),
                createIcon(GameInstanceIconType.CHEST),
                createIcon(GameInstanceIconType.CHICKEN),
                createIcon(GameInstanceIconType.COMMAND),
                createIcon(GameInstanceIconType.APRIL_FOOLS),
                createIcon(GameInstanceIconType.OPTIFINE),
                createIcon(GameInstanceIconType.CRAFT_TABLE),
                createIcon(GameInstanceIconType.FABRIC),
                createIcon(GameInstanceIconType.LEGACY_FABRIC),
                createIcon(GameInstanceIconType.FORGE),
                createIcon(GameInstanceIconType.CLEANROOM),
                createIcon(GameInstanceIconType.NEO_FORGE),
                createIcon(GameInstanceIconType.FURNACE),
                createIcon(GameInstanceIconType.QUILT)
        );
        setBody(pane);

        JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
        cancelButton.getStyleClass().add("dialog-cancel");
        cancelButton.setOnAction(event -> fireEvent(new DialogCloseEvent()));
        onEscPressed(this, cancelButton::fire);

        setActions(cancelButton);
    }

    private void exploreIcon() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(FXUtils.getImageExtensionFilter());
        Path selectedFile = Controllers.showOpenDialog(chooser);
        if (selectedFile != null) {
            try {
                gameInstance.setIconFile(selectedFile);

                if (setting != null) {
                    setting.iconProperty().setValue(GameInstanceIconType.DEFAULT);
                }

                onAccept();
            } catch (IOException | IllegalArgumentException e) {
                LOG.error("Failed to set icon file: " + selectedFile, e);
            }
        }
    }

    private Node createCustomIcon() {
        Node shape = SVG.ADD_CIRCLE.createIcon(32);
        shape.setMouseTransparent(true);
        RipplerContainer container = new RipplerContainer(shape);
        FXUtils.setLimitWidth(container, 36);
        FXUtils.setLimitHeight(container, 36);
        FXUtils.onClicked(container, this::exploreIcon);
        return container;
    }

    private Node createIcon(GameInstanceIconType type) {
        ImageView imageView = new ImageView(type.getIcon());
        imageView.setMouseTransparent(true);
        RipplerContainer container = new RipplerContainer(imageView);
        FXUtils.setLimitWidth(container, 36);
        FXUtils.setLimitHeight(container, 36);
        FXUtils.onClicked(container, () -> {
            if (setting != null) {
                setting.iconProperty().setValue(type);
                onAccept();
            }
        });
        return container;
    }

    protected void onAccept() {
        // Icon file / settings.iconProperty updates already invalidate iconImageProperty.
        onFinish.run();
        fireEvent(new DialogCloseEvent());
    }
}
