/*
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the License). You may not use this file except in
 * compliance with the License.
 * You can obtain a copy of the License at http://www.netbeans.org/cddl.html
 * or http://www.netbeans.org/cddl.txt.
 * When distributing Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://www.netbeans.org/cddl.txt.
 * If applicable, add the following below the CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */

 /*
 * SimpleWizardInfo.java
 *
 * Created on March 4, 2005, 9:46 PM
 */
package org.jackhuang.hellominecraft.util.ui.wizard.spi;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import org.jackhuang.hellominecraft.util.ArrayUtils;
import org.jackhuang.hellominecraft.util.code.Charsets;
import org.jackhuang.hellominecraft.util.sys.IOUtils;

/**
 * Provides information about a simple wizard. Wraps a
 * WizardPanelProvider and provides a connection to the instance of
 * SimpleWizard created for it, acting as the WizardController for
 * calls to WizardPanelProvider.createPanel().
 */
public final class SimpleWizardInfo implements WizardControllerImplementation {

    private WeakReference wizard = null;
    private final String[] descriptions;
    private final String[] steps;
    final int[] navModeByPanel;
    private String problem = null;
    private final String title;
    private final WizardPanelProvider provider;
    private boolean busy = false;

    SimpleWizardInfo(WizardPanelProvider provider) {
        this(provider.title, provider.steps, provider.descriptions, provider);
    }

    /**
     * Create an instance of Info, which will provide panels for a simple,
     * non-branching wizard, passing a localized title, a list of steps
     * and descriptions.
     */
    protected SimpleWizardInfo(String title, String[] steps, String[] descriptions, WizardPanelProvider provider) {
        this.steps = Objects.requireNonNull(steps, "Null steps");
        this.descriptions = Objects.requireNonNull(descriptions, "Null descriptions");
        if (ArrayUtils.hasDuplicateElements(steps))
            throw new IllegalArgumentException("Duplicate ID: " + Arrays.asList(steps));
        if (descriptions.length != steps.length)
            if (steps.length != descriptions.length + 1 && !WizardImplementation.UNDETERMINED_STEP.equals(steps[steps.length - 1]))
                throw new IllegalArgumentException("Steps and descriptions "
                                                   + "array lengths not equal: " + Arrays.asList(steps) + ":"
                                                   + Arrays.asList(descriptions));
        navModeByPanel = new int[steps.length];
        Arrays.fill(navModeByPanel, -1);
        this.title = title;
        this.provider = provider;
    }

    final void setWizard(SimpleWizard wizard) {
        this.wizard = new WeakReference<>(wizard);
    }

    final SimpleWizard getWizard() {
        return wizard != null ? (SimpleWizard) wizard.get() : null;
    }

    //pkg private for unit tests
    final WizardController controller = new WizardController(this);

    /**
     * Create a panel that represents a named step in the wizard.
     * This method will be called exactly <i>once</i> in the life of
     * a wizard. The panel should retain the passed settings Map, and
     * add/remove values from it as the user enters information, calling
     * <code>setProblem()</code> and <code>setCanFinish()</code> as
     * appropriate in response to user input.
     *
     * @param id       The name of the step, as supplied in the constructor
     * @param settings A Map containing settings from earlier steps in
     *                 the wizard
     *
     * @return A JComponent
     */
    protected JComponent createPanel(String id, Map settings) {
        try {
            JComponent result = provider.createPanel(controller, id, settings);
            if (result instanceof WizardPage) {
                ((WizardPage) result).setController(controller);
                ((WizardPage) result).setWizardDataMap(settings);
            }
            return result;
        } catch (RuntimeException re) {
            JTextArea jta = new JTextArea();
            jta.setBorder(BorderFactory.createMatteBorder(2, 2, 2, 2, Color.RED));
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            PrintStream str = IOUtils.createPrintStream(buf, Charsets.UTF_8);
            re.printStackTrace(str);
            jta.setText(new String(buf.toByteArray(), Charsets.UTF_8));
            setProblem(re.getLocalizedMessage());
            return new JScrollPane(jta);
        }
    }

    /**
     * Instantiate whatever object (if any) the wizard creates from its
     * gathered data.
     */
    protected Object finish(Map settings) throws WizardException {
        return provider.finish(settings);
    }

    public String getLongDescription(String id) {
        return provider.getLongDescription(id);
    }

