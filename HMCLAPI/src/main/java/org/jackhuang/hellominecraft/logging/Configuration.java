/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraft.logging;

import java.util.ArrayList;
import org.jackhuang.hellominecraft.logging.appender.ConsoleAppender;
import org.jackhuang.hellominecraft.logging.appender.IAppender;
import org.jackhuang.hellominecraft.logging.layout.DefaultLayout;

/**
 *
 * @author hyh
 */

public class Configuration {
    
    public ArrayList<IAppender> appenders = new ArrayList<IAppender>();
    
    public static Configuration DEFAULT;
    
    static {
	DEFAULT = new Configuration();
	DEFAULT.appenders.add(new ConsoleAppender("Console", new DefaultLayout(), true, new ConsoleAppender.SystemOutStream(), true));
    }
    
}
