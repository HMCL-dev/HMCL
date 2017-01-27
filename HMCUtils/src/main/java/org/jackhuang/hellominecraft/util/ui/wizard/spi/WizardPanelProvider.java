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
 /*
 * PanelProvider.java
 *
 * Created on March 5, 2005, 7:25 PM
 */
package org.jackhuang.hellominecraft.util.ui.wizard.spi;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import javax.swing.JComponent;
import org.jackhuang.hellominecraft.util.ArrayUtils;

/**
 * (Note:  <code>WizardPage</code> offers somewhat simpler functionality for
 * creating a wizard than does WizardPanelProvider; the only advantage of
 * <code>WizardPanelProvider</code> is that it does not require one to subclass
 * a panel component).
 * <p>
 * A simple interface for providing a fixed set of panels for a wizard. To use,
 * simply implement <code>createPanel()</code> to create the appropriate UI
 * component for a given step (a unique String ID - one of the ones passed in
 * the constructor in the <code>steps</code> array), and implement
 * <code>finish()</code> to do whatever should be done when the wizard is
 * finished.
 * <p>
 * To control whether the Next/Finish buttons are enabled, components created in
 * <code>createPanel()</code> should call methods on the <code>
 * WizardController</code> passed. The created panels should listen on the UI
 * components they create, updating the settings Map when the user changes their
 * input.
 * <p>
 * Super-simple one-pane wizard example - if the checkbox is checked, the user
 * can continue:
 * <pre>
 * public class MyProvider extends WizardPanelProvider {
 *    public MyProvider() {
 *       <font color="gray">//here we pass a localized title for the wizard,
 *       //the ID of the one step it will have, and a localized description
 *       //the wizard can show for that one step</font>
 *       super ("Click the box", "click", "Click the checkbox");
 *    }
 *    protected JComponent createPanel (final WizardController controller, String id, final Map settings) {
 *       <font color="gray">//A quick sanity check</font>
 *       assert "click".equals (id);
 *       <font color="gray">//Remember this method will only be called <i>once</i> for any panel</font>
 *       final JCheckBox result = new JCheckBox();
 *       result.addActionListener (new ActionListener() {
 *          public void actionPerformed (ActionEvent ae) {
 *             <font color="gray">//Typically you want to write the result of some user
 *             //action into the settings map as soon as they do it </font>
 *             settings.put ("boxSelected", result.isSelected() ? Boolean.TRUE : Boolean.FALSE);
 *             if (result.isSelected()) {
 *                controller.setProblem(null);
 *             } else {
 *                controller.setProblem("The box is not checked");
 *             }
 *             controller.setCanFinish(true); <font color="gray">//won't matter if we called setProblem() with non-null</font>
 *          }
 *       });
 *       return result;
 *    }
 *
 *    protected Object finish (Map settings) throws WizardException {
 *       <font color="gray">//if we had some interesting information (Strings a user put in a
 *       //text field or something, we'd generate some interesting object or
 *       //create some files or something here</font>
 *       return null;
 *    }
 * }
 * </pre>
 *
 * @author Tim Boudreau
 */
public abstract class WizardPanelProvider {

    final String title;
    final String[] descriptions;
    final String[] steps;
    final String[] knownProblems;

    /**
     * Create a WizardPanelProvider. The passed array of steps and descriptions
     * will be used as IDs and localized descriptions of the various steps in
     * the wizard. Use this constructor (which passes not title) for sub-wizards
     * used in a <code>WizardBranchController</code>, where the first pane will
     * determine the title, and the titles of the sub-wizards will never be
     * shown.
     *
     * @param steps A set of unique IDs identifying each step of this wizard.
     * Each ID must occur only once in the array of steps.
     *
     * @param descriptions A set of human-readable descriptions corresponding
     * 1:1 with the unique IDs passed as the <code>steps</code> parameter
     */
    protected WizardPanelProvider(String[] steps, String[] descriptions) {
        this(null, steps, descriptions);
    }

    /**
     * Create a WizardPanelProvider with the provided title, steps and
     * descriptions. The <code>steps</code> parameter are unique IDs of panels,
     * which will be passed to <code>createPanel</code> to create panels for
     * various steps in the wizard, as the user navigates it. The
     * <code>descriptions</code> parameter is a set of localized descriptions
     * that can appear in the Wizard to describe each step.
     *
     * @param title A human readable title for the wizard dialog
     * @param steps An array of unique IDs for the various panels of this wizard
     * @param descriptions An array of descriptions corresponding 1:1 with the
     * unique IDs. These must be human readable, localized strings.
     */
    protected WizardPanelProvider(String title, String[] steps, String[] descriptions) {
        this.title = title;
        this.steps = steps;
        this.descriptions = descriptions;
        knownProblems = new String[steps.length];
        if (steps.length != descriptions.length)
            throw new IllegalArgumentException("Length of steps and"
                    + " descriptions arrays do not match");
        // assert validData (steps, descriptions) == null : validData (steps, descriptions);
        String v = validData(steps, descriptions);
        if (v != null)
            throw new RuntimeException(v);
    }

    private String validData(String[] steps, String[] descriptions) {
        if (steps.length != descriptions.length)
            return steps.length + " steps but " + descriptions.length
                    + " descriptions";
        for (int i = 0; i < steps.length; i++) {
            Objects.requireNonNull(steps[i], "Step id " + i + " is null");
            Objects.requireNonNull(descriptions[i], "Description " + i + " is null");
        }
        if (ArrayUtils.hasDuplicateElements(steps))
            return "Duplicate step ids: " + Arrays.asList(steps);
        return null;
    }

