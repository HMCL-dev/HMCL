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

package com.jfoenix.controls.base;

import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableObjectProperty;
import javafx.scene.paint.Paint;

/**
 * this class is created for internal use only, to remove duplication between text input controls
 * skins
 *
 * Created by sshahine on 7/14/2017.
 */
public interface IFXLabelFloatControl extends IFXValidatableControl, IFXStaticControl {

    StyleableBooleanProperty labelFloatProperty();

    boolean isLabelFloat();

    void setLabelFloat(boolean labelFloat);

    Paint getUnFocusColor();

    StyleableObjectProperty<Paint> unFocusColorProperty();

    void setUnFocusColor(Paint color);

    Paint getFocusColor();

    StyleableObjectProperty<Paint> focusColorProperty();

    void setFocusColor(Paint color);
}
