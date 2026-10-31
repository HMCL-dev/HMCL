// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXPopup;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.WindowEvent;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.construct.ImageContainer;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Displays game instances in a popup selection list.
///
/// @author Glavo
@NotNullByDefault
public final class GameListPopupMenu extends StackPane {

    private static final String KEY = GameListPopupMenu.class.getName() + ".popup";

    /// Dismisses the owner's popup; returns true while it is showing or closing.
    public static boolean hideShowing(Node owner) {
        if (!(owner.getProperties().get(KEY) instanceof JFXPopup popup && popup.isShowing())) {
            return false;
        }

        popup.hide();
        return true;
    }

    /// Shows an instance selection popup relative to its owner.
    ///
    /// @param owner       the node used to position the popup
    /// @param vAlign      the popup's vertical alignment relative to `owner`
    /// @param hAlign      the popup's horizontal alignment relative to `owner`
    /// @param initOffsetX the horizontal offset from the aligned position
    /// @param initOffsetY the vertical offset from the aligned position
    /// @param instances   the instances to copy into the popup, in display order
    public static void show(Node owner, JFXPopup.PopupVPosition vAlign, JFXPopup.PopupHPosition hAlign,
                            double initOffsetX, double initOffsetY,
                            List<HMCLGameInstance> instances) {
        if (!hideShowing(owner)) {
            showAndGetPopup(owner, vAlign, hAlign, initOffsetX, initOffsetY, instances);
        }
    }

    /// Shows and returns an instance selection popup relative to its owner.
    ///
    /// @param owner       the node used to position the popup
    /// @param vAlign      the popup's vertical alignment relative to `owner`
    /// @param hAlign      the popup's horizontal alignment relative to `owner`
    /// @param initOffsetX the horizontal offset from the aligned position
    /// @param initOffsetY the vertical offset from the aligned position
    /// @param instances   the instances to copy into the popup, in display order
    /// @return the shown popup
    public static JFXPopup showAndGetPopup(Node owner, JFXPopup.PopupVPosition vAlign, JFXPopup.PopupHPosition hAlign,
                                           double initOffsetX, double initOffsetY,
                                           List<HMCLGameInstance> instances) {
        GameListPopupMenu menu = new GameListPopupMenu();
        menu.getItems().setAll(instances.stream()
                .map(GameItem::new)
                .toList());
        JFXPopup popup = new GameListPopup(menu, AnimationUtils.isAnimationEnabled());
        owner.getProperties().put(KEY, popup);
        popup.addEventFilter(WindowEvent.WINDOW_HIDDEN, event -> owner.getProperties().remove(KEY, popup));
        popup.show(owner, vAlign, hAlign, initOffsetX, initOffsetY, true);
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

        FXUtils.onChangeAndOperate(isEmpty, empty -> getChildren().setAll(empty ? placeholder : listView));
    }

    /// Returns the mutable instance list.
    public ObservableList<GameItem> getItems() {
        return listView.getItems();
    }

    private static final class Cell extends ListCell<GameItem> {

        private final Region graphic;
        private final RipplerContainer ripplerContainer;

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
            FXUtils.onChangeAndOperate(tag, (@Nullable String value) -> {
                content.getTags().clear();
                if (StringUtils.isNotBlank(value)) {
                    content.addTag(value);
                }
            });

            BorderPane container = new BorderPane();
            container.getStyleClass().add("container");
            container.setPickOnBounds(false);
            container.setLeft(imageView);
            container.setCenter(content);

            this.ripplerContainer = new RipplerContainer(container);

            StackPane rootPane = new StackPane();
            rootPane.getStyleClass().add("advanced-list-item");
            rootPane.getChildren().setAll(ripplerContainer);
            rootPane.maxWidthProperty().bind(listView.widthProperty().subtract(5));

            FXUtils.onClicked(rootPane, () -> {
                @Nullable GameItem item = getItem();
                if (item != null) {
                    item.getRepository().setSelectedInstance(item.getGameInstance());
                    if (getScene().getWindow() instanceof JFXPopup popup)
                        popup.hide();
                }
            });

            this.graphic = rootPane;
        }

        @Override
        protected void updateItem(@Nullable GameItem item, boolean empty) {
            @Nullable GameItem oldItem = getItem();
            boolean oldEmpty = isEmpty();

            super.updateItem(item, empty);

            if (oldItem == item && oldEmpty == empty) return;

            this.ripplerContainer.resetFeedback();
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
