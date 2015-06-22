/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.utils;

import java.io.OutputStream;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.jackhuang.hellominecraft.HMCLog;

/**
 *
 * @author hyh
 */
public class TextComponentOutputStream extends OutputStream {

    private JTextComponent txt;

    public TextComponentOutputStream(JTextComponent paramJTextComponent) {
        txt = paramJTextComponent;
    }

    @Override
    public final void write(byte[] paramArrayOfByte) {
        write(paramArrayOfByte, 0, paramArrayOfByte.length);
    }

    @Override
    public final void write(byte[] paramArrayOfByte, int off, int len) {
        append(new String(paramArrayOfByte, off, len));
    }

    private void append(final String newString) {
        try {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    String t = txt.getText() + newString.replace("\t", "    ");
                    txt.setText(t);
                    txt.setCaretPosition(t.length());
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public final void write(int paramInt) {
        append(new String(new byte[]{(byte) paramInt}));
    }
}
