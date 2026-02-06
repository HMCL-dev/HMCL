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
package org.jackhuang.hmcl.ui.profile;

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
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class ProfilePage extends BorderPane implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();
    private final StringProperty location;
    private final Profile profile;
    private final JFXTextField txtProfileName;
    private final LineFileChooserButton gameDir;
    private final LineToggleButton toggleUseRelativePath;

    /**
     * @param profile null if creating a new profile.
     */
    public ProfilePage(Profile profile) {
        getStyleClass().add("gray-background");

        this.profile = profile;
        String profileDisplayName = Optional.ofNullable(profile).map(Profiles::getProfileDisplayName).orElse("");

        state.set(State.fromTitle(profile == null ? i18n("profile.new") : i18n("profile") + " - " + profileDisplayName));
        location = new SimpleStringProperty(this, "location",
                Optional.ofNullable(profile).map(Profile::getGameDir).map(FileUtils::getAbsolutePath).orElse(".minecraft"));

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
                    BorderPane profileNamePane = new BorderPane();
                    {
                        Label label = new Label(i18n("profile.name"));
                        profileNamePane.setLeft(label);
                        BorderPane.setAlignment(label, Pos.CENTER_LEFT);

                        txtProfileName = new JFXTextField();
                        profileNamePane.setRight(txtProfileName);
                        RequiredFieldValidator validator = new RequiredFieldValidator();
                        validator.setMessage(i18n("input.not_empty"));
                        txtProfileName.getValidators().add(validator);
                        BorderPane.setMargin(txtProfileName, new Insets(8, 0, 8, 0));

                        txtProfileName.setText(profileDisplayName);
                        txtProfileName.getValidators().add(new ValidatorBase() {
                            {
                                setMessage(i18n("profile.already_exists"));
                            }

                            @Override
                            protected void eval() {
                                JFXTextField control = (JFXTextField) this.getSrcControl();
                                hasErrors.set(Profiles.getProfiles().stream().anyMatch(profile -> profile.getName().equals(control.getText())));
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
                    if (profile != null) {
                        toggleUseRelativePath.setSelected(profile.isUseRelativePath());
                    }

                    componentList.getContent().setAll(profileNamePane, gameDir, toggleUseRelativePath);
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
                    () -> !txtProfileName.validate() || StringUtils.isBlank(getLocation()),
                    txtProfileName.textProperty(), location));
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
                txtProfileName.setText(suggestedName);
            }
        };
        locationProperty().addListener(locationChangeListener);

        txtProfileName.textProperty().addListener(new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                if (txtProfileName.isFocused()) {
                    txtProfileName.textProperty().removeListener(this);
                    locationProperty().removeListener(locationChangeListener);
                }
            }
        });
    }

    private void onSave() {
        if (profile != null) {
            profile.setName(txtProfileName.getText());
            profile.setUseRelativePath(toggleUseRelativePath.isSelected());
            if (StringUtils.isNotBlank(getLocation())) {
                profile.setGameDir(Path.of(getLocation()));
            }
        } else {
            if (StringUtils.isBlank(getLocation())) {
                gameDir.fire();
            }
            Profile newProfile = new Profile(txtProfileName.getText(), Path.of(getLocation()));
            newProfile.setUseRelativePath(toggleUseRelativePath.isSelected());
            Profiles.getProfiles().add(newProfile);
        }

        fireEvent(new PageCloseEvent());
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    public String getLocation() {
        return location.get();
    }

    public StringProperty locationProperty() {
        return location;
    }

    public void setLocation(String location) {
        this.location.set(location);
    }
}
