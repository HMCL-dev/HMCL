/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jackhuang.hmcl.game.Argument;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.RuledArgument;
import org.jackhuang.hmcl.game.StringArgument;
import org.jackhuang.hmcl.task.Schedulers;

import java.awt.*;
import java.io.File;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Constants.
 *
 * @author huangyuhui
 */
public final class Constants {

    private Constants() {
    }

    public static final String DEFAULT_ENCODING = "UTF-8";
    public static final Charset DEFAULT_CHARSET = Charsets.UTF_8;
    public static final Charset SYSTEM_CHARSET = Charset.forName(OperatingSystem.ENCODING);

    public static final String DEFAULT_LIBRARY_URL = "https://libraries.minecraft.net/";
    public static final String DEFAULT_VERSION_DOWNLOAD_URL = "http://s3.amazonaws.com/Minecraft.Download/versions/";
    public static final String DEFAULT_INDEX_URL = "http://s3.amazonaws.com/Minecraft.Download/indexes/";

    public static final SimpleDateFormat DEFAULT_DATE_FORMAT = new SimpleDateFormat("HH:mm:ss");

    public static Consumer<Runnable> UI_THREAD_SCHEDULER = s -> Schedulers.computation().schedule(s::run);
    
    public static final Consumer<Runnable> SWING_UI_THREAD_SCHEDULER = s -> {
        if (EventQueue.isDispatchThread())
            s.run();
        else
            EventQueue.invokeLater(s);
    };
    
    public static final Consumer<Runnable> JAVAFX_UI_THREAD_SCHEDULER = s -> {
        if (javafx.application.Platform.isFxApplicationThread())
            s.run();
        else
            javafx.application.Platform.runLater(s);
    };

    public static final Gson GSON = new GsonBuilder()
            .enableComplexMapKeySerialization()
            .setPrettyPrinting()
            .registerTypeAdapter(Library.class, Library.Serializer.INSTANCE)
            .registerTypeAdapter(Argument.class, Argument.Serializer.INSTANCE)
            .registerTypeAdapter(StringArgument.class, Argument.Serializer.INSTANCE)
            .registerTypeAdapter(RuledArgument.class, RuledArgument.Serializer.INSTANCE)
            .registerTypeAdapter(Date.class, DateTypeAdapter.INSTANCE)
            .registerTypeAdapter(UUID.class, UUIDTypeAdapter.INSTANCE)
            .registerTypeAdapter(Platform.class, Platform.Serializer.INSTANCE)
            .registerTypeAdapter(File.class, FileTypeAdapter.INSTANCE)
            .registerTypeAdapterFactory(ValidationTypeAdapterFactory.INSTANCE)
            .registerTypeAdapterFactory(LowerCaseEnumTypeAdapterFactory.INSTANCE)
            .create();

    public static <T> Predicate<T> truePredicate() {
        return s -> true;
    }

    public static <T> Predicate<T> falsePredicate() {
        return s -> false;
    }

    public static <T> Consumer<T> emptyConsumer() {
        return x -> {};
    }
}
