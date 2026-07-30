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

package com.jfoenix.controls.events;

import javafx.event.Event;
import javafx.event.EventType;

import java.io.Serial;

/// JFXDialog events, used exclusively by the following methods:
///
///   - [com.jfoenix.controls.JFXDialog#show()]
///   - [com.jfoenix.controls.JFXDialog#close()]
///
/// @author Shadi Shaheen
/// @version 1.0
/// @since 2016-03-09
public class JFXDialogEvent extends Event {

    @Serial
    private static final long serialVersionUID = 1L;

    /// This event occurs when a JFXDialog is closed, no longer visible to the user
    /// ( after the exit animation ends )
    public static final EventType<JFXDialogEvent> CLOSED = new EventType<>(Event.ANY, "JFX_DIALOG_CLOSED");

    /// This event occurs when a JFXDialog is opened, visible to the user
    /// ( after the entrance animation ends )
    public static final EventType<JFXDialogEvent> OPENED = new EventType<>(Event.ANY, "JFX_DIALOG_OPENED");

    /// Construct a new JFXDialog `Event` with the specified event type
    ///
    /// @param eventType the event type
    public JFXDialogEvent(EventType<? extends Event> eventType) {
        super(eventType);
    }

}
