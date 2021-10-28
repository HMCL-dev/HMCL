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
package org.jackhuang.hmcl.ui.versions;

import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.setting.VersionIconType;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.DialogPane;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class VersionIconDialog extends DialogPane {
    private final Profile profile;
    private final String versionId;
    private final Runnable onFinish;
    private final VersionSetting vs;

    public VersionIconDialog(Profile profile, String versionId, Runnable onFinish) {
        this.profile = profile;
        this.versionId = versionId;
        this.onFinish = onFinish;
        this.vs = profile.getRepository().getLocalVersionSettingOrCreate(versionId);

        setTitle(i18n("settings.icon"));
        FlowPane pane = new FlowPane();
        setBody(pane);

        pane.getChildren().setAll(
                createCustomIcon(),
                createIcon(VersionIconType.GRASS),
                createIcon(VersionIconType.CHEST),
                createIcon(VersionIconType.CHICKEN),
                createIcon(VersionIconType.COMMAND),
                createIcon(VersionIconType.CRAFT_TABLE),
                createIcon(VersionIconType.FABRIC),
                createIcon(VersionIconType.FORGE),
                createIcon(VersionIconType.FURNACE)
        );
    }

    private void exploreIcon() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("extension.png"), "*.png"));
        File selectedFile = chooser.showOpenDialog(Controllers.getStage());
        if (selectedFile != null) {
            File iconFile = profile.getRepository().getVersionIconFile(versionId);
            try {
                FileUtils.copyFile(selectedFile, iconFile);

                if (vs != null) {
                    vs.setVersionIcon(VersionIconType.DEFAULT);
                }

                onAccept();
            } catch (IOException e) {
                Logging.LOG.log(Level.SEVERE, "Failed to copy icon file from " + selectedFile + " to " + iconFile, e);
            }
        }
    }

    private Node createCustomIcon() {
        Node shape = SVG.plusCircleOutline(Theme.blackFillBinding(), 32, 32);
        shape.setMouseTransparent(true);
        RipplerContainer container = new RipplerContainer(shape);
        FXUtils.setLimitWidth(container, 36);
        FXUtils.setLimitHeight(container, 36);
        container.setOnMouseClicked(e -> {
            exploreIcon();
        });
        return container;
    }

    private Node createIcon(VersionIconType type) {
        ImageView imageView = new ImageView(new Image(type.getResourceUrl(), 32, 32, true, true));
        imageView.setMouseTransparent(true);
        RipplerContainer container = new RipplerContainer(imageView);
        FXUtils.setLimitWidth(container, 36);
        FXUtils.setLimitHeight(container, 36);
        container.setOnMouseClicked(e -> {
            if (vs != null) {
                vs.setVersionIcon(type);
                onAccept();
            }
        });
        return container;
    }

    @Override
    protected void onAccept() {
        profile.getRepository().onVersionIconChanged.fireEvent(new Event(this));
        onFinish.run();
        super.onAccept();
    }
}
