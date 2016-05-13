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

/**
 * Observer which can detect changes in the state of a wizard as the
 * user proceeds.  Only likely to be used by implementations of
 * <code>WizardDisplayer</code>.
 */
public interface WizardObserver {
    /**
     * Called when the number or names of the steps of the
     * wizard changes (for example, the user made a choice in one pane which
     * affects the flow of subsequent steps).
     * @param wizard The wizard whose steps have changed
     */
    public void stepsChanged(Wizard wizard);
    
    /**
     * Called when the enablement of the next/previous/finish buttons 
     * change, or the problem text changes.
     * @param wizard The wizard whose navigability has changed
     */
    public void navigabilityChanged(Wizard wizard);

    /**
     * Called whenever the current step changes.
     *
     * @param wizard The wizard whose current step has changed
     */
    public void selectionChanged(Wizard wizard);
}