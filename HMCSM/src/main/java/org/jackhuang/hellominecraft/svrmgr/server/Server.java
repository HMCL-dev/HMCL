/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
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
import org.jackhuang.hellominecraft.utils.functions.DoneListener0;
import org.jackhuang.hellominecraft.DoneListener1;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.utils.IOUtils;
import org.jackhuang.hellominecraft.utils.MessageBox;
import org.jackhuang.hellominecraft.utils.Pair;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.svrmgr.settings.Schedule;
import org.jackhuang.hellominecraft.svrmgr.settings.SettingsManager;
import org.jackhuang.hellominecraft.svrmgr.threads.MonitorThread;
import org.jackhuang.hellominecraft.svrmgr.threads.WaitForThread;
import org.jackhuang.hellominecraft.svrmgr.utils.Utilities;

/**
 *
 * @author hyh
 */
public class Server implements DoneListener1<Integer>, MonitorThread.MonitorThreadListener,
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
    DoneListener1<Pair<String, String[]>> gettingPlayerNumber;
    ArrayList<MonitorThread.MonitorThreadListener> listeners;
    ArrayList<DoneListener1<Integer>> listenersC;
    ArrayList<DoneListener0> listenersBegin, listenersDone;
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
	listeners = new ArrayList<MonitorThread.MonitorThreadListener>();
	listenersC = new ArrayList<DoneListener1<Integer>>();
	listenersBegin = new ArrayList<DoneListener0>();
	listenersDone = new ArrayList<DoneListener0>();
	schedules = new ArrayList<Schedule>();
	timerTasks = new ArrayList<TimerTask>();
    }

    public void addListener(MonitorThread.MonitorThreadListener l) {
	listeners.add(l);
    }

    public void addListener(DoneListener1<Integer> l) {
	listenersC.add(l);
    }

    public void addServerStartedListener(DoneListener0 l) {
	listenersBegin.add(l);
    }

    public void addServerDoneListener(DoneListener0 l) {
	listenersDone.add(l);
    }

    public void run() throws IOException {
	String jvmPath;
	if (StrUtils.isBlank(SettingsManager.settings.javaDir)) {
	    jvmPath = IOUtils.getJavaDir();
	} else {
	    jvmPath = SettingsManager.settings.javaDir;
	}
	String[] puts = new String[]{
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
	    for (DoneListener0 d : listenersBegin) {
		d.onDone();
	    }
	    sendStatus("*** 启动服务端中 ***");
	} catch (IOException ex) {
	    Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
	    isRunning = false;
	}
    }

    public void sendCommand(String cmd) {
	if (isRunning) {
	    try {
		sendStatus("发送指令: " + cmd);
		bw.write(cmd);
		bw.newLine();
		bw.flush();
	    } catch (IOException ex) {
		Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
	    }
	}
    }

    public void getPlayerNumber(DoneListener1<Pair<String, String[]>> d) {
	isGettingPlayerNumber = 1;
	gettingPlayerNumber = d;
	sendCommand("list");
    }

    public void restart() {
	isRestart = true;
	stop();
    }

    public void stop() {
	if (timer != null) {
	    timer.cancel();
	}
	sendCommand("stop");
    }

    public void shutdown() {
	if (timer != null) {
	    timer.cancel();
	}
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
	if (index == -1) {
	    return;
	}
	schedules.remove(index);
	timerTasks.get(index).cancel();
	timerTasks.remove(index);
    }

    private void registerThread(MonitorThread thread, InputStream is) {
	thread = new MonitorThread(is);
	for (MonitorThread.MonitorThreadListener l : listeners) {
	    thread.addListener(l);
	}
	thread.addListener(this);
	thread.start();
    }

    private void registerThreadC(Process p) {
	threadC = new WaitForThread(p);
	for (DoneListener1<Integer> l : listenersC) {
	    threadC.addListener(l);
	}
	threadC.addListener(this);
	threadC.start();
    }

    @Override
    public void onDone(Integer t) {
	if (t == 0) {
	    sendStatus("*** 服务端已停止 ***");
	    System.out.println("Server stopped successfully");
	} else {
	    sendStatus("*** 服务端崩溃了！(错误码:" + t + ") ***");
	    System.err.println("Server crashed(exit code: " + t + ")");
	}
	isRunning = false;
	for (int i = 0; i < schedules.size(); i++) {
	    if (schedules.get(i).timeType == Schedule.TIME_TYPE_SERVER_STOPPED) {
		ScheduleTranslator.translate(this, schedules.get(i)).run();
	    }
	}
	if (timer != null) {
	    timer.cancel();
	}
	if (pastTimer != null) {
	    pastTimer.stop();
	}
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
	if ((paramArrayOfString == null) || (paramArrayOfString.size() <= 0)) {
	    return;
	}
	File[] files = new File(Utilities.getGameDir(), paramString).listFiles();
	if (files == null) {
	    System.out.println("没有文件: " + paramString);
	    return;
	}
	for (int i = 0; i < files.length; i++) {
	    File file = files[i];
	    if (!file.isDirectory()) {
		String name = file.getName();

		if ((!paramArrayOfString.contains(name))
			|| ((!name.toLowerCase().endsWith(".zip")) && (!name.toLowerCase().endsWith(".jar")))) {
		    continue;
		}

		String newName = name + "X";
		File newFile = new File(file.getParentFile(), newName);

		if (newFile.exists()) {
		    newFile.delete();
		}
		if (file.renameTo(newFile)) {
		    System.out.println("已禁用: " + name + ", 新名称: " + newFile.getName());
		} else {
		    System.out.println("无法禁用: " + name);
		}
	    }
	}
    }

    private static void restoreModsByType(String paramString) {
	System.out.println("还原被禁用的文件: " + paramString);
	File[] files = new File(Utilities.getGameDir(), paramString).listFiles();
	if (files == null) {
	    return;
	}
	for (int i = 0; i < files.length; i++) {
	    File file = files[i];
	    if (file.isDirectory()) {
		continue;
	    }
	    String name = file.getName();
	    String lowName = name.toLowerCase();
	    if ((!lowName.endsWith(".zipx")) && (!lowName.endsWith(".jarx"))) {
		continue;
	    }
	    String newName = name.substring(0, name.length() - 1);

	    File newFile = new File(file.getParentFile(), newName);
	    if (newFile.exists()) {
		file.delete();
	    } else {
		if (!file.renameTo(newFile)) {
		    System.out.println("无法重命名: " + file.getName() + " 到: " + newFile.getName() + " 在: " + file.getParent());
		}
	    }
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
	    if (!m.find()) {
		return;
	    }
	    String s = m.group(0);
	    s = s.substring(10, s.length() - 15);
	    playerNumber = s;
	    isGettingPlayerNumber = 2;
	    return;
	} else if (isGettingPlayerNumber == 2) {
	    try {
		status = status.substring(status.lastIndexOf("]")+1);
		status = status.substring(status.indexOf(":")+1);
	    } catch(Exception e) {
		HMCLog.warn("Failed to substring status.", e);
	    }
	    String[] s;
	    if(StrUtils.isNotBlank(status)) {
		s = status.trim().split(", ");
	    } else {
		s = new String[0];
	    }
	    Pair<String, String[]> p = new Pair<String, String[]>(playerNumber, s);
	    isGettingPlayerNumber = 0;
	    gettingPlayerNumber.onDone(p);
	    return;
	}
	if (isDone == false) {
	    Pattern p = Pattern.compile("\\[INFO\\] Done \\([0-9]*\\.[0-9]*s\\)! For help, type \"help\" or \"\\?\"");
	    Matcher m = p.matcher(status);
	    if (m.find()) {
		for (DoneListener0 d : listenersDone) {
		    d.onDone();
		}
		timer = new Timer();
		timerTasks.clear();
		for (int i = 0; i < schedules.size(); i++) {
		    if (schedules.get(i).timeType == Schedule.TIME_TYPE_SERVER_STARTED) {
			ScheduleTranslator.translate(this, schedules.get(i)).run();
			continue;
		    }
		    if (schedules.get(i).timeType != Schedule.TIME_TYPE_PER) {
			continue;
		    }
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
	if (status.length() > 20) {
	    if (status.substring(20).contains("[SEVERE] This crash report has been saved to: ")) {
		for (int i = 0; i < schedules.size(); i++) {
		    if (schedules.get(i).timeType == Schedule.TIME_TYPE_SERVER_CRASHED) {
			ScheduleTranslator.translate(this, schedules.get(i)).run();
		    }
		}
	    }
	}
    }

    GregorianCalendar c = new GregorianCalendar();

    @Override
    public void actionPerformed(ActionEvent e) {
	c.setTime(new Date());
	if (c.get(Calendar.SECOND) != 0) {
	    return;
	}
	int minute = c.get(Calendar.MINUTE);
	for (int i = 0; i < schedules.size(); i++) {
	    if (schedules.get(i).timeType != Schedule.TIME_TYPE_PAST_HOUR) {
		continue;
	    }
	    if (schedules.get(i).per == minute) {
		ScheduleTranslator.translate(this, schedules.get(i)).run();
	    }
	}
    }

    private void sendStatus(String status) {
	for (MonitorThread.MonitorThreadListener l : listeners) {
	    l.onStatus(status);
	}
    }
}
