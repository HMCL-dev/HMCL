/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.apis.handlers;

import java.util.Map;
import org.jackhuang.hellominecraftlauncher.apis.IPluginHandler;

/**
 * 登录接口
 * @author hyh
 */
public abstract class Login extends IPluginHandler {
    
    protected String clientToken;
    public Login(String clientToken) {
        this.clientToken = clientToken;
    }
    
    /**
     * 登陆方法
     * @param usr 用户名
     * @param pwd 密码
     * @return 登陆结果
     */
    public abstract LoginResult login(LoginInfo info);
    /**
     * 
     * @return 登陆插件显示名称
     */
    public abstract String getName();
    
    /**
     * Has password?
     * @return 是否隐藏密码框
     */
    public boolean isHidePasswordBox() {
        return false;
    }
    
    /**
     * 若返回非空，禁用密码框，用户名，使用旧有session登陆
     * @return 存储的用户名，没有存储为空
     */
    public boolean isLoggedIn() {
        return false;
    }

    /**
     * 设置是否记住我
     * @param is 
     */
    public void setRememberMe(boolean is) {
        
    }
    
    public abstract LoginResult loginBySettings();
    public abstract void logout();
}
