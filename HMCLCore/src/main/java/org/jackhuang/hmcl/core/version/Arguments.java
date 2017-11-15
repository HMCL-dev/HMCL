/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.core.version;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.jackhuang.hmcl.util.ArrayUtils;
import org.jackhuang.hmcl.util.CollectionUtils;

/**
 *
 * @author huang
 */
public class Arguments {
    @SerializedName("game")
    public List<Argument> game;
    @SerializedName("jvm")
    public List<Argument> jvm;
    
    public static Arguments clone(Arguments original) {
        if (original == null)
            return null;
        Arguments ret = new Arguments();
        ret.game = CollectionUtils.deepCopy(original.game, value -> (Argument) value.clone());
        ret.jvm = CollectionUtils.deepCopy(original.jvm, value -> (Argument) value.clone());
        return ret;
    }
    
    public static Arguments merge(Arguments a, Arguments b) {
        if (a == null) return b;
        else if (b == null) return a;
        Arguments ret = new Arguments();
        ret.game = ArrayUtils.merge(a.game, b.game);
        ret.jvm = ArrayUtils.merge(a.jvm, b.jvm);
        if (ret.game == null && ret.jvm == null) return null;
        return ret;
    }

    public static List<String> parseStringArguments(List<String> arguments, Map<String, String> keys) {
        return CollectionUtils.map(arguments, str -> keys.getOrDefault(str, str));
    }

    public static List<String> parseArguments(List<Argument> arguments, Map<String, String> keys) {
        return parseArguments(arguments, keys, Collections.EMPTY_MAP);
    }

    public static List<String> parseArguments(List<Argument> arguments, Map<String, String> keys, Map<String, Boolean> features) {
        return CollectionUtils.flatMap(arguments, arg -> arg.toString(keys, features));
    }
    
    public static final List<Argument> DEFAULT_JVM_ARGUMENTS;
    public static final List<Argument> DEFAULT_GAME_ARGUMENTS;
    
    static {
        List<Argument> jvm = new LinkedList<>();
        jvm.add(new RuledArgument(Collections.singletonList(new Rules("allow", new OSRestriction("osx"))), Collections.singletonList("-XstartOnFirstThread")));
        jvm.add(new RuledArgument(Collections.singletonList(new Rules("allow", new OSRestriction("windows"))), Collections.singletonList("-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump")));
        jvm.add(new RuledArgument(Collections.singletonList(new Rules("allow", new OSRestriction("windows", "^10\\."))), Arrays.asList("-Dos.name=Windows 10", "-Dos.version=10.0")));
        jvm.add(new StringArgument("-Djava.library.path=${natives_directory}"));
        jvm.add(new StringArgument("-Dminecraft.launcher.brand=${launcher_name}"));
        jvm.add(new StringArgument("-Dminecraft.launcher.version=${launcher_version}"));
        jvm.add(new StringArgument("-cp"));
        jvm.add(new StringArgument("${classpath}"));
        DEFAULT_JVM_ARGUMENTS = Collections.unmodifiableList(jvm);
        
        List<Argument> game = new LinkedList<>();
        game.add(new RuledArgument(Collections.singletonList(new Rules("allow", Collections.singletonMap("has_custom_resolution", true))), Arrays.asList("--width", "${resolution_width}", "--height", "${resolution_height}")));
        DEFAULT_GAME_ARGUMENTS = Collections.unmodifiableList(game);
    }
}
