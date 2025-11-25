/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.jfoenix.skins;

import com.jfoenix.controls.*;
import com.jfoenix.svg.SVGGlyph;
import com.jfoenix.transitions.JFXFillTransition;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.*;
import javafx.util.Duration;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Shadi Shaheen
 */
public class JFXCustomColorPickerDialog extends StackPane {

    public static final String rgbFieldStyle = "-fx-background-color:TRANSPARENT;-fx-font-weight: BOLD;-fx-prompt-text-fill: #808080; -fx-alignment: top-left ; -fx-max-width: 300;";
    private final Stage dialog = new Stage();
    // used for concurrency control and preventing FX-thread over use
    private final AtomicInteger concurrencyController = new AtomicInteger(-1);

    private ObjectProperty<Color> currentColorProperty = new SimpleObjectProperty<>(Color.WHITE);
    private ObjectProperty<Color> customColorProperty = new SimpleObjectProperty<>(Color.TRANSPARENT);
    private Runnable onSave;

    private Scene customScene;
    private JFXCustomColorPicker curvedColorPicker;
    private ParallelTransition paraTransition;
    private JFXDecorator pickerDecorator;
    private boolean systemChange = false;
    private boolean userChange = false;
    private boolean initOnce = true;
    private Runnable initRun;

    public JFXCustomColorPickerDialog(Window owner) {
        getStyleClass().add("custom-color-dialog");
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setResizable(false);

        // create JFX Decorator
        pickerDecorator = new JFXDecorator(dialog, this, false, false, false);
        pickerDecorator.setOnCloseButtonAction(() -> updateColor());
        pickerDecorator.setPickOnBounds(false);
        customScene = new Scene(pickerDecorator, Color.TRANSPARENT);
        if(owner!=null) {
            final Scene ownerScene = owner.getScene();
            if (ownerScene != null) {
                if (ownerScene.getUserAgentStylesheet() != null) {
                    customScene.setUserAgentStylesheet(ownerScene.getUserAgentStylesheet());
                }
                customScene.getStylesheets().addAll(ownerScene.getStylesheets());
            }
        }
        curvedColorPicker = new JFXCustomColorPicker();

        StackPane pane = new StackPane(curvedColorPicker);
        pane.setPadding(new Insets(18));

        VBox container = new VBox();
        container.getChildren().add(pane);

        JFXTabPane tabs = new JFXTabPane();

        JFXTextField rgbField = new JFXTextField();
        JFXTextField hsbField = new JFXTextField();
        JFXTextField hexField = new JFXTextField();

        rgbField.setStyle(rgbFieldStyle);
        rgbField.setPromptText("RGB Color");
        rgbField.textProperty().addListener((o, oldVal, newVal) -> updateColorFromUserInput(newVal));

        hsbField.setStyle(
            "-fx-background-color:TRANSPARENT;-fx-font-weight: BOLD;-fx-prompt-text-fill: #808080; -fx-alignment: top-left ; -fx-max-width: 300;");
        hsbField.setPromptText("HSB Color");
        hsbField.textProperty().addListener((o, oldVal, newVal) -> updateColorFromUserInput(newVal));

        hexField.setStyle(
            "-fx-background-color:TRANSPARENT;-fx-font-weight: BOLD;-fx-prompt-text-fill: #808080; -fx-alignment: top-left ; -fx-max-width: 300;");
        hexField.setPromptText("#HEX Color");
        hexField.textProperty().addListener((o, oldVal, newVal) -> updateColorFromUserInput(newVal));

        StackPane tabContent = new StackPane();
        tabContent.getChildren().add(rgbField);
        tabContent.setMinHeight(100);

        Tab rgbTab = new Tab("RGB");
        rgbTab.setContent(tabContent);
        Tab hsbTab = new Tab("HSB");
        hsbTab.setContent(hsbField);
        Tab hexTab = new Tab("HEX");
        hexTab.setContent(hexField);

        tabs.getTabs().add(rgbTab);
        tabs.getTabs().add(hsbTab);
        tabs.getTabs().add(hexTab);

        curvedColorPicker.selectedPath.addListener((o, oldVal, newVal) -> {
            if (paraTransition != null) {
                paraTransition.stop();
            }
            Region tabsHeader = (Region) tabs.lookup(".tab-header-background");
            pane.backgroundProperty().unbind();
            tabsHeader.backgroundProperty().unbind();
            JFXFillTransition fillTransition = new JFXFillTransition(Duration.millis(240),
                pane,
                (Color) oldVal.getFill(),
                (Color) newVal.getFill());
            JFXFillTransition tabsFillTransition = new JFXFillTransition(Duration.millis(240),
                tabsHeader,
                (Color) oldVal.getFill(),
                (Color) newVal.getFill());
            paraTransition = new ParallelTransition(fillTransition, tabsFillTransition);
            paraTransition.setOnFinished((finish) -> {
                tabsHeader.backgroundProperty().bind(Bindings.createObjectBinding(() -> {
                    return new Background(new BackgroundFill(newVal.getFill(), CornerRadii.EMPTY, Insets.EMPTY));
                }, newVal.fillProperty()));
                pane.backgroundProperty().bind(Bindings.createObjectBinding(() -> {
                    return new Background(new BackgroundFill(newVal.getFill(), CornerRadii.EMPTY, Insets.EMPTY));
                }, newVal.fillProperty()));
            });
            paraTransition.play();
        });

        initRun = () -> {
            // change tabs labels font color according to the selected color
            pane.backgroundProperty().addListener((o, oldVal, newVal) -> {
                if (concurrencyController.getAndSet(1) == -1) {
                    Color fontColor = ((Color) newVal.getFills().get(0).getFill()).grayscale()
                        .getRed() > 0.5 ? Color.valueOf(
                        "rgba(40, 40, 40, 0.87)") : Color.valueOf("rgba(255, 255, 255, 0.87)");
                    for (Node tabNode : tabs.lookupAll(".tab")) {
                        for (Node node : tabNode.lookupAll(".tab-label")) {
                            ((Label) node).setTextFill(fontColor);
                        }
                        for (Node node : tabNode.lookupAll(".jfx-rippler")) {
                            ((JFXRippler) node).setRipplerFill(fontColor);
                        }
                    }
                    ((Pane) tabs.lookup(".tab-selected-line")).setBackground(new Background(new BackgroundFill(fontColor,CornerRadii.EMPTY,Insets.EMPTY)));
                    pickerDecorator.lookupAll(".jfx-decorator-button").forEach(button -> {
                        ((JFXButton) button).setRipplerFill(fontColor);
                        ((SVGGlyph) ((JFXButton) button).getGraphic()).setFill(fontColor);
                    });

                    Color newColor = (Color) newVal.getFills().get(0).getFill();
                    String hex = String.format("#%02X%02X%02X",
                        (int) (newColor.getRed() * 255),
                        (int) (newColor.getGreen() * 255),
                        (int) (newColor.getBlue() * 255));
                    String rgb = String.format("rgba(%d, %d, %d, 1)",
                        (int) (newColor.getRed() * 255),
                        (int) (newColor.getGreen() * 255),
                        (int) (newColor.getBlue() * 255));
                    String hsb = String.format("hsl(%d, %d%%, %d%%)",
                        (int) (newColor.getHue()),
                        (int) (newColor.getSaturation() * 100),
                        (int) (newColor.getBrightness() * 100));

                    if (!userChange) {
                        systemChange = true;
                        rgbField.setText(rgb);
                        hsbField.setText(hsb);
                        hexField.setText(hex);
                        systemChange = false;
                    }
                    concurrencyController.getAndSet(-1);
                }
            });

            // initial selected colors
            Platform.runLater(() -> {
                pane.setBackground(new Background(new BackgroundFill(curvedColorPicker.getColor(curvedColorPicker.getSelectedIndex()),
                    CornerRadii.EMPTY,
                    Insets.EMPTY)));
                ((Region) tabs.lookup(".tab-header-background")).setBackground(new Background(new BackgroundFill(
                    curvedColorPicker.getColor(curvedColorPicker.getSelectedIndex()),
                    CornerRadii.EMPTY,
                    Insets.EMPTY)));
                Region tabsHeader = (Region) tabs.lookup(".tab-header-background");
                pane.backgroundProperty().unbind();
                tabsHeader.backgroundProperty().unbind();
                tabsHeader.backgroundProperty().bind(Bindings.createObjectBinding(() -> {
                    return new Background(new BackgroundFill(curvedColorPicker.selectedPath.get().getFill(),
                        CornerRadii.EMPTY,
                        Insets.EMPTY));
                }, curvedColorPicker.selectedPath.get().fillProperty()));
                pane.backgroundProperty().bind(Bindings.createObjectBinding(() -> {
                    return new Background(new BackgroundFill(curvedColorPicker.selectedPath.get().getFill(),
                        CornerRadii.EMPTY,
                        Insets.EMPTY));
                }, curvedColorPicker.selectedPath.get().fillProperty()));

                // bind text field line color
                rgbField.focusColorProperty().bind(Bindings.createObjectBinding(() -> {
                    return pane.getBackground().getFills().get(0).getFill();
                }, pane.backgroundProperty()));
                hsbField.focusColorProperty().bind(Bindings.createObjectBinding(() -> {
                    return pane.getBackground().getFills().get(0).getFill();
                }, pane.backgroundProperty()));
                hexField.focusColorProperty().bind(Bindings.createObjectBinding(() -> {
                    return pane.getBackground().getFills().get(0).getFill();
                }, pane.backgroundProperty()));


                ((Pane) pickerDecorator.lookup(".jfx-decorator-buttons-container")).backgroundProperty()
                    .bind(Bindings.createObjectBinding(() -> {
                        return new Background(new BackgroundFill(
                            pane.getBackground()
                                .getFills()
                                .get(0)
                                .getFill(),
                            CornerRadii.EMPTY,
                            Insets.EMPTY));
                    }, pane.backgroundProperty()));

                ((Pane) pickerDecorator.lookup(".jfx-decorator-content-container")).borderProperty()
                    .bind(Bindings.createObjectBinding(() -> {
                        return new Border(new BorderStroke(
                            pane.getBackground()
                                .getFills()
                                .get(0)
                                .getFill(),
                            BorderStrokeStyle.SOLID,
                            CornerRadii.EMPTY,
                            new BorderWidths(0,
                                4,
                                4,
                                4)));
                    }, pane.backgroundProperty()));
            });
        };


        container.getChildren().add(tabs);

        this.getChildren().add(container);
        this.setPadding(new Insets(0));

        dialog.setScene(customScene);
        final EventHandler<KeyEvent> keyEventListener = key -> {
            switch (key.getCode()) {
                case ESCAPE:
                    close();
                    break;
                case ENTER:
                    updateColor();
                    break;
                default:
                    break;
            }
        };
        dialog.addEventHandler(KeyEvent.ANY, keyEventListener);
    }

