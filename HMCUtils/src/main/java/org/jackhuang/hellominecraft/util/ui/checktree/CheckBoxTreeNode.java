/*
 * Hello Minecraft!.
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
package org.jackhuang.hellominecraft.util.ui.checktree;

import javax.swing.tree.DefaultMutableTreeNode;
import org.jackhuang.hellominecraft.util.Pair;

/**
 *
 * @author huangyuhui
 */
public class CheckBoxTreeNode extends DefaultMutableTreeNode {

    protected boolean isSelected;

    public CheckBoxTreeNode() {
        this(null);
    }

    public CheckBoxTreeNode(Object userObject) {
        this(userObject, true, false);
    }

    public CheckBoxTreeNode(Object userObject, boolean allowsChildren, boolean isSelected) {
        super(userObject, allowsChildren);
        this.isSelected = isSelected;
    }

    @Override
    public String toString() {
        if (userObject instanceof Pair)
            return "<html>" + ((Pair<?, ?>) userObject).key + "<font color=gray>&nbsp;-&nbsp;" + ((Pair<?, ?>) userObject).value + "</font></html>";
        else
            return userObject.toString();
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean _isSelected) {
        this.isSelected = _isSelected;

        if (_isSelected) {
            // 如果选中，则将其所有的子结点都选中
            if (children != null)
                for (Object obj : children) {
                    CheckBoxTreeNode node = (CheckBoxTreeNode) obj;
                    if (_isSelected != node.isSelected())
                        node.setSelected(_isSelected);
                }
            // 向上检查，如果父结点的所有子结点都被选中，那么将父结点也选中
            CheckBoxTreeNode pNode = (CheckBoxTreeNode) parent;
            // 开始检查pNode的所有子节点是否都被选中
            if (pNode != null) {
                int index = 0;
                for (; index < pNode.children.size(); ++index) {
                    CheckBoxTreeNode pChildNode = (CheckBoxTreeNode) pNode.children.get(index);
                    if (!pChildNode.isSelected())
                        break;
                }
                /*
                 * 表明pNode所有子结点都已经选中，则选中父结点，
                 * 该方法是一个递归方法，因此在此不需要进行迭代，因为
                 * 当选中父结点后，父结点本身会向上检查的。
                 */
                if (index == pNode.children.size())
                    if (pNode.isSelected() != _isSelected)
                        pNode.setSelected(_isSelected);
            }
        } else {
            /*
             * 如果是取消父结点导致子结点取消，那么此时所有的子结点都应该是选择上的；
             * 否则就是子结点取消导致父结点取消，然后父结点取消导致需要取消子结点，但
             * 是这时候是不需要取消子结点的。
             */
            if (children != null) {
                int index = 0;
                for (; index < children.size(); ++index) {
                    CheckBoxTreeNode childNode = (CheckBoxTreeNode) children.get(index);
                    if (!childNode.isSelected())
                        break;
                }
                // 从上向下取消的时候
                if (index == children.size())
                    for (int i = 0; i < children.size(); ++i) {
                        CheckBoxTreeNode node = (CheckBoxTreeNode) children.get(i);
                        if (node.isSelected() != _isSelected)
                            node.setSelected(_isSelected);
                    }
            }

            // 向上取消，只要存在一个子节点不是选上的，那么父节点就不应该被选上。
            CheckBoxTreeNode pNode = (CheckBoxTreeNode) parent;
            if (pNode != null && pNode.isSelected() != _isSelected)
                pNode.setSelected(_isSelected);
        }
    }
}
