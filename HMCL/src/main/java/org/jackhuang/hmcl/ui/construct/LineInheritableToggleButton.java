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
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.css.PseudoClass;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jetbrains.annotations.NotNullByDefault;

/// A line component that edits an inheritable boolean while showing the effective toggle state.
///
/// The override state is represented separately from the direct boolean value. The toggle always
/// reflects the effective value currently applied by the setting hierarchy.
@NotNullByDefault
public final class LineInheritableToggleButton extends LineButtonBase {
    /// The style class applied to inheritable toggle rows.
    private static final String DEFAULT_STYLE_CLASS = "line-inheritable-toggle-button";

    /// The pseudo class applied while the value overrides the inherited setting.
    private static final PseudoClass PSEUDO_OVERRIDDEN = PseudoClass.getPseudoClass("overridden");

    /// The style class applied to the compact inheritance state button.
    private static final String INHERIT_BUTTON_STYLE_CLASS = "toggle-icon-tiny";

    /// The icon size used by the compact inheritance state button.
    private static final int INHERIT_BUTTON_ICON_SIZE = 12;

    /// The button that toggles between inherited and overridden mode.
    private final JFXButton inheritButton;

    /// The tooltip shown on the inheritance button.
    private final Tooltip inheritTooltip;

    /// The visual toggle that displays the effective value.
    private final JFXToggleButton toggleButton;

    /// Creates an inheritable boolean toggle row.
    public LineInheritableToggleButton() {
        this.getStyleClass().addAll(DEFAULT_STYLE_CLASS, "line-toggle-button");

        this.inheritButton = new JFXButton();
        inheritButton.getStyleClass().add(INHERIT_BUTTON_STYLE_CLASS);
        inheritButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        inheritButton.setGraphic(SVG.PUBLIC.createIcon(INHERIT_BUTTON_ICON_SIZE));
        this.inheritTooltip = new Tooltip();
        FXUtils.installFastTooltip(inheritButton, inheritTooltip);
        inheritButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (!isInheritAvailable()) {
                return;
            }

            if (!isOverridden()) {
                setRawValue(isEffectiveValue());
                setOverridden(true);
            } else {
                setOverridden(false);
            }
            super.fire();
            event.consume();
        });

        this.toggleButton = new JFXToggleButton();
        toggleButton.setMouseTransparent(true);
        toggleButton.setSize(8);
        FXUtils.setLimitHeight(toggleButton, 30);

        setTitleTrailing(inheritButton);
        setNode(IDX_TRAILING, toggleButton);

        rawValue.addListener(observable -> refresh());
        overridden.addListener(observable -> refresh());
        effectiveValue.addListener(observable -> refresh());
        inheritAvailable.addListener(observable -> refresh());
        inheritedText.addListener(observable -> refresh());
        overriddenText.addListener(observable -> refresh());
        inheritedTooltip.addListener(observable -> refresh());
        overriddenTooltip.addListener(observable -> refresh());
        refresh();
    }

    @Override
    public void fire() {
        setOverridden(true);
        setRawValue(!isEffectiveValue());
        super.fire();
    }

    /// Refreshes the visual state from the raw and effective values.
    private void refresh() {
        boolean inheritAvailable = isInheritAvailable();
        boolean inherited = inheritAvailable && !isOverridden();
        boolean overridden = inheritAvailable && isOverridden();

        inheritButton.setGraphic((inherited ? SVG.PUBLIC : SVG.TUNE).createIcon(INHERIT_BUTTON_ICON_SIZE));
        inheritButton.pseudoClassStateChanged(PSEUDO_OVERRIDDEN, overridden);
        inheritButton.setVisible(inheritAvailable);
        inheritButton.setManaged(inheritAvailable);
        inheritTooltip.setText(inherited ? getInheritedTooltip() : getOverriddenTooltip());

        toggleButton.setSelected(isEffectiveValue());
    }

    /// The raw value stored in this setting.
    private final BooleanProperty rawValue = new SimpleBooleanProperty(this, "rawValue");

    /// Returns the raw setting value.
    public BooleanProperty rawValueProperty() {
        return rawValue;
    }

    /// Returns the raw setting value.
    public boolean getRawValue() {
        return rawValueProperty().get();
    }

    /// Sets the raw setting value.
    public void setRawValue(boolean rawValue) {
        rawValueProperty().set(rawValue);
    }

    /// Whether the direct value overrides the inherited value.
    private final BooleanProperty overridden = new SimpleBooleanProperty(this, "overridden");

    /// Returns the override-state property.
    public BooleanProperty overriddenProperty() {
        return overridden;
    }

    /// Returns whether the direct value overrides the inherited value.
    public boolean isOverridden() {
        return overriddenProperty().get();
    }

    /// Sets whether the direct value overrides the inherited value.
    public void setOverridden(boolean overridden) {
        overriddenProperty().set(overridden);
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

    /// The text that describes inherited mode.
    private final StringProperty inheritedText = new SimpleStringProperty(this, "inheritedText", "");

    /// Returns the text that describes inherited mode.
    public StringProperty inheritedTextProperty() {
        return inheritedText;
    }

    /// Returns the text that describes inherited mode.
    public String getInheritedText() {
        return inheritedTextProperty().get();
    }

    /// Sets the text that describes inherited mode.
    public void setInheritedText(String inheritedText) {
        inheritedTextProperty().set(inheritedText);
    }

    /// The text that describes overridden mode.
    private final StringProperty overriddenText = new SimpleStringProperty(this, "overriddenText", "");

    /// Returns the text that describes overridden mode.
    public StringProperty overriddenTextProperty() {
        return overriddenText;
    }

    /// Returns the text that describes overridden mode.
    public String getOverriddenText() {
        return overriddenTextProperty().get();
    }

    /// Sets the text that describes overridden mode.
    public void setOverriddenText(String overriddenText) {
        overriddenTextProperty().set(overriddenText);
    }

    /// The tooltip displayed while inheriting the parent value.
    private final StringProperty inheritedTooltip = new SimpleStringProperty(this, "inheritedTooltip", "");

    /// Returns the tooltip displayed while inheriting the parent value.
    public StringProperty inheritedTooltipProperty() {
        return inheritedTooltip;
    }

    /// Returns the tooltip displayed while inheriting the parent value.
    public String getInheritedTooltip() {
        return inheritedTooltipProperty().get();
    }

    /// Sets the tooltip displayed while inheriting the parent value.
    public void setInheritedTooltip(String inheritedTooltip) {
        inheritedTooltipProperty().set(inheritedTooltip);
        refresh();
    }

    /// The tooltip displayed while overriding the parent value.
    private final StringProperty overriddenTooltip = new SimpleStringProperty(this, "overriddenTooltip", "");

    /// Returns the tooltip displayed while overriding the parent value.
    public StringProperty overriddenTooltipProperty() {
        return overriddenTooltip;
    }

    /// Returns the tooltip displayed while overriding the parent value.
    public String getOverriddenTooltip() {
        return overriddenTooltipProperty().get();
    }

    /// Sets the tooltip displayed while overriding the parent value.
    public void setOverriddenTooltip(String overriddenTooltip) {
        overriddenTooltipProperty().set(overriddenTooltip);
        refresh();
    }

    /// Sets the tooltip displayed on the inherit button in inherited mode.
    public void setInheritTooltip(String tooltip) {
        setInheritedTooltip(tooltip);
    }
}
