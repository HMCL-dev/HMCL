/*
 * Hello Minecraft! Server Manager.
 * Copyright (C) 2013  huangyuhui
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
package org.jackhuang.hellominecraft.svrmgr.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jackhuang.hellominecraft.util.log.HMCLog;
import org.jackhuang.hellominecraft.util.net.NetUtils;
import org.jackhuang.hellominecraft.util.func.Consumer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

/**
 *
 * @author huangyuhui
 */
public class IPGet extends Thread {

    public Consumer<String> dl;

    @Override
    public void run() {
        try {
            Document d = Jsoup.connect("http://www.ip138.com").get();
            Elements iframe = d.getElementsByTag("iframe");
            if (iframe.size() > 0) {
                String url = iframe.get(0).attr("src");
                String s = NetUtils.get(url, "GBK");
                Pattern p = Pattern.compile("\\[(\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])(\\.(\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])){3}\\]");
                Matcher m = p.matcher(s);
                s = "";
                while (m.find())
                    s += m.group() + ",";
                dl.accept(s.substring(0, s.length() - 1));
            }
        } catch (Exception ex) {
            HMCLog.warn("Failed to get ip from ip138.", ex);
            dl.accept("获取失败");
        }
    }

}
