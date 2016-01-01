/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.installers.forge;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jackhuang.hellominecraftlauncher.apis.DoneListener;
import org.jackhuang.hellominecraftlauncher.download.URLGet;
import org.jackhuang.hellominecraftlauncher.utilities.C;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 *
 * @author hyh
 */
public class ForgeFormatThread extends Thread {
    
    DoneListener<List<ForgeVersion>, Object> lis;
    int type;
    
    public ForgeFormatThread(DoneListener<List<ForgeVersion>, Object> lis, int type) {
        this.lis = lis;
        this.type = type;
    }
    
    public static List<ForgeVersion> formatNew() {
        
        ArrayList<ForgeVersion> al = new ArrayList<ForgeVersion>();
        
        try {
            Document doc = Jsoup.connect("http://files.minecraftforge.net/").get();
            Elements allbuilds = doc.getElementsByClass("builds");
            Elements tables = new Elements();
            for(Element build : allbuilds)
                tables.addAll(build.getElementsByTag("table"));
            Elements allforge = new Elements();
            for(Element table : tables)
                allforge.addAll(table.getElementsByTag("tr"));
            tables = null;
            for(Element e : allforge) {
                Elements tds = e.getElementsByTag("td");
                if(tds.isEmpty()) continue;
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
                for(Element e2 : a) {
                    String href = e2.attributes().get("href").toLowerCase();
                    if(e2.text().toLowerCase().indexOf("changelog") != -1) {
                        v.changelog = href;
                    } else if(prev != null) {
                        int index;
                        if(href.indexOf("adf.ly") != -1) index = 0;
                        else index = 1;
                        if(prev.toLowerCase().indexOf("installer") != -1) {
                            v.installer[index] = href;
                        } else
                        if(prev.toLowerCase().indexOf("universal") != -1) {
                            v.universal[index] = href;
                        } else
                        if(prev.toLowerCase().indexOf("javadoc") != -1) {
                            v.javadoc[index] = href;
                        } else
                        if(prev.toLowerCase().indexOf("src") != -1) {
                            v.src[index] = href;
                        } else
                        if(prev.toLowerCase().indexOf("userdev") != -1) {
                            v.userdev[index] = href;
                        }
                    }
                    prev = e2.text();
                }
                al.add(v);
            }
        } catch (Exception ex) {
            Logger.getLogger(ForgeFormatThread.class.getName()).log(Level.SEVERE, null, ex);
            ForgeVersion v = new ForgeVersion();
            v.ver = "获取失败";
            al.add(v);
        }
        
        return al;
    }
    
    public static List<ForgeVersion> formatLegacy() {
        
        ArrayList<ForgeVersion> al = new ArrayList<ForgeVersion>();
        
        try {
            Document doc = Jsoup.connect("http://files.minecraftforge.net/minecraftforge/index_legacy.html").get();
            Element allbuilds = doc.getElementById("all_builds");
            Elements mainTable = allbuilds.getElementsByTag("table");
            Element table = mainTable.first();
            Elements allforge = table.getElementsByTag("tr");
            for(Element e : allforge) {
                Elements tds = e.getElementsByTag("td");
                if(tds.isEmpty()) continue;
                ForgeVersion v = new ForgeVersion();
                v.ver = tds.get(0).text();
                v.mcver = tds.get(1).text();
                v.releasetime = tds.get(2).text();
                v.installer = new String[2];
                v.universal = new String[2];
                v.javadoc = new String[2];
                Elements a = tds.get(3).getElementsByTag("a");
                String prev = null;
                for(Element e2 : a) {
                    String href = e2.attributes().get("href").toLowerCase();
                    if(e2.text().toLowerCase().indexOf("changelog") != -1) {
                        v.changelog = href;
                    } else if(prev != null) {
                        int index;
                        if(href.indexOf("adf.ly") != -1) index = 0;
                        else index = 1;
                        if(prev.toLowerCase().indexOf("installer") != -1) {
                            v.installer[index] = href;
                        } else
                        if(prev.toLowerCase().indexOf("universal") != -1) {
                            v.universal[index] = href;
                        } else
                        if(prev.toLowerCase().indexOf("javadoc") != -1) {
                            v.javadoc[index] = href;
                        } else
                        if(prev.toLowerCase().indexOf("src") != -1) {
                            v.src[index] = href;
                        }
                    }
                    prev = e2.text();
                }
                al.add(v);
            }
        } catch (Exception ex) {
            //Logger.getLogger(ForgeFormatThread.class.getName()).log(Level.SEVERE, null, ex);
            ForgeVersion v = new ForgeVersion();
            v.ver = "获取失败";
            al.add(v);
        }
        
        return al;
    }
    
    public static ForgeVersion[] formatBMCL(String url) {
        String s = URLGet.get(url);
        return new Gson().fromJson(s, ForgeVersion[].class);
    }
    
    @Override
    public void run() {
        List<ForgeVersion> al = null;
        switch(type) {
            case 0:
                al = formatNew();
                al.addAll(formatLegacy());
                break;
            case 1:
                al = new ArrayList<ForgeVersion>();
                for(ForgeVersion fv : formatBMCL(C.URL_FORGE_BMCL_NEW)) {
                    al.add(fv);
                }
                for(ForgeVersion fv : formatBMCL(C.URL_FORGE_BMCL_LEGACY)) {
                    al.add(fv);
                }
                break;
        }
        if(lis != null)
            lis.onDone(al, null);
    }
    
}
