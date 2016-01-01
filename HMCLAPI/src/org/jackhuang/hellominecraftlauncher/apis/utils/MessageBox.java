/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.apis.utils;

import javax.swing.JOptionPane;   
  
/**  
 * 提供提示消息的功能
 * @author hyh  
 */  
public class MessageBox   
{
    private static String Title = "提示";
    /**
     * 按钮为：确定
     */
    public static final int DEFAULT_OPTION = -1;   
    /**
     * 按钮为：是 否
     */
    public static final int YES_NO_OPTION = 10;   
    /**
     * 按钮为：是 否 取消
     */
    public static final int YES_NO_CANCEL_OPTION =11;   
    /**
     * 按钮为：确定 取消
     */
    public static final int OK_CANCEL_OPTION = 12;   
    /**
     * 用户操作为：是
     */
    public static final int YES_OPTION = 0;
    /**
     * 用户操作为：否
     */ 
    public static final int NO_OPTION = 1;
    /**
     * 用户操作为：取消
     */
    public static final int CANCEL_OPTION = 2;
    /**
     * 用户操作为：确定
     */
    public static final int OK_OPTION = 0;
    /**
     * 用户操作为：关闭了消息框
     */
    public static final int CLOSED_OPTION = -1;
    /**
     * 消息框类型为：错误
     */
    public static final int ERROR_MESSAGE = 0;
    /**
     * 消息框类型为：消息
     */  
    public static final int INFORMATION_MESSAGE = 1;
    /**
     * 消息框类型为：警告
     */
    public static final int WARNING_MESSAGE = 2;
    /**
     * 消息框类型为：询问
     */
    public static final int QUESTION_MESSAGE = 3;
    /**
     * 消息框类型为：完全
     */
    public static final int PLAIN_MESSAGE = -1;
    
    /**
     * 弹出消息框
     * @param Msg 消息
     * @param Option 消息框类型, 多个请用&连接
     * @return 用户操作结果
     */
    public static int Show(String Msg, String Title, int Option)   
    {   
        switch(Option)   
        {   
            case YES_NO_OPTION:   
            case YES_NO_CANCEL_OPTION:   
            case OK_CANCEL_OPTION:   
                return JOptionPane.showConfirmDialog(null, Msg, Title, Option - 10);   
            default:   
                JOptionPane.showMessageDialog(null, Msg, Title, Option);   
        }   
        return 0;   
    }
    
    /**
     * 弹出默认消息框
     * @param Msg 消息
     * @return 用户操作结果
     */
    public static int Show(String Msg, int Option)   
    {   
        return Show(Msg, Title, Option);   
    }   
    
    /**
     * 弹出默认消息框
     * @param Msg 消息
     * @return 用户操作结果
     */
    public static int Show(String Msg)   
    {   
        return Show(Msg, Title, INFORMATION_MESSAGE);   
    }   
}  