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
 * This is an optional interface for panels that want to be notified when
 * the next and back buttons are pressed.
 *
 * The WizardPanelProvider is NOT required to create panels that implement
 * this interface.
 *
 * Each of these methods returns a WizardPanelNavResult that can be used to
 * indicate PROCEED or REMAIN_ON_PAGE.
 *
 * The result can also be an instance of a subclass of WizardPanelNavResult
 * that implements the <code>start</code> method to use a background thread
 * to determine if the next page can be shown.
 *
 * @author stanley@stanleyknutson.com
 */
public interface WizardPanel {

    /**
     * This method is invoked when the "next" button has been pushed,
     * to do a final validation of input (such as doing a database login).
     *
     * If this method return false, then the "next" button will not change the
     * displayed panel. Presumably some error will have been shown to the user.
     *
     * @param stepName
     * @param settings
     * @param wizard
     *
     * @return WizardPanelNavResult.PROCEED if the "next" button should proceed,
     *         WizardPanelNavResult.REMAIN_ON_PAGE if "next" should not proceed,
     *         or a instance of subclass of WizardPanelResult that will do some
     *         background computation
     *         and call the progress.finished method with one of those constants
     *         (or call progress.failed with the error message)
     */
    public WizardPanelNavResult allowNext(String stepName, Map settings, Wizard wizard);

    /**
     * This method is invoked when the "back" button has been pushed,
     * to discard any data from the setings that will not been needed and for
     * which the
     * normal "just hide that data" is not the desired behavior.
     * (See MergeMap for discussion of the "hide the data" behavior)
     *
     * If this method return false, then the "next" button will not change the
     * displayed panel. Presumably some error will have been shown to the user.
     *
     * @param stepName
     * @param settings
     * @param wizard
     *
     * @return WizardPanelNavResult.PROCEED if the "back" button should proceed,
     *         WizardPanelNavResult.REMAIN_ON_PAGE if "back" should not proceed,
     *         or a instance of subclass of WizardPanelResult that will do some
     *         background computation
     *         and call the progress.finished method with one of those constants.
     *         (or call progress.failed with the error message)
     */
    public WizardPanelNavResult allowBack(String stepName, Map settings, Wizard wizard);

    /**
     * This method is invoked when the "finish" button has been pushed,
     * to allow veto of the finish action BEFORE the wizard finish method is
     * invoked.
     *
     * If this method return false, then the "finish" button will have no
     * effect.
     * Presumably some error will have been shown to the user.
     *
     * @param stepName
     * @param settings
     * @param wizard
     *
     * @return WizardPanelNavResult.PROCEED if the "finish" button should
     *         proceed,
     *         WizardPanelNavResult.REMAIN_ON_PAGE if "finish" should not proceed,
     *         or a instance of subclass of WizardPanelResult that will do some
     *         background computation
     *         and call the progress.finished method with one of those constants.
     *         (or call progress.failed with the error message)
     */
    public WizardPanelNavResult allowFinish(String stepName, Map settings, Wizard wizard);

}
