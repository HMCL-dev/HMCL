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
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.VersionIconType;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.DialogPane;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class VersionIconDialog extends DialogPane {
    public static final Path GAME_ICONS_DIR = Metadata.HMCL_CURRENT_DIRECTORY.resolve("game_icons");

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
                createIcon(VersionIconType.APRIL_FOOLS),
                createIcon(VersionIconType.OPTIFINE),
                createIcon(VersionIconType.CRAFT_TABLE),
                createIcon(VersionIconType.FABRIC),
                createIcon(VersionIconType.LEGACY_FABRIC),
                createIcon(VersionIconType.FORGE),
                createIcon(VersionIconType.CLEANROOM),
                createIcon(VersionIconType.NEO_FORGE),
                createIcon(VersionIconType.FURNACE),
                createIcon(VersionIconType.QUILT)
        );

        if (Files.isDirectory(GAME_ICONS_DIR)) {
            try (var stream = Files.list(GAME_ICONS_DIR)) {
                pane.getChildren().addAll(
                        stream.filter(p -> Files.isRegularFile(p) && FXUtils.IMAGE_EXTENSIONS.contains(FileUtils.getExtension(p).toLowerCase(Locale.ROOT)))
                                .map(this::createIcon)
                                .filter(Objects::nonNull)
                                .toList()
                );
            } catch (Exception e) {
                LOG.warning("Failed to load custom game icons", e);
            }
        }
    }

    private void exploreIcon() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(FXUtils.getImageExtensionFilter());
        Path selectedFile = FileUtils.toPath(chooser.showOpenDialog(Controllers.getStage()));
        if (selectedFile != null) {
            try {
                Path dest;
                if (GAME_ICONS_DIR.equals(selectedFile.getParent())) {
                    dest = selectedFile;
                } else {
                    dest = GAME_ICONS_DIR.resolve(selectedFile.getFileName());
                    int i = 1;
                    String name = FileUtils.getNameWithoutExtension(selectedFile);
                    String ext = FileUtils.getExtension(selectedFile);
                    while (Files.exists(dest)) {
                        dest = GAME_ICONS_DIR.resolve(name + " " + i + "." + ext);
                        i++;
                    }
                    FileUtils.copyFile(selectedFile, dest);
                }
                profile.getRepository().setVersionIconFile(versionId, dest);

                if (vs != null) {
                    vs.setVersionIcon(VersionIconType.DEFAULT);
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

    private Node createIcon(VersionIconType type) {
        ImageView imageView = new ImageView(type.getIcon());
        imageView.setMouseTransparent(true);
        RipplerContainer container = new RipplerContainer(imageView);
        FXUtils.setLimitWidth(container, 36);
        FXUtils.setLimitHeight(container, 36);
        FXUtils.onClicked(container, () -> {
            if (vs != null) {
                vs.setVersionIcon(type);
                onAccept();
            }
        });
        return container;
    }

    private Node createIcon(Path path) {
        ImageView imageView;
        try {
            imageView = new ImageView(new Image(Files.newInputStream(path), 72, 72, true, false));
        } catch (IOException e) {
            LOG.warning("Failed to load custom game icon: " + path, e);
            return null;
        }
        imageView.setMouseTransparent(true);
        FXUtils.limitSize(imageView, 36, 36);
        RipplerContainer container = new RipplerContainer(imageView);
        FXUtils.setLimitWidth(container, 36);
        FXUtils.setLimitHeight(container, 36);
        FXUtils.onClicked(container, () -> {
            if (vs != null) {
                vs.setVersionIcon(VersionIconType.DEFAULT);
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