    /**
     * Convenience constructor to create a WizardPanelProvider which has only
     * one step to it. Mainly useful for initial steps in a
     * <code>WizardBranchController</code>.
     *
     * @param title A human readable title for the wizard dialog
     * @param singleStep The unique ID of the only step this wizard has
     * @param singleDescription The human-readable description of what the user
     * should do in the one step of this one-step wizard or sub-wizard
     */
    protected WizardPanelProvider(String title, String singleStep, String singleDescription) {
        this(title, new String[] { singleStep }, new String[] { singleDescription });
    }

    /**
     * Create a panel that represents a named step in the wizard. This method
     * will be called exactly <i>once</i> in the life of a wizard. The panel
     * should retain the passed settings Map, and add/remove values from it as
     * the user enters information, calling <code>setProblem()</code> and
     * <code>setCanFinish()</code> as appropriate in response to user input.
     *
     * @param controller - the object which controls whether the Next/Finish
     * buttons in the wizard are enabled, and what instructions are displayed to
     * the user if they are not
     * @param id The name of the step, one of the array of steps passed in the
     * constructor
     * @param settings A Map containing settings from earlier steps in the
     * wizard. It is safe to retain a reference to this map and put values in it
     * as the user manipulates the UI; the reference should be refreshed
     * whenever this method is called again.
     * @return A JComponent that should be displayed in the center of the wizard
     */
    protected abstract JComponent createPanel(WizardController controller, String id, Map settings);

    /**
     * Instantiate whatever object (if any) the wizard creates from its gathered
     * data. The default implementation is a no-op that returns null.
     * <p>
     * If an instance of <code>Summary</code> is returned from this method, the
     * UI shall display it on a final page and disable all navigation buttons
     * except the Close/Cancel button.
     * <p>
     * If an instance of <code>DeferredWizardResult</code> is returned from this
     * method, the UI shall display some sort of progress bar while the result
     * is computed in the background. If that <code>DeferredWizardResult</code>
     * produces a <code>Summary</code> object, that summary shall be displayed
     * as described above.
     * <p>
     * The default implementation returns the settings map it is passed.
     *
     * @param settings The settings map, now fully populated with all settings
     * needed to complete the wizard (this method will only be called if
     * <code>setProblem(null)</code> and <code>setCanFinish(true)</code> have
     * been called on the <code>WizardController</code> passed to
     * <code>createPanel()</code>.
     * @return an object composed based on what the user entered in the wizard -
     * somethingmeaningful to whatever code invoked the wizard, or null. Note
     * special handling if an instance of <code>DeferredWizardResult</code> or
     * <code>Summary</code> is returned from this method.
     */
    protected Object finish(Map settings) throws WizardException {
        return settings;
    }

    /**
     * The method provides a chance to call setProblem() or setCanFinish() when
     * the user re-navigates to a panel they've already seen - in the case that
     * the user pressed the Previous button and then the Next button.
     * <p>
     * The default implementation does nothing, which is sufficient for most
     * cases. If whether this panel is valid or not could have changed because
     * of changed data from a previous panel, or it displays data entered on
     * previous panes which may have changed, you may want to override this
     * method to ensure validity and canFinish are set correctly, and that the
     * components have the correct text.
     * <p>
     * This method will <i>not</i> be called when a panel is first instantiated
     * - <code>createPanel()</code> is expected to set validity and canFinish
     * appropriately.
     * <p>
     * The settings Map passed to this method will always be the same Settings
     * map instance that was passed to <code>createPanel()</code> when the panel
     * was created.
     * <p>
     * If you are implementing WizardPanelProvider and some of the pages are
     * <code>WizardPage</code>s, you should call the super implementation if you
     * override this method.
     */
    protected void recycleExistingPanel(String id, WizardController controller, Map wizardData, JComponent panel) {
        //do nothing
    }

    void recycle(String id, WizardController controller, Map wizardData, JComponent panel) {
        if (panel instanceof WizardPage) {
            WizardPage page = (WizardPage) panel;
            page.setController(controller);
            page.setWizardDataMap(wizardData);
            page.recycle();
        }
        recycleExistingPanel(id, controller, wizardData, panel);
    }

    private Wizard wizard;

    /**
     * Create a Wizard for this PanelProvider. The instance created by this
     * method is cached and the same instance will be returned on subsequent
     * calls.
     */
    public final Wizard createWizard() {
        if (wizard == null)
            wizard = new Wizard(new SimpleWizard(this));
        return wizard;
    }

    /**
     * This method can optionally be overridden to provide a longer description
     * of a step to be shown in the top of its panel. The default implementation
     * returns null, indicating that the short description should be used.
     *
     * @param stepId a unique id for one step of the wizard
     * @return An alternate description for use in the top of the wizard page
     * when this page is the current one, or null
     */
    public String getLongDescription(String stepId) {
        return null;
    }

    /**
     * Convenience method to get the index into the array of steps passed to the
     * constructor of a specific step id.
     */
    protected final int indexOfStep(String id) {
        return Arrays.asList(steps).indexOf(id);
    }

    void setKnownProblem(String problem, int idx) {
        //Record a problem message so we can put it back if the user does
        //prev and then next
        if (idx >= 0) //setProblem() can be called during initialization
            knownProblems[idx] = problem;
    }

    String getKnownProblem(int idx) {
        return knownProblems[idx];
    }

    /**
     * Called if the user invokes cancel. The default impl returns true.
     *
     * @return false to abort cancellation (almost all implementations will want
     * to return true - this is really only applicable in cases such as an OS
     * installer or such).
     */
    public boolean cancel(Map settings) {
        return true;
    }

    @Override
    public String toString() {
        return super.toString() + " with wizard " + wizard;
    }
}
