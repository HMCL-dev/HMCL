/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.apis;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 此类用于保存配置
 * @author hyh
 */
public class Configuration<T> {
    
    /**
     * 构造函数
     * @param file 配置文件地址
     */
    public Configuration(String file) {
        this.file = new File(file);
    }
    T data;
    File file;
    Gson gson = new Gson();
    
    /**
     * 获取到的设置对象
     * @return 设置对象
     */
    public T get() {
        return data;
    }
    
    /**
     * 设置设置对象
     * @param data 设置对象
     */
    public void set(T data) {
        this.data = data;
    }
    
    /**
     * 从提供的配置文件读取配置
     * @param c T的class
     * @throws FileNotFoundException 
     */
    public void load(Class<T> c) {
        try {
            data = gson.fromJson(new FileReader(file), c);
        } catch (FileNotFoundException ex) {
            data = null;
        }
    }
    
    /**
     * 用Json的形式保存存储在configuration里的data
     * @throws IOException 
     */
    public void save() throws IOException {
        file.getParentFile().mkdirs();
        FileWriter fw = new FileWriter(file);
        fw.write(gson.toJson(data));
        fw.close();
    }
}
