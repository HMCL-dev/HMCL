/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.download;

import java.util.ArrayList;
import org.jackhuang.hellominecraftlauncher.apis.DoneListener;
import org.jackhuang.hellominecraftlauncher.apis.HMCLLog;
import org.jackhuang.hellominecraftlauncher.apis.utils.NetUtils;

/**
 *
 * @author hyh
 */
public class URLGetThread extends Thread {

    String url, encoding;
    ArrayList<DoneListener<String, Object>> tdtsl;

    public URLGetThread(String url) {
        this(url, "UTF-8");
    }
    
    public URLGetThread(String url, String encoding) {
        super();
        this.url = url;
        this.encoding = encoding;
        this.tdtsl = new ArrayList<DoneListener<String, Object>>();
    }

    public void addListener(DoneListener<String, Object> l) {
        tdtsl.add(l);
    }

    @Override
    public void run() {
        try {
            String result = NetUtils.doGet(url, encoding);
            for (DoneListener<String, Object> l : tdtsl) {
                if(l != null)
                    l.onDone(result, null);
            }
        } catch (Exception ex) {
            HMCLLog.err("Failed to get " + url, ex);
        }
    }
}