/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.ui;

import javax.swing.JTextPane;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BoxView;
import javax.swing.text.ComponentView;
import javax.swing.text.Element;
import javax.swing.text.IconView;
import javax.swing.text.LabelView;
import javax.swing.text.ParagraphView;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;

/**
 *
 * @author huang
 */
public class JLineWrapTextPane extends JTextPane {
  
    // 内部类  
    // 以下内部类全都用于实现自动强制折行  
  
    private class WarpEditorKit extends StyledEditorKit {  
  
        private ViewFactory defaultFactory = new WarpColumnFactory();  
  
        @Override  
        public ViewFactory getViewFactory() {  
            return defaultFactory;  
        }  
    }  
  
    private class WarpColumnFactory implements ViewFactory {  
  
        public View create(Element elem) {  
            String kind = elem.getName();  
            if (kind != null) {  
                if (kind.equals(AbstractDocument.ContentElementName)) {  
                    return new WarpLabelView(elem);  
                } else if (kind.equals(AbstractDocument.ParagraphElementName)) {  
                    return new ParagraphView(elem);  
                } else if (kind.equals(AbstractDocument.SectionElementName)) {  
                    return new BoxView(elem, View.Y_AXIS);  
                } else if (kind.equals(StyleConstants.ComponentElementName)) {  
                    return new ComponentView(elem);  
                } else if (kind.equals(StyleConstants.IconElementName)) {  
                    return new IconView(elem);  
                }  
            }  
  
            // default to text display  
            return new LabelView(elem);  
        }  
    }  
  
    private class WarpLabelView extends LabelView {  
  
        public WarpLabelView(Element elem) {  
            super(elem);  
        }  
  
        @Override  
        public float getMinimumSpan(int axis) {  
            switch (axis) {  
                case View.X_AXIS:  
                    return 0;  
                case View.Y_AXIS:  
                    return super.getMinimumSpan(axis);  
                default:  
                    throw new IllegalArgumentException("Invalid axis: " + axis);  
            }  
        }  
    }  
  
    // 本类  
  
    // 构造函数  
    public JLineWrapTextPane() {  
        super();  
        this.setEditorKit(new WarpEditorKit());  
    }  
}  
