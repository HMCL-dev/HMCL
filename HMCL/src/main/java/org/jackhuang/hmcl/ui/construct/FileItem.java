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
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXButton;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.jackhuang.hmcl.ui.FXUtils.onInvalidating;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class FileItem extends BorderPane {
    private final Label lblPath = new Label();

    private final SimpleStringProperty name = new SimpleStringProperty(this, "name");
    private final SimpleStringProperty title = new SimpleStringProperty(this, "title");
    private final SimpleStringProperty tooltip = new SimpleStringProperty(this, "tooltip");
    private final SimpleStringProperty path = new SimpleStringProperty(this, "path");
    private final SimpleBooleanProperty convertToRelativePath = new SimpleBooleanProperty(this, "convertToRelativePath");

    public FileItem() {
        VBox left = new VBox();
        Label name = new Label();
        name.textProperty().bind(nameProperty());
        lblPath.getStyleClass().addAll("subtitle-label");
        lblPath.textProperty().bind(path);
        left.getChildren().addAll(name, lblPath);
        setLeft(left);

        JFXButton right = new JFXButton();
        right.setGraphic(SVG.pencil(Theme.blackFillBinding(), 15, 15));
        right.getStyleClass().add("toggle-icon4");
        right.setOnMouseClicked(e -> onExplore());
        FXUtils.installFastTooltip(right, i18n("button.edit"));
        setRight(right);

        Tooltip tip = new Tooltip();
        tip.textProperty().bind(tooltipProperty());
        Tooltip.install(this, tip);

        convertToRelativePath.addListener(onInvalidating(() -> path.set(processPath(path.get()))));
    }

    /**
     * Converts the given path to absolute/relative(if possible) path according to {@link #convertToRelativePathProperty()}.
     */
    private String processPath(String path) {
        Path given = Paths.get(path).toAbsolutePath();
        if (isConvertToRelativePath()) {
            try {
                return Paths.get(".").normalize().toAbsolutePath().relativize(given).normalize().toString();
            } catch (IllegalArgumentException e) {
                // the given path can't be relativized against current path
            }
        }
        return given.normalize().toString();
    }

    public void onExplore() {
        DirectoryChooser chooser = new DirectoryChooser();
        if (path.get() != null) {
            File file = new File(path.get());
            if (file.exists()) {
                if (file.isFile())
                    file = file.getAbsoluteFile().getParentFile();
                else if (file.isDirectory())
                    file = file.getAbsoluteFile();
                chooser.setInitialDirectory(file);
            }
        }
        chooser.titleProperty().bind(titleProperty());
        File selectedDir = chooser.showDialog(Controllers.getStage());
        if (selectedDir != null) {
            path.set(processPath(selectedDir.toString()));
        }
        chooser.titleProperty().unbind();
    }

    public String getName() {
        return name.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public String getTitle() {
        return title.get();
    }

    public StringProperty titleProperty() {
        return title;
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public String getTooltip() {
        return tooltip.get();
    }

    public StringProperty tooltipProperty() {
        return tooltip;
    }

    public void setTooltip(String tooltip) {
        this.tooltip.set(tooltip);
    }

    public String getPath() {
        return path.get();
    }

    public StringProperty pathProperty() {
        return path;
    }

    public void setPath(String path) {
        this.path.set(path);
    }

    public boolean isConvertToRelativePath() {
        return convertToRelativePath.get();
    }

    public BooleanProperty convertToRelativePathProperty() {
        return convertToRelativePath;
    }

    public void setConvertToRelativePath(boolean convertToRelativePath) {
        this.convertToRelativePath.set(convertToRelativePath);
    }
}
