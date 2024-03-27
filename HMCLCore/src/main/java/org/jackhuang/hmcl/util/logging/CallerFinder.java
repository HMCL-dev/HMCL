package org.jackhuang.hmcl.util.logging;

/**
 * @author Glavo
 */
final class CallerFinder {
    static String getCaller() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

        int i = 0;
        while (i++ < stackTrace.length) {
            if (stackTrace[i].getClassName().equals(Logger.CLASS_NAME))
                break;
        }

        while (i++ < stackTrace.length) {
            StackTraceElement element = stackTrace[i];
            String cname = element.getClassName();
            if (!cname.equals(Logger.CLASS_NAME) && !cname.startsWith("java.lang.reflect.") && !cname.startsWith("sun.reflect.")) {
                return cname + '.' + element.getMethodName();
            }
        }

        return null;
    }

    private CallerFinder() {
    }
}
