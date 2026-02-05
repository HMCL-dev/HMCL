package org.jackhuang.hmcl.util.platform.windows;

import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.platform.SystemUtils;

import java.util.ArrayList;
import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class WindowsEvents {
    private WindowsEvents() {
        throw new AssertionError();
    }

    private static final String psCommand = "Get-WinEvent -MaxEvents 50 -FilterHashtable @{LogName='Application'} | ForEach-Object { " + "  [PSCustomObject]@{ " + "    timeCreated = $_.TimeCreated.ToString('yyyy-MM-dd HH:mm:ss'); " + "    level = $_.LevelDisplayName; " + "    source = $_.ProviderName; " + "    eventId = $_.Id; " + "    message = $_.Message; " + "    rawXml = $_.ToXml() " + "  } " + "} | ConvertTo-Json -Compress";


    public record LogEntry(String timeCreated, String level, String source, int eventId, String message) {
    }

    public static List<LogEntry> getApplicationEvents() {
        List<LogEntry> results = new ArrayList<>();

        try {
            String rawJson = SystemUtils.run("powershell.exe", "-NoProfile", "-Command", psCommand).trim();

            if (!rawJson.isEmpty()) {
                if (rawJson.startsWith("{")) {
                    results.add(JsonUtils.fromNonNullJson(rawJson, LogEntry.class));
                } else if (rawJson.startsWith("[")) {
                    results = JsonUtils.fromNonNullJson(rawJson, new TypeToken<>() {
                    });
                }
            }
        } catch (Exception e) {
            LOG.warning("Failed to fetch detailed application logs", e);
        }
        return results;
    }
}

