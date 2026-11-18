// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.directory;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.validation.RequiredFieldValidator;
import com.jfoenix.validation.base.ValidatorBase;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
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
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.PortablePath;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Page used to create or edit a game directory entry.
@NotNullByDefault
public final class GameDirectoryPage extends BorderPane implements DecoratorPage {
    /// Decorator title state for this page.
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();

    /// Game directory being edited, or `null` when creating a new entry.
    private final @Nullable GameDirectory gameDirectory;

    /// Text field used to edit the game directory display name.
    private final JFXTextField txtGameDirectoryName;

    /// Directory chooser of the editable game directory.
    private final FileSelector gameDir;

    /// Switch that controls whether a saved path is converted to a relative path when possible.
    private final LineSwitch switchUseRelativePath;

    /// Creates a page for adding a new game directory or editing an existing game directory.
    ///
    /// @param gameDirectory the edited game directory, or `null` when creating a new entry
    public GameDirectoryPage(@Nullable GameDirectory gameDirectory) {
        getStyleClass().add("gray-background");

        this.gameDirectory = gameDirectory;
        String gameDirectoryDisplayName = Optional.ofNullable(gameDirectory).map(GameDirectoryManager::getGameDirectoryDisplayName).orElse("");

        state.set(State.fromTitle(gameDirectory == null ? i18n("game_directory.new") : i18n("game_directory") + " - " + gameDirectoryDisplayName));

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

                        txtGameDirectoryName = new JFXTextField();
                        gameDirectoryNamePane.setRight(txtGameDirectoryName);
                        RequiredFieldValidator validator = new RequiredFieldValidator();
                        validator.setMessage(i18n("input.not_empty"));
                        txtGameDirectoryName.getValidators().add(validator);
                        BorderPane.setMargin(txtGameDirectoryName, new Insets(8, 0, 8, 0));

                        txtGameDirectoryName.setText(gameDirectoryDisplayName);
                        txtGameDirectoryName.getValidators().add(new ValidatorBase() {
                            {
                                setMessage(i18n("game_directory.already_exists"));
                            }

                            /// Rejects names used by other game directories, excluding the entry being edited.
                            @Override
                            protected void eval() {
                                JFXTextField control = (JFXTextField) this.getSrcControl();
                                hasErrors.set(GameDirectoryManager.getGameDirectories().stream()
                                        .filter(existingGameDirectory -> existingGameDirectory != gameDirectory)
                                        .anyMatch(existingGameDirectory -> Objects.equals(
                                                GameDirectoryManager.getGameDirectoryCustomName(existingGameDirectory), control.getText())));
                            }
                        });
                    }

                    LinePane gameDirectorySelectPane = new LinePane();
                    gameDirectorySelectPane.setTitle(i18n("game_directory.instance_directory"));
                    {
                        gameDir = new FileSelector(FileSelector.SelectionMode.DIRECTORY);
                        gameDir.getSelector().setTitle(i18n("game_directory.instance_directory.choose"));
                        gameDir.setLocation(Optional.ofNullable(gameDirectory).map(GameDirectory::getPath).map(PortablePath::toPath).map(FileUtils::getAbsolutePath).orElse(".minecraft"));
                        gameDir.setPrefWidth(400);
                        gameDir.setExistingValidationEnabled(false);
                        gameDirectorySelectPane.setRight(gameDir);
                    }

                    switchUseRelativePath = new LineSwitch();
                    switchUseRelativePath.setTitle(i18n("game_directory.use_relative_path"));

                    gameDir.convertToRelativePathProperty().bind(switchUseRelativePath.selectedProperty());
                    if (gameDirectory != null) {
                        switchUseRelativePath.setSelected(!gameDirectory.getPath().isAbsolute());
                    }

                    componentList.getContent().setAll(gameDirectoryNamePane, gameDirectorySelectPane, switchUseRelativePath);
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
            String initialLocation = gameDir.getLocation();
            boolean initialUseRelativePath = switchUseRelativePath.isSelected();
            saveButton.disableProperty().bind(Bindings.createBooleanBinding(
                    () -> !txtGameDirectoryName.validate() || gameDir.getLocation().isBlank()
                            || (gameDirectory != null
                            && Objects.equals(txtGameDirectoryName.getText(), gameDirectoryDisplayName)
                            && gameDir.getLocation().equals(initialLocation)
                            && gameDir.getValidPath() != null
                            && switchUseRelativePath.isSelected() == initialUseRelativePath),
                    txtGameDirectoryName.textProperty(), gameDir.locationProperty(), switchUseRelativePath.selectedProperty()));
        }

        if (gameDirectory == null) {
            InvalidationListener listener = (e) -> {
                if (gameDir.getValidPath() == null)
                    return;

                if (!".minecraft".equals(FileUtils.getName(gameDir.getValidPath())))
                    return;

                @Nullable Path parent = gameDir.getValidPath().getParent();
                if (parent == null)
                    return;

                String suggestedName = FileUtils.getName(parent);
                if (!suggestedName.isBlank()) {
                    txtGameDirectoryName.setText(suggestedName);
                }
            };
            listener.invalidated(gameDir.validPathProperty());
            gameDir.validPathProperty().addListener(listener);

            txtGameDirectoryName.textProperty().addListener(new InvalidationListener() {
                /// Stops automatic name suggestions after the user edits the name.
                @Override
                public void invalidated(Observable observable) {
                    if (txtGameDirectoryName.isFocused()) {
                        txtGameDirectoryName.textProperty().removeListener(this);
                        gameDir.validPathProperty().removeListener(listener);
                    }
                }
            });
        }
    }

    @FXThread
    private void doSave() {
        if (gameDirectory != null) {
            LocalizedText name = LocalizedText.plain(txtGameDirectoryName.getText());
            PortablePath path = gameDir.getLocation().isBlank() ? gameDirectory.getPath() : createPortableLocation();
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
            if (gameDir.getLocation().isBlank()) {
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
        if (gameDir.getValidPath() != null && gameDir.getValidPath().equals(gameDir.getValidPath().getRoot())) {
            Controllers.confirm(i18n("game_directory.root"), i18n("message.warning"), MessageDialogPane.MessageType.WARNING, this::doSave, null);
        } else doSave();
    }

    /// Creates the portable path for the current location according to the relative-path switch.
    private PortablePath createPortableLocation() {
        if (switchUseRelativePath.isSelected()) {
            Path path = Path.of(gameDir.getLocation());
            Path absolutePath = FileUtils.toAbsolute(path);
            try {
                return PortablePath.fromPath(Metadata.CURRENT_DIRECTORY.relativize(absolutePath).normalize());
            } catch (IllegalArgumentException ignored) {
                // Keep the original path when it cannot be expressed relative to the launcher directory.
            }
        }

        return PortablePath.of(gameDir.getLocation());
    }

    /// Returns the decorator title state for this page.
    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}
