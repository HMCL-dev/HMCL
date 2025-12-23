// Copy from https://github.com/sshahine/JFoenix/blob/d427fd801a338f934307ba41ce604eb5c79f0b20/jfoenix/src/main/java/com/jfoenix/skins/JFXListViewSkin.java

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
package com.jfoenix.skins;

import com.jfoenix.controls.JFXListView;
import com.jfoenix.effects.JFXDepthManager;
import javafx.scene.control.ListCell;
import javafx.scene.control.skin.ListViewSkin;
import javafx.scene.control.skin.VirtualFlow;
import org.jackhuang.hmcl.ui.FXUtils;

public class JFXListViewSkin<T> extends ListViewSkin<T> {

    public JFXListViewSkin(final JFXListView<T> listView) {
        super(listView);
        VirtualFlow<ListCell<T>> flow = getVirtualFlow();
        JFXDepthManager.setDepth(flow, listView.depthProperty().get());
        listView.depthProperty().addListener((o, oldVal, newVal) -> JFXDepthManager.setDepth(flow, newVal));

        if (!Boolean.TRUE.equals(listView.getProperties().get("no-smooth-scrolling"))) {
            FXUtils.smoothScrolling(flow);
        }
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return 200;
    }

}
