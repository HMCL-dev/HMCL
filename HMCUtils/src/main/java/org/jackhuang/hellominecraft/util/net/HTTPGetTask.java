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
package org.jackhuang.hellominecraft.util.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.Charset;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.log.HMCLog;
import org.jackhuang.hellominecraft.util.task.comm.PreviousResult;
import org.jackhuang.hellominecraft.api.EventHandler;
import org.jackhuang.hellominecraft.api.SimpleEvent;
import org.jackhuang.hellominecraft.util.code.Charsets;
import org.jackhuang.hellominecraft.util.task.Task;

/**
 *
 * @author huangyuhui
 */
public class HTTPGetTask extends Task implements PreviousResult<String> {

    String url, result;
    Charset encoding;
    EventHandler<SimpleEvent<String>> doneEvent = new EventHandler<>();
    boolean shouldContinue = true;

    public HTTPGetTask(String url) {
        this(url, Charsets.UTF_8);
    }

    public HTTPGetTask(String url, Charset encoding) {
        this.url = url;
        this.encoding = encoding;
    }

    @Override
    public void executeTask(boolean areDependTasksSucceeded) throws Exception {
        Exception t = null;
        for (int time = 0; time < 6; time++) {
            if (time > 0)
                HMCLog.warn("Failed to download, repeat times: " + time);
            try {
                if (ppl != null)
                    ppl.setProgress(this, -1, 1);
                HttpURLConnection con = NetUtils.createConnection(new URL(url), Proxy.NO_PROXY);
                
                InputStream is = con.getInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int size = con.getContentLength(), read = 0, len;
                long lastTime = System.currentTimeMillis();
                while ((len = is.read(buf)) != -1) {
                    baos.write(buf, 0, len);
                    read += len;
                    
                    // Update progress information per second
                    long now = System.currentTimeMillis();
                    if (ppl != null && (now - lastTime) >= 1000) {
                        ppl.setProgress(this, read, size);
                        lastTime = now;
                    }
                    if (!shouldContinue)
                        return;
                }
                result = baos.toString(encoding.name());
                doneEvent.fire(new SimpleEvent<>(this, result));
                return;
            } catch (IOException ex) {
                t = new IOException("Failed to get " + url, ex);
            }
        }
        if (t != null)
            throw t;
    }

    @Override
    public boolean abort() {
        shouldContinue = false;
        return aborted = true;
    }

    @Override
    public String getInfo() {
        return C.i18n("download") + ": " + (tag == null ? url : tag);
    }

    @Override
    public String getResult() {
        return result;
    }

}
