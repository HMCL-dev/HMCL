/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.apis.utils;

/**
 *
 * @author hyh
 */
public interface IMonitorService {

    /**
     * 获得当前的监控对象.
     *
     * @return 返回构造好的监控对象
     * @throws Exception
     */
    public MonitorInfoBean getMonitorInfoBean() throws Exception;
}