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
package org.jackhuang.hmcl.util.javafx;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.*;
import org.jackhuang.hmcl.util.Holder;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static javafx.beans.binding.Bindings.createBooleanBinding;
import static org.jackhuang.hmcl.util.Pair.pair;

/**
 * @author yushijinhun
 */
public final class ExtendedProperties {

    private static final String PROP_PREFIX = ExtendedProperties.class.getName();

    // ==== ComboBox ====
    @SuppressWarnings("unchecked")
    public static <T> ObjectProperty<T> selectedItemPropertyFor(ComboBox<T> comboBox) {
        return (ObjectProperty<T>) comboBox.getProperties().computeIfAbsent(
                PROP_PREFIX + ".comboxBox.selectedItem",
                any -> createPropertyForSelectionModel(comboBox, comboBox.selectionModelProperty()));
    }

    private static <T> ObjectProperty<T> createPropertyForSelectionModel(Object bean, Property<? extends SelectionModel<T>> modelProperty) {
        return new ReadWriteComposedProperty<>(bean, "extra.selectedItem",
                BindingMapping.of(modelProperty)
                        .flatMap(SelectionModel::selectedItemProperty),
                obj -> modelProperty.getValue().select(obj));
    }
    // ====

    // ==== Toggle ====
    @SuppressWarnings("unchecked")
    public static ObjectProperty<Toggle> selectedTogglePropertyFor(ToggleGroup toggleGroup) {
        return (ObjectProperty<Toggle>) toggleGroup.getProperties().computeIfAbsent(
                PROP_PREFIX + ".toggleGroup.selectedToggle",
                any -> createPropertyForToggleGroup(toggleGroup));
    }

    private static ObjectProperty<Toggle> createPropertyForToggleGroup(ToggleGroup toggleGroup) {
        return new ReadWriteComposedProperty<>(toggleGroup, "extra.selectedToggle",
                toggleGroup.selectedToggleProperty(),
                toggleGroup::selectToggle);
    }

    public static <T> ObjectProperty<T> createSelectedItemPropertyFor(ObservableList<? extends Toggle> items, Class<T> userdataType) {
        return selectedItemPropertyFor(new AutomatedToggleGroup(items), userdataType);
    }

    @SuppressWarnings("unchecked")
    public static <T> ObjectProperty<T> selectedItemPropertyFor(ToggleGroup toggleGroup, Class<T> userdataType) {
        return (ObjectProperty<T>) toggleGroup.getProperties().computeIfAbsent(
                pair(PROP_PREFIX + ".toggleGroup.selectedItem", userdataType),
                any -> createMappedPropertyForToggleGroup(
                        toggleGroup,
                        toggle -> toggle == null ? null : userdataType.cast(toggle.getUserData())));
    }

    private static <T> ObjectProperty<T> createMappedPropertyForToggleGroup(ToggleGroup toggleGroup, Function<Toggle, T> mapper) {
        ObjectProperty<Toggle> selectedToggle = selectedTogglePropertyFor(toggleGroup);
        AtomicReference<Optional<T>> pendingItemHolder = new AtomicReference<>();

        Consumer<T> itemSelector = newItem -> {
            Optional<Toggle> toggleToSelect = toggleGroup.getToggles().stream()
                    .filter(toggle -> Objects.equals(newItem, mapper.apply(toggle)))
                    .findFirst();
            if (toggleToSelect.isPresent()) {
                pendingItemHolder.set(null);
                selectedToggle.set(toggleToSelect.get());
            } else {
                // We are asked to select an nonexistent item.
                // However, this item may become available in the future.
                // So here we store it, and once the associated toggle becomes available, we will update the selection.
                pendingItemHolder.set(Optional.ofNullable(newItem));
                selectedToggle.set(null);
            }
        };

        ReadWriteComposedProperty<T> property = new ReadWriteComposedProperty<>(toggleGroup, "extra.selectedItem",
                BindingMapping.of(selectedTogglePropertyFor(toggleGroup))
                        .map(mapper),
                itemSelector);

        InvalidationListener onTogglesChanged = any -> {
            Optional<T> pendingItem = pendingItemHolder.get();
            if (pendingItem != null) {
                itemSelector.accept(pendingItem.orElse(null));
            }
        };
        toggleGroup.getToggles().addListener(new WeakInvalidationListener(onTogglesChanged));
        property.addListener(new Holder<>(onTogglesChanged));

        return property;
    }
    // ====

    // ==== CheckBox ====
    @SuppressWarnings("unchecked")
    public static ObjectProperty<Boolean> reversedSelectedPropertyFor(CheckBox checkbox) {
        return (ObjectProperty<Boolean>) checkbox.getProperties().computeIfAbsent(
                PROP_PREFIX + ".checkbox.reservedSelected",
                any -> new MappedProperty<Boolean, Boolean>(checkbox, "ext.reservedSelected",
                        checkbox.selectedProperty(), it -> !it, it -> !it));
    }
    // ====

    // ==== General ====
    @SuppressWarnings("unchecked")
    public static ObjectProperty<Boolean> classPropertyFor(Node node, String cssClass) {
        return (ObjectProperty<Boolean>) node.getProperties().computeIfAbsent(
                PROP_PREFIX + ".cssClass." + cssClass,
                any -> {
                    ObservableList<String> classes = node.getStyleClass();
                    return new ReadWriteComposedProperty<>(node, "extra.cssClass." + cssClass,
                            createBooleanBinding(() -> classes.contains(cssClass), classes),
                            state -> {
                                if (state) {
                                    if (!classes.contains(cssClass)) {
                                        classes.add(cssClass);
                                    }
                                } else {
                                    classes.remove(cssClass);
                                }
                            });
                });
    }
    // ====

    private ExtendedProperties() {
    }
}
