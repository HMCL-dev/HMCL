// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXPopup;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

import static org.jackhuang.hmcl.ui.FXUtils.determineOptimalPopupPosition;

/// @author Glavo
public class LineSelectButton<T extends @UnknownNullability Object> extends LineButton {

    private static final String DEFAULT_STYLE_CLASS = "line-select-button";
    private static final PseudoClass SELECTED_PSEUDO_CLASS = PseudoClass.getPseudoClass("selected");

    /// Popup containing the choices, created on first use.
    private @Nullable JFXPopup popup;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private ObservableList<Node> popupItems; // keep a reference

    /// Identifies a menu item's description tooltip for cleanup when its popup closes.
    private static final Object DESCRIPTION_TOOLTIP_KEY = new Object();

    /// Whether descriptions appear on hover instead of occupying space inside menu items.
    private final BooleanProperty descriptionAsTooltip = new SimpleBooleanProperty(this, "descriptionAsTooltip");

    /// Shows the current choice's description when tooltip mode is enabled and the menu is closed.
    private final Tooltip descriptionTooltip = createDescriptionTooltip();

    /// Creates a selector with inline menu descriptions by default and optional tooltip descriptions.
    public LineSelectButton() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);

        InvalidationListener updateTrailingText = observable -> {
            @Nullable Function<T, String> converter = getConverter();
            @Nullable T value = getValue();
            setTrailingText(converter != null ? converter.apply(value) : Objects.toString(value, ""));
        };
        converterProperty().addListener(updateTrailingText);
        valueProperty().addListener(updateTrailingText);

        descriptionTooltip.textProperty().bind(Bindings.createStringBinding(() -> {
            if (!isDescriptionAsTooltip())
                return "";
            @Nullable Function<T, String> description = getDescriptionConverter();
            @Nullable T selected = getValue();
            return description != null && selected != null ? description.apply(selected) : "";
        }, valueProperty(), descriptionConverterProperty(), descriptionAsTooltip));
        descriptionTooltip.textProperty().addListener(observable -> updateDescriptionTooltip());
        descriptionAsTooltip.addListener(observable -> updateDescriptionTooltip());

        setTrailingIcon(SVG.UNFOLD_MORE);

        ripplerContainer.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                if (popup != null)
                    popup.hide();
                event.consume();
            }
        });
    }

    /// Toggles the choices and creates descriptions using the selected inline or tooltip mode.
    @Override
    public void fire() {
        super.fire();
        if (popup == null) {
            PopupMenu popupMenu = new PopupMenu();
            this.popup = new JFXPopup(popupMenu);

            popupMenu.addEventHandler(KeyEvent.KEY_PRESSED, this::handleKeyEvent);

            ripplerContainer.addEventFilter(ScrollEvent.ANY, ignored -> popup.hide());

            Bindings.bindContent(popupMenu.getContent(), popupItems = MappedObservableList.create(itemsProperty(), item -> {
                VBox vbox = new VBox();

                var itemTitleLabel = new Label();
                itemTitleLabel.getStyleClass().add("title-label");
                itemTitleLabel.textProperty().bind(Bindings.createStringBinding(() -> {
                    @Nullable Function<T, String> converter = getConverter();
                    return converter != null ? converter.apply(item) : Objects.toString(item, "");
                }, converterProperty()));

                var itemSubtitleLabel = new Label();
                itemSubtitleLabel.getStyleClass().add("subtitle-label");
                itemSubtitleLabel.textProperty().bind(Bindings.createStringBinding(() -> {
                    @Nullable Function<T, String> descriptionConverter = getDescriptionConverter();
                    return descriptionConverter != null ? descriptionConverter.apply(item) : "";
                }, descriptionConverterProperty()));

                var wrapper = new StackPane(vbox);
                wrapper.setAlignment(Pos.CENTER_LEFT);
                wrapper.getStyleClass().add("menu-container");
                wrapper.setMouseTransparent(true);
                RipplerContainer ripplerContainer = new RipplerContainer(wrapper);
                Tooltip tooltip = createDescriptionTooltip();
                tooltip.textProperty().bind(itemSubtitleLabel.textProperty());
                ripplerContainer.getProperties().put(DESCRIPTION_TOOLTIP_KEY, tooltip);
                Runnable updateDescription = () -> {
                    @Nullable String text = tooltip.getText();
                    boolean hasDescription = text != null && !text.isEmpty();
                    if (!isDescriptionAsTooltip() && hasDescription)
                        vbox.getChildren().setAll(itemTitleLabel, itemSubtitleLabel);
                    else
                        vbox.getChildren().setAll(itemTitleLabel);
                    tooltip.hide();
                    Tooltip.uninstall(ripplerContainer, tooltip);
                    if (isDescriptionAsTooltip() && hasDescription)
                        installDescriptionTooltip(ripplerContainer, tooltip);
                };
                descriptionAsTooltip.addListener(observable -> updateDescription.run());
                tooltip.textProperty().addListener(observable -> updateDescription.run());
                updateDescription.run();
                FXUtils.onClicked(ripplerContainer, () -> {
                    setValue(item);
                    popup.hide();
                });

                FXUtils.onChangeAndOperate(valueProperty(),
                        value -> wrapper.pseudoClassStateChanged(SELECTED_PSEUDO_CLASS, Objects.equals(value, item)));

                return ripplerContainer;
            }));

            popup.showingProperty().addListener((observable, oldValue, newValue) -> {
                ripplerContainer.setPressFeedbackEnabled(!newValue);
                updateDescriptionTooltip();
                if (!newValue) {
                    for (Node item : popupItems) {
                        if (item.getProperties().get(DESCRIPTION_TOOLTIP_KEY) instanceof Tooltip tooltip)
                            tooltip.hide();
                    }
                }
            });
        }

        if (popup.isShowing()) {
            popup.hide();
        } else {
            JFXPopup.PopupVPosition vPosition = determineOptimalPopupPosition(this, popup);
            popup.show(this, vPosition, JFXPopup.PopupHPosition.RIGHT,
                    0,
                    vPosition == JFXPopup.PopupVPosition.TOP ? this.getHeight() : -this.getHeight(),
                    true);
        }
    }

    /// Returns whether choice descriptions are displayed only through tooltips.
    public boolean isDescriptionAsTooltip() {
        return descriptionAsTooltip.get();
    }

    /// Enables tooltips for both the current choice and menu choices without changing selection behavior.
    public void setDescriptionAsTooltip(boolean value) {
        descriptionAsTooltip.set(value);
    }

    /// Returns the opt-in description display mode; the default preserves inline menu descriptions.
    public @NotNull BooleanProperty descriptionAsTooltipProperty() {
        return descriptionAsTooltip;
    }

    /// Refreshes the current choice's tooltip and suppresses it while its menu is open.
    private void updateDescriptionTooltip() {
        Tooltip.uninstall(ripplerContainer, descriptionTooltip);
        descriptionTooltip.hide();
        @Nullable String text = descriptionTooltip.getText();
        if (isDescriptionAsTooltip() && text != null && !text.isEmpty() && (popup == null || !popup.isShowing()))
            installDescriptionTooltip(ripplerContainer, descriptionTooltip);
    }

    /// Creates a bounded, wrapping tooltip so multiline explanations fit within the screen.
    private static @NotNull Tooltip createDescriptionTooltip() {
        Tooltip tooltip = new Tooltip();
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(480);
        return tooltip;
    }

    /// Uses the shared hover delay with extra reading time for detailed choice descriptions.
    private static void installDescriptionTooltip(@NotNull Node node, @NotNull Tooltip tooltip) {
        FXUtils.installSlowTooltip(node, tooltip);
        tooltip.setShowDuration(Duration.seconds(20));
    }

    private final ObjectProperty<T> value = new SimpleObjectProperty<>(this, "value");

    public ObjectProperty<T> valueProperty() {
        return value;
    }

    public T getValue() {
        return valueProperty().get();
    }

    public void setValue(T value) {
        valueProperty().set(value);
    }

    private final ObjectProperty<Function<T, String>> converter = new SimpleObjectProperty<>(this, "converter");

    public ObjectProperty<Function<T, String>> converterProperty() {
        return converter;
    }

    public Function<T, String> getConverter() {
        return converterProperty().get();
    }

    public void setConverter(Function<T, String> value) { // TODO: rename
        converterProperty().set(value);
    }

    public void setNullSafeConverter(Function<@NotNull T, String> value) {
        converterProperty().set(it -> it != null ? value.apply(it) : "");
    }

    /// Optional converter supplying inline or tooltip descriptions for each choice.
    private @Nullable ObjectProperty<@Nullable Function<T, String>> descriptionConverter;

    /// Returns the converter property, whose null value means that choices have no description.
    public @NotNull ObjectProperty<@Nullable Function<T, String>> descriptionConverterProperty() {
        if (descriptionConverter == null)
            descriptionConverter = new SimpleObjectProperty<>(this, "descriptionConverter");
        return descriptionConverter;
    }

    /// Returns the current description converter, or null when descriptions are absent.
    public @Nullable Function<T, String> getDescriptionConverter() {
        return descriptionConverterProperty().get();
    }

    /// Sets or clears the converter used for descriptions in either display mode.
    public void setDescriptionConverter(@Nullable Function<T, String> value) {
        descriptionConverterProperty().set(value);
    }

    private final ListProperty<T> items = new SimpleListProperty<>(this, "items", FXCollections.emptyObservableList());

    public ListProperty<T> itemsProperty() {
        return items;
    }

    public void setItems(ObservableList<T> value) {
        itemsProperty().set(value);
    }

    public void setItems(Collection<T> value) {
        if (value instanceof ObservableList<T> observableList) {
            this.setItems(observableList);
        } else {
            this.setItems(FXCollections.observableArrayList(value));
        }
    }

    @SafeVarargs
    public final void setItems(T... values) {
        this.setItems(FXCollections.observableArrayList(values));
    }

    public ObservableList<T> getItems() {
        return items.get();
    }

    private void handleKeyEvent(KeyEvent event) {
        ObservableList<T> list = getItems();
        if (list == null || list.isEmpty()) return;
        int index = list.indexOf(getValue());

        var code = event.getCode();

        if (code == KeyCode.UP) {
            if (index > 0) {
                setValue(list.get(index - 1));
            } else {
                setValue(list.get(list.size() - 1));
            }
            event.consume();
        } else if (code == KeyCode.DOWN) {
            if (index < list.size() - 1) {
                setValue(list.get(index + 1));
            } else {
                setValue(list.get(0));
            }
            event.consume();
        } else if (code == KeyCode.ENTER || code == KeyCode.ESCAPE) {
            if (popup != null && popup.isShowing()) {
                popup.hide();
                event.consume();
            }
        }
    }
}
