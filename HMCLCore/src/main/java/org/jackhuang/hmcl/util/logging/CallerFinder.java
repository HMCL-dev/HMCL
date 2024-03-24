package org.jackhuang.hmcl.util.logging;

/**
 * @author Glavo
 */
final class CallerFinder {
    private static final String LOGGER_CLASS_NAME = Logger.class.getName();

    static String getCaller() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

        int i = 0;
        while (i++ < stackTrace.length) {
            if (stackTrace[i].getClassName().equals(LOGGER_CLASS_NAME))
                break;
        }

        while (i++ < stackTrace.length) {
            StackTraceElement element = stackTrace[i];
            String cname = element.getClassName();
            if (!cname.equals(LOGGER_CLASS_NAME) && !cname.startsWith("java.lang.reflect.") && !cname.startsWith("sun.reflect.")) {
                return cname + '.' + element.getMethodName();
            }
        }

        return null;
    }

    private CallerFinder() {
    }
}
