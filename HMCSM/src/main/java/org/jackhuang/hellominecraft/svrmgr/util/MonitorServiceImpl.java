/*
 * Hello Minecraft! Server Manager.
 * Copyright (C) 2013  huangyuhui
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hellominecraft.svrmgr.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.lang.management.ManagementFactory;
import java.util.StringTokenizer;
import com.sun.management.OperatingSystemMXBean;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.util.log.HMCLog;
import org.jackhuang.hellominecraft.util.sys.IOUtils;

/**
 *
 * 获取系统信息的业务逻辑实现类.
 *
 * @author GuoHuang
 */
public class MonitorServiceImpl implements IMonitorService {

    private static final int CPUTIME = 30;
    private static final int PERCENT = 100;
    private static final int FAULTLENGTH = 10;
    private static final String LINUX_VERSION = null;

    /**
     * 获得当前的监控对象.
     *
     * @return 返回构造好的监控对象
     *
     * @throws Exception
     * @author GuoHuang
     */
    @Override
    public MonitorInfoBean getMonitorInfoBean() throws Exception {
        int kb = 1024;
        // 可使用内存
        long totalMemory = Runtime.getRuntime().totalMemory() / kb;
        // 剩余内存
        long freeMemory = Runtime.getRuntime().freeMemory() / kb;
        // 最大可使用内存
        long maxMemory = Runtime.getRuntime().maxMemory() / kb;
        OperatingSystemMXBean osmxb = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        // 操作系统
        String osName = System.getProperty("os.name");
        // 总的物理内存
        long totalMemorySize = osmxb.getTotalPhysicalMemorySize() / kb;
        // 剩余的物理内存
        long freePhysicalMemorySize = osmxb.getFreePhysicalMemorySize() / kb;
        // 已使用的物理内存
        long usedMemory = (osmxb.getTotalPhysicalMemorySize() - osmxb.getFreePhysicalMemorySize()) / kb;
        // 获得线程总数
        ThreadGroup parentThread;
        for (parentThread = Thread.currentThread().getThreadGroup(); parentThread.getParent() != null; parentThread = parentThread.getParent());
        int totalThread = parentThread.activeCount();
        double cpuRatio = 0;
        if (osName.toLowerCase().startsWith("windows"))
            cpuRatio = this.getCpuRatioForWindows();
        else if (osName.toLowerCase().startsWith("mac"))
            cpuRatio = this.getCpuRatioForMac();
        else
            cpuRatio = getCpuRatioForLinux();
        // 构造返回对象
        MonitorInfoBean infoBean = new MonitorInfoBean();
        infoBean.setFreeMemory(freeMemory);
        infoBean.setFreePhysicalMemorySize(freePhysicalMemorySize);
        infoBean.setMaxMemory(maxMemory);
        infoBean.setOsName(osName);
        infoBean.setTotalMemory(totalMemory);
        infoBean.setTotalMemorySize(totalMemorySize);
        infoBean.setTotalThread(totalThread);
        infoBean.setUsedMemory(usedMemory);
        infoBean.setCpuRatio(cpuRatio);
        return infoBean;
    }

