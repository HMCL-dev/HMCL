/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.tasks.download;

import org.jackhuang.hellominecraft.utils.functions.Consumer;
import org.jackhuang.hellominecraft.utils.Event;
import org.jackhuang.hellominecraft.views.LogWindow;

/**
 *
 * @author hyh
 */
public class ContentGetAndShowTask extends HTTPGetTask implements Event<String> {

    public ContentGetAndShowTask(String info, String changeLogUrl) {
        super(changeLogUrl);
        this.info = info;
    }

    @Override
    public boolean executeTask() {
        tdtsl.register(this);
        return super.executeTask();
    }

    String info;
    
    @Override
    public String getInfo() {
        return info;
    }

    @Override
    public boolean call(Object sender, String value) {
        LogWindow.instance.clean();
        LogWindow.instance.log(value);
        LogWindow.instance.setVisible(true);
        return true;
    }
}
