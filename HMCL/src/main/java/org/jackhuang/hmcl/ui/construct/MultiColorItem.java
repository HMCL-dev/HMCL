package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXColorPicker;
import com.jfoenix.controls.JFXRadioButton;
import javafx.beans.NamedArg;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.ui.FXUtils;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;

public class MultiColorItem extends ComponentList {
    private final StringProperty customTitle = new SimpleStringProperty(this, "customTitle", Launcher.i18n("selector.custom"));
    private final StringProperty chooserTitle = new SimpleStringProperty(this, "chooserTitle", Launcher.i18n("selector.choose_file"));

    private final ToggleGroup group = new ToggleGroup();
    private final JFXColorPicker colorPicker = new JFXColorPicker();
    private final JFXRadioButton radioCustom = new JFXRadioButton();
    private final BorderPane custom = new BorderPane();
    private final VBox pane = new VBox();
    private final boolean hasCustom;

    private Consumer<Toggle> toggleSelectedListener;
    private Consumer<Color> colorConsumer;

    public MultiColorItem(@NamedArg(value = "hasCustom", defaultValue = "true") boolean hasCustom) {
        this.hasCustom = hasCustom;

        radioCustom.textProperty().bind(customTitleProperty());
        radioCustom.setToggleGroup(group);
        colorPicker.disableProperty().bind(radioCustom.selectedProperty().not());

        colorPicker.setOnAction(e -> Optional.ofNullable(colorConsumer).ifPresent(c -> c.accept(colorPicker.getValue())));

        custom.setLeft(radioCustom);
        custom.setStyle("-fx-padding: 3;");
        HBox right = new HBox();
        right.setSpacing(3);
        right.getChildren().addAll(colorPicker);
        custom.setRight(right);
        FXUtils.setLimitHeight(custom, 40);

        pane.setStyle("-fx-padding: 0 0 10 0;");
        pane.setSpacing(8);

        if (hasCustom)
            pane.getChildren().add(custom);
        addChildren(pane);

        group.selectedToggleProperty().addListener((a, b, newValue) -> {
            if (toggleSelectedListener != null)
                toggleSelectedListener.accept(newValue);
        });
    }

    public Node createChildren(String title) {
        return createChildren(title, null);
    }

    public Node createChildren(String title, Object userData) {
        return createChildren(title, "", userData);
    }

    public Node createChildren(String title, String subtitle, Object userData) {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-padding: 3;");

        JFXRadioButton left = new JFXRadioButton(title);
        left.setToggleGroup(group);
        left.setUserData(userData);
        pane.setLeft(left);

        Label right = new Label(subtitle);
        right.getStyleClass().add("subtitle-label");
        right.setStyle("-fx-font-size: 10;");
        pane.setRight(right);

        return pane;
    }

    public void loadChildren(Collection<Node> list) {
        pane.getChildren().setAll(list);

        if (hasCustom)
            pane.getChildren().add(custom);
    }

    public ToggleGroup getGroup() {
        return group;
    }

    public String getCustomTitle() {
        return customTitle.get();
    }

    public StringProperty customTitleProperty() {
        return customTitle;
    }

    public void setCustomTitle(String customTitle) {
        this.customTitle.set(customTitle);
    }

    public String getChooserTitle() {
        return chooserTitle.get();
    }

    public StringProperty chooserTitleProperty() {
        return chooserTitle;
    }

    public void setChooserTitle(String chooserTitle) {
        this.chooserTitle.set(chooserTitle);
    }

    public void setCustomUserData(Object userData) {
        radioCustom.setUserData(userData);
    }

    public boolean isCustomToggle(Toggle toggle) {
        return radioCustom == toggle;
    }

    public void setToggleSelectedListener(Consumer<Toggle> consumer) {
        toggleSelectedListener = consumer;
    }

    public void setOnColorPickerChanged(Consumer<Color> consumer) {
        colorConsumer = consumer;
    }

    public void setColor(Color color) {
        colorPicker.setValue(color);
    }
}
