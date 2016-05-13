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
 * DeferredWizardResult.java
 *
 * Created on September 24, 2006, 3:42 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.util.ui.wizard.spi;

import java.util.Map;

/**
 * Object which can be returned from
 * <code>WizardPage.WizardResultProducer.finish()</code>
 * or <code>WizardPanelProvider.finish()</code>. A DeferredWizardResult does
 * not immediately calculate its result; it is used for cases where some
 * time consuming work needs to be performed to compute the result (such as
 * creating files on disk), and a progress bar should be shown until the work
 * is completed.
 *
 * @see org.jackhuang.hellominecraft.util.ui.wizard.spi.ResultProgressHandle
 *
 * @author Tim Boudreau
 */
public abstract class DeferredWizardResult {

    private final boolean canAbort;

    /**
     * Creates a new instance of DeferredWizardResult which cannot be
     * aborted and shows a progress bar.
     */
    public DeferredWizardResult() {
        canAbort = false;
    }

    /**
     * Creates a new instance of DeferredWizardResult which may or may not
     * be able to be aborted.
     *
     * @param canAbort determine if background computation can be aborted by
     *                 calling the <code>abort()</code> method
     */
    public DeferredWizardResult(boolean canAbort) {
        this.canAbort = canAbort;
    }

    /**
     * Begin computing the result. This method is called on a background
     * thread, not the AWT event thread, and computation can immediately begin.
     * Use the progress handle to set progress as the work progresses.
     *
     * IMPORTANT: This method MUST call either progress.finished with the
     * result,
     * or progress.failed with an error message. If this method returns without
     * calling either of those methods, it will be assumed to have failed.
     *
     * @param settings The settings gathered over the course of the wizard
     * @param progress A handle which can be used to affect the progress bar.
     */
    public abstract void start(Map settings, ResultProgressHandle progress);

    /**
     * If true, the background thread can be aborted. If it is possible to
     * abort, then the UI may allow the dialog to be closed while the result
     * is being computed.
     */
    public final boolean canAbort() {
        return canAbort;
    }

    /**
     * Abort computation of the result. This method will usually be called on
     * the event thread, after <code>start()<code> has been called, and before
     * <code>finished()</code> has been called on the
     * <code>ResultProgressHandle</code>
     * that is passed to <code>start()</code>, for example, if the user clicks
     * the close button on the dialog showing the wizard while the result is
     * being computed.
     * <p>
     * <b>This method does <i>nothing</i> by default</b> - it is left empty so
     * that people who do not want to support aborting background work do not
     * have to override it. It is up to the implementor
     * to set a flag or otherwise notify the background thread to halt
     * computation. A simple method for doing so is as follows:
     * <pre>
     * volatile Thread thread;
     * public void start (Map settings, ResultProgressHandle handle) {
     * try {
     *  synchronized (this) {
     *     thread = Thread.currentThread();
     *  }
     *
     *  //do the background computation, update progress.  Every so often,
     *  //check Thread.interrupted() and exit if true
     * } finally {
     *    synchronized (this) {
     *       thread = null;
     *    }
     *  }
     * }
     *
     * public synchronized void abort() {
     *  if (thread != null) thread.interrupt();
     * }
     * </pre>
     * or you can use a <code>volatile boolean</code> flag that you set in
     * <code>abort()</code> and periodically check in the body of
     * <code>start()</code>.
     *
     */
    public void abort() {
        //do nothing
    }
}
