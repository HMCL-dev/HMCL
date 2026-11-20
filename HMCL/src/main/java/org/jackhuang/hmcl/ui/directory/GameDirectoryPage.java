// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.directory;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3SettingItem;
import org.glavo.m3fx.controls.M3SwitchSettingItem;
import org.glavo.m3fx.controls.M3TextField;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.PortablePath;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.File;
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
    private final M3TextField txtGameDirectoryName;

    /// Directory chooser bound to [#location].
    private final M3SettingItem gameDir;

    /// Switch that controls whether a saved path is converted to a relative path when possible.
    private final M3SwitchSettingItem switchUseRelativePath;

    /// Creates a page for adding a new game directory or editing an existing game directory.
    ///
    /// @param gameDirectory the edited game directory, or `null` when creating a new entry
    public GameDirectoryPage(@Nullable GameDirectory gameDirectory) {
        getStyleClass().add("gray-background");

        this.gameDirectory = gameDirectory;
        String gameDirectoryDisplayName = Optional.ofNullable(gameDirectory).map(GameDirectoryManager::getGameDirectoryDisplayName).orElse("");

        state.set(State.fromTitle(gameDirectory == null ? i18n("game_directory.new") : i18n("game_directory") + " - " + gameDirectoryDisplayName));
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
                        Label label = new Label(i18n("game_directory.name"));
                        gameDirectoryNamePane.setLeft(label);
                        BorderPane.setAlignment(label, Pos.CENTER_LEFT);

                        txtGameDirectoryName = FXUtils.newTextField();
                        txtGameDirectoryName.setText(gameDirectoryDisplayName);
                        var nameLayout = FXUtils.newValidatedTextField(txtGameDirectoryName,
                                (input, text) -> text.isEmpty() ? i18n("input.not_empty") : null,
                                new Validator(i18n("game_directory.already_exists"), text ->
                                        GameDirectoryManager.getGameDirectories().stream()
                                                .filter(existing -> existing != gameDirectory)
                                                .noneMatch(existing -> Objects.equals(
                                                        GameDirectoryManager.getGameDirectoryCustomName(existing), text))));
                        BorderPane.setMargin(nameLayout, new Insets(8, 0, 8, 0));
                        gameDirectoryNamePane.setRight(nameLayout);
                    }

                    gameDir = new M3SettingItem(i18n("game_directory.instance_directory"));
                    gameDir.setTrailing(SVG.EDIT.createIcon(20));
                    gameDir.trailingSupportingTextProperty().bind(location);
                    gameDir.setOnAction(event -> {
                        var chooser = new DirectoryChooser();
                        chooser.setTitle(i18n("game_directory.instance_directory.choose"));
                        chooser.setInitialDirectory(FXUtils.chooserDirectory(getLocation()));
                        @Nullable File directory = chooser.showDialog(gameDir.getScene().getWindow());
                        if (directory != null) setLocation(formatDirectory(directory.toPath()));
                    });

                    switchUseRelativePath = new M3SwitchSettingItem(i18n("game_directory.use_relative_path"));
                    switchUseRelativePath.selectedProperty().addListener(observable -> {
                        @Nullable String path = getLocation();
                        if (path == null) return;
                        try {
                            setLocation(formatDirectory(Path.of(path)));
                        } catch (InvalidPathException ignored) {
                            // Leave an invalid location available for correction.
                        }
                    });
                    if (gameDirectory != null) {
                        switchUseRelativePath.setSelected(!gameDirectory.getPath().isAbsolute());
                    }

                    componentList.getContent().setAll(gameDirectoryNamePane, gameDir, switchUseRelativePath);
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
            M3Button saveButton = new M3Button(I18n.i18n("button.save"), M3ButtonVariant.FILLED);
            savePane.setRight(saveButton);
            BorderPane.setAlignment(savePane, Pos.BOTTOM_RIGHT);
            StackPane.setAlignment(saveButton, Pos.BOTTOM_RIGHT);
            saveButton.setPrefSize(100, 40);
            saveButton.setOnAction(e -> onSave());
            String initialLocation = getLocation();
            boolean initialUseRelativePath = switchUseRelativePath.isSelected();
            saveButton.disableProperty().bind(Bindings.createBooleanBinding(
                    () -> txtGameDirectoryName.isError() || StringUtils.isBlank(getLocation())
                            || (gameDirectory != null
                            && Objects.equals(txtGameDirectoryName.getText(), gameDirectoryDisplayName)
                            && Objects.equals(getLocation(), initialLocation)
                            && switchUseRelativePath.isSelected() == initialUseRelativePath),
                    txtGameDirectoryName.textProperty(), txtGameDirectoryName.errorProperty(), location, switchUseRelativePath.selectedProperty()));
        }

        if (gameDirectory == null) {
            ChangeListener<String> locationChangeListener = (observable, oldValue, newValue) -> {
                Path newPath;
                try {
                    newPath = FileUtils.toAbsolute(Path.of(newValue));
                } catch (InvalidPathException ignored) {
                    return;
                }

                if (!".minecraft".equals(FileUtils.getName(newPath)))
                    return;

                @Nullable Path parent = newPath.getParent();
                if (parent == null)
                    return;

                String suggestedName = FileUtils.getName(parent);
                if (!suggestedName.isBlank()) {
                    txtGameDirectoryName.setText(suggestedName);
                }
            };
            locationChangeListener.changed(locationProperty(), this.location.get(), this.location.get());
            locationProperty().addListener(locationChangeListener);

            txtGameDirectoryName.textProperty().addListener(new ChangeListener<>() {
                /// Stops automatic name suggestions after the user edits the name.
                @Override
                public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                    if (txtGameDirectoryName.isFocused()) {
                        txtGameDirectoryName.textProperty().removeListener(this);
                        locationProperty().removeListener(locationChangeListener);
                    }
                }
            });
        }
    }

    @FXThread
    private void doSave() {
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
                        GameDirectoryManager.setSelectedGameDirectory(newGameDirectory);
                        fireEvent(new PageCloseEvent());
                    });
                    return;
                }
                GameDirectoryManager.addUserGameDirectory(newGameDirectory);
                GameDirectoryManager.setSelectedGameDirectory(newGameDirectory);
            } else {
                if (SettingsManager.isLocalGameDirectoriesReadOnly()) {
                    Controllers.confirmBackupAndOverwrite(i18n("settings.game_directories.read_only"), () -> {
                        SettingsManager.forceOverwriteLocalGameDirectories();
                        GameDirectoryManager.addLocalGameDirectory(newGameDirectory);
                        GameDirectoryManager.setSelectedGameDirectory(newGameDirectory);
                        fireEvent(new PageCloseEvent());
                    });
                    return;
                }
                GameDirectoryManager.addLocalGameDirectory(newGameDirectory);
                GameDirectoryManager.setSelectedGameDirectory(newGameDirectory);
            }
        }

        fireEvent(new PageCloseEvent());
    }

    /// Saves the edited game directory or adds a new entry to the appropriate game directory store.
    @FXThread
    private void onSave() {
        if (Objects.equals(Path.of(getLocation()).getRoot(), Path.of(getLocation()))) {
            Controllers.confirm(i18n("game_directory.root"), i18n("message.warning"), MessageType.WARNING, this::doSave, null);
        } else doSave();
    }

    /// Formats a directory for display, keeping an absolute path across filesystem roots.
    private String formatDirectory(Path directory) {
        Path absolute = FileUtils.toAbsolute(directory).normalize();
        if (switchUseRelativePath.isSelected()) {
            try {
                return Metadata.CURRENT_DIRECTORY.relativize(absolute).toString();
            } catch (IllegalArgumentException ignored) {
                // Different filesystem roots cannot be relativized.
            }
        }
        return absolute.toString();
    }

    /// Encodes the edited path according to the selected storage mode.
    private PortablePath createPortableLocation() {
        return PortablePath.of(switchUseRelativePath.isSelected() ? formatDirectory(Path.of(getLocation())) : getLocation());
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
