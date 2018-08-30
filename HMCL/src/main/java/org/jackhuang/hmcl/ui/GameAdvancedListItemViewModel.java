/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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

import com.jfoenix.concurrency.JFXUtilities;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.ProfileChangedEvent;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.event.RefreshingVersionsEvent;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Settings;

import java.io.File;

public class GameAdvancedListItemViewModel extends AdvancedListItemViewModel {
    private final ObjectProperty<Image> image = new SimpleObjectProperty<>();
    private final StringProperty title = new SimpleStringProperty();
    private final WeakListenerHelper helper = new WeakListenerHelper();

    private Profile profile;
    private InvalidationListener listener = o -> loadVersion();

    public GameAdvancedListItemViewModel() {
        helper.add(EventBus.EVENT_BUS.channel(ProfileChangedEvent.class).registerWeak(event -> {
            JFXUtilities.runInFX(() -> loadProfile(event.getProfile()));
        }));
        helper.add(EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class).registerWeak(event -> {
            JFXUtilities.runInFX(() -> {
                if (profile != null && profile.getRepository() == event.getSource())
                    loadVersion();
            });
        }));
        loadProfile(Settings.instance().getSelectedProfile());
    }

    private void loadProfile(Profile newProfile) {
        if (profile != null)
            profile.selectedVersionProperty().removeListener(listener);
        profile = newProfile;
        profile.selectedVersionProperty().addListener(listener);
        loadVersion();
    }

    private void loadVersion() {
        Profile profile = this.profile;
        if (profile == null || !profile.getRepository().isLoaded()) return;
        String version = profile.getSelectedVersion();
        File iconFile = profile.getRepository().getVersionIcon(version);
        if (iconFile.exists())
            image.set(new Image("file:" + iconFile.getAbsolutePath()));
        else
            image.set(new Image("/assets/img/grass.png"));

        title.set(version);
    }

    @Override
    public void action() {
    }

    @Override
    public ObjectProperty<Image> imageProperty() {
        return image;
    }

    @Override
    public StringProperty titleProperty() {
        return title;
    }

    @Override
    public StringProperty subtitleProperty() {
        return null;
    }
}
