/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.versions;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Node;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.RefreshingVersionsEvent;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.download.ModpackInstallWizardProvider;
import org.jackhuang.hmcl.ui.download.VanillaInstallWizardProvider;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class GameList extends ListPageBase<GameListItem> implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("version.manage")));

    private ToggleGroup toggleGroup;

    public GameList() {
        EventBus.EVENT_BUS.channel(RefreshingVersionsEvent.class).register(event -> {
            if (event.getSource() == Profiles.getSelectedProfile().getRepository())
                runInFX(() -> setLoading(true));
        });

        Profiles.registerVersionsListener(this::loadVersions);
    }

    private void loadVersions(Profile profile) {
        HMCLGameRepository repository = profile.getRepository();
        toggleGroup = new ToggleGroup();
        WeakListenerHolder listenerHolder = new WeakListenerHolder();
        toggleGroup.getProperties().put("ReferenceHolder", listenerHolder);
        List<GameListItem> children = repository.getVersions().parallelStream()
                .filter(version -> !version.isHidden())
                .sorted(Comparator.comparing((Version version) -> version.getReleaseTime() == null ? new Date(0L) : version.getReleaseTime())
                        .thenComparing(a -> VersionNumber.asVersion(a.getId())))
                .map(version -> new GameListItem(toggleGroup, profile, version.getId()))
                .collect(Collectors.toList());
        runInFX(() -> {
            if (profile == Profiles.getSelectedProfile()) {
                setLoading(false);
                itemsProperty().setAll(children);
                children.forEach(GameListItem::checkSelection);

                profile.selectedVersionProperty().addListener(listenerHolder.weak((a, b, newValue) -> {
                    FXUtils.checkFxUserThread();
                    children.forEach(it -> it.selectedProperty().set(false));
                    children.stream()
                            .filter(it -> it.getVersion().equals(newValue))
                            .findFirst()
                            .ifPresent(it -> it.selectedProperty().set(true));
                }));
            }
            toggleGroup.selectedToggleProperty().addListener((o, a, toggle) -> {
                if (toggle == null) return;
                GameListItem model = (GameListItem) toggle.getUserData();
                model.getProfile().setSelectedVersion(model.getVersion());
            });
        });
    }

    @Override
    protected GameListSkin createDefaultSkin() {
        return new GameListSkin();
    }

    public static void addNewGame() {
        Profile profile = Profiles.getSelectedProfile();
        if (profile.getRepository().isLoaded()) {
            Controllers.getDecorator().startWizard(new VanillaInstallWizardProvider(profile), i18n("install.new_game"));
        }
    }

    public static void importModpack() {
        Profile profile = Profiles.getSelectedProfile();
        if (profile.getRepository().isLoaded()) {
            Controllers.getDecorator().startWizard(new ModpackInstallWizardProvider(profile), i18n("install.modpack"));
        }
    }

    public static void refreshList() {
        Profiles.getSelectedProfile().getRepository().refreshVersionsAsync().start();
    }

    public void modifyGlobalGameSettings() {
        Versions.modifyGlobalSettings(Profiles.getSelectedProfile());
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    private class GameListSkin extends ToolbarListPageSkin<GameList> {

        public GameListSkin() {
            super(GameList.this);

            HBox hbox = new HBox(
                    createToolbarButton(i18n("install.new_game"), SVG::plus, GameList::addNewGame),
                    createToolbarButton(i18n("install.modpack"), SVG::importIcon, GameList::importModpack),
                    createToolbarButton(i18n("button.refresh"), SVG::refresh, GameList::refreshList),
                    createToolbarButton(i18n("settings.type.global.manage"), SVG::gear, GameList.this::modifyGlobalGameSettings)
            );
            hbox.setPickOnBounds(false);

            state.set(new State(i18n("version.manage"), hbox, true, false, true));
        }

        @Override
        protected List<Node> initializeToolbar(GameList skinnable) {
            return Collections.emptyList();
        }
    }
}
