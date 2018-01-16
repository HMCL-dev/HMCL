/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXRippler;
import javafx.animation.Transition;
import javafx.beans.DefaultProperty;
import javafx.beans.NamedArg;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import org.jackhuang.hmcl.util.Lang;

@DefaultProperty("container")
public class RipplerContainer extends StackPane {
    private final ObjectProperty<Node> container = new SimpleObjectProperty<>(this, "container", null);
    private final ObjectProperty<Paint> ripplerFill = new SimpleObjectProperty<>(this, "ripplerFill", null);
    private final BooleanProperty selected = new SimpleBooleanProperty(this, "selected", false);

    private final StackPane buttonContainer = new StackPane();
    private final JFXRippler buttonRippler = new JFXRippler(new StackPane()) {
        @Override
        protected Node getMask() {
            StackPane mask = new StackPane();
            mask.shapeProperty().bind(buttonContainer.shapeProperty());
            mask.backgroundProperty().bind(Bindings.createObjectBinding(() -> new Background(new BackgroundFill(Color.WHITE, buttonContainer.getBackground() != null && buttonContainer.getBackground().getFills().size() > 0 ? buttonContainer.getBackground().getFills().get(0).getRadii() : defaultRadii, buttonContainer.getBackground() != null && buttonContainer.getBackground().getFills().size() > 0 ? buttonContainer.getBackground().getFills().get(0).getInsets() : Insets.EMPTY)), buttonContainer.backgroundProperty()));
            mask.resize(buttonContainer.getWidth() - buttonContainer.snappedRightInset() - buttonContainer.snappedLeftInset(), buttonContainer.getHeight() - buttonContainer.snappedBottomInset() - buttonContainer.snappedTopInset());
            return mask;
        }

        @Override
        protected void initListeners() {
            this.ripplerPane.setOnMousePressed(event -> {
                if (releaseManualRippler != null)
                    releaseManualRippler.run();
                releaseManualRippler = null;
                createRipple(event.getX(), event.getY());
            });
        }
    };

    private Transition clickedAnimation;
    private final CornerRadii defaultRadii = new CornerRadii(3);
    private Runnable releaseManualRippler;

    public RipplerContainer(@NamedArg("container") Node container) {
        setContainer(container);

        getStyleClass().add("rippler-container");
        buttonContainer.getChildren().add(buttonRippler);
        setOnMousePressed(event -> {
            if (clickedAnimation != null) {
                clickedAnimation.setRate(1);
                clickedAnimation.play();
            }
        });
        setOnMouseReleased(event -> {
            if (clickedAnimation != null) {
                clickedAnimation.setRate(-1);
                clickedAnimation.play();
            }
        });
        focusedProperty().addListener((a, b, newValue) -> {
            if (newValue) {
                if (!isPressed())
                    buttonRippler.showOverlay();
            } else {
                buttonRippler.hideOverlay();
            }
        });
        pressedProperty().addListener(o -> buttonRippler.hideOverlay());
        setPickOnBounds(false);

        buttonContainer.setPickOnBounds(false);
        buttonContainer.shapeProperty().bind(shapeProperty());
        buttonContainer.borderProperty().bind(borderProperty());
        buttonContainer.backgroundProperty().bind(Bindings.createObjectBinding(() -> {
            if (getBackground() == null || isJavaDefaultBackground(getBackground()) || isJavaDefaultClickedBackground(getBackground()))
                setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, defaultRadii, null)));

            try {
                return new Background(new BackgroundFill(getBackground() != null ? getBackground().getFills().get(0).getFill() : Color.TRANSPARENT,
                        getBackground() != null ? getBackground().getFills().get(0).getRadii() : defaultRadii, Insets.EMPTY));
            } catch (Exception e) {
                return getBackground();
            }
        }, backgroundProperty()));

        ripplerFillProperty().addListener((a, b, newValue) -> buttonRippler.setRipplerFill(newValue));
        if (getBackground() == null || isJavaDefaultBackground(getBackground()))
            setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, defaultRadii, null)));

        containerProperty().addListener(o -> updateChildren());
        updateChildren();

        selectedProperty().addListener(o -> {
            if (isSelected()) setBackground(new Background(new BackgroundFill(getRipplerFill(), defaultRadii, null)));
            else setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, defaultRadii, null)));
        });

        setShape(Lang.apply(new Rectangle(), rectangle -> {
            rectangle.widthProperty().bind(widthProperty());
            rectangle.heightProperty().bind(heightProperty());
        }));
    }

    protected void updateChildren() {
        getChildren().addAll(buttonContainer, getContainer());

        for (int i = 1; i < getChildren().size(); ++i)
            getChildren().get(i).setPickOnBounds(false);
    }

    private boolean isJavaDefaultBackground(Background background) {
        try {
            String firstFill = background.getFills().get(0).getFill().toString();
            return "0xffffffba".equals(firstFill) || "0xffffffbf".equals(firstFill) || "0xffffffbd".equals(firstFill);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isJavaDefaultClickedBackground(Background background) {
        try {
            String firstFill = background.getFills().get(0).getFill().toString();
            return "0x039ed3ff".equals(firstFill);
        } catch (Exception e) {
            return false;
        }
    }

    public Node getContainer() {
        return container.get();
    }

    public ObjectProperty<Node> containerProperty() {
        return container;
    }

    public void setContainer(Node container) {
        this.container.set(container);
    }

    public Paint getRipplerFill() {
        return ripplerFill.get();
    }

    public ObjectProperty<Paint> ripplerFillProperty() {
        return ripplerFill;
    }

    public void setRipplerFill(Paint ripplerFill) {
        this.ripplerFill.set(ripplerFill);
    }

    public boolean isSelected() {
        return selected.get();
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected.set(selected);
    }
}
