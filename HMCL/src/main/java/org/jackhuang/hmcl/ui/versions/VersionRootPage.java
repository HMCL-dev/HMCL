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
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXTabPane;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Control;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SkinBase;
import javafx.scene.control.Tab;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.construct.TabHeader;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class VersionRootPage extends Control implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();
    private final BooleanProperty loading = new SimpleBooleanProperty();
    private final JFXListView<String> listView = new JFXListView<>();
    private final VersionPage versionPage = new VersionPage();
    private Profile profile;

    {
        Profiles.registerVersionsListener(this::loadVersions);

        listView.getSelectionModel().selectedItemProperty().addListener((a, b, newValue) -> {
            loadVersion(newValue, profile);
        });
    }

    private void loadVersions(Profile profile) {
        HMCLGameRepository repository = profile.getRepository();
        List<String> children = repository.getVersions().parallelStream()
                .filter(version -> !version.isHidden())
                .sorted(Comparator.comparing((Version version) -> version.getReleaseTime() == null ? new Date(0L) : version.getReleaseTime())
                        .thenComparing(a -> VersionNumber.asVersion(a.getId())))
                .map(Version::getId)
                .collect(Collectors.toList());
        runInFX(() -> {
            if (profile == Profiles.getSelectedProfile()) {
                this.profile = profile;
                loading.set(false);
                listView.getItems().setAll(children);
            }
        });
    }

    public void loadVersion(String version, Profile profile) {
        listView.getSelectionModel().select(version);
        versionPage.load(version, profile);
    }

    @Override
    protected Skin createDefaultSkin() {
        return new Skin(this);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    public static class Skin extends SkinBase<VersionRootPage> {

        /**
         * Constructor for all SkinBase instances.
         *
         * @param control The control for which this Skin should attach to.
         */
        protected Skin(VersionRootPage control) {
            super(control);

            control.listView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

            SpinnerPane spinnerPane = new SpinnerPane();
            spinnerPane.getStyleClass().add("large-spinner-pane");

            // the root page, with the sidebar in left, navigator in center.
            BorderPane root = new BorderPane();
            root.getStyleClass().add("gray-background");

            {
                BorderPane leftRootPane = new BorderPane();
                FXUtils.setLimitWidth(leftRootPane, 200);

                StackPane drawerContainer = new StackPane();
                drawerContainer.getChildren().setAll(control.listView);
                leftRootPane.setCenter(drawerContainer);

                Rectangle separator = new Rectangle();
                separator.heightProperty().bind(root.heightProperty());
                separator.setWidth(1);
                separator.setFill(Color.GRAY);

                leftRootPane.setRight(separator);

                root.setLeft(leftRootPane);
            }

            control.state.set(new State(i18n("version.manage.manage"), null, true, false, true));

            root.setCenter(control.versionPage);

            spinnerPane.loadingProperty().bind(control.versionPage.loadingProperty());
            spinnerPane.setContent(root);
            getChildren().setAll(spinnerPane);
        }
    }
}