    /**
     * The method provides a chance to call setProblem() or setCanFinish() when
     * the user re-navigates to a panel they've already seen - in the case
     * that the user pressed the Previous button and then the Next button.
     * <p>
     * The default implementation does nothing, which is sufficient for
     * most implementations. If whether this panel is valid or not could
     * have changed because of changed data from a previous panel,
     * you may want to override this method to ensure validity and currNavMode
     * are set correctly.
     * <p>
     * This method will <i>not</i> be called when a panel is first instantiated
     * -
     * <code>createPanel()</code> is expected to set validity and currNavMode
     * appropriately.
     * <p>
     * The settings Map passed to this method will always be the same
     * Settings map instance that was passed to <code>createPanel()</code>
     * when the panel was created.
     */
    protected void recycleExistingPanel(String id, Map settings, JComponent panel) {
        provider.recycle(id, controller, settings, panel);
    }

    private int index() {
        SimpleWizard a = getWizard();
        return a == null ? 0 : a.currentStepIndex();
    }

    @Override
    public final void setBusy(boolean value) {
        if (value != busy) {
            busy = value;
            fire();
        }
    }

    /**
     * Set whether or not the contents of this panel are valid. When
     * user-entered information in a panel changes, call this method as
     * appropriate.
     */
    @Override
    public final void setProblem(String value) {
        this.problem = value;
        int idx = index();
        provider.setKnownProblem(problem, idx);
        fire();
    }

    private int currNavMode = WizardController.MODE_CAN_CONTINUE;

    /**
     * Set whether or not the Finish button should be enabled. Neither
     * the Finish nor Next buttons will be enabled if setProblem has
     * been called with non-null.
     * <p>
     * Legal values are: WizardController.MODE_CAN_CONTINUE,
     * WizardController.MODE_CAN_FINISH or
     * WizardController.MODE_CAN_CONTINUE_OR_FINISH.
     * <p>
     * This method is used to set what means of forward navigation should
     * be available if the current panel is in a valid state (problem is
     * null). It is <i>not</i> a way to disable both the next button
     * and the finish button, only a way to choose either or both.
     *
     * @param value The forward navigation mode
     *
     * @see setProblem
     */
    @Override
    public final void setForwardNavigationMode(int value) {
        switch (value) {
        case WizardController.MODE_CAN_CONTINUE:
        case WizardController.MODE_CAN_FINISH:
        case WizardController.MODE_CAN_CONTINUE_OR_FINISH:
            break;
        default:
            throw new IllegalArgumentException(Integer.toString(value));
        }
        if (currNavMode != value) {
            currNavMode = value;
            fire();
        }
        navModeByPanel[index()] = value;
    }

    public final int getFwdNavMode() {
        return currNavMode;
    }

    final String getTitle() {
        return title;
    }

    final void update() {
        int idx = index();
        boolean change = navModeByPanel[idx] != -1 && currNavMode != navModeByPanel[idx];
        setProblem(provider.getKnownProblem(idx));
        currNavMode = navModeByPanel[idx] == -1 ? WizardController.MODE_CAN_CONTINUE : navModeByPanel[idx];
        if (change)
            fire();
    }

    final void fire() {
        WizardImplementation wiz = getWizard();
        if (wiz != null)
            getWizard().fireNavigability();
    }

    final boolean isValid() {
        return getProblem() == null;
    }

    final boolean canFinish() {
        return isValid() && (currNavMode != -1 && (currNavMode
                                                   & WizardController.MODE_CAN_FINISH) != 0);
    }

    final boolean canContinue() {
        return isValid() && (currNavMode == -1 || (currNavMode
                                                   & WizardController.MODE_CAN_CONTINUE) != 0);
    }

    String[] getDescriptions() {
        return descriptions;
    }

    String[] getSteps() {
        return steps;
    }

    // lookup the step by name
    boolean containsStep(String name) {
        for (String step : steps)
            if (name.equals(step))
                return true;
        return false;
    }

    final String getProblem() {
        return problem;
    }

    boolean isBusy() {
        return busy;
    }

    @Override
    public boolean equals(Object o) {
        if (o != null && o.getClass() == getClass()) {
            SimpleWizardInfo info = (SimpleWizardInfo) o;

            // assert info.descriptions != null : "Info.descriptions == null";
            // assert info.steps != null : "Info.steps == null";
            if (info.descriptions == null || info.steps == null)
                throw new RuntimeException("Invalid info object");

            return Arrays.equals(info.descriptions, descriptions)
                   && Arrays.equals(info.steps, steps)
                   && (info.title == null ? title == null : info.title.equals(title));
        } else
            return false;
    }

    @Override
    public int hashCode() {
        int result = 0;
        for (int i = 0; i < steps.length; i++)
            result += (steps[i].hashCode() * (i + 1)) ^ 31;
        return result + (title == null ? 0 : title.hashCode());
    }

    boolean cancel(Map settings) {
        return provider.cancel(settings);
    }

    public WizardPanelProvider getProvider() {
        return provider;
    }
}
