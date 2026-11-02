// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXSwitch;
import javafx.beans.InvalidationListener;
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

/// A line component that edits an inheritable boolean while showing the effective switch state.
///
/// The override state is represented separately from the direct boolean value. The switch always
/// reflects the effective value currently applied by the setting hierarchy.
@NotNullByDefault
public final class LineInheritableSwitch extends LineButtonBase {
    /// The style class applied to inheritable switch rows.
    private static final String DEFAULT_STYLE_CLASS = "line-inheritable-switch";

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

    /// The visual switch that displays the effective value.
    private final JFXSwitch switchControl;

    /// Creates an inheritable boolean switch row.
    public LineInheritableSwitch() {
        this.getStyleClass().addAll(DEFAULT_STYLE_CLASS, "line-switch");

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

        this.switchControl = new JFXSwitch();
        switchControl.accessibleTextProperty().bind(titleProperty());
        switchControl.setOnAction(event -> {
            // Override listeners may refresh the switch synchronously; preserve the requested value.
            boolean selected = switchControl.isSelected();
            event.consume();
            setOverridden(true);
            setRawValue(selected);
            super.fire();
        });

        setTitleTrailing(inheritButton);
        setTrailing(switchControl);

        InvalidationListener refreshListener = observable -> refresh();
        rawValue.addListener(refreshListener);
        overridden.addListener(refreshListener);
        effectiveValue.addListener(refreshListener);
        inheritAvailable.addListener(refreshListener);
        inheritedTooltip.addListener(refreshListener);
        overriddenTooltip.addListener(refreshListener);
        refresh();
    }

    /// The raw value stored in this setting.
    private final BooleanProperty rawValue = new SimpleBooleanProperty(this, "rawValue");

    /// Sets the raw setting value.
    public void setRawValue(boolean rawValue) {
        rawValueProperty().set(rawValue);
    }

    /// Returns the raw setting value.
    public boolean getRawValue() {
        return rawValueProperty().get();
    }

    /// Returns the raw setting value.
    public BooleanProperty rawValueProperty() {
        return rawValue;
    }

    /// Whether the direct value overrides the inherited value.
    private final BooleanProperty overridden = new SimpleBooleanProperty(this, "overridden");

    /// Sets whether the direct value overrides the inherited value.
    public void setOverridden(boolean overridden) {
        overriddenProperty().set(overridden);
    }

    /// Returns whether the direct value overrides the inherited value.
    public boolean isOverridden() {
        return overriddenProperty().get();
    }

    /// Returns the override-state property.
    public BooleanProperty overriddenProperty() {
        return overridden;
    }

    /// The effective value displayed by the switch.
    private final BooleanProperty effectiveValue = new SimpleBooleanProperty(this, "effectiveValue");

    /// Sets the effective value displayed by the switch.
    public void setEffectiveValue(boolean effectiveValue) {
        effectiveValueProperty().set(effectiveValue);
    }

    /// Returns the effective value displayed by the switch.
    public boolean isEffectiveValue() {
        return effectiveValueProperty().get();
    }

    /// Returns the effective value displayed by the switch.
    public BooleanProperty effectiveValueProperty() {
        return effectiveValue;
    }

    /// Whether inherited mode can be selected.
    private final BooleanProperty inheritAvailable = new SimpleBooleanProperty(this, "inheritAvailable", true);

    /// Sets whether inherited mode can be selected.
    public void setInheritAvailable(boolean inheritAvailable) {
        inheritAvailableProperty().set(inheritAvailable);
    }

    /// Returns whether inherited mode can be selected.
    public boolean isInheritAvailable() {
        return inheritAvailableProperty().get();
    }

    /// Returns whether inherited mode can be selected.
    public BooleanProperty inheritAvailableProperty() {
        return inheritAvailable;
    }

    /// The tooltip displayed while inheriting the parent value.
    private final StringProperty inheritedTooltip = new SimpleStringProperty(this, "inheritedTooltip", "");

    /// Sets the tooltip displayed while inheriting the parent value.
    public void setInheritedTooltip(String inheritedTooltip) {
        inheritedTooltipProperty().set(inheritedTooltip);
    }

    /// Returns the tooltip displayed while inheriting the parent value.
    public String getInheritedTooltip() {
        return inheritedTooltipProperty().get();
    }

    /// Returns the tooltip displayed while inheriting the parent value.
    public StringProperty inheritedTooltipProperty() {
        return inheritedTooltip;
    }

    /// The tooltip displayed while overriding the parent value.
    private final StringProperty overriddenTooltip = new SimpleStringProperty(this, "overriddenTooltip", "");

    /// Sets the tooltip displayed while overriding the parent value.
    public void setOverriddenTooltip(String overriddenTooltip) {
        overriddenTooltipProperty().set(overriddenTooltip);
    }

    /// Returns the tooltip displayed while overriding the parent value.
    public String getOverriddenTooltip() {
        return overriddenTooltipProperty().get();
    }

    /// Returns the tooltip displayed while overriding the parent value.
    public StringProperty overriddenTooltipProperty() {
        return overriddenTooltip;
    }

    /// Sets the tooltip displayed on the inherit button in inherited mode.
    public void setInheritTooltip(String tooltip) {
        setInheritedTooltip(tooltip);
    }

    /// Overrides the inherited value with its inverse and fires the row action.
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

        switchControl.setSelected(isEffectiveValue());
    }
}
