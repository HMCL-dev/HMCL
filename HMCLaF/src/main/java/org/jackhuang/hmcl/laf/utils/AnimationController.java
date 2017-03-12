/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.laf.utils;

import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import org.jackhuang.hmcl.laf.BeautyEyeLookAndFeel;

import org.jackhuang.hmcl.laf.utils.TMSchema.State;
import static org.jackhuang.hmcl.laf.utils.TMSchema.State.*;
import org.jackhuang.hmcl.laf.utils.TMSchema.Part;

/**
 * A class to help mimic Vista theme animations. The only kind of
 * animation it handles for now is 'transition' animation (this seems
 * to be the only animation which Vista theme can do). This is when
 * one picture fadein over another one in some period of time.
 * According to
 * https://connect.microsoft.com/feedback/ViewFeedback.aspx?FeedbackID=86852&SiteID=4
 * The animations are all linear.
 *
 * This class has a number of responsibilities.
 * <ul>
 * <li> It trigger rapaint for the UI components involved in the animation
 * <li> It tracks the animation state for every UI component involved in the
 * animation and paints {@code Skin} in new {@code State} over the
 * {@code Skin} in last {@code State} using
 * {@code AlphaComposite.SrcOver.derive(alpha)} where {code alpha}
 * depends on the state of animation
 * </ul>
 *
 * @author Igor Kushnirskiy
 */
public class AnimationController implements ActionListener, PropertyChangeListener {

    private static AnimationController INSTANCE = new AnimationController();
    private static final String ANIMATION_CONTROLLER_KEY = "BeautyEye.AnimationController";
    private final Map<JComponent, Map<Part, AnimationState>> animationStateMap
            = new WeakHashMap<>();

    //this timer is used to cause repaint on animated components
    //30 repaints per second should give smooth animation affect
    private final Timer timer = new Timer(1000 / 30, this);

    private static synchronized AnimationController getAnimationController(JComponent c) {
        if (c.getClientProperty(ANIMATION_CONTROLLER_KEY) == null)
            c.putClientProperty(ANIMATION_CONTROLLER_KEY, new AnimationController());
        return (AnimationController) c.getClientProperty(ANIMATION_CONTROLLER_KEY);
    }

    private AnimationController() {
        timer.setRepeats(true);
        timer.setCoalesce(true);
        //we need to dispose the controller on l&f change
        UIManager.addPropertyChangeListener(this);
    }

    private static void triggerAnimation(JComponent c,
            Part part, State newState) {
        if (c instanceof javax.swing.JTabbedPane
                || part == Part.TP_BUTTON)
            //idk: we can not handle tabs animation because
            //the same (component,part) is used to handle all the tabs
            //and we can not track the states
            //Vista theme might have transition duration for toolbar buttons
            //but native application does not seem to animate them
            return;
        AnimationController controller
                = AnimationController.getAnimationController(c);
        State oldState = controller.getState(c, part);
        if (oldState != newState) {
            controller.putState(c, part, newState);
            if (newState == State.DEFAULT)
                // it seems for DEFAULTED button state Vista does animation from
                // HOT
                oldState = State.ROLLOVER;
            if (oldState != null) {
                long duration;
                //if (newState == State.DEFAULTED) {
                //Only button might have DEFAULTED state
                //idk: do not know how to get the value from Vista
                //one second seems plausible value
                duration = 500;
                /*} else {
                    XPStyle xp = XPStyle.getXP();
                    duration = (xp != null)
                               ? xp.getThemeTransitionDuration(
                                       c, part,
                                       normalizeState(oldState),
                                       normalizeState(newState),
                                       Prop.TRANSITIONDURATIONS)
                               : 1000;
                }*/
                controller.startAnimation(c, part, oldState, newState, duration);
            }
        }
    }

    // for scrollbar up, down, left and right button pictures are
    // defined by states.  It seems that theme has duration defined
    // only for up button states thus we doing this translation here.
    private static State normalizeState(State state) {
        State rv;
        switch (state) {
            case DOWNPRESSED:
            case LEFTPRESSED:
            case RIGHTPRESSED:
                rv = UPPRESSED;
                break;

            case DOWNDISABLED:
            case LEFTDISABLED:
            case RIGHTDISABLED:
                rv = UPDISABLED;
                break;

            case DOWNHOT:
            case LEFTHOT:
            case RIGHTHOT:
                rv = UPHOT;
                break;

            case DOWNNORMAL:
            case LEFTNORMAL:
            case RIGHTNORMAL:
                rv = UPNORMAL;
                break;

            default:
                rv = state;
                break;
        }
        return rv;
    }

    private synchronized State getState(JComponent component, Part part) {
        State rv = null;
        Object tmpObject
                = component.getClientProperty(PartUIClientPropertyKey.getKey(part));
        if (tmpObject instanceof State)
            rv = (State) tmpObject;
        return rv;
    }

    private synchronized void putState(JComponent component, Part part,
            State state) {
        component.putClientProperty(PartUIClientPropertyKey.getKey(part),
                state);
    }

    private synchronized void startAnimation(JComponent component,
            Part part,
            State startState,
            State endState,
            long millis) {
        boolean isForwardAndReverse = false;
        if (endState == State.DEFAULT)
            isForwardAndReverse = true;
        Map<Part, AnimationState> map = animationStateMap.get(component);
        if (millis <= 0) {
            if (map != null) {
                map.remove(part);
                if (map.isEmpty())
                    animationStateMap.remove(component);
            }
            return;
        }
        if (map == null) {
            map = new EnumMap<>(Part.class);
            animationStateMap.put(component, map);
        }
        map.put(part,
                new AnimationState(startState, millis, isForwardAndReverse));
        if (!timer.isRunning())
            timer.start();
    }

