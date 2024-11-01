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

import javafx.util.Duration;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Supplier;

/**
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2017-09-21
 */

public class JFXKeyFrame {

    private Duration duration;
    private Set<JFXKeyValue<?>> keyValues = new CopyOnWriteArraySet<>();
    private Supplier<Boolean> animateCondition = null;

    public JFXKeyFrame(Duration duration, JFXKeyValue<?>... keyValues) {
        this.duration = duration;
        for (final JFXKeyValue<?> keyValue : keyValues) {
            if (keyValue != null) {
                this.keyValues.add(keyValue);
            }
        }
    }

    private JFXKeyFrame() {

    }

    public final Duration getDuration() {
        return duration;
    }

    public final Set<JFXKeyValue<?>> getValues() {
        return keyValues;
    }

    public Supplier<Boolean> getAnimateCondition() {
        return animateCondition;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Duration duration;
        private final Set<JFXKeyValue<?>> keyValues = new CopyOnWriteArraySet<>();
        private Supplier<Boolean> animateCondition = null;

        private Builder() {
        }

        public Builder setDuration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public Builder setKeyValues(JFXKeyValue<?>... keyValues) {
            for (final JFXKeyValue<?> keyValue : keyValues) {
                if (keyValue != null) {
                    this.keyValues.add(keyValue);
                }
            }
            return this;
        }

        public Builder setAnimateCondition(Supplier<Boolean> animateCondition) {
            this.animateCondition = animateCondition;
            return this;
        }

        public JFXKeyFrame build() {
            JFXKeyFrame jFXKeyFrame = new JFXKeyFrame();
            jFXKeyFrame.duration = this.duration;
            jFXKeyFrame.keyValues = this.keyValues;
            jFXKeyFrame.animateCondition = this.animateCondition;
            return jFXKeyFrame;
        }
    }
}
