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

import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXPopup;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.KeyValue;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Scale;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.construct.ImageContainer;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.StringUtils;

import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.ui.FXUtils.SINE;
/// @author Glavo
public final class GameListPopupMenu extends StackPane {

    private static final String KEY = GameListPopupMenu.class.getName() + ".popup";
    private static final String HIDING_KEY = GameListPopupMenu.class.getName() + ".hiding";

    public static void hideAnimated(JFXPopup popup) {
        if (popup == null || !popup.isShowing()) {
            return;
        }

        if (!AnimationUtils.isAnimationEnabled()) {
            popup.hide();
            return;
        }

        Node content = popup.getPopupContent();
        if (content == null) {
            popup.hide();
            return;
        }

        Node container = content.getParent() != null ? content.getParent() : content;
        Bounds bounds = container.getLayoutBounds();

        Scale scaleTransform = new Scale(1.0, 1.0, bounds.getWidth(), bounds.getHeight());
        container.getTransforms().setAll(scaleTransform);

        Timeline closeAnimation = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(container.opacityProperty(), 1.0, SINE),
                        new KeyValue(scaleTransform.xProperty(), 1.0, SINE),
                        new KeyValue(scaleTransform.yProperty(), 1.0, SINE)
                ),
                new KeyFrame(Duration.millis(160),
                        new KeyValue(container.opacityProperty(), 0.0, SINE),
                        new KeyValue(scaleTransform.xProperty(), 0.0, SINE),
                        new KeyValue(scaleTransform.yProperty(), 0.0, SINE)
                )
        );

        closeAnimation.setOnFinished(event -> {
            popup.hide();
            container.getTransforms().clear();
            container.setOpacity(1.0);
        });

        FXUtils.playAnimation(container, "popup-close", closeAnimation);
    }

    public static boolean hideShowing(Node owner) {
        if (!(owner.getProperties().get(KEY) instanceof JFXPopup popup && popup.isShowing())) {
            return false;
        }

        if (Boolean.TRUE.equals(popup.getProperties().get(HIDING_KEY))) {
            return true;
        }

        popup.getProperties().put(HIDING_KEY, true);
        hideAnimated(popup);
        return true;
    }

    /// Shows an instance selection popup relative to its owner.
    public static void show(Node owner, JFXPopup.PopupVPosition vAlign, JFXPopup.PopupHPosition hAlign,
                            double initOffsetX, double initOffsetY,
                            HMCLGameRepository repository, List<GameInstanceManifest> versions) {
        showAndGetPopup(owner, vAlign, hAlign, initOffsetX, initOffsetY, repository, versions);
    }

    /// Shows and returns an instance selection popup relative to its owner.
    public static JFXPopup showAndGetPopup(Node owner, JFXPopup.PopupVPosition vAlign, JFXPopup.PopupHPosition hAlign,
                                           double initOffsetX, double initOffsetY,
                                           HMCLGameRepository repository, List<GameInstanceManifest> versions) {
        GameListPopupMenu menu = new GameListPopupMenu();
        menu.getItems().setAll(versions.stream()
                .filter(it -> repository.hasInstance(it.id()))
                .map(it -> new GameItem(repository, it.id()))
                .toList());
        JFXPopup popup = new JFXPopup(menu);
        owner.getProperties().put(KEY, popup);
        popup.setAutoHide(false);
        Scene scene = owner.getScene();
        EventHandler<MouseEvent> outsideClickHandler = event -> {
            if (popup.isShowing()) {
                Bounds popupBounds = menu.localToScreen(menu.getBoundsInLocal());
                if (popupBounds != null && !popupBounds.contains(event.getScreenX(), event.getScreenY())) {
                    hideAnimated(popup);
                }
            }
        };

        javafx.beans.value.ChangeListener<Boolean> focusListener = (obs, wasFocused, isFocused) -> {
            if (!isFocused && popup.isShowing()) {
                hideAnimated(popup);
            }
        };

        if (scene != null) {
            scene.addEventFilter(MouseEvent.MOUSE_PRESSED, outsideClickHandler);
            if (scene.getWindow() != null) {
                scene.getWindow().focusedProperty().addListener(focusListener);
            }
        }
        popup.focusedProperty().addListener(focusListener);

        popup.addEventFilter(WindowEvent.WINDOW_HIDDEN, event -> {
            owner.getProperties().remove(KEY, popup);
            if (scene != null) {
                scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsideClickHandler);
                if (scene.getWindow() != null) {
                    scene.getWindow().focusedProperty().removeListener(focusListener);
                }
            }
            popup.focusedProperty().removeListener(focusListener);
        });
        popup.show(owner, vAlign, hAlign, initOffsetX, initOffsetY, false);
        return popup;
    }

    private final JFXListView<GameItem> listView = new JFXListView<>();
    private final BooleanBinding isEmpty = Bindings.isEmpty(listView.getItems());

    public GameListPopupMenu() {
        this.setMaxHeight(365);
        this.getStyleClass().add("popup-menu-content");

        listView.setCellFactory(Cell::new);

        listView.setFixedCellSize(50);
        listView.setPrefWidth(300);

        listView.prefHeightProperty().bind(Bindings.size(getItems()).multiply(50).add(2));

        Label placeholder = new Label(i18n("instance.empty"));
        placeholder.setStyle("-fx-padding: 10px; -fx-text-fill: -monet-on-surface-variant; -fx-font-style: italic;");

        FXUtils.onChangeAndOperate(isEmpty, empty -> {
            getChildren().setAll(empty ? placeholder : listView);
        });
    }

    public ObservableList<GameItem> getItems() {
        return listView.getItems();
    }

    private static final class Cell extends ListCell<GameItem> {

        private final Region graphic;

        private final ImageContainer imageView;
        private final TwoLineListItem content;

        private final StringProperty tag = new SimpleStringProperty();

        public Cell(ListView<GameItem> listView) {
            this.setPadding(Insets.EMPTY);

            this.imageView = new ImageContainer(32);
            this.imageView.setMouseTransparent(true);
            BorderPane.setAlignment(imageView, Pos.CENTER);

            this.content = new TwoLineListItem();
            this.content.setMouseTransparent(true);
            FXUtils.onChangeAndOperate(tag, tag -> {
                content.getTags().clear();
                if (StringUtils.isNotBlank(tag)) {
                    content.addTag(tag);
                }
            });

            BorderPane container = new BorderPane();
            container.getStyleClass().add("container");
            container.setPickOnBounds(false);
            container.setLeft(imageView);
            container.setCenter(content);

            RipplerContainer ripplerContainer = new RipplerContainer(container);

            StackPane rootPane = new StackPane();
            rootPane.getStyleClass().add("advanced-list-item");
            rootPane.getChildren().setAll(ripplerContainer);
            rootPane.maxWidthProperty().bind(listView.widthProperty().subtract(5));

            FXUtils.onClicked(rootPane, () -> {
                GameItem item = getItem();
                if (item != null) {
                    item.getRepository().setSelectedInstance(new GameInstanceID(item.getId()));
                    if (getScene().getWindow() instanceof JFXPopup popup)
                        hideAnimated(popup);
                }
            });

            this.graphic = rootPane;
        }

        @Override
        protected void updateItem(GameItem item, boolean empty) {
            super.updateItem(item, empty);

            this.imageView.imageProperty().unbind();
            this.content.titleProperty().unbind();
            this.content.subtitleProperty().unbind();
            this.tag.unbind();

            if (empty || item == null) {
                setGraphic(null);
            } else {
                setGraphic(this.graphic);

                this.imageView.imageProperty().bind(item.imageProperty());
                this.content.titleProperty().bind(item.titleProperty());
                this.content.subtitleProperty().bind(item.subtitleProperty());
                this.tag.bind(item.tagProperty());
            }
        }
    }
}
