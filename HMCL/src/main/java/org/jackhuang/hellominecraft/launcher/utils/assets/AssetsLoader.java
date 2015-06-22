/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.assets;

import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.utils.EventHandler;
import org.jackhuang.hellominecraft.utils.MathUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author hyh
 */
public class AssetsLoader extends Thread {

    Document doc;
    Element root;
    NodeList nodes;
    public String uri;
    ArrayList<Contents> al;
    public final EventHandler<Throwable> failedEvent = new EventHandler<Throwable>(this);
    public final EventHandler<List<Contents>> successEvent = new EventHandler<List<Contents>>(this);

    AssetsLoader(String uri) {
        this.uri = uri;
    }

    private Contents modifyContents(NodeList contents) {
        Contents ret = new Contents();
        for (int i = 0; i < contents.getLength(); i++) {
            Node result = contents.item(i);
            if (result.getNodeType() == Node.ELEMENT_NODE) {
                if (result.getNodeName().equalsIgnoreCase("Key")) {
                    ret.key = result.getTextContent();
                }
                if (result.getNodeName().equalsIgnoreCase("ETag")) {
                    ret.eTag = result.getTextContent();
                }
                if (result.getNodeName().equalsIgnoreCase("LastModified")) {
                    ret.lastModified = result.getTextContent();
                }
                if (result.getNodeName().equalsIgnoreCase("Size")) {
                    ret.size = MathUtils.parseInt(result.getTextContent(), 0);
                }
                if (result.getNodeName().equalsIgnoreCase("StorageClass")) {
                    ret.storageClass = result.getTextContent();
                }
            }
        }
        return ret;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("AssetsLoader");
        al = new ArrayList<Contents>();
        try {
            HMCLog.log("AssetsLoader - Download begin.");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = factory.newDocumentBuilder();
            doc = db.parse(uri);
            HMCLog.log("AssetsLoader - Download end and format begin.");
            root = doc.getDocumentElement();
            nodes = root.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node result = nodes.item(i);
                if (result.getNodeType() == Node.ELEMENT_NODE && result.getNodeName().equals("Contents")) {
                    Contents c = modifyContents(result.getChildNodes());
                    if(c.key != null)
                        al.add(c);
                }
            }
            HMCLog.log("AssetsLoader - Format end.");

            successEvent.execute(al);
        } catch (Exception e) {
            HMCLog.warn("AssetsLoader - Failed", e);
            failedEvent.execute(e);
        }
    }

}
