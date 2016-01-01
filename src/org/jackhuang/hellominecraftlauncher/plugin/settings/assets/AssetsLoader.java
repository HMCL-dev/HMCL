/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.plugin.settings.assets;

import java.util.ArrayList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jackhuang.hellominecraftlauncher.apis.LogUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author hyh
 */
public class AssetsLoader {
    
    static class MyThread extends Thread {
        Document doc;
        Element root;
        NodeList nodes;
        public String uri;
        public AssetsLoaderListener l;
        ArrayList<Contents> al;
        
        public MyThread(String uri, AssetsLoaderListener l) {
            this.uri = uri;
            this.l = l;
        }
        
        private Contents modifyContents(NodeList contents) {
            Contents ret = new Contents();
            for(int i = 0; i < contents.getLength(); i++) {
                Node result = contents.item(i);
                if(result.getNodeType() == Node.ELEMENT_NODE) {
                    if(result.getNodeName().equals("Key")) {
                        ret.key = result.getTextContent();
                    }
                    if(result.getNodeName().equals("ETag")) {
                        ret.eTag = result.getTextContent();
                    }
                    if(result.getNodeName().equals("LastModified")) {
                        ret.lastModified = result.getTextContent();
                    }
                    if(result.getNodeName().equals("Size")) {
                        ret.size = result.getTextContent();
                    }
                    if(result.getNodeName().equals("StorageClass")) {
                        ret.storageClass = result.getTextContent();
                    }
                }
            }
            return ret;
        }
        
        @Override
        public void run() {
            al = new ArrayList<Contents>();
            try {
                System.out.println("AssetsLoader - Download begin.");
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = factory.newDocumentBuilder();
                doc = db.parse(uri);
                System.out.println("AssetsLoader - Download end and format begin.");
                root = doc.getDocumentElement();
                nodes = root.getChildNodes();
                for(int i = 0; i < nodes.getLength(); i++) {
                    Node result = nodes.item(i);
                    if(result.getNodeType() == Node.ELEMENT_NODE && result.getNodeName().equals("Contents")) {
                        al.add(modifyContents(result.getChildNodes()));
                    }
                }
                System.out.println("AssetsLoader - Format end.");
                
                if(l != null)
                    l.OnDone(al);
            } catch(Exception e) {
                LogUtils.info("AssetsLoader - Failed");
                if(l != null)
                    l.OnFailed(e);
            }
        }
    }
    
    
    
    public static void getAssets(String uri, AssetsLoaderListener l) {
        MyThread thread = new MyThread(uri, l);
        thread.start();
    }
        
}
