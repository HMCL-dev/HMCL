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
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXListView;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SkinBase;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.Motion;

/// @author Glavo
public final class OptionsListSkin extends SkinBase<OptionsList> {

    private final JFXListView<OptionsList.Element> listView;

    OptionsListSkin(OptionsList control) {
        super(control);

        this.listView = new JFXListView<>();
        listView.setItems(control.getElements());
        listView.setCellFactory(Cell::new);

        this.getChildren().setAll(listView);
    }

    private static final class Cell extends ListCell<OptionsList.Element> {
        private static final PseudoClass PSEUDO_CLASS_FIRST = PseudoClass.getPseudoClass("first");
        private static final PseudoClass PSEUDO_CLASS_LAST = PseudoClass.getPseudoClass("last");

        private StackPane wrapper;

        public Cell(ListView<OptionsList.Element> listView) {
            this.setPadding(Insets.EMPTY);
            FXUtils.limitCellWidth(listView, this);
        }

        @Override
        protected void updateItem(OptionsList.Element item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
            } else if (item instanceof OptionsList.ListElement element) {
                if (wrapper == null)
                    wrapper = createWrapper();
                else
                    wrapper.getStyleClass().remove("no-padding");

                Node node = element.getNode();
                if (node instanceof LineButtonBase || node instanceof LinePane || node instanceof ComponentListWrapper
                        || node.getProperties().containsKey("ComponentList.noPadding"))
                    wrapper.getStyleClass().add("no-padding");

                wrapper.getChildren().setAll(node);

                setGraphic(wrapper);
            } else {
                setGraphic(item.getNode());
            }
        }

        private StackPane createWrapper() {
            var wrapper = new StackPane();
            wrapper.setAlignment(Pos.CENTER_LEFT);
            wrapper.getStyleClass().add("options-list-item");

            InvalidationListener listener = ignored -> {
                OptionsList.Element item = getItem();
                int index = getIndex();
                if (!(item instanceof OptionsList.ListElement) || index < 0)
                    return;

                ObservableList<OptionsList.Element> items = getListView().getItems();

                wrapper.pseudoClassStateChanged(PSEUDO_CLASS_FIRST, index == 0 || !(items.get(index - 1) instanceof OptionsList.ListElement));
                wrapper.pseudoClassStateChanged(PSEUDO_CLASS_LAST, index == items.size() - 1 || !(items.get(index + 1) instanceof OptionsList.ListElement));
            };

            getListView().itemsProperty().addListener((o, oldValue, newValue) -> {
                if (oldValue != null)
                    oldValue.removeListener(listener);
                if (newValue != null)
                    newValue.addListener(listener);

                listener.invalidated(o);
            });
            itemProperty().addListener(listener);
            listener.invalidated(null);

            return wrapper;
        }
    }

    static final class ComponentListWrapper extends VBox {
        private Animation expandAnimation;
        private boolean expanded = false;

        ComponentListWrapper(ComponentList list) {
            Node expandIcon = SVG.KEYBOARD_ARROW_DOWN.createIcon(20);
            expandIcon.setMouseTransparent(true);
            HBox.setMargin(expandIcon, new Insets(0, 8, 0, 8));

            VBox labelVBox = new VBox();
            labelVBox.setMouseTransparent(true);
            labelVBox.setAlignment(Pos.CENTER_LEFT);

            boolean overrideHeaderLeft = false;
            if (list instanceof ComponentSublist) {
                Node leftNode = ((ComponentSublist) list).getHeaderLeft();
                if (leftNode != null) {
                    labelVBox.getChildren().setAll(leftNode);
                    overrideHeaderLeft = true;
                }
            }

            if (!overrideHeaderLeft) {
                Label label = new Label();
                label.textProperty().bind(list.titleProperty());
                label.getStyleClass().add("title-label");
                labelVBox.getChildren().add(label);

                if (list.isHasSubtitle()) {
                    Label subtitleLabel = new Label();
                    subtitleLabel.textProperty().bind(list.subtitleProperty());
                    subtitleLabel.getStyleClass().add("subtitle-label");
                    subtitleLabel.textFillProperty().bind(Themes.colorSchemeProperty().getOnSurfaceVariant());
                    labelVBox.getChildren().add(subtitleLabel);
                }
            }

            HBox header = new HBox();
            header.setSpacing(16);
            header.getChildren().add(labelVBox);
            header.setPadding(new Insets(10, 16, 10, 16));
            header.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(labelVBox, Priority.ALWAYS);
            if (list instanceof ComponentSublist) {
                Node rightNode = ((ComponentSublist) list).getHeaderRight();
                if (rightNode != null)
                    header.getChildren().add(rightNode);
            }
            header.getChildren().add(expandIcon);

            RipplerContainer headerRippler = new RipplerContainer(header);
            this.getChildren().add(headerRippler);

            VBox container = new VBox();
            boolean hasPadding = !(list instanceof ComponentSublist subList) || subList.hasComponentPadding();
            if (hasPadding) {
                container.setPadding(new Insets(8, 16, 10, 16));
            }
            FXUtils.setLimitHeight(container, 0);
            FXUtils.setOverflowHidden(container);
            container.getChildren().setAll(list);
            this.getChildren().add(container);

            headerRippler.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                if (event.getButton() != MouseButton.PRIMARY)
                    return;

                event.consume();

                if (expandAnimation != null && expandAnimation.getStatus() == Animation.Status.RUNNING) {
                    expandAnimation.stop();
                }

                boolean expanded = !this.expanded;
                this.expanded = expanded;
                if (expanded) {
                    list.doLazyInit();
                    list.layout();
                }

                Platform.runLater(() -> {
                    // FIXME: ComponentSubList without padding must have a 4 pixel padding for displaying a border radius.
                    double newAnimatedHeight = (list.prefHeight(list.getWidth()) + (hasPadding ? 8 + 10 : 4)) * (expanded ? 1 : -1);
                    double contentHeight = expanded ? newAnimatedHeight : 0;
                    double targetRotate = expanded ? -180 : 0;

                    if (AnimationUtils.isAnimationEnabled()) {
                        double currentRotate = expandIcon.getRotate();
                        Duration duration = Motion.LONG2.multiply(Math.abs(currentRotate - targetRotate) / 180.0);
                        Interpolator interpolator = Motion.EASE_IN_OUT_CUBIC_EMPHASIZED;

                        expandAnimation = new Timeline(
                                new KeyFrame(duration,
                                        new KeyValue(container.minHeightProperty(), contentHeight, interpolator),
                                        new KeyValue(container.maxHeightProperty(), contentHeight, interpolator),
                                        new KeyValue(expandIcon.rotateProperty(), targetRotate, interpolator))
                        );

                        expandAnimation.play();
                    } else {
                        container.setMinHeight(contentHeight);
                        container.setMaxHeight(contentHeight);
                        expandIcon.setRotate(targetRotate);
                    }
                });
            });
        }
    }
}
