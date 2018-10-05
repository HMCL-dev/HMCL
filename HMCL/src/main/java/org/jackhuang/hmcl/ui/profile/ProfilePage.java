/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui.profile;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.validation.base.ValidatorBase;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.FileItem;
import org.jackhuang.hmcl.ui.construct.PageCloseEvent;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.File;
import java.nio.file.Paths;
import java.util.Optional;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class ProfilePage extends StackPane implements DecoratorPage {
    private final ReadOnlyStringWrapper title;
    private final StringProperty location;
    private final Profile profile;

    @FXML private JFXTextField txtProfileName;
    @FXML private FileItem gameDir;
    @FXML private JFXButton btnSave;
    @FXML private JFXCheckBox toggleUseRelativePath;

    /**
     * @param profile null if creating a new profile.
     */
    public ProfilePage(Profile profile) {
        this.profile = profile;
        String profileDisplayName = Optional.ofNullable(profile).map(Profiles::getProfileDisplayName).orElse("");

        title = new ReadOnlyStringWrapper(this, "title",
                profile == null ? i18n("profile.new") : i18n("profile") + " - " + profileDisplayName);
        location = new SimpleStringProperty(this, "location",
                Optional.ofNullable(profile).map(Profile::getGameDir).map(File::getAbsolutePath).orElse(".minecraft"));

        FXUtils.loadFXML(this, "/assets/fxml/profile.fxml");

        txtProfileName.setText(profileDisplayName);
        txtProfileName.getValidators().add(new ValidatorBase() {
            {
                setMessage(i18n("profile.already_exists"));
            }
            @Override
            protected void eval() {
                JFXTextField control = (JFXTextField) this.getSrcControl();
                if (Profiles.getProfiles().stream().anyMatch(profile -> profile.getName().equals(control.getText())))
                    hasErrors.set(true);
                else
                    hasErrors.set(false);
            }
        });
        FXUtils.onChangeAndOperate(txtProfileName.textProperty(), it -> {
            btnSave.setDisable(!txtProfileName.validate() || StringUtils.isBlank(getLocation()));
        });
        gameDir.pathProperty().bindBidirectional(location);
        FXUtils.onChangeAndOperate(location, it -> {
            btnSave.setDisable(!txtProfileName.validate() || StringUtils.isBlank(getLocation()));
        });
        gameDir.convertToRelativePathProperty().bind(toggleUseRelativePath.selectedProperty());
        if (profile != null) {
            toggleUseRelativePath.setSelected(profile.isUseRelativePath());
        }
    }

    @FXML
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

    public String getTitle() {
        return title.get();
    }

    @Override
    public ReadOnlyStringProperty titleProperty() {
        return title.getReadOnlyProperty();
    }

    public void setTitle(String title) {
        this.title.set(title);
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
