/*  The contents of this file are subject to the terms of the Common Development
and Distribution License (the License). You may not use this file except in
compliance with the License.
    You can obtain a copy of the License at http://www.netbeans.org/cddl.html
or http://www.netbeans.org/cddl.txt.
    When distributing Covered Code, include this CDDL Header Notice in each file
and include the License file at http://www.netbeans.org/cddl.txt.
If applicable, add the following below the CDDL Header, with the fields
enclosed by brackets [] replaced by your own identifying information:
"Portions Copyrighted [year] [name of copyright owner]" */
package org.jackhuang.hellominecraft.util.ui.wizard.spi;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.JComponent;

/**
 * Encapsulates the logic and state of a Wizard. A Wizard gathers information
 * into a Map, and then performs some action with that information when the
 * user clicks Finish. To display a wizard, pass it to one of the methods
 * on <code>WizardDisplayer.getDefault()</code>.
 * <p>
 * A Wizard is a series of one or more steps represented
 * by panels in the user interface. Each step is identified by a unique String
 * ID.
 * Panels are created, usually on-demand, as the user navigates through
 * the UI of the wizard. Panels typically listen on components they contain
 * and put values into the Map where the wizard gathers data. Note that if the
 * user navigates <i>backward</i>, data entered on pages after the current one
 * disappears from the Map.
 * <p>
 * To create a Wizard, you do not implement or instantiate this class directly,
 * but rather, use one of the convenience classes in this package. There are
 * three:
 * <ul>
 * <li><code>WizardPage</code> - use or subclass WizardPage, and pass an array
 * of instances, or an array of the <code>Class</code> objects of your
 * subclasses
 * to <code>WizardPage.createWizard()</code>. This class offers the added
 * convenience that standard Swing components will be listened to automatically,
 * and if their Name property is set, the value from the component will be
 * automatically put into the settings map.
 * </li>
 *
 * <li><code>WizardPanelProvider</code> - subclass this to create a Wizard
 * with a fixed set of steps. You provide a set of unique ID strings to the
 * constructor, for all of the steps in the wizard, and override
 * createPanel() to create the GUI component that should be displayed for
 * each step - it will be called on demand as the user moves through the
 * wizard</li>
 *
 * <li><code>WizardBranchController</code> - this is for creating complex
 * wizards with decision points after which the future steps change, depending
 * on what the user chooses. Create a simple wizard using WizardPage or
 * WizardPanelProvider to represent the initial steps.
 * Then override <code>getWizardForStep()</code> or
 * <code>getPanelProviderForStep()</code> to return a different Wizard to
 * represent the remaining steps at any point where the set of future steps
 * changes. You can have as many branch points as you want, simply by
 * using WizardBranchController to create the wizards for different decision
 * points.
 * <p>
 * In other words, a wizard with a different set of panels (or number of steps)
 * depending on the user's decision is really three wizards composed into one -
 * one wizard that provides the initial set of steps, and then two others, one
 * or the other of which will actually provide the steps/panels after the
 * decision point (the Wizards are created on demand, for efficiency, so if
 * the user never changes his or her mind at the decision point, only two
 * of the three Wizards are ever actually created).
 * </li></ul>
 *
 * @see org.jackhuang.hellominecraft.util.ui.wizard.api.WizardDisplayer
 * @see WizardPage
 * @see WizardPanelProvider
 * @see WizardBranchController
 *
 * @author Timothy Boudreau
 */
public final class Wizard {

    /**
     * Constant that can be returned by <code>getForwardNavigationMode()</code>
     * to indicate that the Next button can be enabled (or the Finish button
     * if the current panel is the last one in the wizard).
     */
    public static final int MODE_CAN_CONTINUE
                            = WizardController.MODE_CAN_CONTINUE;

    /**
     * Constant that can be returned by <code>getForwardNavigationMode</code> to
     * indicate
     * that the Finish button can be enabled if the problem string is null.
     */
    public static final int MODE_CAN_FINISH
                            = WizardController.MODE_CAN_FINISH;
    /**
     * Constant that can be returned by <code>getForwardNavigationMode</code> to
     * indicate
     * that both the Finish and Next buttons can be enabled if the problem
     * string is null. This value is a bitmask - i.e.
     * <code>MODE_CAN_CONTINUE_OR_FINISH == MODE_CAN_CONTINUE |
     * MODE_CAN_FINISH</code>
     */
    public static final int MODE_CAN_CONTINUE_OR_FINISH
                            = WizardController.MODE_CAN_CONTINUE_OR_FINISH;

    /**
     * Special panel ID key indicating a branch point in the wizard,
     * after which the next step(s) are unknown.
     */
    public static final String UNDETERMINED_STEP = "_#UndeterminedStep";

    final WizardImplementation impl; //package private for unit tests

    /**
     * Creates a new instance of Wizard
     */
    Wizard(WizardImplementation impl) {
        this.impl = Objects.requireNonNull(impl);
    }

    /**
     * Notify the wizard that the user is navigating to a different panel,
     * as identified by the passed <code>id</code>.
     *
     * @param id         The id of the panel being navigated to
     * @param wizardData The data gathered thus far as the user has progressed
     *                   through the wizard. The contents of this map should not contain any
     *                   key/values that were assigned on future panels, if the user is
     *                   navigating backward.
     *
     * @return The component that should be shown for step <code>id</code>
     *         of the <code>Wizard</code>
     */
    public JComponent navigatingTo(String id, Map wizardData) {
        return impl.navigatingTo(id, wizardData);
    }

    /**
     * Get the current step the wizard is on, as determined by the most recent
     * call to <code>navigatingTo()</code>.
     */
    public String getCurrentStep() {
        return impl.getCurrentStep();
    }

