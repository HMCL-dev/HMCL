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

/**
 * JFXDrawer events, used exclusively by the following methods:
 * <ul>
 * <li>JFXDrawer#open()
 * <li>JFXDrawer#close()
 * </ul>
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2016-03-09
 */
public class JFXDrawerEvent extends Event {

    private static final long serialVersionUID = 1L;

    /**
     * Construct a new JFXDrawer {@code Event} with the specified event type
     *
     * @param eventType the event type
     */
    public JFXDrawerEvent(EventType<? extends Event> eventType) {
        super(eventType);
    }

    /**
     * This event occurs when a JFXDrawer is closed, no longer visible to the user
     * ( after the exit animation ends )
     */
    public static final EventType<JFXDrawerEvent> CLOSED =
        new EventType<>(Event.ANY, "JFX_DRAWER_CLOSED");

    /**
     * This event occurs when a JFXDrawer is drawn, visible to the user
     * ( after the entrance animation ends )
     */
    public static final EventType<JFXDrawerEvent> OPENED =
        new EventType<>(Event.ANY, "JFX_DRAWER_OPENED");

    /**
     * This event occurs when a JFXDrawer is being drawn, visible to the user
     * ( after the entrance animation ends )
     */
    public static final EventType<JFXDrawerEvent> OPENING =
        new EventType<>(Event.ANY, "JFX_DRAWER_OPENING");


    /**
     * This event occurs when a JFXDrawer is being closed, will become invisible to the user
     * at the end of the animation
     * ( after the entrance animation ends )
     */
    public static final EventType<JFXDrawerEvent> CLOSING =
        new EventType<>(Event.ANY, "JFX_DRAWER_CLOSING");


}
