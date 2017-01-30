/*
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the License). You may not use this file except in
 * compliance with the License.
 * You can obtain a copy of the License at http://www.netbeans.org/cddl.html
 * or http://www.netbeans.org/cddl.txt.
 * When distributing Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://www.netbeans.org/cddl.txt.
 * If applicable, add the following below the CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */
 /*
 * InstructionsPanel.java
 *
 * Created on March 4, 2005, 8:59 PM
 */
package org.jackhuang.hellominecraft.util.ui.wizard.modules;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.IllegalComponentStateException;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Locale;
import java.util.Arrays;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.accessibility.AccessibleText;
import javax.imageio.ImageIO;
import javax.swing.CellRendererPane;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.UIManager;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.log.HMCLog;
import org.jackhuang.hellominecraft.util.ui.wizard.api.displayer.InstructionsPanel;
import org.jackhuang.hellominecraft.util.ui.wizard.spi.Wizard;
import org.jackhuang.hellominecraft.util.ui.wizard.spi.WizardObserver;

/**
 * A panel that displays a background image and optionally instructions from a
 * wizard, tracking the selected panel and showing that in bold.
 * <br>
 * <b><i><font color="red">This class is NOT AN API CLASS. There is no
 * commitment that it will remain backward compatible or even exist in the
 * future. The API of this library is in the packages
 * <code>org.netbeans.api.wizard</code> and
 * <code>org.netbeans.spi.wizard</code></font></i></b>.
 * <br>
 * There is currently a single use-case for subclassing this - a navigation
 * panel that wants to display a different image for each step.
 *
 * @author Tim Boudreau
 */
public class InstructionsPanelImpl extends JComponent implements WizardObserver, Accessible, InstructionsPanel {

    private transient final BufferedImage img;
    private transient final Wizard wizard;
    private static final int MARGIN = 5;

    public InstructionsPanelImpl(Wizard wiz) {
        this(null, wiz);
        Font f = UIManager.getFont("Tree.font");
        if (f != null)
            setFont(f);
    }

    /**
     * Get the wizard this panel is monitoring.
     *
     * @return
     */
    protected final Wizard getWizard() {
        return wizard;
    }

    @Override
    public final Container getComponent() {
        return this;
    }

    /**
     * Overridden to start listening to the wizard when added to a container
     */
    @Override
    public void addNotify() {
        super.addNotify();
        wizard.addWizardObserver(this);
    }

    /**
     * Overridden to stop listening to the wizard when removed from a container
     */
    @Override
    public void removeNotify() {
        wizard.removeWizardObserver(this);
        super.removeNotify();
    }

    /**
     * Get the image to be displayed. Note that unpredictable behavior may
     * result if all images returned from this method are not the same size.
     * Override to display a different wizard depending on the step.
     *
     * @return
     */
    protected BufferedImage getImage() { //for unit test
        return img;
    }

    public InstructionsPanelImpl(BufferedImage img, Wizard wizard) {
        if (img == null)
            try {
                img = ImageIO.read(InstructionsPanelImpl.class.getResourceAsStream(
                        "/org/jackhuang/hellominecraft/wizard.jpg"));
            } catch (IOException ioe) {
                HMCLog.err("Failed to load wizard.jpg, maybe you fucking modified the launcher", ioe);
            }
        this.img = img;
        this.wizard = wizard;
    }

    @Override
    public boolean isOpaque() {
        return img != null;
    }

    /**
     * Paints the background image for this component, or fills the background
     * with a color if no image present.
     *
     * @param g A Graphic2D to paint into
     * @param x The x coordinate of the area that should contain the image
     * @param y The y coordinate of the area that should contain the image
     * @param w The width of the area that should contain the image
     * @param h The height of the area that should contain the image
     */
    protected void paintImage(Graphics2D g, int x, int y, int w, int h) {
        BufferedImage image = getImage();
        if (image != null)
            g.drawImage(image, x, y, w, h, this);
        else {
            Color c = g.getColor();
            g.setColor(Color.WHITE);
            g.fillRect(x, y, w, h);
            g.setColor(c);
        }
    }

    String[] steps = new String[0];

