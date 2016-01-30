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
package org.jackhuang.hellominecraft.util.tasks.download;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import org.jackhuang.hellominecraft.util.logging.HMCLog;
import org.jackhuang.hellominecraft.util.tasks.TaskInfo;
import org.jackhuang.hellominecraft.util.tasks.communication.PreviousResult;
import org.jackhuang.hellominecraft.util.EventHandler;

/**
 *
 * @author huangyuhui
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
    public void executeTask() throws Exception {
        Exception t = null;
        for (int repeat = 0; repeat < 6; repeat++) {
            if (repeat > 0)
                HMCLog.warn("Failed to download, repeat: " + repeat);
            try {
                URLConnection conn = new URL(url).openConnection();
                InputStream is = conn.getInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int i;
                int size = conn.getContentLength(), read = 0;
                while ((i = is.read()) != -1) {
                    baos.write(i);
                    if (ppl != null)
                        ppl.setProgress(this, ++read, size);
                    if (!shouldContinue)
                        return;
                }
                result = baos.toString();
                tdtsl.execute(result);
                return;
            } catch (Exception ex) {
                t = new NetException("Failed to get " + url, ex);
            }
        }
        if (t != null)
            throw t;
    }

    @Override
    public boolean abort() {
        shouldContinue = false;
        aborted = true;
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
