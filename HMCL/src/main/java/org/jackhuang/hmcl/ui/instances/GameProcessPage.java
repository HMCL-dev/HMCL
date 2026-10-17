/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Skin;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.game.GameProcessManager;
import org.jackhuang.hmcl.game.GameProcessManager.GameProcessHolder;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.MDListCell;
import org.jackhuang.hmcl.ui.construct.PageAware;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;

import java.util.*;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Page for managing running game processes.
///
/// @author Calboot
public class GameProcessPage extends ListPageBase<GameProcessHolder> implements DecoratorPage, PageAware {

    private final ReadOnlyObjectWrapper<State> state;

    public GameProcessPage() {
        Bindings.bindContent(getItems(), GameProcessManager.displayedHolders);

        Label titleLabel = new Label();
        titleLabel.textProperty().bind(GameProcessManager.aliveProcessCount.asString(i18n("game.process") + " (%d)"));
        titleLabel.textFillProperty().bind(Themes.titleFillProperty());
        titleLabel.getStyleClass().add("jfx-decorator-title");
        titleLabel.setMinWidth(0);
        state = new ReadOnlyObjectWrapper<>(State.fromTitleNode(titleLabel));
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new GameProcessPageSkin(this);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    public void refresh() {
        setLoading(true);
        GameProcessManager.updateDisplay();
        setLoading(false);
    }

    public void onPageShown() {
        setLoading(true);
        GameProcessManager.setDisplay(true);
        setLoading(false);
    }

    public void onPageHidden() {
        GameProcessManager.setDisplay(false);
    }

    private void launch() {
        Controllers.getRootPage().getMainPage().launchCurrentGame();
    }

    private void terminateSelected(Collection<GameProcessHolder> selectedItems) {
        for (GameProcessHolder item : selectedItems) {
            item.terminate();
        }
    }

    private static final class GameProcessPageSkin extends ToolbarListPageSkin<GameProcessHolder, GameProcessPage> {

        public GameProcessPageSkin(GameProcessPage skinnable) {
            super(skinnable);
        }

        @Override
        protected List<Node> initializeToolbar(GameProcessPage skinnable) {
            return List.of(
                    ToolbarListPageSkin.createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh),
                    ToolbarListPageSkin.createToolbarButton2(i18n("instance.launch"), SVG.ROCKET_LAUNCH, skinnable::launch),
                    ToolbarListPageSkin.createToolbarButton2(i18n("game.process.terminate_all"), SVG.SHUTDOWN, () -> skinnable.terminateSelected(skinnable.getItems()))
            );
        }

        @Override
        protected ListCell<GameProcessHolder> createListCell(JFXListView<GameProcessHolder> listView) {
            return new GameProcessCell(listView);
        }
    }

    private static final class GameProcessCell extends MDListCell<GameProcessHolder> {

        private final TwoLineListItem content = new TwoLineListItem();
        private final JFXButton settingsButton = FXUtils.newToggleButton4(SVG.SETTINGS);
        private final JFXButton relaunchButton = FXUtils.newToggleButton4(SVG.ROCKET_LAUNCH);
        private final JFXButton logWindowButton = FXUtils.newToggleButton4(SVG.TERMINAL);
        private final JFXButton terminateButton = FXUtils.newToggleButton4(SVG.SHUTDOWN);

        public GameProcessCell(JFXListView<GameProcessHolder> listView) {
            super(listView);

            HBox container = new HBox(8);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);

            FXUtils.installFastTooltip(relaunchButton, i18n("game.process.relaunch"));
            FXUtils.installFastTooltip(logWindowButton, i18n("game.process.show_log"));
            FXUtils.installFastTooltip(terminateButton, i18n("game.process.terminate"));

            settingsButton.setOnAction(event -> {
                if (getItem() != null && !isEmpty()) getItem().openSettings();
            });
            relaunchButton.setOnAction(event -> {
                if (getItem() != null && !isEmpty()) getItem().relaunch();
            });
            logWindowButton.setOnAction(event -> {
                if (getItem() != null && !isEmpty()) getItem().showLogWindow();
            });
            terminateButton.setOnAction(event -> {
                if (getItem() != null && !isEmpty()) getItem().terminate();
            });

            container.getChildren().setAll(content, settingsButton, relaunchButton, logWindowButton, terminateButton);
            StackPane.setMargin(container, new Insets(8));
            getContainer().getChildren().setAll(container);
        }

        @Override
        protected void updateControl(GameProcessHolder item, boolean empty) {
            if (item == null || empty) {
                relaunchButton.disableProperty().unbind();
                terminateButton.disableProperty().unbind();
                return;
            }

            content.setTitle(item.getId());
            content.subtitleProperty().bind(item.lastLogLineProperty());

            relaunchButton.disableProperty().bind(item.exitedProperty().not());
            terminateButton.disableProperty().bind(item.exitedProperty());
        }
    }

}