    /**
     * Get the id of the step that comes after current step returned by
     * <code>getCurrentStep()</code>.
     *
     * @return Null if this is the last step of the wizard;
     *         <code>UNDETERMINED_STEP</code> if this is a branch point and the
     *         user yet needs to do some interaction with the UI of the current
     *         panel to trigger computation of the id of the next step; otherwise,
     *         the unique id of the next step.
     */
    public String getNextStep() {
        return impl.getNextStep();
    }

    /**
     * Get the id of the preceding step to the current one as returned by
     * <code>getCurrentStep()</code>, or null if the current step is the
     * first page of the wizard.
     *
     * @return the id of the previous step or null
     */
    public String getPreviousStep() {
        return impl.getPreviousStep();
    }

    /**
     * Get the problem string that should be displayed to the user.
     *
     * @return A string describing what the user needs to do to enable
     *         the Next or Finish buttons, or null if the buttons may be enabled
     */
    public String getProblem() {
        return impl.getProblem();
    }

    /**
     * Get the string IDs of all known steps in this wizard, terminating
     * with <code>UNDETERMINED_STEP</code> if subsequent steps of the
     * wizard depend on the user's interaction beyond that point.
     *
     * @return an array of strings which may individually be passed to
     *         <code>navigatingTo</code> to change the current step of the wizard
     */
    public String[] getAllSteps() {
        return impl.getAllSteps();
    }

    /**
     * Get a long description for this panel. The long description should be
     * used in preference to the short description in the top of a wizard
     * panel in the UI, if it returns non-null.
     *
     * @param stepId The ID of the step for which a description is requested
     *
     * @return A more detailed localized description or null
     */
    public String getLongDescription(String stepId) {
        return impl.getLongDescription(stepId);
    }

    /**
     * Get a localized String description of the step for the passed id,
     * which may be displayed in the UI of the wizard.
     *
     * @param id A step id among those returned by <code>getAllSteps()</code>
     */
    public String getStepDescription(String id) {
        return impl.getStepDescription(id);
    }

    /**
     * Called when the user has clicked the finish button. This method
     * computes whatever the result of the wizard is.
     *
     * @param settings The complete set of key-value pairs gathered by the
     *                 various panels as the user proceeded through the wizard
     *
     * @return An implementation-dependent object that is the outcome of
     *         the wizard. May be null. Special return values are instances of
     *         DeferredWizardResult and Summary which will affect the behavior of
     *         the UI.
     */
    public Object finish(Map settings) throws WizardException {
        return impl.finish(settings);
    }

    /**
     * Called when the user has clicked the Cancel button in the wizard UI
     * or otherwise closed the UI component without completing the wizard.
     *
     * @param settings The (possibly incomplete) set of key-value pairs gathered
     *                 by the
     *                 various panels as the user proceeded through the wizard
     *
     * @return true if the UI may indeed be closed, false if closing should
     *         not be permitted
     */
    public boolean cancel(Map settings) {
        return impl.cancel(settings);
    }

    /**
     * Get the title of the Wizard that should be displayed in its dialog
     * titlebar (if any).
     *
     * @return A localized string
     */
    public String getTitle() {
        return impl.getTitle();
    }

    /**
     * Determine if the wizard is busy doing work in a background thread and
     * all navigation controls should be disabled.
     *
     * @return whether or not the wizard is busy
     */
    public boolean isBusy() {
        return impl.isBusy();
    }

    /**
     * Get the navigation mode, which determines the enablement state of
     * the Next and Finish buttons.
     *
     * @return one of the constants <code>MODE_CAN_CONTINUE</code>,
     *         <code>MODE_CAN_FINISH</code>, or
     *         <code>MODE_CAN_CONTINUE_OR_FINISH</code>.
     */
    public int getForwardNavigationMode() {
        return impl.getForwardNavigationMode();
    }

    private volatile boolean listeningToImpl = false;
    private final List<WizardObserver> listeners = Collections.synchronizedList(
        new LinkedList<>());

    private WizardObserver l = null;

    /**
     * Add a WizardObserver that will be notified of navigability and step
     * changes.
     *
     * @param observer A WizardObserver
     */
    public void addWizardObserver(WizardObserver observer) {
        listeners.add(observer);
        if (!listeningToImpl) {
            l = new ImplL();
            impl.addWizardObserver(l);
            listeningToImpl = true;
        }
    }

    /**
     * Remove a WizardObserver.
     *
     * @param observer A WizardObserver
     */
    public void removeWizardObserver(WizardObserver observer) {
        listeners.remove(observer);
        if (listeningToImpl && listeners.isEmpty()) {
            impl.removeWizardObserver(l);
            l = null;
            listeningToImpl = false;
        }
    }

    private class ImplL implements WizardObserver {

        @Override
        public void stepsChanged(Wizard wizard) {
            WizardObserver[] l = (WizardObserver[]) listeners.toArray(
                new WizardObserver[listeners.size()]);
            for (WizardObserver l1 : l)
                l1.stepsChanged(Wizard.this);
        }

        @Override
        public void navigabilityChanged(Wizard wizard) {
            WizardObserver[] l = (WizardObserver[]) listeners.toArray(
                new WizardObserver[listeners.size()]);
            for (WizardObserver l1 : l)
                l1.navigabilityChanged(Wizard.this);
        }

        @Override
        public void selectionChanged(Wizard wizard) {
            WizardObserver[] l = (WizardObserver[]) listeners.toArray(
                new WizardObserver[listeners.size()]);
            for (WizardObserver l1 : l)
                l1.selectionChanged(Wizard.this);
        }
    }

    @Override
    public int hashCode() {
        return impl.hashCode() * 17;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        else if (o instanceof Wizard)
            return impl.equals(((Wizard) o).impl);
        else
            return false;
    }

}
