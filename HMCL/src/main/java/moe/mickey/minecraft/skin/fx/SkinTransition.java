package moe.mickey.minecraft.skin.fx;

import javafx.animation.Transition;
import javafx.beans.value.WritableValue;
import javafx.util.Duration;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class SkinTransition extends Transition {

    protected Function<Double, Double> expression;
    protected List<WritableValue<Number>> observables;
    protected boolean fix;
    protected int count;

    public int getCount() {
        return count;
    }

    public SkinTransition(Duration duration, Function<Double, Double> expression, WritableValue<Number>... observables) {
        setCycleDuration(duration);
        this.expression = expression;
        this.observables = Arrays.asList(observables);
    }

    @Override
    protected void interpolate(double frac) {
        if (frac == 0 || frac == 1)
            count++;
        double val = expression.apply(frac);
        observables.forEach(w -> w.setValue(val));
    }

    @Override
    public void play() {
        count = 0;
        super.play();
    }

}
