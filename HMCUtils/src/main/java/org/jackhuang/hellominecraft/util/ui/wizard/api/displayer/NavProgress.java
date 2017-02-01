package org.jackhuang.hellominecraft.util.ui.wizard.api.displayer;

import java.awt.Container;
import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.border.EmptyBorder;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.log.HMCLog;
import org.jackhuang.hellominecraft.util.ui.wizard.spi.ResultProgressHandle;
import org.jackhuang.hellominecraft.util.ui.wizard.spi.Summary;

/**
 * Show progress bar for deferred results, with a label showing percent done and
 * progress bar.
 *
 * <p>
 * <b><i><font color="red">This class is NOT AN API CLASS. There is no
 * commitment that it will remain backward compatible or even exist in the
 * future. The API of this library is in the packages
 * <code>org.netbeans.api.wizard</code>
 * and <code>org.netbeans.spi.wizard</code></font></i></b>.
 *
 * @author stanley@stanleyknutson.com
 */
public class NavProgress implements ResultProgressHandle {

    JProgressBar progressBar = new JProgressBar();

    JLabel lbl = new JLabel();

    WizardDisplayerImpl parent;

    String failMessage = null;

    Container ipanel = null;

    boolean isInitialized = false;

    /**
     * isRunning is true until finished or failed is called
     */
    boolean isRunning = true;

    NavProgress(WizardDisplayerImpl impl) {
        this.parent = impl;
    }

    @Override
    public void addProgressComponents(Container panel) {
        panel.add(lbl);
        panel.add(progressBar);
        isInitialized = true;
        ipanel = panel;
    }

    @Override
    public void setProgress(final String description, final int currentStep, final int totalSteps) {
        invoke(() -> {
            lbl.setText(description == null ? " " : description);
            setProgress(currentStep, totalSteps);
        });
    }

    @Override
    public void setProgress(final int currentStep, final int totalSteps) {
        invoke(() -> {
            if (totalSteps == -1) {
                if (!progressBar.isIndeterminate())
                    progressBar.setIndeterminate(true);
            } else {
                if (currentStep > totalSteps || currentStep < 0) {
                    if (currentStep == -1 && totalSteps == -1)
                        return;
                    throw new IllegalArgumentException("Bad step values: "
                                                       + currentStep + " out of " + totalSteps);
                }
                if (progressBar.isIndeterminate())
                    progressBar.setIndeterminate(false);
                progressBar.setMaximum(totalSteps);
                progressBar.setValue(currentStep);
            }
        });
    }

    @Override
    public void setBusy(final String description) {
        invoke(() -> {
            lbl.setText(description == null ? " " : description);
            if (!progressBar.isIndeterminate())
                progressBar.setIndeterminate(true);
        });
    }

    private void invoke(Runnable r) {
        if (EventQueue.isDispatchThread())
            r.run();
        else
            try {
                EventQueue.invokeAndWait(r);
            } catch (InvocationTargetException | InterruptedException ex) {
                HMCLog.err("NavProgress: Error invoking operation", ex);
            }
    }

    @Override
    public void finished(final Object o) {
        isRunning = false;
        Runnable r = () -> {
            if (o instanceof Summary) {
                Summary summary = (Summary) o;
                parent.handleSummary(summary);
                parent.setWizardResult(summary.getResult());
            } else if (parent.getDeferredResult() != null) {
                parent.setWizardResult(o);

                // handle result based on which button was pushed
                parent.getButtonManager().deferredResultFinished(o);
            }
        };
        invoke(r);
    }

    @Override
    public void failed(final String message, final boolean canGoBack) {
        failMessage = message;
        isRunning = false;

        Runnable r = () -> {
            // cheap word wrap
            JLabel comp = new JLabel("<html><body>" + message);
            comp.setBorder(new EmptyBorder(5, 5, 5, 5));
            parent.setCurrentWizardPanel(comp);
            parent.getTtlLabel().setText(C.i18n("wizard.failed"));
            NavButtonManager bm = parent.getButtonManager();
            bm.deferredResultFailed(canGoBack);
        };
        invoke(r);
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }
}
