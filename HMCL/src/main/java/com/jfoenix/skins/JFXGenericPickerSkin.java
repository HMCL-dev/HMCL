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

import com.jfoenix.adapters.ReflectionHelper;
import com.jfoenix.controls.behavior.JFXGenericPickerBehavior;
import com.sun.javafx.binding.ExpressionHelper;
import com.sun.javafx.event.EventHandlerManager;
import com.sun.javafx.stage.WindowEventDispatcher;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanPropertyBase;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.PopupControl;
import javafx.scene.control.TextField;
import javafx.scene.control.skin.ComboBoxBaseSkin;
import javafx.scene.control.skin.ComboBoxPopupControl;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Window;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public abstract class JFXGenericPickerSkin<T> extends ComboBoxPopupControl<T> {

    private final EventHandler<MouseEvent> mouseEnteredEventHandler;
    private final EventHandler<MouseEvent> mousePressedEventHandler;
    private final EventHandler<MouseEvent> mouseReleasedEventHandler;
    private final EventHandler<MouseEvent> mouseExitedEventHandler;

    protected JFXGenericPickerBehavior<T> behavior;

    // reference of the arrow button node in getChildren (not the actual field)
    protected Pane arrowButton;
    protected PopupControl popup;

    public JFXGenericPickerSkin(ComboBoxBase<T> comboBoxBase) {
        super(comboBoxBase);
        behavior = new JFXGenericPickerBehavior<T>(comboBoxBase);

        removeParentFakeFocusListener(comboBoxBase);

        this.mouseEnteredEventHandler = event -> behavior.mouseEntered(event);
        this.mousePressedEventHandler = event -> {
            behavior.mousePressed(event);
            event.consume();
        };
        this.mouseReleasedEventHandler = event -> {
            behavior.mouseReleased(event);
            event.consume();
        };
        this.mouseExitedEventHandler = event -> behavior.mouseExited(event);

        arrowButton = (Pane) getChildren().get(0);

        parentArrowEventHandlerTerminator.accept("mouseEnteredEventHandler", MouseEvent.MOUSE_ENTERED);
        parentArrowEventHandlerTerminator.accept("mousePressedEventHandler", MouseEvent.MOUSE_PRESSED);
        parentArrowEventHandlerTerminator.accept("mouseReleasedEventHandler", MouseEvent.MOUSE_RELEASED);
        parentArrowEventHandlerTerminator.accept("mouseExitedEventHandler", MouseEvent.MOUSE_EXITED);
        this.unregisterChangeListeners(comboBoxBase.editableProperty());

        updateArrowButtonListeners();
        registerChangeListener(comboBoxBase.editableProperty(), obs -> {
            updateArrowButtonListeners();
            reflectUpdateDisplayArea();
        });

        removeParentPopupHandlers();

        popup = ReflectionHelper.getFieldContent(ComboBoxPopupControl.class, this, "popup");
    }

    @Override
    public void dispose() {
        super.dispose();
        if (this.behavior != null) {
            this.behavior.dispose();
        }
    }


    /***************************************************************************
     *                                                                         *
     * Reflections internal API                                                *
     *                                                                         *
     **************************************************************************/

    private final BiConsumer<String, EventType<?>> parentArrowEventHandlerTerminator = (handlerName, eventType) -> {
        try {
            EventHandler handler = ReflectionHelper.getFieldContent(ComboBoxBaseSkin.class, this, handlerName);
            arrowButton.removeEventHandler(eventType, handler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    };

    private static final VarHandle READ_ONLY_BOOLEAN_PROPERTY_BASE_HELPER =
            findVarHandle(ReadOnlyBooleanPropertyBase.class, "helper", ExpressionHelper.class);

    /// @author Glavo
    private static VarHandle findVarHandle(Class<?> targetClass, String fieldName, Class<?> type) {
        try {
            return MethodHandles.privateLookupIn(targetClass, MethodHandles.lookup()).findVarHandle(targetClass, fieldName, type);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            LOG.warning("Failed to get var handle", e);
            return null;
        }
    }

    private void removeParentFakeFocusListener(ComboBoxBase<T> comboBoxBase) {
        // handle FakeFocusField cast exception
        try {
            final ReadOnlyBooleanProperty focusedProperty = comboBoxBase.focusedProperty();
            //noinspection unchecked
            ExpressionHelper<Boolean> value = (ExpressionHelper<Boolean>) READ_ONLY_BOOLEAN_PROPERTY_BASE_HELPER.get(focusedProperty);
            ChangeListener<? super Boolean>[] changeListeners = ReflectionHelper.getFieldContent(value.getClass(), value, "changeListeners");
            // remove parent focus listener to prevent editor class cast exception
            for (int i = changeListeners.length - 1; i > 0; i--) {
                if (changeListeners[i] != null && changeListeners[i].getClass().getName().contains("ComboBoxPopupControl")) {
                    focusedProperty.removeListener(changeListeners[i]);
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removeParentPopupHandlers() {
        try {
            PopupControl popup = ReflectionHelper.invoke(ComboBoxPopupControl.class, this, "getPopup");
            popup.setOnAutoHide(event -> behavior.onAutoHide(popup));
            WindowEventDispatcher dispatcher = ReflectionHelper.invoke(Window.class, popup, "getInternalEventDispatcher");
            Map compositeEventHandlersMap = ReflectionHelper.getFieldContent(EventHandlerManager.class, dispatcher.getEventHandlerManager(), "eventHandlerMap");
            compositeEventHandlersMap.remove(MouseEvent.MOUSE_CLICKED);
//            CompositeEventHandler compositeEventHandler = (CompositeEventHandler) compositeEventHandlersMap.get(MouseEvent.MOUSE_CLICKED);
//            Object obj = fieldConsumer.apply(()->CompositeEventHandler.class.getDeclaredField("firstRecord"),compositeEventHandler);
//            EventHandler handler = (EventHandler) fieldConsumer.apply(() -> obj.getClass().getDeclaredField("eventHandler"), obj);
//            popup.removeEventHandler(MouseEvent.MOUSE_CLICKED, handler);
            popup.addEventHandler(MouseEvent.MOUSE_CLICKED, click -> behavior.onAutoHide(popup));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateArrowButtonListeners() {
        if (getSkinnable().isEditable()) {
            arrowButton.addEventHandler(MouseEvent.MOUSE_ENTERED, mouseEnteredEventHandler);
            arrowButton.addEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedEventHandler);
            arrowButton.addEventHandler(MouseEvent.MOUSE_RELEASED, mouseReleasedEventHandler);
            arrowButton.addEventHandler(MouseEvent.MOUSE_EXITED, mouseExitedEventHandler);
        } else {
            arrowButton.removeEventHandler(MouseEvent.MOUSE_ENTERED, mouseEnteredEventHandler);
            arrowButton.removeEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedEventHandler);
            arrowButton.removeEventHandler(MouseEvent.MOUSE_RELEASED, mouseReleasedEventHandler);
            arrowButton.removeEventHandler(MouseEvent.MOUSE_EXITED, mouseExitedEventHandler);
        }
    }


    /***************************************************************************
     *                                                                         *
     * Reflections internal API for ComboBoxPopupControl                       *
     *                                                                         *
     **************************************************************************/

    private final HashMap<String, Method> parentCachedMethods = new HashMap<>();

    Function<String, Method> methodSupplier = name -> {
        if (!parentCachedMethods.containsKey(name)) {
            try {
                Method method = ComboBoxPopupControl.class.getDeclaredMethod(name);
                method.setAccessible(true);
                parentCachedMethods.put(name, method);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return parentCachedMethods.get(name);
    };

    final Consumer<Method> methodInvoker = method -> {
        try {
            method.invoke(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    };

    final Function<Method, Object> methodReturnInvoker = method -> {
        try {
            return method.invoke(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    };

    protected void reflectUpdateDisplayArea() {
        methodInvoker.accept(methodSupplier.apply("updateDisplayArea"));
    }

    protected void reflectSetTextFromTextFieldIntoComboBoxValue() {
        methodInvoker.accept(methodSupplier.apply("setTextFromTextFieldIntoComboBoxValue"));
    }

    protected TextField reflectGetEditableInputNode() {
        return (TextField) methodReturnInvoker.apply(methodSupplier.apply("getEditableInputNode"));
    }

    protected void reflectUpdateDisplayNode() {
        methodInvoker.accept(methodSupplier.apply("updateDisplayNode"));
    }
}
