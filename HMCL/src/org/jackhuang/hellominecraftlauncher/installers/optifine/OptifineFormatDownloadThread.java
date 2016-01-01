/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.installers.optifine;

import java.net.URLEncoder;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jackhuang.hellominecraftlauncher.apis.DoneListener;
import org.jackhuang.hellominecraftlauncher.download.URLGet;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 *
 * @author hyh
 */
public class OptifineFormatDownloadThread extends Thread {
       
    DoneListener<String, Object> lis;
    OptifineVersion source;
    int type;
    
    public OptifineFormatDownloadThread(DoneListener<String, Object> lis, OptifineVersion source, int type) {
        this.lis = lis;
        this.type = type;
        this.source = source;
    }
    
    public static String format(String source) {
        
        String url;
        
        try {
            Document doc = Jsoup.connect(source).get();
            Element download = doc.getElementById("Download");
            Element a = download.getElementsByTag("a").first();
            url = "http://optifine.net/" + a.attributes().get("href");
        } catch (Exception ex) {
            Logger.getLogger(OptifineFormatThread.class.getName()).log(Level.SEVERE, null, ex);
            OptifineVersion v = new OptifineVersion();
            url = null;
        }
        
        return url;
    }
    
    public static String format_bmcl(String source) {
        String url = "http://bmclapi.bangbang93.com/optifine/" + URLEncoder.encode(source);
        return URLGet.get(url);
    }
    
    @Override
    public void run() {
        String url = null;
        switch(type) {
            case 0:
                url = format(source.dl);
                break;
            case 1:
                url = format_bmcl(source.ver);
                break;
        }
        if(lis != null)
            lis.onDone(url, null);
    }
}
