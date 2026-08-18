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

package com.jfoenix.transitions;

import javafx.animation.AnimationTimer;
import javafx.beans.value.WritableDoubleValue;
import javafx.beans.value.WritableValue;
import javafx.scene.Node;
import javafx.util.Duration;

import java.util.*;
import java.util.function.Supplier;

/**
 * Custom AnimationTimer that can be created the same way as a timeline,
 * however it doesn't behave the same yet. it only animates in one direction,
 * it doesn't support animation 0 -> 1 -> 0.5
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2017-09-21
 */

public class JFXAnimationTimer extends AnimationTimer {

    private Set<AnimationHandler> animationHandlers = new HashSet<>();
    private long startTime = -1;
    private boolean running = false;
    private List<CacheMemento> caches = new ArrayList<>();
    private double totalElapsedMilliseconds;

    public JFXAnimationTimer(JFXKeyFrame... keyFrames) {
        for (JFXKeyFrame keyFrame : keyFrames) {
            Duration duration = keyFrame.getDuration();
            final Set<JFXKeyValue<?>> keyValuesSet = keyFrame.getValues();
            if (!keyValuesSet.isEmpty()) {
                animationHandlers.add(new AnimationHandler(duration, keyFrame.getAnimateCondition(), keyFrame.getValues()));
            }
        }
    }

    private final HashMap<JFXKeyFrame, AnimationHandler> mutableFrames = new HashMap<>();

    public void addKeyFrame(JFXKeyFrame keyFrame) throws Exception {
        if (isRunning()) {
            throw new Exception("Can't update animation timer while running");
        }
        Duration duration = keyFrame.getDuration();
        final Set<JFXKeyValue<?>> keyValuesSet = keyFrame.getValues();
        if (!keyValuesSet.isEmpty()) {
            final AnimationHandler handler = new AnimationHandler(duration, keyFrame.getAnimateCondition(), keyFrame.getValues());
            animationHandlers.add(handler);
            mutableFrames.put(keyFrame, handler);
        }
    }

    public void removeKeyFrame(JFXKeyFrame keyFrame) throws Exception {
        if (isRunning()) {
            throw new Exception("Can't update animation timer while running");
        }
        AnimationHandler handler = mutableFrames.get(keyFrame);
        animationHandlers.remove(handler);
    }

    @Override
    public void start() {
        super.start();
        running = true;
        startTime = -1;
        for (AnimationHandler animationHandler : animationHandlers) {
            animationHandler.init();
        }
        for (CacheMemento cache : caches) {
            cache.cache();
        }
    }

    @Override
    public void handle(long now) {
        startTime = startTime == -1 ? now : startTime;
        totalElapsedMilliseconds = (now - startTime) / 1000000.0;
        boolean stop = true;
        for (AnimationHandler handler : animationHandlers) {
            handler.animate(totalElapsedMilliseconds);
            if (!handler.finished) {
                stop = false;
            }
        }
        if (stop) {
            this.stop();
        }
    }

    /**
     * this method will pause the timer and reverse the animation if the timer already
     * started otherwise it will start the animation.
     */
    public void reverseAndContinue() {
        if (isRunning()) {
            super.stop();
            for (AnimationHandler handler : animationHandlers) {
                handler.reverse(totalElapsedMilliseconds);
            }
            startTime = -1;
            super.start();
        } else {
            start();
        }
    }

    @Override
    public void stop() {
        super.stop();
        running = false;
        for (AnimationHandler handler : animationHandlers) {
            handler.clear();
        }
        for (CacheMemento cache : caches) {
            cache.restore();
        }
        if (onFinished != null) {
            onFinished.run();
        }
    }

    public void applyEndValues() {
        if (isRunning()) {
            super.stop();
        }
        for (AnimationHandler handler : animationHandlers) {
            handler.applyEndValues();
        }
        startTime = -1;
    }

    public boolean isRunning() {
        return running;
    }

    private Runnable onFinished = null;

    public void setOnFinished(Runnable onFinished) {
        this.onFinished = onFinished;
    }

    public void setCacheNodes(Node... nodesToCache) {
        caches.clear();
        if (nodesToCache != null) {
            for (Node node : nodesToCache) {
                caches.add(new CacheMemento(node));
            }
        }
    }

