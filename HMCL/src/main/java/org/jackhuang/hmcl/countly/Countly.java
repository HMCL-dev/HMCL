/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.countly;

import org.jackhuang.hmcl.util.io.HttpRequest;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static org.jackhuang.hmcl.util.Pair.pair;

public class Countly {

    private String deviceId;
    private String endpoint;
    private String serverURL;

    public void sendMetric(String metrics) throws IOException {
        HttpRequest.GET(serverURL + endpoint,
                pair("begin_session", "1"),
                pair("session_id", "1"),
                pair("metrics", metrics),
                pair("device_id", deviceId),
                pair("timestamp", Long.toString(System.currentTimeMillis())),
                pair("tz", Integer.toString(TimeZone.getDefault().getOffset(new Date().getTime()) / 60000)),
                pair("hour", Integer.toString(currentHour())),
                pair("dow", Integer.toString(currentDayOfWeek())),
                pair("app_key", APP_KEY),
                pair("sdk_name", "java-native"),
                pair("sdk_version", "20.11.1"))
                .getString();
    }

    private static int getTimezoneOffset() {
        return TimeZone.getDefault().getOffset(new Date().getTime()) / 60000;
    }

    private static String getLocale() {
        final Locale locale = Locale.getDefault();
        return locale.getLanguage() + "_" + locale.getCountry();
    }

    private static int currentHour() {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
    }

    private int currentDayOfWeek() {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        switch (day) {
            case Calendar.SUNDAY:
                return 0;
            case Calendar.MONDAY:
                return 1;
            case Calendar.TUESDAY:
                return 2;
            case Calendar.WEDNESDAY:
                return 3;
            case Calendar.THURSDAY:
                return 4;
            case Calendar.FRIDAY:
                return 5;
            case Calendar.SATURDAY:
                return 6;
        }
        return 0;
    }

    private static final String APP_KEY = "";
}
