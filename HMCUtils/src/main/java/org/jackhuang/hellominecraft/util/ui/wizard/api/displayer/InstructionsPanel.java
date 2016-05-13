/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraft.util.ui.wizard.api.displayer;

import java.awt.Container;

/**
 * Interface for providing the UI for instructions displayed in a wizard.
 *
 * @author Tim Boudreau
 */
public interface InstructionsPanel {
    /**
     * Get the component that will actually display the instructions.
     * Note that this component should have a layout manager that can position
     * a single component in a location that will not obscure the instructions,
     * for showing a progress bar.
     * 
     * This method should always return the same component.
     * 
     * @return A component that can listen to the wizard, display the steps
     * in that wizard, notice and update when they change, and optionally
     * highlight the current step.
     */
    public Container getComponent();
    /**
     * Set whether or not the panel is in the summary page at the end of a
     * wizard (in which case it should add a &quot;summary&quot; item to its
     * list of steps and highlight that).
     * 
     * @param val Whether or not the wizard has navigated to a summary page.
     */
    public void setInSummaryPage (boolean val);
}
