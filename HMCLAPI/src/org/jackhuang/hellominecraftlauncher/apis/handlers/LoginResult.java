/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.apis.handlers;

/**
 *
 * @author hyh
 */
public class LoginResult //此类不可修改
{
    /**
     * 用户名
     */
    public String username = "";
    /**
     * 用户ID
     */
    public String userId = "";
    /**
     * Session
     */
    public String session = "";
    /**
     * Access Token
     */
    public String accessToken = "";
    /**
     * 是否登陆成功
     */
    public boolean success = false;
    /**
     * 登陆失败信息
     */
    public String error = "";
    /**
     * 用户信息
     * 一定为json格式
     */
    public String userProperties = "{}"; 
    /**
     * 其他信息
     */
    public String otherInfo = "";
    public String clientIdentifier = "";
    
    public String userType = "Offline";
}
