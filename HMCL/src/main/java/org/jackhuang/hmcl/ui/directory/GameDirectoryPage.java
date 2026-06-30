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
package org.jackhuang.hmcl.ui.directory;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.validation.RequiredFieldValidator;
import com.jfoenix.validation.base.ValidatorBase;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.PortablePath;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Page used to create or edit a game directory entry.
@NotNullByDefault
public final class GameDirectoryPage extends BorderPane implements DecoratorPage {
    /// Decorator title state for this page.
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();

    /// Editable game directory path shown in the directory chooser.
    private final StringProperty location;

    /// Game directory being edited, or `null` when creating a new entry.
    private final @Nullable GameDirectory gameDirectory;

    /// Text field used to edit the game directory display name.
    private final JFXTextField txtGameDirectoryName;

    /// Directory chooser bound to [#location].
    private final LineFileChooserButton gameDir;

    /// Toggle that controls whether a saved path is converted to a relative path when possible.
    private final LineToggleButton toggleUseRelativePath;

    /// Creates a page for adding a new game directory or editing an existing game directory.
    ///
    /// @param gameDirectory the edited game directory, or `null` when creating a new entry
    public GameDirectoryPage(@Nullable GameDirectory gameDirectory) {
        getStyleClass().add("gray-background");

        this.gameDirectory = gameDirectory;
        String gameDirectoryDisplayName = Optional.ofNullable(gameDirectory).map(GameDirectoryManager::getGameDirectoryDisplayName).orElse("");

        state.set(State.fromTitle(gameDirectory == null ? i18n("profile.new") : i18n("profile") + " - " + gameDirectoryDisplayName));
        location = new SimpleStringProperty(this, "location",
                Optional.ofNullable(gameDirectory).map(GameDirectory::getPath).map(PortablePath::toPath).map(FileUtils::getAbsolutePath).orElse(".minecraft"));

        ScrollPane scroll = new ScrollPane();
        this.setCenter(scroll);
        scroll.setFitToHeight(true);
        scroll.setFitToWidth(true);
        {
            VBox rootPane = new VBox();
            rootPane.setStyle("-fx-padding: 20;");
            {
                ComponentList componentList = new ComponentList();
                {
                    BorderPane gameDirectoryNamePane = new BorderPane();
                    {
                        Label label = new Label(i18n("profile.name"));
                        gameDirectoryNamePane.setLeft(label);
                        BorderPane.setAlignment(label, Pos.CENTER_LEFT);

                        txtGameDirectoryName = new JFXTextField();
                        gameDirectoryNamePane.setRight(txtGameDirectoryName);
                        RequiredFieldValidator validator = new RequiredFieldValidator();
                        validator.setMessage(i18n("input.not_empty"));
                        txtGameDirectoryName.getValidators().add(validator);
                        BorderPane.setMargin(txtGameDirectoryName, new Insets(8, 0, 8, 0));

                        txtGameDirectoryName.setText(gameDirectoryDisplayName);
                        txtGameDirectoryName.getValidators().add(new ValidatorBase() {
                            {
                                setMessage(i18n("profile.already_exists"));
                            }

                            @Override
                            protected void eval() {
                                JFXTextField control = (JFXTextField) this.getSrcControl();
                                hasErrors.set(GameDirectoryManager.getGameDirectories().stream()
                                        .anyMatch(existingGameDirectory -> Objects.equals(
                                                GameDirectoryManager.getGameDirectoryCustomName(existingGameDirectory), control.getText())));
                            }
                        });
                    }

                    gameDir = new LineFileChooserButton();
                    gameDir.setTitle(i18n("profile.instance_directory"));
                    gameDir.setFileChooserTitle(i18n("profile.instance_directory.choose"));
                    gameDir.setType(LineFileChooserButton.Type.OPEN_DIRECTORY);
                    gameDir.locationProperty().bindBidirectional(location);

                    toggleUseRelativePath = new LineToggleButton();
                    toggleUseRelativePath.setTitle(i18n("profile.use_relative_path"));

                    gameDir.convertToRelativePathProperty().bind(toggleUseRelativePath.selectedProperty());
                    if (gameDirectory != null) {
                        toggleUseRelativePath.setSelected(!gameDirectory.getPath().isAbsolute());
                    }

                    componentList.getContent().setAll(gameDirectoryNamePane, gameDir, toggleUseRelativePath);
                }

                rootPane.getChildren().setAll(componentList);
            }

            scroll.setContent(rootPane);
        }

        BorderPane savePane = new BorderPane();
        this.setBottom(savePane);
        savePane.setPickOnBounds(false);
        savePane.setStyle("-fx-padding: 20;");
        StackPane.setAlignment(savePane, Pos.BOTTOM_RIGHT);
        {
            JFXButton saveButton = FXUtils.newRaisedButton(i18n("button.save"));
            savePane.setRight(saveButton);
            BorderPane.setAlignment(savePane, Pos.BOTTOM_RIGHT);
            StackPane.setAlignment(saveButton, Pos.BOTTOM_RIGHT);
            saveButton.setPrefSize(100, 40);
            saveButton.setOnAction(e -> onSave());
            saveButton.disableProperty().bind(Bindings.createBooleanBinding(
                    () -> !txtGameDirectoryName.validate() || StringUtils.isBlank(getLocation()),
                    txtGameDirectoryName.textProperty(), location));
        }

        ChangeListener<String> locationChangeListener = (observable, oldValue, newValue) -> {
            Path newPath;
            try {
                newPath = FileUtils.toAbsolute(Path.of(newValue));
            } catch (InvalidPathException ignored) {
                return;
            }

            if (!".minecraft".equals(FileUtils.getName(newPath)))
                return;

            Path parent = newPath.getParent();
            if (parent == null)
                return;

            String suggestedName = FileUtils.getName(parent);
            if (!suggestedName.isBlank()) {
                txtGameDirectoryName.setText(suggestedName);
            }
        };
        locationProperty().addListener(locationChangeListener);

        txtGameDirectoryName.textProperty().addListener(new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                if (txtGameDirectoryName.isFocused()) {
                    txtGameDirectoryName.textProperty().removeListener(this);
                    locationProperty().removeListener(locationChangeListener);
                }
            }
        });
    }

    /// Saves the edited game directory or adds a new entry to the appropriate game directory store.
    private void onSave() {
        if (gameDirectory != null) {
            LocalizedText name = LocalizedText.plain(txtGameDirectoryName.getText());
            PortablePath path = StringUtils.isNotBlank(getLocation()) ? createPortableLocation() : gameDirectory.getPath();
            if (!GameDirectoryManager.canUpdateGameDirectory(gameDirectory, path)) {
                Controllers.confirmBackupAndOverwrite(i18n("settings.game_directories.read_only"), () -> {
                    GameDirectoryManager.forceOverwriteGameDirectoryFiles(gameDirectory, path);
                    GameDirectoryManager.updateGameDirectory(gameDirectory, name, path);
                    fireEvent(new PageCloseEvent());
                });
                return;
            }

            GameDirectoryManager.updateGameDirectory(gameDirectory, name, path);
        } else {
            if (StringUtils.isBlank(getLocation())) {
                gameDir.fire();
            }
            GameDirectory newGameDirectory = new GameDirectory(
                    GameDirectoryManager.newGameDirectoryId(),
                    LocalizedText.plain(txtGameDirectoryName.getText()),
                    createPortableLocation());
            if (newGameDirectory.getPath().isAbsolute()) {
                if (SettingsManager.isUserGameDirectoriesReadOnly()) {
                    Controllers.confirmBackupAndOverwrite(i18n("settings.game_directories.read_only"), () -> {
                        SettingsManager.forceOverwriteUserGameDirectories();
                        GameDirectoryManager.addUserGameDirectory(newGameDirectory);
                        fireEvent(new PageCloseEvent());
                    });
                    return;
                }
                GameDirectoryManager.addUserGameDirectory(newGameDirectory);
            } else {
                if (SettingsManager.isLocalGameDirectoriesReadOnly()) {
                    Controllers.confirmBackupAndOverwrite(i18n("settings.game_directories.read_only"), () -> {
                        SettingsManager.forceOverwriteLocalGameDirectories();
                        GameDirectoryManager.addLocalGameDirectory(newGameDirectory);
                        fireEvent(new PageCloseEvent());
                    });
                    return;
                }
                GameDirectoryManager.addLocalGameDirectory(newGameDirectory);
            }
        }

        fireEvent(new PageCloseEvent());
    }

    /// Creates the portable path for the current location according to the relative-path toggle.
    private PortablePath createPortableLocation() {
        if (toggleUseRelativePath.isSelected()) {
            Path path = Path.of(getLocation());
            Path absolutePath = FileUtils.toAbsolute(path);
            try {
                return PortablePath.fromPath(Metadata.CURRENT_DIRECTORY.relativize(absolutePath).normalize());
            } catch (IllegalArgumentException ignored) {
                // Keep the original path when it cannot be expressed relative to the launcher directory.
            }
        }

        return PortablePath.of(getLocation());
    }

    /// Returns the decorator title state for this page.
    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    /// Returns the current directory path text.
    public String getLocation() {
        return location.get();
    }

    /// Returns the editable directory path property.
    public StringProperty locationProperty() {
        return location;
    }

    /// Updates the directory path text.
    ///
    /// @param location the new directory path text
    public void setLocation(String location) {
        this.location.set(location);
    }
}