    @Override
    public final void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Font f = getFont() != null ? getFont() : UIManager.getFont("controlFont");
        FontMetrics fm = g.getFontMetrics(f);
        Insets ins = getInsets();
        int dx = ins.left;
        int dy = ins.top;
        int w = getWidth() - (ins.left + ins.right);
        int hh = getHeight() - (ins.top + ins.bottom);
        paintImage(g2d, dx, dy, w, hh);
        String currentStep = wizard.getCurrentStep();
        if (!inSummaryPage)
            //Don't fetch step list if in summary page, there will
            //only be the base ones
            steps = wizard.getAllSteps();
        String[] steps2 = this.steps;
        if (inSummaryPage) {
            String summaryStep = C.i18n("wizard.summary");
            String[] nue = new String[steps2.length + 1];
            System.arraycopy(steps2, 0, nue, 0, steps2.length);
            nue[nue.length - 1] = summaryStep;
            steps2 = nue;
        }
        int y = fm.getMaxAscent() + ins.top + MARGIN;
        int x = ins.left + MARGIN;
        int h = fm.getMaxAscent() + fm.getMaxDescent() + 3;

        Font boldFont = f.deriveFont(Font.BOLD);
        g.setFont(boldFont);
        g.drawString(C.i18n("wizard.steps"), x, y);

        int underlineY = ins.top + MARGIN + fm.getAscent() + 3;
        g.drawLine(x, underlineY, x + (getWidth() - (x + ins.left + MARGIN)),
                underlineY);

        int bottom = getComponentCount() == 0 ? getHeight() - getInsets().bottom
                : getHeight() - getInsets().bottom - getComponents()[0].getPreferredSize().height;

        y += h + 10;
        int first = 0;
        int stop = steps2.length;
        boolean wontFit = y + (h * (steps2.length)) > getHeight();
        if (wontFit) {
            //try to center the current step
            int availHeight = bottom - y;
            int willFit = availHeight / h;
            int currStepIndex = Arrays.asList(steps2).indexOf(currentStep);
            int rangeStart = Math.max(0, currStepIndex - (willFit / 2));
            int rangeEnd = Math.min(rangeStart + willFit, steps2.length);
            if (rangeStart + willFit > steps2.length) {
                //Don't scroll off if there's room
                rangeStart = steps2.length - willFit;
                rangeEnd = steps2.length;
            }
            steps2 = (String[]) steps2.clone();
            if (rangeStart != 0) {
                steps2[rangeStart] = elipsis;
                first = rangeStart;
            }
            if (rangeEnd != steps2.length && rangeEnd > 0) {
                steps2[rangeEnd - 1] = elipsis;
                stop = rangeEnd;
            }
        }

        g.setFont(getFont());
        g.setColor(getForeground());

