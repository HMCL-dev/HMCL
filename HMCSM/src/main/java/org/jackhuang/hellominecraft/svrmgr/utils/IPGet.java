/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraft.svrmgr.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.utils.NetUtils;
import org.jackhuang.hellominecraft.utils.functions.Consumer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

/**
 *
 * @author hyh
 */
public class IPGet extends Thread {

    public Consumer<String> dl;

    @Override
    public void run() {
	try {
	    Document d = Jsoup.connect("http://www.ip138.com").get();
	    Elements iframe = d.getElementsByTag("iframe");
	    if(iframe.size() > 0) {
		String url = iframe.get(0).attr("src");
		String s = NetUtils.doGet(url, "GBK");
		Pattern p = Pattern.compile("\\[(\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])(\\.(\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])){3}\\]");
		Matcher m = p.matcher(s);
		s = "";
		while(m.find()) {
		    s += m.group() + ",";
		}
		dl.accept(s.substring(0, s.length()-1));
	    }
	} catch (Exception ex) {
	    HMCLog.warn("Failed to get ip from ip138.", ex);
	    dl.accept("获取失败");
	}
    }
    
}
