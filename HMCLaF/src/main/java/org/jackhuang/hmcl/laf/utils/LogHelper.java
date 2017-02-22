/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * LogHelper.java at 2015-2-1 20:25:40, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf;

import org.jackhuang.hmcl.laf.BeautyEyeLNFHelper;

/**
 * The Class LogHelper.
 * 
 * @author Jack Jiang, 2013-04-05
 * @since 3.5
 * @see BeautyEyeLNFHelper#debug
 */
public class LogHelper
{
	/**
	 * Error.
	 *
	 * @param msg the msg
	 */
	public static void error(String msg)
	{
		if(BeautyEyeLNFHelper.debug)
			System.err.println("[BE-ERROR] - "+msg);
	}
	
	/**
	 * Debug.
	 *
	 * @param msg the msg
	 */
	public static void debug(String msg)
	{
		if(BeautyEyeLNFHelper.debug)
			System.err.println("[BE-DEBUG] - "+msg);
	}
	
	/**
	 * Info.
	 *
	 * @param msg the msg
	 */
	public static void info(String msg)
	{
		System.err.println("[BE-INFO] - "+msg);
	}
}
