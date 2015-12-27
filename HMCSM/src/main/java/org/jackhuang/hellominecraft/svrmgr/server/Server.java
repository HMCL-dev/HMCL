/*
 * Hello Minecraft! Launcher.
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
package org.jackhuang.hellominecraft.svrmgr.server;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.utils.system.IOUtils;
import org.jackhuang.hellominecraft.utils.MessageBox;
import org.jackhuang.hellominecraft.utils.Pair;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.svrmgr.settings.Schedule;
import org.jackhuang.hellominecraft.svrmgr.settings.SettingsManager;
import org.jackhuang.hellominecraft.svrmgr.threads.MonitorThread;
import org.jackhuang.hellominecraft.svrmgr.threads.WaitForThread;
import org.jackhuang.hellominecraft.svrmgr.utils.Utilities;
import org.jackhuang.hellominecraft.utils.Event;
import org.jackhuang.hellominecraft.utils.EventHandler;
import org.jackhuang.hellominecraft.utils.functions.Consumer;

/**
 *
 * @author huangyuhui
 */
public class Server implements Event<Integer>, MonitorThread.MonitorThreadListener,
                               ActionListener {

    private static Server instance;
    private static boolean disactived = false;

    public static Server getInstance() {
        return instance;
    }

    public static boolean isInstanceRunning() {
        return instance != null && instance.isRunning;
    }

    public static void init(String jar, String memory) {
        instance = new Server(jar, memory);
    }

    String jar, memory;
    Process server;
    MonitorThread threadA, threadB;
    WaitForThread threadC;
    Consumer<Pair<String, String[]>> gettingPlayerNumber;
    ArrayList<MonitorThread.MonitorThreadListener> listeners;
    ArrayList<Event<Integer>> listenersC;
    //ArrayList<DoneListener0> listenersBegin, listenersDone;
    public final EventHandler<Void> startedEvent = new EventHandler<>(this), stoppedEvent = new EventHandler<>(this);
    ArrayList<TimerTask> timerTasks;
    ArrayList<Schedule> schedules;
    BufferedWriter bw;
    Timer timer;
    javax.swing.Timer pastTimer;
    public boolean isRunning, isRestart, isDone;
    int isGettingPlayerNumber;
    String playerNumber;

    private Server(String jar, String memory) {
        this.jar = jar;
        this.memory = memory;
        isRestart = isDone = false;
        listeners = new ArrayList<>();
        listenersC = new ArrayList<>();
        schedules = new ArrayList<>();
        timerTasks = new ArrayList<>();
    }

    public void addListener(MonitorThread.MonitorThreadListener l) {
        listeners.add(l);
    }

    public void addListener(Event<Integer> l) {
        listenersC.add(l);
    }

    public void run() throws IOException {
        String jvmPath;
        if (StrUtils.isBlank(SettingsManager.settings.javaDir))
            jvmPath = IOUtils.getJavaDir();
        else
            jvmPath = SettingsManager.settings.javaDir;
        String[] puts = new String[] {
            jvmPath,
            "-Xmx" + memory + "m",
            "-jar",
            SettingsManager.settings.mainjar,
            "nogui",
            "-nojline"
        };
        ProcessBuilder pb = new ProcessBuilder(puts);
        pb.directory(new File(SettingsManager.settings.mainjar).getParentFile());
        try {
            disactiveMods(SettingsManager.settings.inactiveExtMods,
                          SettingsManager.settings.inactiveCoreMods,
                          SettingsManager.settings.inactivePlugins);
            server = pb.start();
            registerThread(threadA, server.getInputStream());
            registerThread(threadB, server.getErrorStream());
            registerThreadC(server);
            try {
                bw = new BufferedWriter(new OutputStreamWriter(server.getOutputStream(), System.getProperty("sun.jnu.encoding", "utf-8")));
            } catch (UnsupportedEncodingException ex) {
                bw = new BufferedWriter(new OutputStreamWriter(server.getOutputStream()));
            }
            isRunning = true;
            startedEvent.execute(null);
            sendStatus("*** 启动服务端中 ***");
        } catch (IOException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
            isRunning = false;
        }
    }

    public void sendCommand(String cmd) {
        if (isRunning)
            try {
                sendStatus("发送指令: " + cmd);
                bw.write(cmd);
                bw.newLine();
                bw.flush();
            } catch (IOException ex) {
                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
            }
    }

    public void getPlayerNumber(Consumer<Pair<String, String[]>> d) {
        isGettingPlayerNumber = 1;
        gettingPlayerNumber = d;
        sendCommand("list");
    }

    public void restart() {
        isRestart = true;
        stop();
    }

    public void stop() {
        if (timer != null)
            timer.cancel();
        sendCommand("stop");
    }

    public void shutdown() {
        if (timer != null)
            timer.cancel();
        server.destroy();
    }

    public void clearSchedule() {
        schedules.clear();
    }

    public void addSchedule(Schedule s) {
        schedules.add(s);
    }

    public void delSchedule(Schedule s) {
        int index = schedules.indexOf(s);
        if (index == -1)
            return;
        schedules.remove(index);
        timerTasks.get(index).cancel();
        timerTasks.remove(index);
    }

    private void registerThread(MonitorThread thread, InputStream is) {
        thread = new MonitorThread(is);
        for (MonitorThread.MonitorThreadListener l : listeners)
            thread.addListener(l);
        thread.addListener(this);
        thread.start();
    }

    private void registerThreadC(Process p) {
        threadC = new WaitForThread(p);
        for (Event<Integer> l : listenersC)
            threadC.event.register(l);
        threadC.event.register(this);
        threadC.start();
    }

    @Override
    public boolean call(Object sender, Integer t) {
        if (t == 0) {
            sendStatus("*** 服务端已停止 ***");
            System.out.println("Server stopped successfully");
        } else {
            sendStatus("*** 服务端崩溃了！(错误码:" + t + ") ***");
            System.err.println("Server crashed(exit code: " + t + ")");
        }
        isRunning = false;
        for (Schedule schedule : schedules)
            if (schedule.timeType == Schedule.TIME_TYPE_SERVER_STOPPED)
                ScheduleTranslator.translate(this, schedule).run();
        if (timer != null)
            timer.cancel();
        if (pastTimer != null)
            pastTimer.stop();
        restoreMods();
        if (isRestart) {
            try {
                run();
            } catch (IOException ex) {
                MessageBox.Show("重启失败！");
                HMCLog.warn("Failed to launch!", ex);
            }
            isRestart = false;
        }
        return true;
    }

    private static void disactiveMods(ArrayList<String> inactiveExtMods,
                                      ArrayList<String> inactiveCoreMods, ArrayList<String> inactivePlugins) {
        disactiveModsByType(inactiveExtMods, "mods");
        disactiveModsByType(inactiveCoreMods, "coremods");
        disactiveModsByType(inactivePlugins, "plugins");
        disactived = true;
    }

    private static void disactiveModsByType(ArrayList<String> paramArrayOfString, String paramString) {
        restoreModsByType(paramString);

        System.out.println("禁用不活动的文件: " + paramString);
        if ((paramArrayOfString == null) || (paramArrayOfString.size() <= 0))
            return;
        File[] files = new File(Utilities.getGameDir(), paramString).listFiles();
        if (files == null) {
            System.out.println("没有文件: " + paramString);
            return;
        }
        for (File file : files)
            if (!file.isDirectory()) {
                String name = file.getName();

                if ((!paramArrayOfString.contains(name))
                    || ((!name.toLowerCase().endsWith(".zip")) && (!name.toLowerCase().endsWith(".jar"))))
                    continue;

                String newName = name + "X";
                File newFile = new File(file.getParentFile(), newName);

                if (newFile.exists())
                    newFile.delete();
                if (file.renameTo(newFile))
                    System.out.println("已禁用: " + name + ", 新名称: " + newFile.getName());
                else
                    System.out.println("无法禁用: " + name);
            }
    }

    private static void restoreModsByType(String paramString) {
        System.out.println("还原被禁用的文件: " + paramString);
        File[] files = new File(Utilities.getGameDir(), paramString).listFiles();
        if (files == null)
            return;
        for (File file : files)
            if (!file.isDirectory()) {
                String name = file.getName();
                String lowName = name.toLowerCase();
                if ((!lowName.endsWith(".zipx")) && (!lowName.endsWith(".jarx")))
                    continue;
                String newName = name.substring(0, name.length() - 1);

                File newFile = new File(file.getParentFile(), newName);
                if (newFile.exists())
                    file.delete();
                else if (!file.renameTo(newFile))
                    System.out.println("无法重命名: " + file.getName() + " 到: " + newFile.getName() + " 在: " + file.getParent());
            }
    }

    static void restoreMods() {
        if (disactived) {
            restoreModsByType("mods");
            restoreModsByType("coremods");
            restoreModsByType("plugins");
            disactived = false;
        }
    }

    @Override
    public void onStatus(String status) {
        System.out.println(status);
        if (isGettingPlayerNumber == 1) {
            Pattern p = Pattern.compile("There are [0-9]*/[0-9]* players online");
            Matcher m = p.matcher(status);
            if (!m.find())
                return;
            String s = m.group(0);
            s = s.substring(10, s.length() - 15);
            playerNumber = s;
            isGettingPlayerNumber = 2;
            return;
        } else if (isGettingPlayerNumber == 2) {
            try {
                status = status.substring(status.lastIndexOf("]") + 1);
                status = status.substring(status.indexOf(":") + 1);
            } catch (Exception e) {
                HMCLog.warn("Failed to substring status.", e);
            }
            String[] s;
            if (StrUtils.isNotBlank(status))
                s = status.trim().split(", ");
            else
                s = new String[0];
            Pair<String, String[]> p = new Pair<>(playerNumber, s);
            isGettingPlayerNumber = 0;
            gettingPlayerNumber.accept(p);
            return;
        }
        if (isDone == false) {
            Pattern p = Pattern.compile("\\[INFO\\] Done \\([0-9]*\\.[0-9]*s\\)! For help, type \"help\" or \"\\?\"");
            Matcher m = p.matcher(status);
            if (m.find()) {
                stoppedEvent.execute(null);
                timer = new Timer();
                timerTasks.clear();
                for (int i = 0; i < schedules.size(); i++) {
                    if (schedules.get(i).timeType == Schedule.TIME_TYPE_SERVER_STARTED) {
                        ScheduleTranslator.translate(this, schedules.get(i)).run();
                        continue;
                    }
                    if (schedules.get(i).timeType != Schedule.TIME_TYPE_PER)
                        continue;
                    long mill = (long) Math.floor(schedules.get(i).per * 60 * 1000);
                    timerTasks.add(ScheduleTranslator.translate(this, schedules.get(i)));
                    timer.schedule(timerTasks.get(i), mill, mill);
                }
                pastTimer = new javax.swing.Timer(1000, this);
                pastTimer.start();
                System.out.println("Server started!");
                sendStatus("*** 服务端已启动完成 ***");
                isDone = true;
            }
        }
        if (status.length() > 20)
            if (status.substring(20).contains("[SEVERE] This crash report has been saved to: "))
                for (Schedule schedule : schedules)
                    if (schedule.timeType == Schedule.TIME_TYPE_SERVER_CRASHED)
                        ScheduleTranslator.translate(this, schedule).run();
    }

    GregorianCalendar c = new GregorianCalendar();

    @Override
    public void actionPerformed(ActionEvent e) {
        c.setTime(new Date());
        if (c.get(Calendar.SECOND) != 0)
            return;
        int minute = c.get(Calendar.MINUTE);
        for (Schedule schedule : schedules) {
            if (schedule.timeType != Schedule.TIME_TYPE_PAST_HOUR)
                continue;
            if (schedule.per == minute)
                ScheduleTranslator.translate(this, schedule).run();
        }
    }

    private void sendStatus(String status) {
        for (MonitorThread.MonitorThreadListener l : listeners)
            l.onStatus(status);
    }
}
