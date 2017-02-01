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
 * WizardBranchController.java
 *
 * Created on March 5, 2005, 6:33 PM
 */
package org.jackhuang.hellominecraft.util.ui.wizard.spi;

import java.util.Map;
import java.util.Objects;

/**
 * Extend this class to create wizards which have branch points in them -
 * either override <code>getWizardForStep</code> to return one or another a
 * wizard which
 * represents the subsequent steps after a decision point, or override
 * <code>getPanelProviderForStep</code> to provide instances of
 * <code>WizardPanelProvider</code>
 * if there are no subsequent branch points and the continuation is a
 * simple wizard.
 * <p>
 * The basic idea is to supply a base wizard for the initial steps, stopping
 * at the branch point. The panel for the branch point should put enough
 * information into the settings map that the WizardBranchController can
 * decide what to return as the remaining steps of the wizard.
 * <p>
 * The result is a <code>Wizard</code> which embeds sub-wizards; when the
 * <code>PanelProvider</code> passed to the constructor runs out of steps,
 * the master <code>Wizard</code> will try to find a sub-<code>Wizard</code>
 * by calling <code>getWizardForStep</code>. If non-null, the user seamlessly
 * continues in the returned wizard. To create <code>Wizard</code>s with
 * multiple branches, simply override <code>getWizardForStep</code> to create
 * another <code>WizardBranchController</code> and return the result of its
 * <code>createWizard</code> method.
 * <p>
 * Note that it is important to cache the instances of
 * <code>WizardPanelProvider</code>
 * or <code>Wizard</code> which are returned here - this class's methods may
 * be called frequently to determine if the sequence of steps (the next wizard)
 * have changed.
 *
 * @author Tim Boudreau
 */
public abstract class WizardBranchController {

    private final SimpleWizardInfo base;

    /**
     * Create a new WizardBranchController. The <code>base</code> argument
     * provides the initial step(s) of the wizard up; when the user comes to
     * the last step of the base wizard, this WizardBranchController will be
     * asked for a wizard to provide subsequent panes. So the base wizard
     * should put some token into the settings map based on what the user
     * selects on its final pane, which the WizardBranchController can use
     * to decide what the next steps should be.
     */
    protected WizardBranchController(WizardPanelProvider base) {
        this(new SimpleWizardInfo(base));
    }

    /**
     * Create a new WizardBranchController using the passed WizardPage
     * instances as the initial pages of the wizard.
     *
     * @param pages An array of WizardPage instances
     */
    protected WizardBranchController(WizardPage[] pages) {
        this(WizardPage.createWizardPanelProvider(pages));
    }

    /**
     * Create a new WizardBranchController using the passed WizardPage
     * as the initial page of the wizard. The initial page should
     * determine the subsequent steps of the wizard.
     *
     * @param onlyPage An instance of WizardPage
     */
    protected WizardBranchController(WizardPage onlyPage) {
        this(WizardPage.createWizardPanelProvider(onlyPage));
    }

    /**
     * Create a new WizardBranchController, using the passed
     * <code>SimpleWizardInfo</code>
     * for the initial panes of the wizard.
     */
    WizardBranchController(SimpleWizardInfo base) {
        this.base = Objects.requireNonNull(base, "No base");
    }

    /**
     * Get the wizard which represents the subsequent panes after this step.
     * The UI for the current step should have put sufficient data into the
     * settings map to decide what to return; return null if not.
     * <p>
     * The default implementation delegates to
     * <code>getPanelProviderForStep()</code>
     * and returns a <code>Wizard</code> representing the result of that
     * call.
     * <p>
     * <b>Note:</b> This method can be called very frequently, to determine
     * if the sequence of steps has changed - so it needs to run fast.
     * Returning the same instance every time the same arguments are passed
     * is highly recommended. It will typically be called whenever a change
     * is fired by the base wizard (i.e. every call <code>setProblem()</code>
     * should generate a check to see if the navigation has changed).
     * <p>
     * Note that the wizard for the subsequent steps will be instantiated
     * as soon as it is known what the user's choice is, so the list of
     * pending steps can be updated (this does not mean that all subsequent
     * panel UI components of the wizard will be instantiated, just the
     * Wizard object itself, which will create panels on demand if they
     * have not already been created).
     *
     * @param step     The current step the user is on in the wizard
     * @param settings The settings map, which previous panes of the wizard
     *                 have been writing information into
     */
    protected Wizard getWizardForStep(String step, Map settings) {
        WizardPanelProvider provider = getPanelProviderForStep(step, settings);
        return provider == null ? null : provider.createWizard();
    }

    /**
     * Override this method to return a <code>WizardPanelProvider</code>
     * representing the
     * steps from here to the final step of the wizard, varying the returned
     * object based on the contents of the map and the step in question.
     * The default implementation of this method throws an <code>Error</code> -
     * either override this method, or override <code>getWizardForStep()</code>
     * (in which case this method will not be called).
     * <p>
     * <b>Note:</b> This method can be called very frequently, to determine
     * if the sequence of steps has changed - so it needs to run fast.
     * Returning the same instance every time called with equivalent arguments
     * is highly recommended.
     *
     * @param step     The string ID of the current step
     * @param settings The settings map, which previous panes of the wizard
     *                 will have written content into
     */
    protected WizardPanelProvider getPanelProviderForStep(String step, Map settings) {
        throw new Error("Override either createInfoForStep or "
                        + "createWizardForStep");
    }

    SimpleWizardInfo getBase() {
        return base;
    }

    private WizardImplementation wizard = null;
    private Wizard real = null;

    /**
     * Create a Wizard to represent this branch controller. The resulting
     * Wizard instance is cached; subsequent calls to this method will return
     * the same instance.
     */
    public final Wizard createWizard() {
        if (wizard == null) {
            wizard = new BranchingWizard(this);
            real = new Wizard(wizard);
        }
        return real;
    }
}
