/*
 * Hello Minecraft!.
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
package org.jackhuang.hellominecraft.util.log.message;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author huangyuhui
 */
public class ParameterizedMessage
    implements IMessage {

    public static final String RECURSION_PREFIX = "[...";
    public static final String RECURSION_SUFFIX = "...]";
    public static final String ERROR_PREFIX = "[!!!";
    public static final String ERROR_SEPARATOR = "=>";
    public static final String ERROR_MSG_SEPARATOR = ":";
    public static final String ERROR_SUFFIX = "!!!]";
    private static final long serialVersionUID = -665975803997290697L;
    private static final int HASHVAL = 31;
    private static final char DELIM_START = '{';
    private static final char DELIM_STOP = '}';
    private static final char ESCAPE_CHAR = '\\';
    private final String messagePattern;
    private final String[] stringArgs;
    private transient Object[] argArray;
    private transient String formattedMessage;
    private transient Throwable throwable;

    public ParameterizedMessage(String messagePattern, String[] stringArgs, Throwable throwable) {
        this.messagePattern = messagePattern;
        this.stringArgs = stringArgs;
        this.throwable = throwable;
    }

    public ParameterizedMessage(String messagePattern, Object[] objectArgs, Throwable throwable) {
        this.messagePattern = messagePattern;
        this.throwable = throwable;
        this.stringArgs = parseArguments(objectArgs);
    }

    public ParameterizedMessage(String messagePattern, Object[] arguments) {
        this.messagePattern = messagePattern;
        this.stringArgs = parseArguments(arguments);
    }

    public ParameterizedMessage(String messagePattern, Object arg) {
        this(messagePattern, new Object[] { arg });
    }

    public ParameterizedMessage(String messagePattern, Object arg1, Object arg2) {
        this(messagePattern, new Object[] { arg1, arg2 });
    }

    private String[] parseArguments(Object[] arguments) {
        if (arguments == null)
            return null;
        int argsCount = countArgumentPlaceholders(this.messagePattern);
        int resultArgCount = arguments.length;
        if ((argsCount < arguments.length)
            && (this.throwable == null) && ((arguments[(arguments.length - 1)] instanceof Throwable))) {
            this.throwable = ((Throwable) arguments[(arguments.length - 1)]);
            resultArgCount--;
        }

        this.argArray = new Object[resultArgCount];
        System.arraycopy(arguments, 0, this.argArray, 0, resultArgCount);
        String[] strArgs;
        if ((argsCount == 1) && (this.throwable == null) && (arguments.length > 1)) {
            strArgs = new String[1];
            strArgs[0] = deepToString(arguments);
        } else {
            strArgs = new String[resultArgCount];
            for (int i = 0; i < strArgs.length; i++)
                strArgs[i] = deepToString(arguments[i]);
        }
        return strArgs;
    }

    @Override
    public String getFormattedMessage() {
        if (this.formattedMessage == null)
            this.formattedMessage = formatMessage(this.messagePattern, this.stringArgs);
        return this.formattedMessage;
    }

    @Override
    public String getFormat() {
        return this.messagePattern;
    }

    @Override
    public Object[] getParameters() {
        if (this.argArray != null)
            return this.argArray;
        return this.stringArgs;
    }

    @Override
    public Throwable getThrowable() {
        return this.throwable;
    }

    protected String formatMessage(String msgPattern, String[] sArgs) {
        return format(msgPattern, sArgs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        ParameterizedMessage that = (ParameterizedMessage) o;
        if (this.messagePattern != null ? !this.messagePattern.equals(that.messagePattern) : that.messagePattern != null)
            return false;

        return Arrays.equals(this.stringArgs, that.stringArgs);
    }

    @Override
    public int hashCode() {
        int result = this.messagePattern != null ? this.messagePattern.hashCode() : 0;
        result = HASHVAL * result + (this.stringArgs != null ? Arrays.hashCode(this.stringArgs) : 0);
        return result;
    }

    public static String format(String messagePattern, Object[] arguments) {
        if ((messagePattern == null) || (arguments == null) || (arguments.length == 0))
            return messagePattern;

        StringBuilder result = new StringBuilder();
        int escapeCounter = 0;
        int currentArgument = 0;
        for (int i = 0; i < messagePattern.length(); i++) {
            char curChar = messagePattern.charAt(i);
            if (curChar == ESCAPE_CHAR)
                escapeCounter++;
            else if ((curChar == DELIM_START)
                     && (i < messagePattern.length() - 1)
                     && (messagePattern.charAt(i + 1) == DELIM_STOP)) {
                int escapedEscapes = escapeCounter / 2;
                for (int j = 0; j < escapedEscapes; j++)
                    result.append(ESCAPE_CHAR);

                if (escapeCounter % 2 == 1) {
                    result.append(DELIM_START);
                    result.append(DELIM_STOP);
                } else {
                    if (currentArgument < arguments.length)
                        result.append(arguments[currentArgument]);
                    else
                        result.append(DELIM_START).append(DELIM_STOP);
                    currentArgument++;
                }
                i++;
                escapeCounter = 0;
            } else {
                if (escapeCounter > 0) {
                    for (int j = 0; j < escapeCounter; j++)
                        result.append(ESCAPE_CHAR);
                    escapeCounter = 0;
                }
                result.append(curChar);
            }
        }
        return result.toString();
    }

    public static int countArgumentPlaceholders(String messagePattern) {
        if (messagePattern == null)
            return 0;

        int delim = messagePattern.indexOf(123);

        if (delim == -1)
            return 0;
        int result = 0;
        boolean isEscaped = false;
        for (int i = 0; i < messagePattern.length(); i++) {
            char curChar = messagePattern.charAt(i);
            switch (curChar) {
            case ESCAPE_CHAR:
                isEscaped = !isEscaped;
                break;
            case DELIM_START:
                if ((!isEscaped)
                    && (i < messagePattern.length() - 1)
                    && (messagePattern.charAt(i + 1) == DELIM_STOP)) {
                    result++;
                    i++;
                }
                isEscaped = false;
                break;
            default:
                isEscaped = false;
                break;
            }
        }
        return result;
    }

    public static String deepToString(Object o) {
        if (o == null)
            return null;
        if (o instanceof String)
            return (String) o;
        StringBuilder str = new StringBuilder();
        Set<String> dejaVu = new HashSet<>();
        recursiveDeepToString(o, str, dejaVu);
        return str.toString();
    }

    private static void recursiveDeepToString(Object o, StringBuilder str, Set<String> dejaVu) {
        if (o == null) {
            str.append("null");
            return;
        }
        if ((o instanceof String)) {
            str.append(o);
            return;
        }
        Class<?> oClass = o.getClass();
        if (oClass.isArray())
            if (oClass == byte[].class)
                str.append(Arrays.toString((byte[]) (byte[]) o));
            else if (oClass == short[].class)
                str.append(Arrays.toString((short[]) (short[]) o));
            else if (oClass == int[].class)
                str.append(Arrays.toString((int[]) (int[]) o));
            else if (oClass == long[].class)
                str.append(Arrays.toString((long[]) (long[]) o));
            else if (oClass == float[].class)
                str.append(Arrays.toString((float[]) (float[]) o));
            else if (oClass == double[].class)
                str.append(Arrays.toString((double[]) (double[]) o));
            else if (oClass == boolean[].class)
                str.append(Arrays.toString((boolean[]) (boolean[]) o));
            else if (oClass == char[].class)
                str.append(Arrays.toString((char[]) (char[]) o));
            else {
                String id = identityToString(o);
                if (dejaVu.contains(id))
                    str.append(RECURSION_PREFIX).append(id).append(RECURSION_SUFFIX);
                else {
                    dejaVu.add(id);
                    Object[] oArray = (Object[]) (Object[]) o;
                    str.append("[");
                    boolean first = true;
                    for (Object current : oArray) {
                        if (first)
                            first = false;
                        else
                            str.append(", ");
                        recursiveDeepToString(current, str, new HashSet<>(dejaVu));
                    }
                    str.append("]");
                }
            }
        else if ((o instanceof Map)) {
            String id = identityToString(o);
            if (dejaVu.contains(id))
                str.append(RECURSION_PREFIX).append(id).append(RECURSION_SUFFIX);
            else {
                dejaVu.add(id);
                Map<?, ?> oMap = (Map<?, ?>) o;
                str.append("{");
                boolean isFirst = true;
                for (Map.Entry<?, ?> current : oMap.entrySet()) {
                    if (isFirst)
                        isFirst = false;
                    else
                        str.append(", ");
                    Object key = current.getKey();
                    Object value = current.getValue();
                    recursiveDeepToString(key, str, new HashSet<>(dejaVu));
                    str.append("=");
                    recursiveDeepToString(value, str, new HashSet<>(dejaVu));
                }
                str.append("}");
            }
        } else if ((o instanceof Collection)) {
            String id = identityToString(o);
            if (dejaVu.contains(id))
                str.append(RECURSION_PREFIX).append(id).append(RECURSION_SUFFIX);
            else {
                dejaVu.add(id);
                Collection<?> oCol = (Collection<?>) o;
                str.append("[");
                boolean isFirst = true;
                for (Object anOCol : oCol) {
                    if (isFirst)
                        isFirst = false;
                    else
                        str.append(", ");
                    recursiveDeepToString(anOCol, str, new HashSet<>(dejaVu));
                }
                str.append("]");
            }
        } else if ((o instanceof Date)) {
            Date date = (Date) o;
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

            str.append(format.format(date));
        } else
            try {
                str.append(o.toString());
            } catch (Throwable t) {
                str.append(ERROR_PREFIX);
                str.append(identityToString(o));
                str.append(ERROR_SEPARATOR);
                String msg = t.getMessage();
                String className = t.getClass().getName();
                str.append(className);
                if (!className.equals(msg)) {
                    str.append(ERROR_MSG_SEPARATOR);
                    str.append(msg);
                }
                str.append(ERROR_SUFFIX);
            }
    }

    public static String identityToString(Object obj) {
        if (obj == null)
            return null;
        return obj.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(obj));
    }

    @Override
    public String toString() {
        return "ParameterizedMessage[messagePattern=" + this.messagePattern + ", stringArgs=" + Arrays.toString(this.stringArgs) + ", throwable=" + this.throwable + "]";
    }
}
