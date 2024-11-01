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
 * Wrapper for JFXDrawer animation key value
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2018-05-03
 */
public class JFXDrawerKeyValue<T> {

    private WritableValue<T> target;
    private Supplier<T> closeValueSupplier;
    private Supplier<T> openValueSupplier;
    private Interpolator interpolator;
    private Supplier<Boolean> animateCondition = () -> true;

    public WritableValue<T> getTarget() {
        return target;
    }

    public Supplier<T> getCloseValueSupplier() {
        return closeValueSupplier;
    }

    public Supplier<T> getOpenValueSupplier() {
        return openValueSupplier;
    }

    public Interpolator getInterpolator() {
        return interpolator;
    }

    public boolean isValid() {
        return animateCondition == null || animateCondition.get();
    }

    public static <T> JFXDrawerKeyValueBuilder<T> builder() {
        return new JFXDrawerKeyValueBuilder<>();
    }

    public void applyOpenValues() {
        target.setValue(getOpenValueSupplier().get());
    }

    public void applyCloseValues(){
        target.setValue(getCloseValueSupplier().get());
    }

    public static final class JFXDrawerKeyValueBuilder<T> {
        private WritableValue<T> target;
        private Interpolator interpolator = Interpolator.EASE_BOTH;
        private Supplier<Boolean> animateCondition = () -> true;
        private Supplier<T> closeValueSupplier;
        private Supplier<T> openValueSupplier;

        private JFXDrawerKeyValueBuilder() {
        }

        public JFXDrawerKeyValueBuilder<T> setTarget(WritableValue<T> target) {
            this.target = target;
            return this;
        }

        public JFXDrawerKeyValueBuilder<T> setInterpolator(Interpolator interpolator) {
            this.interpolator = interpolator;
            return this;
        }

        public JFXDrawerKeyValueBuilder<T> setAnimateCondition(Supplier<Boolean> animateCondition) {
            this.animateCondition = animateCondition;
            return this;
        }

        public JFXDrawerKeyValueBuilder<T> setCloseValue(T closeValue) {
            this.closeValueSupplier = () -> closeValue;
            return this;
        }

        public JFXDrawerKeyValueBuilder<T> setCloseValueSupplier(Supplier<T> closeValueSupplier) {
            this.closeValueSupplier = closeValueSupplier;
            return this;
        }

        public JFXDrawerKeyValueBuilder<T> setOpenValueSupplier(Supplier<T> openValueSupplier) {
            this.openValueSupplier = openValueSupplier;
            return this;
        }

        public JFXDrawerKeyValueBuilder<T> setOpenValue(T openValue) {
            this.openValueSupplier = () -> openValue;
            return this;
        }

        public JFXDrawerKeyValue<T> build() {
            JFXDrawerKeyValue<T> jFXDrawerKeyValue = new JFXDrawerKeyValue<>();
            jFXDrawerKeyValue.openValueSupplier = this.openValueSupplier;
            jFXDrawerKeyValue.closeValueSupplier = this.closeValueSupplier;
            jFXDrawerKeyValue.target = this.target;
            jFXDrawerKeyValue.interpolator = this.interpolator;
            jFXDrawerKeyValue.animateCondition = this.animateCondition;
            return jFXDrawerKeyValue;
        }
    }
}
