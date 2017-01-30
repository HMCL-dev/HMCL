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
package org.jackhuang.hellominecraft.svrmgr.install.cauldron;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jackhuang.hellominecraft.util.func.Consumer;
import org.jackhuang.hellominecraft.util.log.HMCLog;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 *
 * @author huangyuhui
 */
public class ForgeFormatThread extends Thread {

    Consumer<Map<String, List<ForgeVersion>>> lis;
    Map<String, List<ForgeVersion>> formattedList;

    public ForgeFormatThread(Consumer<Map<String, List<ForgeVersion>>> lis) {
        this.lis = lis;
    }

    public void formatNew() {

        formattedList = new HashMap();

        try {
            Document doc = Jsoup.connect("http://files.minecraftforge.net/").get();
            Elements allbuilds = doc.getElementsByClass("builds");
            Elements tables = new Elements();
            for (Element build : allbuilds)
                tables.addAll(build.getElementsByTag("table"));
            Elements allforge = new Elements();
            for (Element table : tables)
                allforge.addAll(table.getElementsByTag("tr"));
            for (Element e : allforge) {
                Elements tds = e.getElementsByTag("td");
                if (tds.isEmpty())
                    continue;
                ForgeVersion v = new ForgeVersion();
                v.ver = tds.get(0).text();
                v.mcver = tds.get(1).text();
                v.releasetime = tds.get(2).text();
                v.installer = new String[2];
                v.universal = new String[2];
                v.javadoc = new String[2];
                v.src = new String[2];
                v.userdev = new String[2];
                Elements a = tds.get(3).getElementsByTag("a");
                String prev = null;
                for (Element e2 : a) {
                    String href = e2.attributes().get("href").toLowerCase();
                    if (e2.text().toLowerCase().contains("changelog"))
                        v.changelog = href;
                    else if (prev != null) {
                        int index;
                        if (href.contains("adf.ly"))
                            index = 0;
                        else
                            index = 1;
                        if (prev.toLowerCase().contains("installer"))
                            v.installer[index] = href;
                        else if (prev.toLowerCase().contains("server"))
                            v.universal[index] = href;
                    }
                    prev = e2.text();
                }

                if (!formattedList.containsKey(v.mcver))
                    formattedList.put(v.mcver, new ArrayList<>());
                formattedList.get(v.mcver).add(v);
            }
        } catch (IOException ex) {
            HMCLog.warn("Failed to get forge list", ex);
            ForgeVersion v = new ForgeVersion();
            v.mcver = v.ver = "获取失败";
            formattedList.put(v.mcver, new ArrayList<>());
            formattedList.get(v.mcver).add(v);
        }
    }

    @Override
    public void run() {
        formatNew();
        if (lis != null)
            lis.accept(formattedList);
    }

}
