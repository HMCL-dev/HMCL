package com.jfoenix.controls;

import javafx.animation.*;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.control.Tooltip;
import javafx.scene.control.skin.TooltipSkin;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Text;
import javafx.stage.Screen;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.Motion;

import java.lang.ref.WeakReference;

public class JFXTooltip extends Tooltip {
    private final Timeline animation = new Timeline();
    private final PauseTransition timeout = new PauseTransition();
    private WeakReference<Node> anchor = new WeakReference<>(null);
    private boolean hiding;

    public JFXTooltip() {
        this("");
    }

    public JFXTooltip(String text) {
        super(text);
        setWrapText(true);
        setMaxWidth(200);
        setShowDelay(Duration.millis(500));
        setShowDuration(Duration.millis(5000));
        setHideDelay(Duration.ZERO);
        setHideOnEscape(true);
        addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> {
            animation.stop();
            timeout.stop();
            hiding = false;
        });
    }

    public static void install(Node node, Tooltip tooltip) {
        Object previous = node.getProperties().get("javafx.scene.control.Tooltip");
        if (previous != tooltip) {
            if (previous instanceof Tooltip oldTooltip)
                Tooltip.uninstall(node, oldTooltip);
            Tooltip.install(node, tooltip);
        }
        if (!(tooltip instanceof JFXTooltip plainTooltip))
            return;
        plainTooltip.anchor = new WeakReference<>(node);
        if (node.getProperties().containsKey(JFXTooltip.class))
            return;
        node.getProperties().put(JFXTooltip.class, Boolean.TRUE);
        var focusVisible = FXUtils.focusVisibleProperty(node);
        var focus = focusVisible != null ? focusVisible : node.focusedProperty();
        focus.addListener((observable, oldValue, newValue) -> {
            var installed = installedTooltip(node);
            if (installed == null)
                return;
            if (newValue && !node.isDisabled() && !node.isPressed() && node.getScene() != null
                    && node.getScene().getWindow() != null
                    && node.getScene().getWindow().isFocused()
                    && installed.getText() != null && !installed.getText().isBlank()) {
                installed.anchor = new WeakReference<>(node);
                installed.show(node.getScene().getWindow(), 0, 0);
            } else {
                installed.hide();
            }
        });
        node.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> {
            var installed = installedTooltip(node);
            if (installed != null)
                installed.anchor = new WeakReference<>(node);
        });
        node.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            var installed = installedTooltip(node);
            if (installed != null && event.getCode() == KeyCode.ESCAPE && installed.isShowing()) {
                installed.hide();
                event.consume();
            }
        });
        node.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            var installed = installedTooltip(node);
            if (installed != null)
                installed.hide();
        });
    }

    private static JFXTooltip installedTooltip(Node node) {
        Object value = node.getProperties().get("javafx.scene.control.Tooltip");
        return value instanceof JFXTooltip tooltip ? tooltip : null;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        var skin = new TooltipSkin(this);
        var label = (Label) skin.getNode();
        Runnable updateLineSpacing = () -> {
            var text = new Text("Ag");
            text.setFont(label.getFont());
            label.setLineSpacing(Math.max(0, 16 - text.getLayoutBounds().getHeight()));
        };
        label.fontProperty().addListener(observable -> updateLineSpacing.run());
        updateLineSpacing.run();
        return skin;
    }

    @Override
    public void show(Window owner, double x, double y) {
        boolean visible = isShowing();
        animation.stop();
        hiding = false;
        super.show(owner, x, y);
        if (!isShowing())
            return;
        var root = getScene().getRoot();
        root.applyCss();
        root.layout();
        position();
        timeout.stop();
        if (getShowDuration() != null && !getShowDuration().isIndefinite()
                && getShowDuration().greaterThan(Duration.ZERO)) {
            timeout.setDuration(getShowDuration());
            timeout.setOnFinished(event -> hide());
            timeout.playFromStart();
        }
        if (!AnimationUtils.isAnimationEnabled()) {
            root.setOpacity(1);
            root.setScaleX(1);
            root.setScaleY(1);
            return;
        }
        if (!visible) {
            root.setOpacity(0);
            root.setScaleX(0.8);
            root.setScaleY(0.8);
        }
        animate(1, 1, Duration.millis(150), Motion.STANDARD_DECELERATE);
    }

    private void position() {
        Node node = anchor.get();
        if (node == null)
            return;
        Bounds bounds = node.localToScreen(node.getBoundsInLocal());
        if (bounds == null)
            return;
        var screens = Screen.getScreensForRectangle(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
        Rectangle2D screen = (screens.isEmpty() ? Screen.getPrimary() : screens.get(0)).getVisualBounds();
        double x = bounds.getMinX() + (bounds.getWidth() - getWidth()) / 2;
        double y = bounds.getMaxY() + 4;
        if (y + getHeight() > screen.getMaxY())
            y = bounds.getMinY() - getHeight() - 4;
        setX(Math.max(screen.getMinX(), Math.min(x, screen.getMaxX() - getWidth())));
        setY(Math.max(screen.getMinY(), Math.min(y, screen.getMaxY() - getHeight())));
    }

    @Override
    public void hide() {
        if (!isShowing() || !AnimationUtils.isAnimationEnabled()
                || getOwnerWindow() == null || !getOwnerWindow().isShowing()) {
            animation.stop();
            super.hide();
            return;
        }
        if (hiding)
            return;
        hiding = true;
        animation.stop();
        animate(0, 0.8, Duration.millis(100), Motion.STANDARD_ACCELERATE);
        animation.setOnFinished(event -> {
            animation.setOnFinished(null);
            super.hide();
        });
    }

    private void animate(double opacity, double scale, Duration duration, Interpolator interpolator) {
        var root = getScene().getRoot();
        animation.setOnFinished(null);
        animation.getKeyFrames().setAll(new KeyFrame(duration,
                new KeyValue(root.opacityProperty(), opacity, interpolator),
                new KeyValue(root.scaleXProperty(), scale, interpolator),
                new KeyValue(root.scaleYProperty(), scale, interpolator)));
        animation.playFromStart();
    }
}
