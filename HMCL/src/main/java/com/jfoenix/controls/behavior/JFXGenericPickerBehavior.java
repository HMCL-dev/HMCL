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

package com.jfoenix.controls.behavior;

import com.sun.javafx.scene.control.behavior.ComboBoxBaseBehavior;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.PopupControl;

/**
 * @author Shadi Shaheen
 * @version 2.0
 * @since 2017-10-05
 */
public class JFXGenericPickerBehavior<T> extends ComboBoxBaseBehavior<T> {

    public JFXGenericPickerBehavior(ComboBoxBase<T> var1) {
        super(var1);
    }

    public void onAutoHide(PopupControl var1) {
        if (!var1.isShowing() && this.getNode().isShowing()) {
            this.getNode().hide();
        }
        if (!this.getNode().isShowing()) {
            super.onAutoHide(var1);
        }
    }

}