        for (int i = first; i < stop; i++) {
            boolean isUndetermined = Wizard.UNDETERMINED_STEP.equals(steps2[i]);
            boolean canOnlyFinish = wizard.getForwardNavigationMode()
                    == Wizard.MODE_CAN_FINISH;
            if (isUndetermined && canOnlyFinish)
                break;
            String curr;
            if (!elipsis.equals(steps2[i]))
                if (inSummaryPage && i == this.steps.length)
                    curr = (i + 1) + ". " + steps2[i];
                else
                    curr = (i + 1) + ". " + (isUndetermined
                            ? elipsis
                            : steps2[i].equals(elipsis) ? elipsis
                            : wizard.getStepDescription(steps2[i]));
            else
                curr = elipsis;
            boolean selected = (steps2[i].equals(currentStep) && !inSummaryPage)
                    || (inSummaryPage && i == steps2.length - 1);
            if (selected)
                g.setFont(boldFont);

            int width = fm.stringWidth(curr);
            while (width > getWidth() - (ins.left + ins.right) && curr.length() > 5)
                curr = curr.substring(0, curr.length() - 5) + elipsis;

            g.drawString(curr, x, y);
            if (selected)
                g.setFont(f);
            y += h;
        }
    }

    private int historicWidth = Integer.MIN_VALUE;

    private static final String elipsis = "...";

    @Override
    public final Dimension getPreferredSize() {
        Font f = getFont() != null ? getFont()
                : UIManager.getFont("controlFont");

        Graphics g = getGraphics();
        if (g == null)
            g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).getGraphics();
        f = f.deriveFont(Font.BOLD);
        FontMetrics fm = g.getFontMetrics(f);
        Insets ins = getInsets();
        int h = fm.getHeight();

        String[] steps2 = wizard.getAllSteps();
        int w = Integer.MIN_VALUE;
        for (int i = 0; i < steps2.length; i++) {
            String desc = i + ". " + (Wizard.UNDETERMINED_STEP.equals(steps2[i])
                    ? elipsis
                    : wizard.getStepDescription(steps2[i]));
            w = Math.max(w, fm.stringWidth(desc) + MARGIN);
        }
        if (Integer.MIN_VALUE == w)
            w = 250;
        BufferedImage image = getImage();
        if (image != null)
            w = Math.max(w, image.getWidth());
        //Make sure we can grow but not shrink
        w = Math.max(w, historicWidth);
        historicWidth = w;
        return new Dimension(w, ins.top + ins.bottom + ((h + 3) * steps2.length));
    }

    private boolean inSummaryPage;

    @Override
    public void setInSummaryPage(boolean val) {
        this.inSummaryPage = val;
        repaint();
    }

    @Override
    public final Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public void stepsChanged(Wizard wizard) {
        repaint();
    }

    @Override
    public void navigabilityChanged(Wizard wizard) {
        //do nothing
    }

    @Override
    public void selectionChanged(Wizard wizard) {
        repaint();
    }

    @Override
    public final void doLayout() {
        Component[] c = getComponents();
        Insets ins = getInsets();
        int y = getHeight() - (MARGIN + ins.bottom);
        int x = MARGIN + ins.left;
        int w = getWidth() - ((MARGIN * 2) + ins.left + ins.right);
        if (w < 0)
            w = 0;
        for (int i = c.length - 1; i >= 0; i--) {
            Dimension d = c[i].getPreferredSize();
            c[i].setBounds(x, y - d.height, w, d.height);
            y -= d.height;
        }
    }

    @Override
    public final AccessibleContext getAccessibleContext() {
        return new ACI(this);
    }

    private static final class ACI extends AccessibleContext {

        private final Wizard wizard;
        private final InstructionsPanelImpl panel;

        public ACI(InstructionsPanelImpl pnl) {
            this.wizard = pnl.wizard;
            panel = pnl;
            if (pnl.getParent() instanceof Accessible)
                setAccessibleParent((Accessible) pnl.getParent());
        }

        JEditorPane pane;

        @Override
        public AccessibleText getAccessibleText() {
            if (pane == null) {
                //Cheat just a bit here - will do for now - the text is
                //there, more or less where it should be, and a screen
                //reader should be able to find it;  exact bounds don't
                //make much difference
                pane = new JEditorPane();
                pane.setBounds(panel.getBounds());
                pane.getAccessibleContext().getAccessibleText();
                pane.setFont(panel.getFont());
                CellRendererPane cell = new CellRendererPane();
                cell.add(pane);
            }
            pane.setText(getText());
            pane.selectAll();
            pane.validate();
            return pane.getAccessibleContext().getAccessibleText();
        }

        public String getText() {
            StringBuilder sb = new StringBuilder();
            String[] s = wizard.getAllSteps();
            for (String item : s)
                sb.append(wizard.getStepDescription(item)).append('\n');
            return sb.toString();
        }

        @Override
        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.LIST;
        }

        @Override
        public AccessibleStateSet getAccessibleStateSet() {
            AccessibleState[] states = new AccessibleState[]{
                AccessibleState.VISIBLE,
                AccessibleState.OPAQUE,
                AccessibleState.SHOWING,
                AccessibleState.MULTI_LINE,};
            return new AccessibleStateSet(states);
        }

        @Override
        public int getAccessibleIndexInParent() {
            return -1;
        }

        @Override
        public int getAccessibleChildrenCount() {
            return 0;
        }

        @Override
        public Accessible getAccessibleChild(int i) {
            throw new IndexOutOfBoundsException("" + i);
        }

        @Override
        public Locale getLocale() throws IllegalComponentStateException {
            return Locale.getDefault();
        }
    }
}
