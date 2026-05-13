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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXToggleButton;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// A line component that edits an inheritable boolean while showing the effective toggle state.
///
/// The raw value uses {@code null} to inherit from the parent setting. The toggle always reflects
/// the effective value currently applied by the setting hierarchy.
@NotNullByDefault
public final class LineInheritableToggleButton extends LineButtonBase {
    /// The style class applied to inheritable toggle rows.
    private static final String DEFAULT_STYLE_CLASS = "line-inheritable-toggle-button";

    /// The label that displays whether the value is inherited or overridden.
    private final Label sourceLabel;

    /// The button that resets the raw value to inherited mode.
    private final JFXButton inheritButton;

    /// The visual toggle that displays the effective value.
    private final JFXToggleButton toggleButton;

    /// Creates an inheritable boolean toggle row.
    public LineInheritableToggleButton() {
        this.getStyleClass().addAll(DEFAULT_STYLE_CLASS, "line-toggle-button");

        this.sourceLabel = new Label();
        sourceLabel.setMinWidth(Region.USE_PREF_SIZE);
        sourceLabel.setMouseTransparent(true);

        this.inheritButton = FXUtils.newToggleButton4(SVG.RESTORE, 18);
        inheritButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            setRawValue(null);
            super.fire();
            event.consume();
        });

        this.toggleButton = new JFXToggleButton();
        toggleButton.setMouseTransparent(true);
        toggleButton.setSize(8);
        FXUtils.setLimitHeight(toggleButton, 30);

        var box = new HBox(8, sourceLabel, inheritButton, toggleButton);
        box.setAlignment(Pos.CENTER);
        setNode(IDX_TRAILING, box);

        rawValue.addListener(observable -> refresh());
        effectiveValue.addListener(observable -> refresh());
        inheritAvailable.addListener(observable -> refresh());
        inheritedText.addListener(observable -> refresh());
        overriddenText.addListener(observable -> refresh());
        refresh();
    }

    @Override
    public void fire() {
        setRawValue(!isEffectiveValue());
        super.fire();
    }

    /// Refreshes the visual state from the raw and effective values.
    private void refresh() {
        boolean inherited = isInheritAvailable() && getRawValue() == null;

        sourceLabel.setText(inherited ? getInheritedText() : getOverriddenText());
        sourceLabel.setVisible(isInheritAvailable());
        sourceLabel.setManaged(isInheritAvailable());

        inheritButton.setVisible(isInheritAvailable() && !inherited);
        inheritButton.setManaged(isInheritAvailable() && !inherited);

        toggleButton.setSelected(isEffectiveValue());
    }

    /// The raw value stored in this setting.
    private final ObjectProperty<@Nullable Boolean> rawValue = new SimpleObjectProperty<>(this, "rawValue");

    /// Returns the raw setting value.
    public ObjectProperty<@Nullable Boolean> rawValueProperty() {
        return rawValue;
    }

    /// Returns the raw setting value.
    public @Nullable Boolean getRawValue() {
        return rawValueProperty().get();
    }

    /// Sets the raw setting value.
    public void setRawValue(@Nullable Boolean rawValue) {
        rawValueProperty().set(rawValue);
    }

    /// The effective value displayed by the toggle.
    private final BooleanProperty effectiveValue = new SimpleBooleanProperty(this, "effectiveValue");

    /// Returns the effective value displayed by the toggle.
    public BooleanProperty effectiveValueProperty() {
        return effectiveValue;
    }

    /// Returns the effective value displayed by the toggle.
    public boolean isEffectiveValue() {
        return effectiveValueProperty().get();
    }

    /// Sets the effective value displayed by the toggle.
    public void setEffectiveValue(boolean effectiveValue) {
        effectiveValueProperty().set(effectiveValue);
    }

    /// Whether inherited mode can be selected.
    private final BooleanProperty inheritAvailable = new SimpleBooleanProperty(this, "inheritAvailable", true);

    /// Returns whether inherited mode can be selected.
    public BooleanProperty inheritAvailableProperty() {
        return inheritAvailable;
    }

    /// Returns whether inherited mode can be selected.
    public boolean isInheritAvailable() {
        return inheritAvailableProperty().get();
    }

    /// Sets whether inherited mode can be selected.
    public void setInheritAvailable(boolean inheritAvailable) {
        inheritAvailableProperty().set(inheritAvailable);
    }

    /// The text displayed while inheriting the parent value.
    private final StringProperty inheritedText = new SimpleStringProperty(this, "inheritedText", "");

    /// Returns the text displayed while inheriting the parent value.
    public StringProperty inheritedTextProperty() {
        return inheritedText;
    }

    /// Returns the text displayed while inheriting the parent value.
    public String getInheritedText() {
        return inheritedTextProperty().get();
    }

    /// Sets the text displayed while inheriting the parent value.
    public void setInheritedText(String inheritedText) {
        inheritedTextProperty().set(inheritedText);
    }

    /// The text displayed while overriding the parent value.
    private final StringProperty overriddenText = new SimpleStringProperty(this, "overriddenText", "");

    /// Returns the text displayed while overriding the parent value.
    public StringProperty overriddenTextProperty() {
        return overriddenText;
    }

    /// Returns the text displayed while overriding the parent value.
    public String getOverriddenText() {
        return overriddenTextProperty().get();
    }

    /// Sets the text displayed while overriding the parent value.
    public void setOverriddenText(String overriddenText) {
        overriddenTextProperty().set(overriddenText);
    }

    /// Sets the tooltip displayed on the inherit button.
    public void setInheritTooltip(String tooltip) {
        FXUtils.installFastTooltip(inheritButton, tooltip);
    }
}
