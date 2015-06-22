/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.installers.optifine.vanilla;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jackhuang.hellominecraft.tasks.Task;
import org.jackhuang.hellominecraft.tasks.communication.PreviousResult;
import org.jackhuang.hellominecraft.utils.NetUtils;

/**
 *
 * @author hyh
 */
public class OptiFineDownloadFormatter extends Task implements PreviousResult<String> {
    String url, result;
    
    public OptiFineDownloadFormatter(String url) {
        this.url = url;
    }

    @Override
    public boolean executeTask() {
        try {
            String content = NetUtils.doGet(url);
            Pattern p = Pattern.compile("\"downloadx\\?f=OptiFine(.*)\"");
            Matcher m = p.matcher(content);
            while(m.find()) result = m.group(1);
            result = "http://optifine.net/downloadx?f=OptiFine" + result;
            return true;
        } catch (Exception ex) {
            setFailReason(ex);
            return false;
        }
    }

    @Override
    public String getInfo() {
        return "Get OptiFine Download Link.";
    }

    @Override
    public String getResult() {
        return result;
    }
}
