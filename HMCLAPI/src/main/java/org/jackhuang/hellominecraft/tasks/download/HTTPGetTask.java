/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.tasks.download;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.tasks.TaskInfo;
import org.jackhuang.hellominecraft.tasks.communication.PreviousResult;
import org.jackhuang.hellominecraft.utils.EventHandler;

/**
 *
 * @author hyh
 */
public class HTTPGetTask extends TaskInfo implements PreviousResult<String> {

    String url, encoding, result;
    EventHandler<String> tdtsl = new EventHandler<>(this);
    boolean shouldContinue = true;

    public HTTPGetTask(String url) {
        this(null, url);
    }

    public HTTPGetTask(String info, String url) {
        this(info, url, "UTF-8");
    }

    public HTTPGetTask(String info, String url, String encoding) {
        super(info);
        this.url = url;
        this.encoding = encoding;
    }

    @Override
    public boolean executeTask() {
        for (int repeat = 0; repeat < 6; repeat++) {
            if (repeat > 0) HMCLog.warn("Failed to download, repeat: " + repeat);
            try {
                URLConnection conn = new URL(url).openConnection();
                InputStream is = conn.getInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int i;
                int size = conn.getContentLength(), read = 0;
                while ((i = is.read()) != -1) {
                    baos.write(i);
                    if (ppl != null) {
                        ppl.setProgress(++read, size);
                    }
                    if (!shouldContinue) {
                        return true;
                    }
                }
                result = baos.toString();
                tdtsl.execute(result);
                return true;
            } catch (Exception ex) {
                setFailReason(new NetException("Failed to get " + url, ex));
            }
        }
        return false;
    }

    @Override
    public boolean abort() {
        shouldContinue = false;
        return true;
    }

    @Override
    public String getInfo() {
        return super.getInfo() != null ? super.getInfo() : "Get: " + url;
    }

    @Override
    public String getResult() {
        return result;
    }

}
