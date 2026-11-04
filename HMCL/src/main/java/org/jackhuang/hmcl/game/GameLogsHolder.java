// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.Stage;
import org.jackhuang.hmcl.ui.LogWindow;
import org.jackhuang.hmcl.util.CircularArrayList;
import org.jackhuang.hmcl.util.Log4jLevel;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Calboot
 */
public final class GameLogsHolder {

    private final CircularArrayList<Log> logs;
    private final ReadOnlyStringWrapper lastLogLine = new ReadOnlyStringWrapper();
    private final Map<Log4jLevel, SimpleIntegerProperty> levelCountMap = new EnumMap<>(Log4jLevel.class);
    private final Map<Log4jLevel, SimpleBooleanProperty> levelShownMap = new EnumMap<>(Log4jLevel.class);

    private final ObservableList<Log> items = FXCollections.observableList(new CircularArrayList<>());

    private volatile @Nullable Path logFile = null;

    {
        for (Log4jLevel level : Log4jLevel.values()) {
            levelCountMap.put(level, new SimpleIntegerProperty());
            levelShownMap.put(level, new SimpleBooleanProperty(true));
        }
    }

    private final ManagedProcess gameProcess;
    private @Nullable Stage stage = null;

    public GameLogsHolder(ManagedProcess gameProcess) {
        this.logs = new CircularArrayList<>();
        this.gameProcess = gameProcess;
        for (SimpleBooleanProperty property : levelShownMap.values()) {
            property.addListener(o -> shakeLogs());
        }
    }

    void setLogFile(@NotNull Path logFile) {
        this.logFile = logFile;
    }

    public @Nullable Path getLogFile() {
        return logFile;
    }

    public ManagedProcess getGameProcess() {
        return gameProcess;
    }

    public Map<Log4jLevel, SimpleIntegerProperty> getLevelCountMap() {
        return levelCountMap;
    }

    public Map<Log4jLevel, SimpleBooleanProperty> getLevelShownMap() {
        return levelShownMap;
    }

    public ObservableList<Log> getItems() {
        return items;
    }

    public Stage showWindow() {
        if (stage == null) {
            stage = new LogWindow(this);
        }
        stage.show();
        stage.requestFocus();
        return stage;
    }

    public void logLine(Log log) {
        Log4jLevel level = log.getLevel();
        logs.add(log);
        lastLogLine.set(log.getLog());
        if (levelShownMap.get(level).get())
            items.add(log);

        SimpleIntegerProperty property = levelCountMap.get(level);
        property.set(property.get() + 1);
        checkLogCount();
    }

    public void logLines(List<Log> logs) {
        this.logs.addAll(logs);
        this.items.addAll(logs.stream().filter(log -> levelShownMap.get(log.getLevel()).get()).toList());

        Map<Log4jLevel, Integer> levelCounts = new EnumMap<>(Log4jLevel.class);
        logs.forEach(log -> {
            var level = log.getLevel();
            levelCounts.put(level, levelCounts.computeIfAbsent(level, k -> 0) + 1);
        });
        levelCounts.forEach((level, count) -> {
            SimpleIntegerProperty property = levelCountMap.get(level);
            property.set(property.get() + count);
        });

        this.lastLogLine.set(logs.get(logs.size() - 1).getLog());
        checkLogCount();
    }

    public ReadOnlyStringProperty lastLogLineProperty() {
        return lastLogLine.getReadOnlyProperty();
    }

    private void shakeLogs() {
        items.setAll(logs.stream().filter(log -> levelShownMap.get(log.getLevel()).get()).collect(Collectors.toList()));
    }

    private void checkLogCount() {
        int nRemove = logs.size() - Log.getLogLines();
        if (nRemove <= 0)
            return;

        int itemsSize = items.size();
        int count = 0;

        for (int i = 0; i < nRemove; i++) {
            Log removedLog = logs.removeFirst();
            if (itemsSize > count && items.get(count) == removedLog)
                count++;
        }

        items.remove(0, count);
    }

    public void clear() {
        items.clear();
        logs.clear();
    }
}
