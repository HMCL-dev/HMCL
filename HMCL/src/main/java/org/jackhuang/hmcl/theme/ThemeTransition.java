package org.jackhuang.hmcl.theme;

import javafx.animation.*;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.paint.Color;
import javafx.util.Duration;


public class ThemeTransition {
    private static final Duration DURATION = Duration.millis(300);
    public static void animate(Theme oldTheme, Theme newTheme) {
        Color oldColor = oldTheme.primaryColorSeed().color();
        Color newColor = newTheme.primaryColorSeed().color();
        DoubleProperty progress = new SimpleDoubleProperty(0);
        progress.addListener((obs, o, t) -> {
            Color animColor = interpolate(oldColor, newColor, t.doubleValue());
            Theme tempTheme = new Theme(
                    new ThemeColor("anim", animColor),
                    newTheme.brightness(),
                    newTheme.colorStyle(),
                    newTheme.contrast()
            );
            Themes.internalColorSchemeProperty().set(tempTheme.toColorScheme());
        });
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progress, 0)),
                new KeyFrame(DURATION, new KeyValue(progress, 1, Interpolator.EASE_BOTH))
                //new KeyFrame(Duration.millis(1000), new KeyValue(progress, 1, Interpolator.EASE_IN))
        );
        timeline.play();

    }

    private static Color interpolate(Color from, Color to, double delta) {
        double r = from.getRed() + (to.getRed() - from.getRed()) * delta;
        double g = from.getGreen() + (to.getGreen() - from.getGreen()) * delta;
        double b = from.getBlue() + (to.getBlue() - from.getBlue()) * delta;
        double a = from.getOpacity() + (to.getOpacity() - from.getOpacity()) * delta;
        return new Color(r, g, b, a);
    }

}
