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
 * BranchingWizard.java
 *
 * Created on March 4, 2005, 10:56 PM
 */
package org.jackhuang.hellominecraft.utils.views.wizard.spi;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * A Wizard with indeterminate branches. The actual branch decision-making
 * is done by the WizardBranchController passed to the constructor.
 * <p/>
 * Wizards with arbitrary numbers of branches can be handled by a
 * WizardBranchController by returning wizards created by
 * another WizardBranchController's <code>createWizard()</code> method.
 * <p/>
 * One important point: There should be no duplicate IDs between steps of
 * this wizard.
 *
 * @author Tim Boudreau
 */
final class BranchingWizard implements WizardImplementation {

    private final List listenerList = Collections.synchronizedList(
        new LinkedList());

    private final WizardBranchController brancher;
    final WizardImplementation initialSteps;

    private WizardImplementation subsequentSteps;
    private WizardImplementation activeWizard;
    private WL wl;

    private String currStep;
    private Map wizardData;

    public BranchingWizard(WizardBranchController brancher) {
        this.brancher = brancher;
        initialSteps = new SimpleWizard(brancher.getBase(), true);
        setCurrent(initialSteps);
    }

    protected final WizardImplementation createSecondary(Map settings) {
        Wizard wiz = brancher.getWizardForStep(currStep, settings);
        return wiz == null ? null : wiz.impl;
    }

    private void checkForSecondary() {
        if (wizardData == null)
            return;

        WizardImplementation newSecondary = createSecondary(wizardData);

        /*
         * johnflournoy 7/20/07
         * check for secondary should be adding the secondary to the activeWizard
         * not the initial wizard.  Adding it to the initial wizard was breaking
         * multiple branching - to accomplish this created a new method:
         * setSecondary()
         */
        if (activeWizard instanceof BranchingWizard)
            ((BranchingWizard) activeWizard).setSecondary(newSecondary);
        else
            this.setSecondary(newSecondary);
    }

    /**
     * Set the secondary for this <code>BranchingWizard</code>.
     *
     * @param newSecondary is a WizardImplementation.
     */
    private void setSecondary(WizardImplementation newSecondary) {
        /* johnflournoy added additional condition: secondary != this */
        if ((((subsequentSteps == null) != (newSecondary == null))
             || (subsequentSteps != null && !subsequentSteps.equals(newSecondary)))
            && !this.equals(newSecondary))

            /*
              * johnflournoy: only set the subsequent steps if it
              * this wizard owns the current step.
             */
            if (Arrays.asList(initialSteps.getAllSteps()).contains(currStep)) {
                subsequentSteps = newSecondary;
                fireStepsChanged();
            }
    }

    public int getForwardNavigationMode() {
        return activeWizard.getForwardNavigationMode();
    }

    private void setCurrent(WizardImplementation wizard) {
        if (activeWizard == wizard)
            return;

        if (wizard == null)
            throw new NullPointerException("Can't set current wizard to null");

        if ((activeWizard != null) && (wl != null))
            activeWizard.removeWizardObserver(wl);

        activeWizard = wizard;

        if (wl == null)
            wl = new WL();

        activeWizard.addWizardObserver(wl);
    }

    public final boolean isBusy() {
        return activeWizard.isBusy();
    }

    public final Object finish(Map settings) throws WizardException {
        try {
            Object result = activeWizard.finish(settings);
            initialSteps.removeWizardObserver(wl);
            //Can be null, we allow bail-out with finish mid-wizard now
            if (subsequentSteps != null)
                subsequentSteps.removeWizardObserver(wl);
            return result;
        } catch (WizardException we) {
            if (we.getStepToReturnTo() != null) {
                initialSteps.addWizardObserver(wl);
                //Can be null, we allow bail-out with finish mid-wizard now
                if (subsequentSteps != null)
                    subsequentSteps.addWizardObserver(wl);
            }
            throw we;
        }
    }

    public final String[] getAllSteps() {
        String[] result;
        if (subsequentSteps == null) {
            String[] bsteps = initialSteps.getAllSteps();
            result = new String[bsteps.length + 1];
            System.arraycopy(bsteps, 0, result, 0, bsteps.length);
            result[result.length - 1] = UNDETERMINED_STEP;
        } else {
            String[] bsteps = initialSteps.getAllSteps();
            String[] csteps = subsequentSteps.getAllSteps();
            result = new String[bsteps.length + csteps.length];
            System.arraycopy(bsteps, 0, result, 0, bsteps.length);
            System.arraycopy(csteps, 0, result, bsteps.length, csteps.length);
        }
        return result;
    }

    public String getCurrentStep() {
        return currStep;
    }

