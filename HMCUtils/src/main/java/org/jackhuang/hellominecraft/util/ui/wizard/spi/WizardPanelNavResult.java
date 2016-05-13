/*  The contents of this file are subject to the terms of the Common Development
and Distribution License (the License). You may not use this file except in
compliance with the License.
    You can obtain a copy of the License at http://www.netbeans.org/cddl.html
or http://www.netbeans.org/cddl.txt.
    When distributing Covered Code, include this CDDL Header Notice in each file
and include the License file at http://www.netbeans.org/cddl.txt.
 */
package org.jackhuang.hellominecraft.util.ui.wizard.spi;

import java.util.Map;

/**
 * Result class for the methods in WizardPanel.
 *
 * For immediate action, one of the two constantants PROCEED or REMAIN_ON_PAGE
 * should be returned. Otherwise an instance of a subclass should be returned
 * that computes a Boolean result.
 *
 * @author stanley@stanleyknutson.com
 */
public abstract class WizardPanelNavResult extends DeferredWizardResult {

    /**
     * value for procced to next step in the wizard.
     */
    public static final WizardPanelNavResult PROCEED = new WPNRimmediate(true);
    /**
     * Value to remain on the current page in the wizard
     */
    public static final WizardPanelNavResult REMAIN_ON_PAGE = new WPNRimmediate(false);

    private WizardPanelNavResult() {
        super();
    }

    public boolean isDeferredComputation() {
        return true;
    }

    /*
     * internal class for the constants only
     */
    private final static class WPNRimmediate extends WizardPanelNavResult {

        boolean value;

        WPNRimmediate(boolean v) {
            value = v;
        }

        public boolean isDeferredComputation() {
            return false;
        }

        public boolean equals(Object o) {
            return o instanceof WPNRimmediate && ((WPNRimmediate) o).value == value;
        }

        public int hashCode() {
            return value ? 1 : 2;
        }

        public void start(Map settings, ResultProgressHandle progress) {
            // Should never get here, this is supposed to be immediate!
            throw new RuntimeException("Immediate result was called as deferral!");
        }

    }
}
