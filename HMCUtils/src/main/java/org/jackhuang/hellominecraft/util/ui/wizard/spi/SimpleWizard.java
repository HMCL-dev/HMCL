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
 * SimpleWizard.java
 *
 * Created on February 22, 2005, 2:33 PM
 */
package org.jackhuang.hellominecraft.util.ui.wizard.spi;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JComponent;

/**
 * A simple implementation of Wizard for use in wizards which have a
 * straightforward set of steps with no branching. To use, implement the
 * simplified interface SimpleWizard.Info and pass that to the constructor.
 *
 * @see SimpleWizardInfo
 * @author Tim Boudreau
 */
final class SimpleWizard extends AbstractWizard {

    private final Map ids2panels = new HashMap<>();

    final SimpleWizardInfo info;

    private String currID = null;
    private boolean subwizard;

    public SimpleWizard(WizardPanelProvider prov) {
        this(new SimpleWizardInfo(prov), false);
    }

    /**
     * Creates a new instance of SimpleWizard
     */
    public SimpleWizard(SimpleWizardInfo info) {
        this.info = info;
        info.setWizard(this);
    }

    /**
     * Creates a new instance of SimpleWizard
     */
    public SimpleWizard(SimpleWizardInfo info, boolean subwizard) {
        this.info = info;
        this.subwizard = subwizard;
        info.setWizard(this);
    }

    @Override
    public void addWizardObserver(WizardObserver observer) {
        listenerList.add(observer);
    }

    @Override
    public void removeWizardObserver(WizardObserver observer) {
        listenerList.remove(observer);
    }

    @Override
    public int getForwardNavigationMode() {
        int result = info.getFwdNavMode();
        if (!subwizard && ((result & WizardController.MODE_CAN_CONTINUE) != 0) && isLastStep())
            result = WizardController.MODE_CAN_FINISH;
        return result;
    }

    boolean isLastStep() {
        String[] steps = info.getSteps();
        return currID != null && steps.length > 0 && currID.equals(steps[steps.length - 1]);
    }

    @Override
    public String[] getAllSteps() {
        String[] allSteps = info.getSteps();
        String[] result = new String[allSteps.length];
        //Defensive copy
        System.arraycopy(allSteps, 0, result, 0, allSteps.length);
        return result;
    }

    @Override
    public String getStepDescription(String id) {
        int idx = Arrays.asList(info.getSteps()).indexOf(id);
        if (idx == -1)
            throw new IllegalArgumentException("Undefined id: " + id);
        return info.getDescriptions()[idx];
    }

    @Override
    public String getLongDescription(String id) {
        return info.getLongDescription(id);
    }

    @Override
    public JComponent navigatingTo(String id, Map settings) {
//        assert SwingUtilities.isEventDispatchThread();

        // if info.getSteps() does not yet contain the ID, then create it
        JComponent result = (JComponent) ids2panels.get(id);
        currID = id;
        if (result == null) {
            result = info.createPanel(id, settings);
            ids2panels.put(id, result);
        } else {
            info.update();
            info.recycleExistingPanel(id, settings, result);
        }
        fireSelectionChanged();
        return result;
    }

    @Override
    public String getCurrentStep() {
        return currID;
    }

    @Override
    public String getNextStep() {
        if (!info.isValid())
            return null;
        if ((info.getFwdNavMode() & WizardController.MODE_CAN_CONTINUE) == 0)
            return null;

        int idx = currentStepIndex();
        if (idx < info.getSteps().length - 1)
            return info.getSteps()[idx + 1];
        else
            return null;
    }

    @Override
    public String getPreviousStep() {
        int idx = currentStepIndex();
        if (idx < info.getSteps().length && idx > 0)
            return info.getSteps()[idx - 1];
        else
            return null;
    }

    int currentStepIndex() {
        int idx = 0;
        if (currID != null)
            idx = Arrays.asList(info.getSteps()).indexOf(currID);
        return idx;
    }

    void fireNavigability() {
        fireChanged(l -> l.navigabilityChanged(null));
    }

    private void fireSelectionChanged() {
        fireChanged(l -> l.selectionChanged(null));
    }

    @Override
    public Object finish(Map settings) throws WizardException {
        return info.finish(settings);
    }

    @Override
    public boolean cancel(Map settings) {
        return info.cancel(settings);
    }

    @Override
    public String getTitle() {
        return info.getTitle();
    }

    @Override
    public String getProblem() {
        return info.getProblem();
    }

    @Override
    public boolean isBusy() {
        return info.isBusy();
    }

    @Override
    public int hashCode() {
        return info.hashCode() ^ 17;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof SimpleWizard)
            return ((SimpleWizard) o).info.equals(info);
        else
            return false;
    }

    @Override
    public String toString() {
        return "SimpleWizard for " + info;
    }
}
