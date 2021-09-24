package moe.mickey.minecraft.skin.fx;

import javafx.animation.AnimationTimer;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Random;

public class SkinAnimationPlayer {

    protected final Random random = new Random();
    protected LinkedList<SkinAnimation> animations = new LinkedList<>();
    protected SkinAnimation playing;
    protected boolean running;
    protected int weightedSum = 0;
    protected long lastPlayTime = -1L, interval = 10_000_000_000L;
    protected AnimationTimer animationTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            if (playing == null || !playing.isPlaying() && now - lastPlayTime > interval) {
                int nextAni = random.nextInt(weightedSum);
                SkinAnimation tmp = null;
                for (SkinAnimation animation : animations) {
                    nextAni -= animation.getWeight();
                    tmp = animation;
                    if (nextAni <= 0)
                        break;
                }
                playing = tmp;
                if (playing == null && animations.size() > 0)
                    playing = animations.getLast();
                if (playing != null) {
                    playing.playFromStart();
                    lastPlayTime = now;
                }
            }
        }
    };

    public int getWeightedSum() {
        return weightedSum;
    }

    public void setInterval(long interval) {
        this.interval = interval;
        if (interval < 1)
            animationTimer.stop();
        else
            start();
    }

    public long getInterval() {
        return interval;
    }

    public long getLastPlayTime() {
        return lastPlayTime;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isPlaying() {
        return playing != null;
    }

    public SkinAnimation getPlaying() {
        return playing;
    }

    public void addSkinAnimation(SkinAnimation... animations) {
        this.animations.addAll(Arrays.asList(animations));
        this.weightedSum = this.animations.stream().mapToInt(SkinAnimation::getWeight).sum();
        start();
    }

    public void start() {
        if (!running && weightedSum > 0 && interval > 0) {
            animationTimer.start();
            running = true;
        }
    }

    public void stop() {
        if (running)
            animationTimer.stop();
        if (playing != null)
            playing.stop();
        running = false;
    }

}
