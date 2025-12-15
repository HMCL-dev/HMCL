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

import javafx.animation.Interpolator;
import javafx.beans.value.WritableValue;

import java.util.function.Supplier;

/**
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2017-09-21
 */

public final class JFXKeyValue<T> {

    private WritableValue<T> target;
    private Supplier<WritableValue<T>> targetSupplier;
    private Supplier<T> endValueSupplier;
    private T endValue;
    private Supplier<Boolean> animateCondition = () -> true;
    private Interpolator interpolator;

    private JFXKeyValue() {
    }

    // this builder is created to ensure type inference from method arguments
    public static Builder builder() {
        return new Builder();
    }

    public T getEndValue() {
        return endValue == null ? endValueSupplier.get() : endValue;
    }

    public WritableValue<T> getTarget() {
        return target == null ? targetSupplier.get() : target;
    }

    public Interpolator getInterpolator() {
        return interpolator;
    }

    public boolean isValid() {
        return animateCondition == null || animateCondition.get();
    }

    public static final class Builder {
        public <T> JFXKeyValueBuilder<T> setTarget(WritableValue<T> target) {
            JFXKeyValueBuilder<T> builder = new JFXKeyValueBuilder<>();
            builder.setTarget(target);
            return builder;
        }

        public <T> JFXKeyValueBuilder<T> setTargetSupplier(Supplier<WritableValue<T>> targetSupplier) {
            JFXKeyValueBuilder<T> builder = new JFXKeyValueBuilder<>();
            builder.setTargetSupplier(targetSupplier);
            return builder;
        }

        public <T> JFXKeyValueBuilder<T> setEndValueSupplier(Supplier<T> endValueSupplier) {
            JFXKeyValueBuilder<T> builder = new JFXKeyValueBuilder<>();
            builder.setEndValueSupplier(endValueSupplier);
            return builder;
        }

        public <T> JFXKeyValueBuilder<T> setEndValue(T endValue) {
            JFXKeyValueBuilder<T> builder = new JFXKeyValueBuilder<>();
            builder.setEndValue(endValue);
            return builder;
        }

        public <T> JFXKeyValueBuilder<T> setAnimateCondition(Supplier<Boolean> animateCondition) {
            JFXKeyValueBuilder<T> builder = new JFXKeyValueBuilder<>();
            builder.setAnimateCondition(animateCondition);
            return builder;
        }

        public <T> JFXKeyValueBuilder<T> setInterpolator(Interpolator interpolator) {
            JFXKeyValueBuilder<T> builder = new JFXKeyValueBuilder<>();
            builder.setInterpolator(interpolator);
            return builder;
        }
    }

    public static final class JFXKeyValueBuilder<T> {

        private WritableValue<T> target;
        private Supplier<WritableValue<T>> targetSupplier;
        private Supplier<T> endValueSupplier;
        private T endValue;
        private Supplier<Boolean> animateCondition = () -> true;
        private Interpolator interpolator = Interpolator.EASE_BOTH;

        private JFXKeyValueBuilder() {
        }

        public JFXKeyValueBuilder<T> setTarget(WritableValue<T> target) {
            this.target = target;
            return this;
        }

        public JFXKeyValueBuilder<T> setTargetSupplier(Supplier<WritableValue<T>> targetSupplier) {
            this.targetSupplier = targetSupplier;
            return this;
        }

        public JFXKeyValueBuilder<T> setEndValueSupplier(Supplier<T> endValueSupplier) {
            this.endValueSupplier = endValueSupplier;
            return this;
        }

        public JFXKeyValueBuilder<T> setEndValue(T endValue) {
            this.endValue = endValue;
            return this;
        }

        public JFXKeyValueBuilder<T> setAnimateCondition(Supplier<Boolean> animateCondition) {
            this.animateCondition = animateCondition;
            return this;
        }

        public JFXKeyValueBuilder<T> setInterpolator(Interpolator interpolator) {
            this.interpolator = interpolator;
            return this;
        }

        public JFXKeyValue<T> build() {
            JFXKeyValue<T> jFXKeyValue = new JFXKeyValue<>();
            jFXKeyValue.target = this.target;
            jFXKeyValue.interpolator = this.interpolator;
            jFXKeyValue.targetSupplier = this.targetSupplier;
            jFXKeyValue.endValue = this.endValue;
            jFXKeyValue.endValueSupplier = this.endValueSupplier;
            jFXKeyValue.animateCondition = this.animateCondition;
            return jFXKeyValue;
        }
    }
}
