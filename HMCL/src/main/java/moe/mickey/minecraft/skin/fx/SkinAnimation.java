package moe.mickey.minecraft.skin.fx;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SkinAnimation {

    protected int weight, left;
    protected List<SkinTransition> transitions;

    public SkinAnimation() {
        this.transitions = new ArrayList<>();
    }

    public SkinAnimation(int weight, SkinTransition... transitions) {
        this.weight = weight;
        this.transitions = Arrays.asList(transitions);
        init();
    }

    protected void init() {
        transitions.forEach(t -> {
            EventHandler<ActionEvent> oldHandler = t.getOnFinished();
            EventHandler<ActionEvent> newHandler = e -> left--;
            newHandler = oldHandler == null ? newHandler : FunctionHelper.link(oldHandler, newHandler);
            t.setOnFinished(newHandler);
        });
    }

    public int getWeight() {
        return weight;
    }

    public boolean isPlaying() {
        return left > 0;
    }

    public void play() {
        transitions.forEach(SkinTransition::play);
        left = transitions.size();
    }

    public void playFromStart() {
        transitions.forEach(SkinTransition::playFromStart);
        left = transitions.size();
    }

    public void stop() {
        transitions.forEach(SkinTransition::stop);
        left = 0;
    }

}
