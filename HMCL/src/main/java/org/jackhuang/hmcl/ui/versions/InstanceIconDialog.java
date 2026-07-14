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
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.setting.InstanceIconType;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.setting.TriPreference;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.DialogPane;
import org.jackhuang.hmcl.ui.construct.ImageContainer;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class InstanceIconDialog extends DialogPane {

    public static final Path INSTANCE_ICONS_DIR = Metadata.HMCL_LOCAL_HOME.resolve("instance_icons");

    private static final SimpleDateFormat fileNameFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_");

    private final HMCLGameRepository repository;
    private final String versionId;
    private final Runnable onFinish;
    private final GameSettings.Instance setting;

    public InstanceIconDialog(HMCLGameRepository repository, String versionId, Runnable onFinish) {
        this.repository = repository;
        this.versionId = versionId;
        this.onFinish = onFinish;
        this.setting = repository.getInstanceGameSettingsOrCreate(versionId);

        setTitle(i18n("settings.icon"));
        FlowPane pane = new FlowPane();
        setBody(pane);

        pane.getChildren().setAll(
                createCustomIcon(),
                createIcon(InstanceIconType.GRASS),
                createIcon(InstanceIconType.CHEST),
                createIcon(InstanceIconType.CHICKEN),
                createIcon(InstanceIconType.COMMAND),
                createIcon(InstanceIconType.APRIL_FOOLS),
                createIcon(InstanceIconType.OPTIFINE),
                createIcon(InstanceIconType.CRAFT_TABLE),
                createIcon(InstanceIconType.FABRIC),
                createIcon(InstanceIconType.LEGACY_FABRIC),
                createIcon(InstanceIconType.FORGE),
                createIcon(InstanceIconType.CLEANROOM),
                createIcon(InstanceIconType.NEO_FORGE),
                createIcon(InstanceIconType.FURNACE),
                createIcon(InstanceIconType.QUILT)
        );
        if (Files.isDirectory(INSTANCE_ICONS_DIR)) {
            try (var stream = Files.list(INSTANCE_ICONS_DIR)) {
                pane.getChildren().addAll(
                        stream.filter(p -> Files.isRegularFile(p) && FXUtils.IMAGE_EXTENSIONS.contains(FileUtils.getExtension(p).toLowerCase(Locale.ROOT)))
                                .map(this::createIcon)
                                .filter(Objects::nonNull)
                                .toList()
                );
            } catch (Exception e) {
                LOG.warning("Failed to load custom instance icons at " + INSTANCE_ICONS_DIR, e);
            }
        }
    }

    private void exploreIcon() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(FXUtils.getImageExtensionFilter());
        Path selectedFile = FileUtils.toPath(chooser.showOpenDialog(Controllers.getStage()));
        if (selectedFile != null) {
            TriPreference pref = SettingsManager.settings().saveCustomGameIconsProperty().get();
            if (pref == TriPreference.CONFIRM_EACH_TIME && !INSTANCE_ICONS_DIR.equals(selectedFile.getParent())) {
                Controllers.askTriPreference(
                        i18n("settings.icon.save"),
                        (b) -> setCustomIcon(selectedFile, b),
                        (p) -> SettingsManager.settings().saveCustomGameIconsProperty().set(p)
                );
            } else {
                setCustomIcon(selectedFile, pref == TriPreference.ALWAYS);
            }
        }
    }

    private void setCustomIcon(Path selectedFile, boolean save) {
        try {
            Path dest;
            if (INSTANCE_ICONS_DIR.equals(selectedFile.getParent()) || !save) {
                dest = selectedFile;
            } else {
                String date = fileNameFormat.format(new Date());
                dest = INSTANCE_ICONS_DIR.resolve(date + selectedFile.getFileName());
                {
                    int i = 1;
                    String nameBase = date + FileUtils.getNameWithoutExtension(selectedFile);
                    String ext = FileUtils.getExtension(selectedFile);
                    while (Files.exists(dest)) {
                        dest = INSTANCE_ICONS_DIR.resolve(nameBase + "_" + i + "." + ext);
                        i++;
                    }
                }
                FileUtils.copyFile(selectedFile, dest);
            }
            repository.setVersionIconFile(versionId, dest);

            if (setting != null) {
                setting.iconProperty().setValue(InstanceIconType.DEFAULT);
            }

            onAccept();
        } catch (IOException | IllegalArgumentException e) {
            LOG.error("Failed to set instance icon file: " + selectedFile, e);
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

    private Node createIcon(InstanceIconType type) {
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

    private Node createIcon(Path path) {
        ImageContainer imageContainer;
        try {
            imageContainer = new ImageContainer(32, FXUtils.loadImage(path, 64, 64, true, true));
        } catch (Exception e) {
            LOG.warning("Failed to load custom instance icon at " + path, e);
            return null;
        }
        imageContainer.setMouseTransparent(true);
        RipplerContainer container = new RipplerContainer(imageContainer);
        FXUtils.setLimitWidth(container, 36);
        FXUtils.setLimitHeight(container, 36);
        FXUtils.onClicked(container, () -> {
            try {
                repository.setVersionIconFile(versionId, path);
            } catch (IOException e) {
                LOG.error("Failed to set icon file: " + path, e);
            }
            if (setting != null) {
                setting.iconProperty().setValue(InstanceIconType.DEFAULT);
                onAccept();
            }
        });
        return container;
    }

    @Override
    protected void onAccept() {
        repository.onVersionIconChanged.fireEvent(new Event(this));
        onFinish.run();
        super.onAccept();
    }
}
