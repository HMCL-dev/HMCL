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
package org.jackhuang.hellominecraft.svrmgr.install.bukkit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jackhuang.hellominecraft.util.log.HMCLog;
import org.jackhuang.hellominecraft.util.func.Consumer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 *
 * @author huangyuhui
 */
public class BukkitFormatThread extends Thread {

    Consumer<List<BukkitVersion>> lis;
    List<BukkitVersion> formattedList;
    String url;

    public BukkitFormatThread(String url, Consumer<List<BukkitVersion>> lis) {
        this.lis = lis;
        this.url = url;
    }

    public void format(String url) {

        formattedList = new ArrayList<>();

        try {
            Document doc = Jsoup.connect(url).get();
            Elements versionsTable = doc.getElementsByClass("versionsTable");
            Elements allforge = new Elements();
            for (Element table : versionsTable)
                allforge.addAll(table.getElementsByTag("tr"));
            for (Element e : allforge) {
                Elements tds = e.getElementsByTag("td");
                if (tds.isEmpty())
                    continue;
                BukkitVersion v = new BukkitVersion();
                Elements ths = e.getElementsByTag("th");
                v.buildNumber = v.infoLink = null;
                if (ths.size() > 0) {
                    Elements a = ths.get(0).getElementsByTag("a");
                    if (a.size() > 0) {
                        v.buildNumber = a.get(0).text();
                        v.infoLink = a.get(0).attr("href");
                    }
                }
                v.version = tds.get(0).text();
                v.type = tds.get(1).text();
                if (tds.get(2).getElementsByTag("a").isEmpty())
                    continue;
                v.downloadLink = "http://dl.bukkit.org" + tds.get(2).getElementsByTag("a").get(0).attr("href");
                formattedList.add(v);
            }
        } catch (IOException ex) {
            HMCLog.warn("Failed to get bukkit list", ex);
            BukkitVersion v = new BukkitVersion();
            v.type = v.version = "获取失败";
            v.buildNumber = v.infoLink = null;
            formattedList.add(v);
        }
    }

    @Override
    public void run() {
        List<BukkitVersion> al = null;
        format(url);
        if (lis != null)
            lis.accept(formattedList);
    }

}
