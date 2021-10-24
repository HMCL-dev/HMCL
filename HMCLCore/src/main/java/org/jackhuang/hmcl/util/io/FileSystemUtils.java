package org.jackhuang.hmcl.util.io;

import java.util.regex.PatternSyntaxException;

public final class FileSystemUtils {
    private FileSystemUtils() {
    }

    private static char EOL = 0;

    private static boolean isRegexMeta(char var0) {
        return ".^$+{[]|()".indexOf(var0) != -1;
    }

    private static boolean isGlobMeta(char var0) {
        return "\\*?[{".indexOf(var0) != -1;
    }

    private static char next(String var0, int var1) {
        return var1 < var0.length() ? var0.charAt(var1) : EOL;
    }

    public static String toRegexPattern(String var0) {
        boolean var1 = false;
        StringBuilder var2 = new StringBuilder("^");
        int var3 = 0;

        while(true) {
            while(var3 < var0.length()) {
                char var4 = var0.charAt(var3++);
                switch(var4) {
                    case '*':
                        if (next(var0, var3) == '*') {
                            var2.append(".*");
                            ++var3;
                        } else {
                            var2.append("[^/]*");
                        }
                        break;
                    case ',':
                        if (var1) {
                            var2.append(")|(?:");
                        } else {
                            var2.append(',');
                        }
                        break;
                    case '/':
                        var2.append(var4);
                        break;
                    case '?':
                        var2.append("[^/]");
                        break;
                    case '[':
                        var2.append("[[^/]&&[");
                        if (next(var0, var3) == '^') {
                            var2.append("\\^");
                            ++var3;
                        } else {
                            if (next(var0, var3) == '!') {
                                var2.append('^');
                                ++var3;
                            }

                            if (next(var0, var3) == '-') {
                                var2.append('-');
                                ++var3;
                            }
                        }

                        boolean var6 = false;
                        char var7 = 0;

                        while(var3 < var0.length()) {
                            var4 = var0.charAt(var3++);
                            if (var4 == ']') {
                                break;
                            }

                            if (var4 == '/') {
                                throw new PatternSyntaxException("Explicit 'name separator' in class", var0, var3 - 1);
                            }

                            if (var4 == '\\' || var4 == '[' || var4 == '&' && next(var0, var3) == '&') {
                                var2.append('\\');
                            }

                            var2.append(var4);
                            if (var4 == '-') {
                                if (!var6) {
                                    throw new PatternSyntaxException("Invalid range", var0, var3 - 1);
                                }

                                if ((var4 = next(var0, var3++)) == EOL || var4 == ']') {
                                    break;
                                }

                                if (var4 < var7) {
                                    throw new PatternSyntaxException("Invalid range", var0, var3 - 3);
                                }

                                var2.append(var4);
                                var6 = false;
                            } else {
                                var6 = true;
                                var7 = var4;
                            }
                        }

                        if (var4 != ']') {
                            throw new PatternSyntaxException("Missing ']", var0, var3 - 1);
                        }

                        var2.append("]]");
                        break;
                    case '\\':
                        if (var3 == var0.length()) {
                            throw new PatternSyntaxException("No character to escape", var0, var3 - 1);
                        }

                        char var5 = var0.charAt(var3++);
                        if (isGlobMeta(var5) || isRegexMeta(var5)) {
                            var2.append('\\');
                        }

                        var2.append(var5);
                        break;
                    case '{':
                        if (var1) {
                            throw new PatternSyntaxException("Cannot nest groups", var0, var3 - 1);
                        }

                        var2.append("(?:(?:");
                        var1 = true;
                        break;
                    case '}':
                        if (var1) {
                            var2.append("))");
                            var1 = false;
                        } else {
                            var2.append('}');
                        }
                        break;
                    default:
                        if (isRegexMeta(var4)) {
                            var2.append('\\');
                        }

                        var2.append(var4);
                }
            }

            if (var1) {
                throw new PatternSyntaxException("Missing '}", var0, var3 - 1);
            }

            return var2.append('$').toString();
        }
    }
}
