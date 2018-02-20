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
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.ui.construct.FileItem;
import org.jackhuang.hmcl.ui.wizard.DecoratorPage;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.File;
import java.util.Optional;

public final class ProfilePage extends StackPane implements DecoratorPage {
    private final StringProperty title;
    private final StringProperty location;
    private final Profile profile;

    @FXML
    private JFXTextField txtProfileName;
    @FXML
    private FileItem gameDir;
    @FXML private JFXButton btnSave;
    @FXML private JFXButton btnDelete;

    /**
     * @param profile null if creating a new profile.
     */
    public ProfilePage(Profile profile) {
        this.profile = profile;

        title = new SimpleStringProperty(this, "title",
                profile == null ? Main.i18n("profile.new") : Main.i18n("profile") + " - " + profile.getName());
        location = new SimpleStringProperty(this, "location",
                Optional.ofNullable(profile).map(Profile::getGameDir).map(File::getAbsolutePath).orElse(""));

        FXUtils.loadFXML(this, "/assets/fxml/profile.fxml");

        txtProfileName.setText(Optional.ofNullable(profile).map(Profiles::getProfileDisplayName).orElse(""));
        FXUtils.onChangeAndOperate(txtProfileName.textProperty(), it -> {
            btnSave.setDisable(!txtProfileName.validate() || StringUtils.isBlank(getLocation()));
        });
        gameDir.setProperty(location);
        FXUtils.onChangeAndOperate(location, it -> {
            btnSave.setDisable(!txtProfileName.validate() || StringUtils.isBlank(getLocation()));
        });

        if (profile == null)
            btnDelete.setVisible(false);
    }

    @FXML
    private void onDelete() {
        if (profile != null) {
            Settings.INSTANCE.deleteProfile(profile);
            Controllers.navigate(null);
        }
    }

    @FXML
    private void onSave() {
        if (profile != null) {
            profile.setName(txtProfileName.getText());
            if (StringUtils.isNotBlank(getLocation()))
                profile.setGameDir(new File(getLocation()));
        } else {
            if (StringUtils.isBlank(getLocation())) {
                gameDir.onExplore();
            }
            Settings.INSTANCE.putProfile(new Profile(txtProfileName.getText(), new File(getLocation())));
        }

        Settings.INSTANCE.onProfileLoading();
        Controllers.navigate(null);
    }

    public String getTitle() {
        return title.get();
    }

    @Override
    public StringProperty titleProperty() {
        return title;
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