    private void updateColor() {
        close();
        this.customColorProperty.set(curvedColorPicker.getColor(curvedColorPicker.getSelectedIndex()));
        this.onSave.run();
    }

    private void updateColorFromUserInput(String colorWebString) {
        if (!systemChange) {
            userChange = true;
            try {
                curvedColorPicker.setColor(Color.valueOf(colorWebString));
            } catch (IllegalArgumentException ignored) {
                // if color is not valid then do nothing
            }
            userChange = false;
        }
    }

    private void close() {
        dialog.setScene(null);
        dialog.close();
    }

    public void setCurrentColor(Color currentColor) {
        this.currentColorProperty.set(currentColor);
    }

    Color getCurrentColor() {
        return currentColorProperty.get();
    }

    ObjectProperty<Color> customColorProperty() {
        return customColorProperty;
    }

    void setCustomColor(Color color) {
        customColorProperty.set(color);
    }

    Color getCustomColor() {
        return customColorProperty.get();
    }

    public Runnable getOnSave() {
        return onSave;
    }

    public void setOnSave(Runnable onSave) {
        this.onSave = onSave;
    }

    public void setOnHidden(EventHandler<WindowEvent> onHidden) {
        dialog.setOnHidden(onHidden);
    }

    public void show() {
        dialog.setOpacity(0);
        //		pickerDecorator.setOpacity(0);
        if (dialog.getOwner() != null) {
            dialog.widthProperty().addListener(positionAdjuster);
            dialog.heightProperty().addListener(positionAdjuster);
            positionAdjuster.invalidated(null);
        }
        if (dialog.getScene() == null) {
            dialog.setScene(customScene);
        }
        curvedColorPicker.preAnimate();
        dialog.show();
        if (initOnce) {
            initRun.run();
            initOnce = false;
        }

        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(120),
            new KeyValue(dialog.opacityProperty(),
                1,
                Interpolator.EASE_BOTH)));
        timeline.setOnFinished((finish) -> curvedColorPicker.animate());
        timeline.play();
    }


    // add option to show color picker using JFX Dialog
    private InvalidationListener positionAdjuster = new InvalidationListener() {
        @Override
        public void invalidated(Observable ignored) {
            if (Double.isNaN(dialog.getWidth()) || Double.isNaN(dialog.getHeight())) {
                return;
            }
            dialog.widthProperty().removeListener(positionAdjuster);
            dialog.heightProperty().removeListener(positionAdjuster);
            fixPosition();
        }
    };

    private void fixPosition() {
        Window w = dialog.getOwner();
        Screen s = com.sun.javafx.util.Utils.getScreen(w);
        Rectangle2D sb = s.getBounds();
        double xR = w.getX() + w.getWidth();
        double xL = w.getX() - dialog.getWidth();
        double x;
        double y;
        if (sb.getMaxX() >= xR + dialog.getWidth()) {
            x = xR;
        } else if (sb.getMinX() <= xL) {
            x = xL;
        } else {
            x = Math.max(sb.getMinX(), sb.getMaxX() - dialog.getWidth());
        }
        y = Math.max(sb.getMinY(), Math.min(sb.getMaxY() - dialog.getHeight(), w.getY()));
        dialog.setX(x);
        dialog.setY(y);
    }

    @Override
    public void layoutChildren() {
        super.layoutChildren();
        if (dialog.getMinWidth() > 0 && dialog.getMinHeight() > 0) {
            return;
        }
        double minWidth = Math.max(0, computeMinWidth(getHeight()) + (dialog.getWidth() - customScene.getWidth()));
        double minHeight = Math.max(0, computeMinHeight(getWidth()) + (dialog.getHeight() - customScene.getHeight()));
        dialog.setMinWidth(minWidth);
        dialog.setMinHeight(minHeight);
    }
}
