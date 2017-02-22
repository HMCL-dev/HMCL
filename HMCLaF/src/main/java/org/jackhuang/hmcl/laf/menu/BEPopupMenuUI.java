/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BEPopupMenuUI.java at 2015-2-1 20:25:36, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.menu;

import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicPopupMenuUI;

/**
 * BeautyEye L&F的弹出菜单主类实现类.
 *
 * 兼容性说明：WindowsPopupMenuUI类在java1.6.0_u10（可能不仅限于1.6.0_u10，但java1.6.0中不存在这个问题）
 * 中会判断，如果是在Vista或者说xp平台时(测试证明是在win7平台会有这些问题)会用windows系
 * 统实现进行背景填充，而在1.6.0_u18（可能不限于此版本中）取消了这样的判断，改为直接继续
 * BasicPopupMenuUI的实默认实现。因此而带来的差异使得BE LNF在不同的java版本里（u10）会
 * 错误地使用windows系统实现.本类的目的就是要去掉这种差异，仍然交由父类BasicPopupMenuUI 实现即可（类似的官方Metal
 * LNF也是采用了BeautyEye一样的处理逻辑）.
 *
 * @author Jack Jiang(jb2011@163.com), 2012-09-14
 * @version 1.0
 * @since 3.1
 *
 * @see com.sun.java.swing.plaf.windows.WindowsPopupMenuUI
 */
public class BEPopupMenuUI extends BasicPopupMenuUI {

    public static ComponentUI createUI(JComponent c) {
        return new BEPopupMenuUI();
    }
}
