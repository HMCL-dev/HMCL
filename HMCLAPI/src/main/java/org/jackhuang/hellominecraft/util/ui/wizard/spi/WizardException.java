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
 * WizardException.java
 *
 * Created on February 22, 2005, 3:56 PM
 */

package org.jackhuang.hellominecraft.util.ui.wizard.spi;

/**
 * Some arguments a user enters in a wizard may be too expensive to validate
 * as the user is going through the wizard.  Therefore, Wizard.finish() throws
 * WizardException.
 * <p>
 * Exceptions of this type always have a localized message, and optionally
 * provide a step in the wizard that to return to, so that the user can
 * enter corrected information.
 *
 * @author Tim Boudreau
 */
public final class WizardException extends Exception {
    private final String localizedMessage;
    private final String step;
    /** Creates a new instance of WizardException */
    public WizardException(String localizedMessage, String stepToReturnTo) {
        super ("wizardException");
        this.localizedMessage = localizedMessage;
        this.step = stepToReturnTo;
    }

    public WizardException (String localizedMessage) {
        this (localizedMessage, Wizard.UNDETERMINED_STEP);
    }
    
    public String getLocalizedMessage() {
        return localizedMessage;
    }
    
    public String getStepToReturnTo() {
        return step;
    }

}
