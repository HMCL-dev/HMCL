package org.jackhuang.hellominecraft.utils.views.wizard.api.displayer;

import java.awt.Container;
import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.logging.Logger;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.border.EmptyBorder;
import org.jackhuang.hellominecraft.utils.C;

import org.jackhuang.hellominecraft.utils.views.wizard.spi.ResultProgressHandle;
import org.jackhuang.hellominecraft.utils.views.wizard.spi.Summary;

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

    private static final Logger logger
                                = Logger.getLogger(NavProgress.class.getName());

    JProgressBar progressBar = new JProgressBar();

    JLabel lbl = new JLabel();

    JLabel busy = new JLabel();

    WizardDisplayerImpl parent;

    String failMessage = null;

    boolean isUseBusy = false;

    Container ipanel = null;

    boolean isInitialized = false;

    /**
     * isRunning is true until finished or failed is called
     */
    boolean isRunning = true;

    NavProgress(WizardDisplayerImpl impl, boolean useBusy) {
        this.parent = impl;
        isUseBusy = useBusy;
    }

    public void addProgressComponents(Container panel) {
        panel.add(lbl);
        if (isUseBusy) {
            ensureBusyInitialized();
            panel.add(busy);
        } else
            panel.add(progressBar);
        isInitialized = true;
        ipanel = panel;
    }

    public void setProgress(final String description, final int currentStep, final int totalSteps) {
        invoke(() -> {
            lbl.setText(description == null ? " " : description); // NOI18N
            setProgress(currentStep, totalSteps);
        });
    }

    public void setProgress(final int currentStep, final int totalSteps) {
        invoke(() -> {
            if (totalSteps == -1)
                progressBar.setIndeterminate(true);
            else {
                if (currentStep > totalSteps || currentStep < 0) {
                    if (currentStep == -1 && totalSteps == -1)
                        return;
                    throw new IllegalArgumentException("Bad step values: "
                                                       + currentStep + " out of " + totalSteps);
                }
                progressBar.setIndeterminate(false);
                progressBar.setMaximum(totalSteps);
                progressBar.setValue(currentStep);
            }

            setUseBusy(false);
        });
    }

    public void setBusy(final String description) {
        invoke(() -> {
            lbl.setText(description == null ? " " : description);

            progressBar.setIndeterminate(true);

            setUseBusy(true);
        });
    }

    protected void setUseBusy(boolean useBusy) {
        if (isInitialized)
            if (useBusy && (!isUseBusy)) {
                ipanel.remove(progressBar);
                ensureBusyInitialized();
                ipanel.add(busy);
                ipanel.invalidate();
            } else if (!useBusy && isUseBusy) {
                ipanel.remove(busy);
                ipanel.add(progressBar);
                ipanel.invalidate();
            }
        isUseBusy = useBusy;
    }

    private void ensureBusyInitialized() {
        if (busy.getIcon() == null) {
            URL url = getClass().getResource("/org/jackhuang/hellominecraft/busy.gif");
            Icon icon = new ImageIcon(url);
            busy.setIcon(icon);
        }
    }

    private void invoke(Runnable r) {
        if (EventQueue.isDispatchThread())
            r.run();
        else
            try {
                EventQueue.invokeAndWait(r);
            } catch (InvocationTargetException | InterruptedException ex) {
                ex.printStackTrace();
                logger.severe("Error invoking operation " + ex.getClass().getName() + " " + ex.getMessage());
            }
    }

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

    public void failed(final String message, final boolean canGoBack) {
        failMessage = message;
        isRunning = false;

        Runnable r = () -> {
            // cheap word wrap
            JLabel comp = new JLabel("<html><body>" + message); // NOI18N
            comp.setBorder(new EmptyBorder(5, 5, 5, 5));
            parent.setCurrentWizardPanel(comp);
            parent.getTtlLabel().setText(C.i18n("wizard.failed"));
            NavButtonManager bm = parent.getButtonManager();
            bm.deferredResultFailed(canGoBack);
        };
        invoke(r);
    }

    public boolean isRunning() {
        return isRunning;
    }
}
