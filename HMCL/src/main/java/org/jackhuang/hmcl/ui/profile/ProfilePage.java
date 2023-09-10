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
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
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
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.FileItem;
import org.jackhuang.hmcl.ui.construct.OptionToggleButton;
import org.jackhuang.hmcl.ui.construct.PageCloseEvent;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.File;
import java.util.Optional;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class ProfilePage extends BorderPane implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();
    private final StringProperty location;
    private final Profile profile;

    private final JFXTextField txtProfileName;
    private final FileItem gameDir;
    private final OptionToggleButton toggleUseRelativePath;

    /**
     * @param profile null if creating a new profile.
     */
    public ProfilePage(Profile profile) {
        this.profile = profile;
        String profileDisplayName = Optional.ofNullable(profile).map(Profiles::getProfileDisplayName).orElse("");

        state.set(State.fromTitle(profile == null ? i18n("profile.new") : i18n("profile") + " - " + profileDisplayName));
        location = new SimpleStringProperty(this, "location",
                Optional.ofNullable(profile).map(Profile::getGameDir).map(File::getAbsolutePath).orElse(".minecraft"));

        ScrollPane scroll = new ScrollPane();
        this.setCenter(scroll);
        scroll.setFitToHeight(true);
        scroll.setFitToWidth(true);
        {
            VBox rootPane = new VBox();
            rootPane.setStyle("-fx-padding: 20;");
            {
                ComponentList componentList = new ComponentList();
                componentList.setDepth(1);
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

                    gameDir = new FileItem();
                    gameDir.setName(i18n("profile.instance_directory"));
                    gameDir.setTitle(i18n("profile.instance_directory.choose"));
                    gameDir.pathProperty().bindBidirectional(location);

                    toggleUseRelativePath = new OptionToggleButton();
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
    }

    private void onSave() {
        if (profile != null) {
            profile.setName(txtProfileName.getText());
            profile.setUseRelativePath(toggleUseRelativePath.isSelected());
            if (StringUtils.isNotBlank(getLocation())) {
                profile.setGameDir(new File(getLocation()));
            }
        } else {
            if (StringUtils.isBlank(getLocation())) {
                gameDir.onExplore();
            }
            Profile newProfile = new Profile(txtProfileName.getText(), new File(getLocation()));
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
