/*
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at http://www.netbeans.org/cddl.html
 * or http://www.netbeans.org/cddl.txt.
 *
 * When distributing Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://www.netbeans.org/cddl.txt.
 * If applicable, add the following below the CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 * Microsystems, Inc. All Rights Reserved.
 */
package org.jackhuang.hellominecraft.util.ui.wizard.api;

import java.util.Map;

/**
 * Object which is called when the wizard is completed or cancelled.  Only
 * useful if you want to call WizardDisplayer.installInContainer() to install
 * a wizard in a custom container (such as a JInternalDialog) - this class
 * is a callback to notify the caller that the Finish or Cancel button has
 * been pressed.
 *
 * @author Tim Boudreau
 */
public interface WizardResultReceiver {
    /**
     * Called when the wizard has been completed, providing whatever object
     * the wizard created as its result.
     * @param wizardResult The object created by Wizard.finish()
     */ 
    void finished (Object wizardResult);
    /**
     * Called when the wizard has been cancelled.
     * @param settings The settings that were gathered thus far in the
     *  wizard
     */ 
    void cancelled (Map settings);
}
