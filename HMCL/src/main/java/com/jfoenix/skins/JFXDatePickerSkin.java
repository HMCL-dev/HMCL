//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.jfoenix.skins;

import com.jfoenix.adapters.ReflectionHelper;
import com.jfoenix.adapters.skins.DatePickerSkin;
import com.jfoenix.controls.JFXDatePicker;
import com.jfoenix.controls.JFXDialog;
import com.jfoenix.controls.JFXDialog.DialogTransition;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Stack;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class JFXDatePickerSkin extends DatePickerSkin {
    public static final String PROPERTY_DATE_PICKER_CONTENTS = JFXDatePicker.class.getName() + ".dialog.contents";

    private final JFXDatePicker jfxDatePicker;
    private TextField displayNode;
    private JFXDatePickerContent content;
    private JFXDialog dialog;

    public JFXDatePickerSkin(JFXDatePicker datePicker) {
        super(datePicker);
        this.jfxDatePicker = datePicker;
        super.getPopupContent();

        try {
            Object expressionHelper = ReflectionHelper.getFieldContent(datePicker.focusedProperty().getClass().getSuperclass(), datePicker.focusedProperty(), "helper");
            ChangeListener<Boolean>[] changeListeners = ReflectionHelper.getFieldContent(expressionHelper, "changeListeners");

            int i = changeListeners.length - 1;
            while (changeListeners[i] == null) {
                --i;
            }

            datePicker.focusedProperty().removeListener(changeListeners[i]);
        } catch (NullPointerException e) {
            LOG.warning("Cannot remove focusedProperty listener", e);
        }

        datePicker.focusedProperty().addListener((obj, oldVal, newVal) -> {
            if (this.getEditor() != null && !newVal) {
                this.setTextFromTextFieldIntoComboBoxValue2();
            }

        });
        var arrowBox = new HBox(new RipplerContainer(SVG.EDIT_CALENDAR.createIcon(30)));
        arrowBox.setAlignment(Pos.CENTER);
        this.setArrow(arrowBox);
        this.getArrowButton().getChildren().setAll(arrowBox);
        this.getArrowButton().setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)));
        this.getArrowButton().setPadding(new Insets(1.0F, 8.0F, 1.0F, 8.0F));
        this.registerChangeListener2(datePicker.converterProperty(), "CONVERTER", this::updateDisplayNode2);
        this.registerChangeListener2(datePicker.dayCellFactoryProperty(), "DAY_CELL_FACTORY", () -> {
            this.updateDisplayNode2();
            this.content = null;
            this.setPopup2(null);
        });
        this.registerChangeListener2(datePicker.editorProperty(), "EDITOR", this::getEditableInputNode2);
        this.registerChangeListener2(datePicker.showingProperty(), "SHOWING", () -> {
            if (this.jfxDatePicker.isShowing()) {
                if (this.content != null) {
                    LocalDate date = this.jfxDatePicker.getValue();
                    this.content.displayedYearMonthProperty().set(date != null ? YearMonth.from(date) : YearMonth.now());
                    this.content.updateValues();
                }

                this.show();
            } else {
                this.hide();
            }

        });
        this.registerChangeListener2(datePicker.showWeekNumbersProperty(), "SHOW_WEEK_NUMBERS", () -> {
            if (this.content != null) {
                this.content.updateContentGrid();
                this.content.updateWeekNumberDateCells();
            }

        });
        this.registerChangeListener2(datePicker.valueProperty(), "VALUE", () -> {
            this.updateDisplayNode2();
            this.jfxDatePicker.fireEvent(new ActionEvent());
        });
    }

    public JFXDatePickerContent getPopupContent() {
        if (this.content == null) {
            this.content = new JFXDatePickerContent(this.jfxDatePicker);
        }

        return this.content;
    }

    public void show() {
        if (!((JFXDatePicker) this.getSkinnable()).isOverLay()) {
            super.show();
        }

        if (this.content != null) {
            this.content.init();
            this.content.clearFocus();
        }

        if (((JFXDatePicker) this.getSkinnable()).isOverLay() && this.dialog == null) {
            StackPane dialogParent = this.jfxDatePicker.getDialogParent();
            if (dialogParent == null) {
                dialogParent = (StackPane) this.getSkinnable().getScene().getRoot();
            }

            this.dialog = new JFXDialog(dialogParent, this.getPopupContent(), DialogTransition.CENTER, true) {
                @Override
                public void close() {
                    super.close();
                    Object o = getDialogContainer().getProperties().get(PROPERTY_DATE_PICKER_CONTENTS);
                    if (o instanceof Stack<?> stack) {
                        stack.pop();
                    }
                }
            };
            this.getPopupContent().addEventHandler(DialogCloseEvent.CLOSE, e -> this.dialog.close());
            getArrowButton().setOnMouseClicked((click) -> {
                if (((JFXDatePicker) this.getSkinnable()).isOverLay()) {
                    StackPane parent = this.jfxDatePicker.getDialogParent();
                    if (parent == null) {
                        parent = (StackPane) this.getSkinnable().getScene().getRoot();
                    }
                    Stack<JFXDatePickerContent> contentStack = (Stack<JFXDatePickerContent>) parent.getProperties().computeIfAbsent(PROPERTY_DATE_PICKER_CONTENTS, k -> new Stack<>());
                    contentStack.push(this.getPopupContent());

                    this.getPopupContent().valueProperty.set(this.getSkinnable().getValue());
                    this.dialog.show(parent);
                }
            });
        }

    }

    JFXDialog getDialog() {
        return this.dialog;
    }

    public Node getDisplayNode() {
        if (this.displayNode == null) {
            this.displayNode = this.getEditableInputNode2();
            this.displayNode.getStyleClass().add("date-picker-display-node");
            this.updateDisplayNode2();
        }

        this.displayNode.setEditable(this.jfxDatePicker.isEditable());
        return this.displayNode;
    }

    public void syncWithAutoUpdate() {
        if (!this.getPopup2().isShowing() && this.jfxDatePicker.isShowing()) {
            this.jfxDatePicker.hide();
        }

    }
}
