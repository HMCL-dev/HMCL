/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui.wizard;

/**
 * A controller for the progress bar shown in the user interface.  Used in
 * conjunction then `DeferredWizardResult` for cases where at
 * the conclusion of the wizard, the work to create the final wizard result
 * will take a while and needs to happen on a background thread.
 * @author Tim Boudreau
 */
public interface ResultProgressHandle {

    /**
     * Set the current position and total number of steps.  Note it is
     * inadvisable to be holding any locks when calling this method, as it
     * may immediately update the GUI using
     * `EventQueue.invokeAndWait()`.
     *
     * @param currentStep the current step in the progress of computing the
     * * result.
     * *
     * @param totalSteps the total number of steps.  Must be greater than
     * *  or equal to currentStep.
     */
    void setProgress(int currentStep, int totalSteps);

    /**
     * Set the current position and total number of steps, and description
     * of what the computation is doing.  Note it is
     * inadvisable to be holding any locks when calling this method, as it
     * may immediately update the GUI using
     * `EventQueue.invokeAndWait()`.
     * @param description Text to describe what is being done, which can
     * *  be displayed in the UI.
     * *
     * @param currentStep the current step in the progress of computing the
     * *  result.
     * *
     * @param totalSteps the total number of steps.  Must be greater than
     * *  or equal to currentStep.
     */
    void setProgress(String description, int currentStep, int totalSteps);

    /**
     * Set the status as "busy" - a rotating icon will be displayed instead
     * of a percent complete progress bar.

     * Note it is inadvisable to be holding any locks when calling this method, as it
     * may immediately update the GUI using
     * `EventQueue.invokeAndWait()`.
     * @param description Text to describe what is being done, which can
     * *  be displayed in the UI.
     */
    void setBusy(String description);

    /**
     * Call this method when the computation is complete, and pass in the
     * final result of the computation.  The method doing the computation
     * (`DeferredWizardResult.start()` or something it
     * called) should exit immediately after calling this method.  If the
     * `failed()` method is called after this method has been
     * called, a runtime exception may be thrown.
     * @param result the Object which was computed, if any.
     */
    void finished(Object result);

    /**
     * Call this method if computation fails.  The message may be some text
     * describing what went wrong, or null if no description.
     * @param message The text to display to the user. The method
     * * doing the computation (`DeferredWizardResult.start()` or something it
     * * called).  If the `finished()` method is called after this
     * * method has been called, a runtime exception may be thrown.
     * * should exit immediately after calling this method.
     * * It is A description of what went wrong, or null.
     * *
     * @param canNavigateBack whether or not the Prev button should be
     * *  enabled.
     */
    void failed(String message, boolean canNavigateBack);

    /**
     * Returns true if the computation is still running, i.e., if neither finished or failed have been called.
     *
     * @return true if there is no result yet.
     */
    boolean isRunning();
}
