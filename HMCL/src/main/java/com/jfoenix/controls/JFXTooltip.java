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
import javafx.scene.input.InputEvent;
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
    private static final String TOOLTIP_KEY = "javafx.scene.control.Tooltip";
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
        timeout.setOnFinished(event -> hide());
        addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> {
            animation.stop();
            timeout.stop();
            hiding = false;
        });
    }

    public static void install(Node node, Tooltip tooltip) {
        Object previous = node.getProperties().get(TOOLTIP_KEY);
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
        node.addEventFilter(InputEvent.ANY, event -> {
            var installed = installedTooltip(node);
            if (installed == null)
                return;
            if (event.getEventType() == MouseEvent.MOUSE_ENTERED)
                installed.anchor = new WeakReference<>(node);
            else if (event.getEventType() == MouseEvent.MOUSE_PRESSED)
                installed.hide();
            else if (event.getEventType() == KeyEvent.KEY_PRESSED
                    && ((KeyEvent) event).getCode() == KeyCode.ESCAPE && installed.isShowing()) {
                installed.hide();
                event.consume();
            }
        });
    }

    private static JFXTooltip installedTooltip(Node node) {
        Object value = node.getProperties().get(TOOLTIP_KEY);
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
            timeout.playFromStart();
        }
        if (!visible && AnimationUtils.isAnimationEnabled()) {
            root.setOpacity(0);
            root.setScaleX(0.8);
            root.setScaleY(0.8);
        }
        animate(true);
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
        if (isShowing() && !hiding) {
            hiding = true;
            timeout.stop();
            animate(false);
        }
    }

    private void animate(boolean visible) {
        animation.stop();
        var root = getScene().getRoot();
        double opacity = visible ? 1 : 0;
        double scale = visible ? 1 : 0.8;
        if (!AnimationUtils.isAnimationEnabled() || getOwnerWindow() == null || !getOwnerWindow().isShowing()) {
            root.setOpacity(opacity);
            root.setScaleX(scale);
            root.setScaleY(scale);
            if (!visible)
                super.hide();
            return;
        }
        Interpolator interpolator = visible ? Motion.STANDARD_DECELERATE : Motion.STANDARD_ACCELERATE;
        animation.setOnFinished(visible ? null : event -> super.hide());
        animation.getKeyFrames().setAll(new KeyFrame(Duration.millis(visible ? 150 : 100),
                new KeyValue(root.opacityProperty(), opacity, interpolator),
                new KeyValue(root.scaleXProperty(), scale, interpolator),
                new KeyValue(root.scaleYProperty(), scale, interpolator)));
        animation.playFromStart();
    }
}
