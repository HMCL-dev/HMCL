package org.jackhuang.hellominecraft.util.ui.wizard.spi;

import java.awt.Container;

/**
 * A controller for the progress bar shown in the user interface.  Used in
 * conjunction with <code>DeferredWizardResult</code> for cases where at
 * the conclusion of the wizard, the work to create the final wizard result
 * will take a while and needs to happen on a background thread.
 * @author Tim Boudreau
 */ 
public interface ResultProgressHandle {
    
    /**
     * Set the current position and total number of steps.  Note it is
     * inadvisable to be holding any locks when calling this method, as it
     * may immediately update the GUI using 
     * <code>EventQueue.invokeAndWait()</code>.
     * 
     * @param currentStep the current step in the progress of computing the
     * result.
     * @param totalSteps the total number of steps.  Must be greater than
     *  or equal to currentStep.
     */
    public abstract void setProgress (int currentStep, int totalSteps);
    
    /** 
     * Set the current position and total number of steps, and description 
     * of what the computation is doing.  Note it is
     * inadvisable to be holding any locks when calling this method, as it
     * may immediately update the GUI using 
     * <code>EventQueue.invokeAndWait()</code>.         
     * @param description Text to describe what is being done, which can
     *  be displayed in the UI.
     * @param currentStep the current step in the progress of computing the
     *  result.
     * @param totalSteps the total number of steps.  Must be greater than
     *  or equal to currentStep.
     */
    public abstract void setProgress (String description, int currentStep, int totalSteps);
    
    /**
     * Set the status as "busy" - a rotating icon will be displayed instead
     * of a percent complete progress bar.
     * 
     * Note it is inadvisable to be holding any locks when calling this method, as it
     * may immediately update the GUI using 
     * <code>EventQueue.invokeAndWait()</code>.         
     * @param description Text to describe what is being done, which can
     *  be displayed in the UI.
     */
    public abstract void setBusy (String description);
    
    /**
     * Call this method when the computation is complete, and pass in the 
     * final result of the computation.  The method doing the computation
     * (<code>DeferredWizardResult.start()</code> or something it 
     * called) should exit immediately after calling this method.  If the
     * <code>failed()</code> method is called after this method has been
     * called, a runtime exception may be thrown.
     * @param result the Object which was computed, if any.
     */ 
    public abstract void finished(Object result);
    /**
     * Call this method if computation fails.  The message may be some text
     * describing what went wrong, or null if no description.
     * @param message The text to display to the user. The method 
     * doing the computation (<code>DeferredWizardResult.start()</code> or something it 
     * called).  If the <code>finished()</code> method is called after this
     * method has been called, a runtime exception may be thrown.
     * should exit immediately after calling this method.
     * It is A description of what went wrong, or null.
     * @param canNavigateBack whether or not the Prev button should be 
     *  enabled.
     */ 
    public abstract void failed (String message, boolean canNavigateBack);
    
    /**
     * Add the component to show for the progress display to the instructions panel. 
     */
    public abstract void addProgressComponents (Container panel);
    
    /**
     * Returns true if the computation is still running, i.e., if neither finished or failed have been called.
     *
     * @return true if there is no result yet.
     */
    public boolean isRunning();
}