    private static double getCpuRatioForLinux() {
        float cpuUsage = 0;
        Process pro1, pro2;
        Runtime r = Runtime.getRuntime();
        try {
            String command = "cat /proc/stat";
            long startTime = System.currentTimeMillis();
            pro1 = r.exec(command);
            String line;
            long idleCpuTime1, totalCpuTime1;   //分别为系统启动后空闲的CPU时间和总的CPU时间
            try (BufferedReader in1 = new BufferedReader(new InputStreamReader(pro1.getInputStream()))) {
                idleCpuTime1 = 0;
                totalCpuTime1 = 0; //分别为系统启动后空闲的CPU时间和总的CPU时间
                while ((line = in1.readLine()) != null)
                    if (line.startsWith("cpu")) {
                        line = line.trim();
                        String[] temp = line.split("\\s+");
                        idleCpuTime1 = Long.parseLong(temp[4]);
                        for (String s : temp)
                            if (!s.equals("cpu"))
                                totalCpuTime1 += Long.parseLong(s);
                        break;
                    }
            }
            pro1.destroy();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                HMCLog.err("Failed to catch sysout", e);
            }
            //第二次采集CPU时间
            long endTime = System.currentTimeMillis();
            pro2 = r.exec(command);
            try (BufferedReader in2 = new BufferedReader(new InputStreamReader(pro2.getInputStream()))) {
                long idleCpuTime2 = 0, totalCpuTime2 = 0;   //分别为系统启动后空闲的CPU时间和总的CPU时间
                while ((line = in2.readLine()) != null)
                    if (line.startsWith("cpu")) {
                        line = line.trim();
                        String[] temp = line.split("\\s+");
                        idleCpuTime2 = Long.parseLong(temp[4]);
                        for (String s : temp)
                            if (!s.equals("cpu"))
                                totalCpuTime2 += Long.parseLong(s);
                        break;
                    }
                if (idleCpuTime1 != 0 && totalCpuTime1 != 0 && idleCpuTime2 != 0 && totalCpuTime2 != 0)
                    cpuUsage = 1 - (float) (idleCpuTime2 - idleCpuTime1) / (float) (totalCpuTime2 - totalCpuTime1);
            }
            pro2.destroy();
        } catch (IOException e) {
            HMCLog.err("Failed to catch sysout", e);
        }
        return cpuUsage * 100;
    }

    private double getCpuRatioForMac() {
        InputStream is = null;
        InputStreamReader isr = null;
        BufferedReader brStat = null;
        StringTokenizer tokenStat;
        try {
            Process process = Runtime.getRuntime().exec("top -l 1");
            is = process.getInputStream();
            isr = new InputStreamReader(is);
            brStat = new BufferedReader(isr);
            brStat.readLine();
            brStat.readLine();
            brStat.readLine();
            tokenStat = new StringTokenizer(brStat.readLine());
            tokenStat.nextToken();
            tokenStat.nextToken();
            String user = tokenStat.nextToken();
            tokenStat.nextToken();
            String system = tokenStat.nextToken();
            tokenStat.nextToken();
            user = user.substring(0, user.indexOf("%"));
            system = system.substring(0, system.indexOf("%"));
            float userUsage = new Float(user);
            float systemUsage = new Float(system);
            return (userUsage + systemUsage) / 100;
        } catch (IOException ioe) {
            System.out.println(ioe.getMessage());
            return 1;
        } finally {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(isr);
            IOUtils.closeQuietly(brStat);
        }
    }

    /**
     * 获得CPU使用率.
     *
     * @return 返回cpu使用率
     *
     * @author GuoHuang
     */
    private double getCpuRatioForWindows() {
        try {
            String procCmd = System.getenv("windir") + "\\system32\\wbem\\wmic.exe process get Caption,CommandLine,KernelModeTime,ReadOperationCount,ThreadCount,UserModeTime,WriteOperationCount";
            // 取进程信息
            long[] c0 = readCpu(Runtime.getRuntime().exec(procCmd));
            Thread.sleep(CPUTIME);
            long[] c1 = readCpu(Runtime.getRuntime().exec(procCmd));
            if (c0 != null && c1 != null) {
                long idletime = c1[0] - c0[0];
                long busytime = c1[1] - c0[1];
                return (double) PERCENT * (busytime) / (busytime + idletime);
            } else
                return 0.0;
        } catch (IOException | InterruptedException ex) {
            ex.printStackTrace();
            return 0.0;
        }
    }
    
    public static String substring(String src, int start_idx, int end_idx) {
        byte[] b = src.getBytes();
        String tgt = "";
        for (int i = start_idx; i <= end_idx; i++)
            tgt += (char) b[i];
        return tgt;
    }

    /**
     * 读取CPU信息.
     *
     * @param proc
     *
     * @return
     *
     * @author GuoHuang
     */
    private long[] readCpu(final Process proc) {
        long[] retn = new long[2];
        try {
            proc.getOutputStream().close();
            InputStreamReader ir = new InputStreamReader(proc.getInputStream());
            LineNumberReader input = new LineNumberReader(ir);
            String line = input.readLine();
            if (line == null || line.length() < FAULTLENGTH)
                return null;
            int capidx = line.indexOf("Caption");
            int cmdidx = line.indexOf("CommandLine");
            int rocidx = line.indexOf("ReadOperationCount");
            int umtidx = line.indexOf("UserModeTime");
            int kmtidx = line.indexOf("KernelModeTime");
            int wocidx = line.indexOf("WriteOperationCount");
            long idletime = 0;
            long kneltime = 0;
            long usertime = 0;
            while ((line = input.readLine()) != null) {
                if (line.length() < wocidx)
                    continue;
                // 字段出现顺序：Caption,CommandLine,KernelModeTime,ReadOperationCount,
                // ThreadCount,UserModeTime,WriteOperation
                String caption = substring(line, capidx, cmdidx - 1).trim();
                String cmd = substring(line, cmdidx, kmtidx - 1).trim();
                if (cmd.contains("wmic.exe"))
                    continue;
                String s1 = substring(line, kmtidx, rocidx - 1).trim();
                String s2 = substring(line, umtidx, wocidx - 1).trim();
                if (caption.equals("System Idle Process") || caption.equals("System")) {
                    if (s1.length() > 0)
                        idletime += Long.parseLong(s1);
                    if (s2.length() > 0)
                        idletime += Long.parseLong(s2);
                    continue;
                }
                if (s1.length() > 0)
                    kneltime += Long.parseLong(s1);
                if (s2.length() > 0)
                    usertime += Long.parseLong(s2);
            }
            retn[0] = idletime;
            retn[1] = kneltime + usertime;
            return retn;
        } catch (IOException | NumberFormatException ex) {
            ex.printStackTrace();
        } finally {
            IOUtils.closeQuietly(proc.getInputStream());
        }
        return null;
    }
}