    public void dispose() {
        caches.clear();
        for (AnimationHandler handler : animationHandlers) {
            handler.dispose();
        }
        animationHandlers.clear();
    }

    static class AnimationHandler {
        private final double duration;
        private double currentDuration;
        private final JFXKeyValue<?>[] keyValues;
        private final WritableValue<?>[] targets;
        private final Object[] initialValues;
        private final Object[] endValues;
        private final boolean[] valid;
        private Supplier<Boolean> animationCondition = null;
        private boolean finished = false;

        AnimationHandler(Duration duration, Supplier<Boolean> animationCondition, Set<JFXKeyValue<?>> keyValues) {
            this.duration = duration.toMillis();
            currentDuration = this.duration;
            this.animationCondition = animationCondition;

            this.keyValues = keyValues.toArray(new JFXKeyValue<?>[0]);
            final int length = this.keyValues.length;
            this.targets = new WritableValue<?>[length];
            this.initialValues = new Object[length];
            this.endValues = new Object[length];
            this.valid = new boolean[length];
        }

        /// Captures the current value and the end value of every key value.
        ///
        /// Must be called after every state change that can affect the animated targets
        /// (that is, whenever the animation is started or reversed).
        private void collect() {
            for (int i = 0; i < keyValues.length; i++) {
                JFXKeyValue<?> keyValue = keyValues[i];
                WritableValue<?> target = keyValue.getTarget();
                targets[i] = target;
                if (target != null) {
                    valid[i] = true;
                    initialValues[i] = target.getValue();
                    endValues[i] = keyValue.getEndValue();
                } else {
                    valid[i] = false;
                }
            }
        }

        public void init() {
            finished = animationCondition != null && !animationCondition.get();
            collect();
        }

        void reverse(double now) {
            finished = animationCondition != null && !animationCondition.get();
            currentDuration = duration - (currentDuration - now);
            // update initial values
            for (int i = 0; i < keyValues.length; i++) {
                WritableValue<?> target = targets[i];
                if (target != null) {
                    initialValues[i] = target.getValue();
                    endValues[i] = keyValues[i].getEndValue();
                }
            }
        }

        // now in milliseconds
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void animate(double now) {
            // if animate condition for the key frame is not met then do nothing
            if (finished) {
                return;
            }
            if (now <= currentDuration) {
                final double frac = now / currentDuration;
                for (int i = 0; i < keyValues.length; i++) {
                    if (!valid[i] || !keyValues[i].isValid())
                        continue;

                    final Object endValue = endValues[i];
                    if (endValue == null)
                        continue;

                    final WritableValue target = targets[i];

                    // Primitive fast path for double properties: avoids boxing the
                    // interpolated value on every frame.
                    if (target instanceof WritableDoubleValue
                            && initialValues[i] instanceof Double initial
                            && endValue instanceof Double end) {
                        double value = keyValues[i].getInterpolator().interpolate(
                                initial.doubleValue(), end.doubleValue(), frac);
                        if (((WritableDoubleValue) target).get() != value) {
                            ((WritableDoubleValue) target).set(value);
                        }
                        continue;
                    }

                    if (!target.getValue().equals(endValue)) {
                        target.setValue(keyValues[i].getInterpolator().interpolate(initialValues[i], endValue, frac));
                    }
                }
            } else {
                if (!finished) {
                    finished = true;
                    for (int i = 0; i < keyValues.length; i++) {
                        if (!valid[i] || !keyValues[i].isValid())
                            continue;

                        final WritableValue target = targets[i];
                        // set updated end value instead of cached
                        final Object endValue = keyValues[i].getEndValue();
                        if (endValue != null) {
                            target.setValue(endValue);
                        }
                    }
                    currentDuration = duration;
                }
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public void applyEndValues() {
            for (int i = 0; i < keyValues.length; i++) {
                if (!valid[i] || !keyValues[i].isValid())
                    continue;

                final WritableValue target = targets[i];
                final Object endValue = keyValues[i].getEndValue();
                if (endValue != null && !target.getValue().equals(endValue)) {
                    target.setValue(endValue);
                }
            }
        }

        public void clear() {
            // Values are captured again by the next collect() call, so there is
            // nothing to reset here.
        }

        void dispose() {
            // The parallel arrays are released together with the handler itself;
            // they are re-captured by collect() if the timer is ever restarted.
            clear();
        }
    }
}
