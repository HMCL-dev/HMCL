/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionHandler;
import org.jackhuang.hmcl.util.Logging;

import java.util.Optional;
import java.util.Stack;
import java.util.logging.Level;

public class Navigator extends StackPane {
    private static final String PROPERTY_DIALOG_CLOSE_HANDLER = Navigator.class.getName() + ".closeListener";

    private final Stack<Node> stack = new Stack<>();
    private final TransitionHandler animationHandler = new TransitionHandler(this);
    private boolean initialized = false;

    public void init(Node init) {
        stack.push(init);
        getChildren().setAll(init);

        fireEvent(new NavigationEvent(this, init, NavigationEvent.NAVIGATED));

        initialized = true;
    }

    public void navigate(Node node) {
        FXUtils.checkFxUserThread();

        if (!initialized)
            throw new IllegalStateException("Navigator must have a root page");

        Node from = stack.peek();
        if (from == node)
            return;

        Logging.LOG.info("Navigate to " + node);

        stack.push(node);

        NavigationEvent navigating = new NavigationEvent(this, from, NavigationEvent.NAVIGATING);
        fireEvent(navigating);
        node.fireEvent(navigating);

        setContent(node);

        NavigationEvent navigated = new NavigationEvent(this, node, NavigationEvent.NAVIGATED);
        fireEvent(navigated);
        node.fireEvent(navigated);

        EventHandler<PageCloseEvent> handler = event -> close(node);
        node.getProperties().put(PROPERTY_DIALOG_CLOSE_HANDLER, handler);
        node.addEventHandler(PageCloseEvent.CLOSE, handler);
    }

    public void close() {
        close(stack.peek());
    }

    public void clear() {
        while (stack.size() > 1)
            close(stack.peek());
    }

    @SuppressWarnings("unchecked")
    public void close(Node from) {
        FXUtils.checkFxUserThread();

        if (!initialized)
            throw new IllegalStateException("Navigator must have a root page");

        if (stack.peek() != from) {
            // Allow page to be closed multiple times.
            Logging.LOG.log(Level.INFO, "Closing already closed page: " + from, new Throwable());
            return;
        }

        Logging.LOG.info("Closed page " + from);

        stack.pop();
        Node node = stack.peek();

        NavigationEvent navigating = new NavigationEvent(this, from, NavigationEvent.NAVIGATING);
        fireEvent(navigating);
        node.fireEvent(navigating);

        setContent(node);

        NavigationEvent navigated = new NavigationEvent(this, node, NavigationEvent.NAVIGATED);
        fireEvent(navigated);
        node.fireEvent(navigated);

        Optional.ofNullable(from.getProperties().get(PROPERTY_DIALOG_CLOSE_HANDLER))
                .ifPresent(handler -> from.removeEventHandler(PageCloseEvent.CLOSE, (EventHandler<PageCloseEvent>) handler));
    }

    public Node getCurrentPage() {
        return stack.peek();
    }

    public boolean canGoBack() {
        return stack.size() > 1;
    }

    private void setContent(Node content) {
        animationHandler.setContent(content, ContainerAnimations.FADE.getAnimationProducer());

        if (content instanceof Region) {
            ((Region) content).setMinSize(0, 0);
            FXUtils.setOverflowHidden((Region) content, true);
        }
    }

    public EventHandler<NavigationEvent> getOnNavigated() {
        return onNavigated.get();
    }

    public ObjectProperty<EventHandler<NavigationEvent>> onNavigatedProperty() {
        return onNavigated;
    }

    public void setOnNavigated(EventHandler<NavigationEvent> onNavigated) {
        this.onNavigated.set(onNavigated);
    }

    private ObjectProperty<EventHandler<NavigationEvent>> onNavigated = new SimpleObjectProperty<EventHandler<NavigationEvent>>(this, "onNavigated") {
        @Override
        protected void invalidated() {
            setEventHandler(NavigationEvent.NAVIGATED, get());
        }
    };

    public EventHandler<NavigationEvent> getOnNavigating() {
        return onNavigating.get();
    }

    public ObjectProperty<EventHandler<NavigationEvent>> onNavigatingProperty() {
        return onNavigating;
    }

    public void setOnNavigating(EventHandler<NavigationEvent> onNavigating) {
        this.onNavigating.set(onNavigating);
    }

    private ObjectProperty<EventHandler<NavigationEvent>> onNavigating = new SimpleObjectProperty<EventHandler<NavigationEvent>>(this, "onNavigating") {
        @Override
        protected void invalidated() {
            setEventHandler(NavigationEvent.NAVIGATING, get());
        }
    };

    public static class NavigationEvent extends Event {
        public static final EventType<NavigationEvent> NAVIGATED = new EventType<>("NAVIGATED");
        public static final EventType<NavigationEvent> NAVIGATING = new EventType<>("NAVIGATING");

        private final Node node;

        public NavigationEvent(Object source, Node target, EventType<? extends Event> eventType) {
            super(source, target, eventType);

            this.node = target;
        }

        public Node getNode() {
            return node;
        }
    }
}
