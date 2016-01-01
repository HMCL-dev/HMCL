/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.installers.optifine;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
public class OptifineFormatThread extends Thread {
       
    DoneListener<List<OptifineVersion>, Object> lis;
    int type;
    
    public OptifineFormatThread(DoneListener<List<OptifineVersion>, Object> lis, int type) {
        this.lis = lis;
        this.type = type;
    }
    
    public static List<OptifineVersion> format(String source) {
        
        List<OptifineVersion> al = new ArrayList<OptifineVersion>();
        
        try {
            Document doc = Jsoup.connect(source).get();
            Elements downloads = doc.getElementsByClass("downloads");
            Element table = downloads.first();
            Elements tds = table.getElementsByTag("td");
            Element td = tds.first();
            Elements allopt = td.children();
            String lasth2 = "", lasth3 = "";
            for(int i = 0; i < allopt.size(); i++) {
                Element e = allopt.get(i);
                String tagName = e.tagName().toLowerCase();
                if(tagName.indexOf("h3") != -1) {
                    lasth3 = e.text();
                    String[] s = lasth3.split(" ");
                    lasth3 = s[s.length - 1];
                } else if(tagName.indexOf("h2") != -1) {
                    lasth2 = e.text().split(" ")[1];
                } else
                if(tagName.indexOf("table") != -1) {
                    Elements downloadLines = e.getElementsByTag("tr");
                    for(Element downloadLine : downloadLines) {
                        OptifineVersion v = new OptifineVersion();
                        v.type = lasth3;
                        String[] ver = downloadLine.child(0).text().split(" ");
                        if(ver.length < 1) continue;
                        v.ver = ver[ver.length - 1];
                        v.mcver = lasth2;
                        Element mirror = downloadLine.child(2).child(0);
                        v.dl = mirror.attributes().get("href");
                        v.date = downloadLine.child(3).text();
                        al.add(v);
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
            //Logger.getLogger(OptifineFormatThread.class.getName()).log(Level.SEVERE, null, ex);
            OptifineVersion v = new OptifineVersion();
            v.ver = "获取失败";
            al.add(v);
        }
        
        return al;
    }
    
    public static List<OptifineVersion> format_bmcl(String source) {
        Gson gson = new Gson();
        return Arrays.asList(gson.fromJson(URLGet.get(source), OptifineVersion[].class));
    } 
    
    @Override
    public void run() {
        List<OptifineVersion> al;
        if(type == 0) {
            al = format(C.URL_OPTIFINE[0]);
        } else {
            al = format_bmcl(C.URL_OPTIFINE[1]);
        }
        if(lis != null)
            lis.onDone(al, null);
    }
}