    public static void paintSkin(JComponent component, Skin skin,
            Graphics g, int dx, int dy, int dw, int dh, State state) {
        triggerAnimation(component, skin.getPart(component), state);
        AnimationController controller = getAnimationController(component);
        synchronized (controller) {
            AnimationState animationState = null;
            Map<Part, AnimationState> map
                    = controller.animationStateMap.get(component);
            if (map != null)
                animationState = map.get(skin.getPart(component));
            if (animationState != null)
                animationState.paintSkin(skin, g, dx, dy, dw, dh, state);
            else
                skin.paintSkinRaw(g, dx, dy, dw, dh, state);
        }
    }

    @Override
    public synchronized void propertyChange(PropertyChangeEvent e) {
        if ("lookAndFeel".equals(e.getPropertyName())
                && !(e.getNewValue() instanceof BeautyEyeLookAndFeel))
            dispose();
    }

    @Override
    public synchronized void actionPerformed(ActionEvent e) {
        java.util.List<JComponent> componentsToRemove = null;
        java.util.List<Part> partsToRemove = null;
        for (JComponent component : animationStateMap.keySet()) {
            component.repaint();
            if (partsToRemove != null)
                partsToRemove.clear();
            Map<Part, AnimationState> map = animationStateMap.get(component);
            if (!component.isShowing()
                    || map == null
                    || map.isEmpty()) {
                if (componentsToRemove == null)
                    componentsToRemove = new ArrayList<>();
                componentsToRemove.add(component);
                continue;
            }
            for (Part part : map.keySet())
                if (map.get(part).isDone()) {
                    if (partsToRemove == null)
                        partsToRemove = new ArrayList<>();
                    partsToRemove.add(part);
                }
            if (partsToRemove != null)
                if (partsToRemove.size() == map.size()) {
                    //animation is done for the component
                    if (componentsToRemove == null)
                        componentsToRemove = new ArrayList<>();
                    componentsToRemove.add(component);
                } else
                    for (Part part : partsToRemove)
                        map.remove(part);
        }
        if (componentsToRemove != null)
            for (JComponent component : componentsToRemove)
                animationStateMap.remove(component);
        if (animationStateMap.isEmpty())
            timer.stop();
    }

    private synchronized void dispose() {
        timer.stop();
        UIManager.removePropertyChangeListener(this);
        INSTANCE = null;
    }

    private static class AnimationState {

        private final State startState;

        //animation duration in nanoseconds
        private final long duration;

        //animatin start time in nanoseconds
        private long startTime;

        //direction the alpha value is changing
        //forward  - from 0 to 1
        //!forward - from 1 to 0
        private boolean isForward = true;

        //if isForwardAndReverse the animation continually goes
        //forward and reverse. alpha value is changing from 0 to 1 then
        //from 1 to 0 and so forth
        private final boolean isForwardAndReverse;

        private float progress;

        AnimationState(final State startState,
                final long milliseconds,
                boolean isForwardAndReverse) {
            assert startState != null && milliseconds > 0;
            assert SwingUtilities.isEventDispatchThread();

            this.startState = startState;
            this.duration = milliseconds * 1000000;
            this.startTime = System.nanoTime();
            this.isForwardAndReverse = isForwardAndReverse;
            progress = 0f;
        }

        private void updateProgress() {
            assert SwingUtilities.isEventDispatchThread();

            if (isDone())
                return;
            long currentTime = System.nanoTime();

            progress = ((float) (currentTime - startTime))
                    / duration;
            progress = Math.max(progress, 0); //in case time was reset
            if (progress >= 1) {
                progress = 1;
                if (isForwardAndReverse) {
                    startTime = currentTime;
                    progress = 0;
                    isForward = !isForward;
                }
            }
        }

        void paintSkin(Skin skin, Graphics _g,
                int dx, int dy, int dw, int dh, State state) {
            assert SwingUtilities.isEventDispatchThread();

            updateProgress();
            if (!isDone()) {
                Graphics2D g = (Graphics2D) _g.create();
                skin.paintSkinRaw(g, dx, dy, dw, dh, startState);
                float alpha;
                if (isForward)
                    alpha = progress;
                else
                    alpha = 1 - progress;
                g.setComposite(AlphaComposite.SrcOver.derive(alpha));
                skin.paintSkinRaw(g, dx, dy, dw, dh, state);
                g.dispose();
            } else
                skin.paintSkinRaw(_g, dx, dy, dw, dh, state);
        }

        boolean isDone() {
            assert SwingUtilities.isEventDispatchThread();

            return progress >= 1;
        }
    }

    private static class PartUIClientPropertyKey {

        private static final Map<Part, PartUIClientPropertyKey> MAP
                = new EnumMap<>(Part.class);

        static synchronized PartUIClientPropertyKey getKey(Part part) {
            PartUIClientPropertyKey rv = MAP.get(part);
            if (rv == null) {
                rv = new PartUIClientPropertyKey(part);
                MAP.put(part, rv);
            }
            return rv;
        }

        private final Part part;

        private PartUIClientPropertyKey(Part part) {
            this.part = part;
        }

        @Override
        public String toString() {
            return part.toString();
        }
    }
}
