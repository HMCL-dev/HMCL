/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.download;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jackhuang.hellominecraftlauncher.apis.DoneListener;
import org.jackhuang.hellominecraftlauncher.apis.HMCLLog;

/**
 *
 * @author hyh
 */
public class DownloadAllThread extends Thread {

    class URL {
        String mark, url, filepath;
    }
    ArrayList<URL> downloads;
    ArrayList<DoneListener<String, Integer>> listenerDone, listenerDoing;
    DownloadListener dl;

    public DownloadAllThread(DownloadListener dl) {
        downloads = new ArrayList<URL>();
        listenerDone = new ArrayList<DoneListener<String, Integer>>();
        listenerDoing = new ArrayList<DoneListener<String, Integer>>();
        this.dl = dl;
    }

    public void addDownloadURL(String mark, String url, String filepath) {
        URL u = new URL();
        u.mark = mark;
        u.url = url;
        u.filepath = filepath;
        downloads.add(u);
    }

    public void addDoingListener(DoneListener<String, Integer> l) {
        listenerDoing.add(l);
    }

    public void addDoneListener(DoneListener<String, Integer> l) {
        listenerDone.add(l);
    }
    
    public int size() {
        return downloads.size();
    }

    @Override
    public void run() {
        for (int i = 0; i < downloads.size(); i++) {
            URL url = downloads.get(i);
            for (DoneListener<String, Integer> d : listenerDoing) {
                d.onDone(url.mark, i);
            }
            HMCLLog.log(url.url + ' ' + url.filepath);
	    try {
		HttpDownloader.download(url.url, url.filepath, dl);
	    } catch (MalformedURLException ex) {
		HMCLLog.err("Failed to parse the url", ex);
	    }
            for (DoneListener<String, Integer> d : listenerDone) {
                d.onDone(url.mark, i);
            }
        }
        dl.OnDone();
    }
    
    public boolean isEmpty() {
        return downloads.isEmpty();
    }
}