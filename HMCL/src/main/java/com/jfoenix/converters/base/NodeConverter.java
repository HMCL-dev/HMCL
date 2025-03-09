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

package com.jfoenix.converters.base;

import javafx.scene.Node;

/**
 * Converter defines conversion behavior between Nodes and Objects.
 * The type of Objects are defined by the subclasses of Converter.
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2016-03-09
 */
public abstract class NodeConverter<T> {
    /**
     * Converts the object provided into its node form.
     * Styling of the returned node is defined by the specific converter.
     *
     * @return a node representation of the object passed in.
     */
    public abstract Node toNode(T object);

    /**
     * Converts the node provided into an object defined by the specific converter.
     * Format of the node and type of the resulting object is defined by the specific converter.
     *
     * @return an object representation of the node passed in.
     */
    public abstract T fromNode(Node node);

    /**
     * Converts the object provided into a String defined by the specific converter.
     * Format of the String is defined by the specific converter.
     *
     * @return a String representation of the node passed in.
     */
    public abstract String toString(T object);

}
