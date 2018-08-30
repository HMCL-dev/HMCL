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
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.concurrency.JFXUtilities;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.ToggleGroup;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.ProfileChangedEvent;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.event.RefreshingVersionsEvent;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.download.DownloadWizardProvider;
import org.jackhuang.hmcl.util.VersionNumber;

import java.util.List;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class GameListViewModel {
    private final BooleanProperty loading = new SimpleBooleanProperty(true);
    private final ListProperty<GameListItemViewModel> items = new SimpleListProperty<>(FXCollections.observableArrayList());

    private Profile profile;
    private ToggleGroup toggleGroup;

    public GameListViewModel() {
        EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class).register(event -> {
            if (event.getSource() == profile.getRepository())
                loadVersions((HMCLGameRepository) event.getSource());
        });
        EventBus.EVENT_BUS.channel(RefreshingVersionsEvent.class).register(event -> {
            if (event.getSource() == profile.getRepository())
                JFXUtilities.runInFX(() -> loading.set(true));
        });
        EventBus.EVENT_BUS.channel(ProfileChangedEvent.class).register(event -> {
            this.profile = event.getProfile();
        });

        profile = Settings.instance().getSelectedProfile();
        if (profile.getRepository().isLoaded())
            loadVersions(profile.getRepository());
        else
            profile.getRepository().refreshVersionsAsync().start();
    }

    private void loadVersions(HMCLGameRepository repository) {
        toggleGroup = new ToggleGroup();
        List<GameListItemViewModel> children = repository.getVersions().parallelStream()
                .filter(version -> !version.isHidden())
                .sorted((a, b) -> VersionNumber.COMPARATOR.compare(VersionNumber.asVersion(a.getId()), VersionNumber.asVersion(b.getId())))
                .map(version -> new GameListItemViewModel(toggleGroup, profile, version.getId()))
                .collect(Collectors.toList());
        JFXUtilities.runInFX(() -> {
            if (profile == repository.getProfile()) {
                loading.set(false);
                items.setAll(children);
                children.forEach(GameListItemViewModel::checkSelection);
            }
            toggleGroup.selectedToggleProperty().addListener((o, a, toggle) -> {
                GameListItemViewModel model = (GameListItemViewModel) toggle.getUserData();
                model.getProfile().setSelectedVersion(model.getVersion());
            });
        });
    }

    public void addNewGame() {
        Controllers.getDecorator().startWizard(new DownloadWizardProvider(0), i18n("install.new_game"));
    }

    public void importModpack() {
        Controllers.getDecorator().startWizard(new DownloadWizardProvider(1), i18n("install.modpack"));
    }

    public void refresh() {
        profile.getRepository().refreshVersionsAsync().start();
    }

    public void modifyGlobalGameSettings() {
        // Controllers.navigate();
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public ListProperty<GameListItemViewModel> itemsProperty() {
        return items;
    }
}