    public final String getNextStep() {
        String result;
        if (currStep == null)
            result = getAllSteps()[0];
        else {
            String[] steps = getAllSteps();
            int idx = Arrays.asList(steps).indexOf(currStep);
            if (idx == -1)
                throw new IllegalStateException("Current step not in"
                                                + " available steps:  " + currStep + " not in "
                                                + Arrays.asList(steps));
            else if (idx == steps.length - 1)
                if (subsequentSteps == null)
                    result = UNDETERMINED_STEP;
                else
                    result = subsequentSteps.getNextStep();
            else {
                WizardImplementation w = ownerOf(currStep);
                if (w == initialSteps && idx == initialSteps.getAllSteps().length - 1) {
                    checkForSecondary();
                    if (subsequentSteps != null)
                        result = subsequentSteps.getAllSteps()[0];
                    else
                        result = UNDETERMINED_STEP;
                } else
                    result = w.getNextStep();
            }
        }
        return getProblem() == null ? result : UNDETERMINED_STEP.equals(result) ? result : null;
    }

    public final String getPreviousStep() {
        if (activeWizard == subsequentSteps && subsequentSteps.getAllSteps()[0].equals(currStep))
            return initialSteps.getAllSteps()[initialSteps.getAllSteps().length - 1];
        else
            return activeWizard.getPreviousStep();
    }

    public final String getProblem() {
        return activeWizard.getProblem();
    }

    public final String getStepDescription(String id) {
        WizardImplementation w = ownerOf(id);
        if (w == null)
            return null;
        return w.getStepDescription(id);
    }

    public final String getLongDescription(String id) {
        WizardImplementation w = ownerOf(id);
        if (w == null)
            return null;
        return w.getLongDescription(id);
    }

    private WizardImplementation ownerOf(String id) {
        if (UNDETERMINED_STEP.equals(id)) {
            checkForSecondary();
            return subsequentSteps;
        }
        if (Arrays.asList(initialSteps.getAllSteps()).contains(id))
            return initialSteps;
        else {
            /*
             * johnflournoy
             * need to check an existing subsequentsteps to see if
             * we can find the owner of "id", otherwise we were losing
             * a wizard if we had multiple branches and we backed up to an
             * earlier wizard and then went down the same path again.
             */
            if (subsequentSteps != null) {
                if (!Arrays.asList(subsequentSteps.getAllSteps()).contains(id))
                    checkForSecondary();
            } else
                checkForSecondary();

            return subsequentSteps;
        }
    }

    public final String getTitle() {
        return activeWizard.getTitle();
    }

    public final JComponent navigatingTo(String id, Map settings) {
        if (id == null)
            throw new NullPointerException();
        currStep = id;
        wizardData = settings;

        WizardImplementation impl = ownerOf(id);
        if (impl == null)
            throw new NullPointerException("No owning WizardImplementation for"
                                           + " id " + id);
        setCurrent(impl);

        return activeWizard.navigatingTo(id, settings);
    }

    public final void removeWizardObserver(WizardObserver observer) {
        listenerList.remove(observer);
    }

    public final void addWizardObserver(WizardObserver observer) {
        listenerList.add(observer);
    }

    private void fireStepsChanged() {
        WizardObserver[] listeners = (WizardObserver[]) listenerList.toArray(new WizardObserver[0]);

        for (int i = listeners.length - 1; i >= 0; i--) {
            WizardObserver l = (WizardObserver) listeners[i];
            l.stepsChanged(null);
        }
    }

    private void fireNavigabilityChanged() {
        checkForSecondary();

        WizardObserver[] listeners = (WizardObserver[]) listenerList.toArray(new WizardObserver[0]);

        for (int i = listeners.length - 1; i >= 0; i--) {
            WizardObserver l = (WizardObserver) listeners[i];
            l.navigabilityChanged(null);
        }
    }

    private void fireSelectionChanged() {
        WizardObserver[] listeners = (WizardObserver[]) listenerList.toArray(new WizardObserver[0]);

        for (int i = listeners.length - 1; i >= 0; i--) {
            WizardObserver l = (WizardObserver) listeners[i];
            l.selectionChanged(null);
        }
    }

    public boolean cancel(Map settings) {
        return activeWizard == null ? true : activeWizard.cancel(settings);
    }

    private class WL implements WizardObserver {

        public void stepsChanged(Wizard wizard) {
            fireStepsChanged();
        }

        public void navigabilityChanged(Wizard wizard) {
            fireNavigabilityChanged();
        }

        public void selectionChanged(Wizard wizard) {
            fireSelectionChanged();
        }
    }
}
