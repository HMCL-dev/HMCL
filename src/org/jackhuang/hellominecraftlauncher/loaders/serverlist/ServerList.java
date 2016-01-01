/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.loaders.serverlist;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import sun.misc.BASE64Decoder;

/**
 *
 * @author hyh
 */
public class ServerList {
    public static String address = "AmlwAA==";
    public static String header = "CgAACQAHc2VydmVycwoAAA==";
    public static String hideAddress = "AAtoaWRlQWRkcmVzcw==";
    public static String name = "AARuYW1lAA==";
    
    public ArrayList<ServerInfo> list; 
    
    public File file;
    
    public ServerList(File file) {
        
        this.file = file;
        if(!file.exists())
        try {
            file.getParentFile().mkdirs();
            file.createNewFile();
        } catch (IOException ex) {
            Logger.getLogger(ServerList.class.getName()).log(Level.SEVERE, null, ex);
        }
        this.list = new ArrayList<ServerInfo>();
        
        try {
            FileInputStream stream = new FileInputStream(file);
            byte[] header = new byte[0x13];
            stream.read(header, 0, header.length);
            if(stream.available() < 5) {
                stream.close();
                return;
            }
            while(stream.available() > 0) {
                byte[] hideAddress = new byte[13];
                stream.read(hideAddress, 0, hideAddress.length);
                boolean isHide = stream.read() == 1 ? true : false;
                byte[] name = new byte[9];
                stream.read(name, 0, name.length);
                ArrayList<Byte> nameList = new ArrayList<Byte>(20);
                byte ch = (byte) stream.read();
                while(ch != 0) {
                    if(ch == -1) break;
                    nameList.add(ch);
                    ch = (byte) stream.read();
                }
                if(ch == -1) break;
                byte[] names = new byte[nameList.size()];
                for(int i = 0; i < nameList.size(); i++) {
                    names[i] = nameList.get(i);
                }
                String Name = new String(names);
                byte[] address = new byte[5];
                stream.read(address, 0, address.length);
                ArrayList<Byte> AddRess = new ArrayList<Byte>(20);
                ch = (byte) stream.read();
                while(ch != 0) {
                    if(ch == -1) break;
                    AddRess.add(ch);
                    ch = (byte) stream.read();
                }
                if(ch == -1) break;
                byte[] addresses = new byte[AddRess.size()];
                for(int i = 0; i < AddRess.size(); i++) {
                    addresses[i] = AddRess.get(i);
                }
                String Address = new String(addresses);
                ServerInfo info = new ServerInfo();
                info.address = Address;
                info.isHide = isHide;
                info.name = Name;
                list.add(info);
                if(stream.read() == 0)
                    break;
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(ServerList.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(ServerList.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void write() {
        try {
            FileOutputStream stream = new FileOutputStream(file);
            BASE64Decoder decoder = new BASE64Decoder();
            stream.write(decoder.decodeBuffer(header));
            int p = 0;
            stream.write(0);
            stream.write(list.size());
            stream.write(1);
            for(ServerInfo info : list) {
                p++;
                stream.write(decoder.decodeBuffer(hideAddress));
                if(info.isHide)
                    stream.write(1);
                else
                    stream.write(0);
                stream.write(8);
                stream.write(decoder.decodeBuffer(name));
                stream.write(info.name.length());
                stream.write(info.name.getBytes());
                stream.write(new byte[]{8, 0});
                stream.write(decoder.decodeBuffer(address));
                stream.write(info.address.length());
                stream.write(info.address.getBytes());
                stream.write(0);
                if(p != list.size())
                    stream.write(1);
                else
                    stream.write(0);
            }
            stream.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(ServerList.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(ServerList.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public static void writeEmptyFile(File path) {
        try {
            FileOutputStream serverdat = new FileOutputStream(path);
            BASE64Decoder decoder = new BASE64Decoder();
            byte[] writes = decoder.decodeBuffer(header);
            serverdat.write(writes, 0, writes.length);
            serverdat.write(0);
            serverdat.close();
        } catch (IOException ex) {
            Logger.getLogger(ServerList.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
