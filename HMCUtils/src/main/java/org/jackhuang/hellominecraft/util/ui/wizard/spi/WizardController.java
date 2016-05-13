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
 * WizardController.java
 *
 * Created on March 5, 2005, 7:24 PM
 */

package org.jackhuang.hellominecraft.util.ui.wizard.spi;

/**
 * Controller which can be used to modify the UI state of a wizard.  Passed
 * as an argument to methods of <code>WizardPanelProvider</code>.  Use this 
 * interface
 * to determine whether the Next/Finish buttons should be enabled, and if some
 * problem explanation text should be displayed.
 * <p>
 * If you are using {@link WizardPage WizardPage}, methods equivalent to this
 * interface are available directly on instances of <code>WizardPage</code>.
 *
 * @see WizardPanelProvider
 * @author Tim Boudreau
 */
public final class WizardController {
    /**
     * Constant that can be passed to <code>setForwardNavigationMode</code> to indicate
     * that the Next button can be enabled if the problem string is null.
     * Value is identical to the similarly named constant on <code>Wizard</code>.
     */
    public static final int MODE_CAN_CONTINUE = 1;
    /**
     * Constant that can be passed to <code>setForwardNavigationMode</code> to indicate
     * that the Finish button can be enabled if the problem string is null.
     * Value is identical to the similarly named constant on <code>Wizard</code>.
     */
    public static final int MODE_CAN_FINISH = 2;
    /**
     * Constant that can be passed to <code>setForwardNavigationMode</code> to indicate
     * that both the Finish and Next buttons can be enabled if the problem 
     * string is null.  This value is a bitmask - i.e. 
     * <code>MODE_CAN_CONTINUE_OR_FINISH == MODE_CAN_CONTINUE | 
     * MODE_CAN_FINISH</code>.
     * Value is identical to the similarly named constant on 
     * <code>Wizard</code>.
     */
    public static final int MODE_CAN_CONTINUE_OR_FINISH = 
            MODE_CAN_CONTINUE | MODE_CAN_FINISH;

    private final WizardControllerImplementation impl;
    
    WizardController (WizardControllerImplementation impl) {
        this.impl = impl;
    }
    
    /**
     * Indicate that there is a problem with what the user has (or has not)
     * input, such that the Next/Finish buttons should be disabled until the
     * user has made some change.
     * <p>
     * If you want to disable the Next/Finish buttons, do that by calling
     * this method with a short description of what is wrong.
     * <p>
     * Pass null to indicate there is no problem;  non-null indicates there is
     * a problem - the passed string should be a localized, human-readable
     * description that assists the user in correcting the situation.  It will
     * be displayed in the UI.
     */
    public void setProblem (String value) {
        impl.setProblem (value);
    }
    
    /**
     * Set the forward navigation mode.  This method determines whether 
     * the Next button, the Finish button or both should be enabled if the
     * problem string is set to null.  
     * <p>
     * On panels where, based on the UI state, the only reasonable next
     * step is to finish the wizard (even though there may be more panels
     * if the UI is in a different state), set the navigation mode to
     * MODE_CAN_FINISH, and the Finish button will be enabled, and the
     * Next button not.
     * <p>
     * On panels where, based on the UI state, the user could either continue
     * or complete the wizard at that point, set the navigation mode to
     * MODE_CAN_CONTINUE_OR_FINISH.
     * <p>
     * If the finish button should not be enabled, set the navigation mode
     * to MODE_CAN_CONTINUE.  This is the default on any panel if no 
     * explicit call to <code>setForwardNavigationMode()</code> has been made.
     * 
     * @param navigationMode Legal values are MODE_CAN_CONTINUE, 
     *  MODE_CAN_FINISH or MODE_CAN_CONTINUE_OR_FINISH
     */
    public void setForwardNavigationMode (int navigationMode) {
        impl.setForwardNavigationMode(navigationMode);
    }
    
    /**
     * Indicate that some sort of background process is happening (presumably
     * a progress bar is being shown to the user) which cannot be interrupted.
     * <i>Calling this menu disables all navigation and the ability to close
     * the wizard dialog.  Use this option with caution and liberal use of
     * <code>finally</code> to reenable navigation.</i>
     */
    public void setBusy (boolean busy) {
        impl.setBusy (busy);
    }
    
    WizardControllerImplementation getImpl() {
        return impl;
    }
}
