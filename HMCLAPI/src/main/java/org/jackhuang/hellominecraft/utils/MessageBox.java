/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.utils;

import javax.swing.JOptionPane;   
import org.jackhuang.hellominecraft.C;
  
/**  
 * @author hyh  
 */  
public class MessageBox   
{
    private static String Title = C.i18n("message.info");
    /**
     * Buttons: OK
     */
    public static final int DEFAULT_OPTION = -1;   
    /**
     * Buttons: Yes No
     */
    public static final int YES_NO_OPTION = 10;   
    /**
     * Buttons: Yes No Cancel
     */
    public static final int YES_NO_CANCEL_OPTION =11;   
    /**
     * Buttons: OK Cancel
     */
    public static final int OK_CANCEL_OPTION = 12;   
    /**
     * User Operation: Yes
     */
    public static final int YES_OPTION = 0;
    /**
     * User Operation: No
     */ 
    public static final int NO_OPTION = 1;
    /**
     * User Operation: Cancel
     */
    public static final int CANCEL_OPTION = 2;
    /**
     * User Operation: OK
     */
    public static final int OK_OPTION = 0;
    /**
     * User Operation: Closed Message Box
     */
    public static final int CLOSED_OPTION = -1;
    /**
     * Message Box Type: Error
     */
    public static final int ERROR_MESSAGE = 0;
    /**
     * Message Box Type: Info
     */  
    public static final int INFORMATION_MESSAGE = 1;
    /**
     * Message Box Type: Warning
     */
    public static final int WARNING_MESSAGE = 2;
    /**
     * Message Box Type: Question
     */
    public static final int QUESTION_MESSAGE = 3;
    /**
     * Message Box Type: Plain
     */
    public static final int PLAIN_MESSAGE = -1;
    
    /**
     * Show MsgBox with title and options
     * @param Msg The Message
     * @param Title The title of MsgBox.
     * @param Option The type of MsgBox.
     * @return user operation.
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
     * Show MsgBox with options
     * @param Msg The Message
     * @param Option The type of MsgBox.
     * @return User Operation
     */
    public static int Show(String Msg, int Option)   
    {   
        return Show(Msg, Title, Option);   
    }   
    
    /**
     * Show Default MsgBox
     * @param Msg The Message
     * @return User Operation
     */
    public static int Show(String Msg)   
    {   
        return Show(Msg, Title, INFORMATION_MESSAGE);   
    }   
}  