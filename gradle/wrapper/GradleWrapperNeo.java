/*
 * Gradle Wrapper Neo single-file source distribution.
 *
 * This source file is part of Gradle Wrapper Neo 0.1.0.
 * Documentation and updates: https://github.com/Glavo/gradle-wrapper-neo
 *
 * Place this file at gradle/wrapper/GradleWrapperNeo.java in a Gradle project.
 * Keep gradle-wrapper.properties in the same directory and the Gradle Wrapper Neo
 * launch scripts (gradlew, gradlew.bat, and gradlew.ps1) in the project root.
 * Run ./gradlew on POSIX systems or gradlew.bat on Windows as you would use the
 * standard Gradle Wrapper.
 */

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import javax.lang.model.SourceVersion;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import static java.lang.String.format;
import static java.util.Collections.emptyList;

/*
 * Copyright 2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.wrapper;

public class GradleWrapperNeo {

    public static final String GRADLE_USER_HOME_OPTION = "g";

    public static final String GRADLE_USER_HOME_DETAILED_OPTION = "gradle-user-home";

    public static final String GRADLE_QUIET_OPTION = "q";

    public static final String GRADLE_QUIET_DETAILED_OPTION = "quiet";

    public static void main(String[] args) throws Exception {
        if (Bootstrap.handle(args, GradleWrapperNeo.class)) {
            return;
        }
        prepareWrapper(args).execute();
    }

    private static Action prepareWrapper(String[] args) throws Exception {
        File appHome = appHome();
        File propertiesFile = wrapperProperties();
        CommandLineParser parser = new CommandLineParser();
        parser.allowUnknownOptions();
        parser.option(GRADLE_USER_HOME_OPTION, GRADLE_USER_HOME_DETAILED_OPTION).hasArgument();
        parser.option(GRADLE_QUIET_OPTION, GRADLE_QUIET_DETAILED_OPTION);
        SystemPropertiesCommandLineConverter converter = new SystemPropertiesCommandLineConverter();
        converter.configure(parser);
        ParsedCommandLine options = parser.parse(args);
        Map<String, String> commandLineSystemProperties = converter.convert(options, new HashMap<String, String>());
        Map<String, String> projectSystemProperties = PropertiesFileHandler.getSystemProperties(new File(appHome, "gradle.properties"));
        // If the Gradle system properties may define a custom Gradle home, which needs to be set before loading user gradle.properties
        maybeAddGradleUserHomeSystemProperty(projectSystemProperties, commandLineSystemProperties);
        File gradleUserHome = gradleUserHome(options);
        File userGradleProperties = new File(gradleUserHome, "gradle.properties");
        Map<String, String> userSystemProperties = new HashMap<>(PropertiesFileHandler.getSystemProperties(userGradleProperties));
        // Inception: Gradle user home cannot be changed with configuration from the Gradle user home
        boolean invalidGradleUserHome = userSystemProperties.remove(GradleUserHomeLookup.GRADLE_USER_HOME_PROPERTY_KEY) != null;
        // Set all system properties from all Gradle sources with correct precedence: project (lowest) < user < cli (highest)
        addSystemProperties(projectSystemProperties, userSystemProperties, commandLineSystemProperties);
        Logger logger = logger(options);
        if (invalidGradleUserHome) {
            logger.log("WARNING Ignored custom Gradle user home location configured in Gradle user home: " + userGradleProperties.getAbsolutePath());
        }
        WrapperExecutor wrapperExecutor = WrapperExecutor.forWrapperPropertiesFile(propertiesFile);
        WrapperConfiguration configuration = wrapperExecutor.getConfiguration();
        configuration.setMirrorConfiguration(MirrorConfiguration.load(gradleUserHome));
        IDownload download = new Download(logger, "gradlew", Download.UNKNOWN_VERSION, configuration.getNetworkTimeout());
        return () -> {
            wrapperExecutor.execute(args, new Install(logger, download, new PathAssembler(gradleUserHome, appHome)), new BootstrapMainStarter());
        };
    }

    private static void addSystemProperties(Map<String, String> projectSystemProperties, Map<String, String> userSystemProperties, Map<String, String> commandLineSystemProperties) {
        Map<String, String> gradleSystemProperties = merge(merge(projectSystemProperties, userSystemProperties), commandLineSystemProperties);
        System.getProperties().putAll(gradleSystemProperties);
    }

    private static void maybeAddGradleUserHomeSystemProperty(Map<String, String> projectSystemProperties, Map<String, String> commandLineSystemProperties) {
        Map<String, String> gradleSystemProperties = merge(projectSystemProperties, commandLineSystemProperties);
        String property = gradleSystemProperties.get(GradleUserHomeLookup.GRADLE_USER_HOME_PROPERTY_KEY);
        if (property != null) {
            System.setProperty(GradleUserHomeLookup.GRADLE_USER_HOME_PROPERTY_KEY, property);
        }
    }

    private static Map<String, String> merge(Map<String, String> p1, Map<String, String> p2) {
        // If there are duplicate keys, the values from p2 take precedence.
        Map<String, String> result = new HashMap<>(p1);
        result.putAll(p2);
        return result;
    }

    static File appHome() {
        return Bootstrap.appHome().toFile();
    }

    static File wrapperProperties() {
        return WrapperExecutor.wrapperPropertiesForProjectDirectory(appHome());
    }

    private static File gradleUserHome(ParsedCommandLine options) {
        if (options.hasOption(GRADLE_USER_HOME_OPTION)) {
            return new File(options.option(GRADLE_USER_HOME_OPTION).getValue());
        }
        return GradleUserHomeLookup.gradleUserHome();
    }

    private static Logger logger(ParsedCommandLine options) {
        return new Logger(options.hasOption(GRADLE_QUIET_OPTION));
    }

    // @NullMarked
    @FunctionalInterface
    private interface Action {

        void execute() throws Exception;
    }
}

/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.cli;

abstract class AbstractCommandLineConverter<T> implements CommandLineConverter<T> {

    @Override
    public T convert(Iterable<String> args, T target) throws CommandLineArgumentException {
        CommandLineParser parser = new CommandLineParser();
        configure(parser);
        return convert(parser.parse(args), target);
    }
}

/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.cli;

abstract class AbstractPropertiesCommandLineConverter extends AbstractCommandLineConverter<Map<String, String>> {

    protected abstract String getPropertyOption();

    protected abstract String getPropertyOptionDetailed();

    protected abstract String getPropertyOptionDescription();

    protected abstract OptionCategory getCategory();

    @Override
    public void configure(CommandLineParser parser) {
        CommandLineOption option = parser.option(getPropertyOption(), getPropertyOptionDetailed());
        option = option.hasArguments();
        option.hasDescription(getPropertyOptionDescription());
        option.hasCategory(getCategory());
    }

    @Override
    public Map<String, String> convert(ParsedCommandLine options, Map<String, String> properties) throws CommandLineArgumentException {
        for (String keyValueExpression : options.option(getPropertyOption()).getValues()) {
            int pos = keyValueExpression.indexOf("=");
            if (pos < 0) {
                properties.put(keyValueExpression, "");
            } else {
                properties.put(keyValueExpression.substring(0, pos), keyValueExpression.substring(pos + 1));
            }
        }
        return properties;
    }
}

/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.cli;

/**
 * A {@code CommandLineArgumentException} is thrown when command-line arguments cannot be parsed.
 */
class CommandLineArgumentException extends RuntimeException {

    public CommandLineArgumentException(String message) {
        super(message);
    }

    public CommandLineArgumentException(String message, Throwable cause) {
        super(message, cause);
    }
}

/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.cli;

interface CommandLineConverter<T> {

    T convert(Iterable<String> args, T target) throws CommandLineArgumentException;

    T convert(ParsedCommandLine args, T target) throws CommandLineArgumentException;

    void configure(CommandLineParser parser);
}

/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.cli;

class CommandLineOption {

    private final Set<String> options = new HashSet<String>();

    private Class<?> argumentType = Void.TYPE;

    private String /* // @Nullable */ description;

    private boolean incubating;

    private final Set<CommandLineOption> groupWith = new HashSet<CommandLineOption>();

    private boolean deprecated;

    private OptionCategory category = OptionCategory.OTHER;

    public CommandLineOption(Iterable<String> options) {
        for (String option : options) {
            this.options.add(option);
        }
    }

    public Set<String> getOptions() {
        return options;
    }

    public CommandLineOption hasArgument(Class<?> argumentType) {
        this.argumentType = argumentType;
        return this;
    }

    public CommandLineOption hasArgument() {
        this.argumentType = String.class;
        return this;
    }

    public CommandLineOption hasArguments() {
        argumentType = List.class;
        return this;
    }

    public String getDescription() {
        StringBuilder result = new StringBuilder();
        if (description != null) {
            result.append(description);
        }
        appendMessage(result, deprecated, "[deprecated]");
        appendMessage(result, incubating, "[incubating]");
        return result.toString();
    }

    private void appendMessage(StringBuilder result, boolean append, String message) {
        if (append) {
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(message);
        }
    }

    public CommandLineOption hasDescription(String description) {
        this.description = description;
        return this;
    }

    public boolean getAllowsArguments() {
        return argumentType != Void.TYPE;
    }

    public boolean getAllowsMultipleArguments() {
        return argumentType == List.class;
    }

    public CommandLineOption deprecated() {
        this.deprecated = true;
        return this;
    }

    public CommandLineOption incubating() {
        incubating = true;
        return this;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public boolean isIncubating() {
        return incubating;
    }

    Set<CommandLineOption> getGroupWith() {
        return groupWith;
    }

    void groupWith(Set<CommandLineOption> options) {
        this.groupWith.addAll(options);
        this.groupWith.remove(this);
    }

    public CommandLineOption hasCategory(OptionCategory category) {
        this.category = category == null ? OptionCategory.OTHER : category;
        return this;
    }

    public OptionCategory getCategory() {
        return category == null ? OptionCategory.OTHER : category;
    }
}

/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.cli;

/**
 * <p>A command-line parser which supports a command/sub-command style command-line interface. Supports the following
 * syntax:</p>
 * <pre>
 * &lt;option&gt;* (&lt;sub-command&gt; &lt;sub-command-option&gt;*)*
 * </pre>
 *
 * <ul> <li>Short options are a '-' followed by a single character. For example: {@code -a}.</li>
 *
 * <li>Long options are '--' followed by multiple characters. For example: {@code --long-option}.</li>
 *
 * <li>Options can take arguments. The argument follows the option. For example: {@code -a arg} or {@code --long
 * arg}.</li>
 *
 * <li>Arguments can be attached to the option using '='. For example: {@code -a=arg} or {@code --long=arg}.</li>
 *
 * <li>Arguments can be attached to short options. For example: {@code -aarg}.</li>
 *
 * <li>Short options can be combined. For example {@code -ab} is equivalent to {@code -a -b}.</li>
 *
 * <li>Anything else is treated as an extra argument. This includes a single {@code -} character.</li>
 *
 * <li>'--' indicates the end of the options. Anything following is not parsed and is treated as extra arguments.</li>
 *
 * <li>The parser is forgiving, and allows '--' to be used with short options and '-' to be used with long
 * options.</li>
 *
 * <li>The set of options must be known at parse time. Sub-commands and their options do not need to be known at parse
 * time. Use {@link ParsedCommandLine#getExtraArguments()} to obtain the non-option command-line arguments.</li>
 *
 * </ul>
 */
class CommandLineParser {

    private static final Pattern OPTION_NAME_PATTERN = Pattern.compile("(\\?|\\p{Alnum}[\\p{Alnum}-_]*)");

    private static final String DISABLE_OPTION_PREFIX = "no-";

    private Map<String, CommandLineOption> optionsByString = new HashMap<String, CommandLineOption>();

    private boolean allowMixedOptions;

    private boolean allowUnknownOptions;

    /**
     * Parses the given command-line.
     *
     * @param commandLine The command-line.
     * @return The parsed command line.
     * @throws org.gradle.cli.CommandLineArgumentException
     *          On parse failure.
     */
    public ParsedCommandLine parse(String... commandLine) throws CommandLineArgumentException {
        return parse(Arrays.asList(commandLine));
    }

    /**
     * Parses the given command-line.
     *
     * @param commandLine The command-line.
     * @return The parsed command line.
     * @throws org.gradle.cli.CommandLineArgumentException
     *          On parse failure.
     */
    public ParsedCommandLine parse(Iterable<String> commandLine) throws CommandLineArgumentException {
        ParsedCommandLine parsedCommandLine = new ParsedCommandLine(new HashSet<CommandLineOption>(optionsByString.values()));
        ParserState parseState = new BeforeFirstSubCommand(parsedCommandLine);
        for (String arg : commandLine) {
            if (parseState.maybeStartOption(arg)) {
                if (arg.equals("--")) {
                    parseState = new AfterOptions(parsedCommandLine);
                } else if (arg.matches("--[^=]+")) {
                    OptionParserState parsedOption = parseState.onStartOption(arg, arg.substring(2));
                    parseState = parsedOption.onStartNextArg();
                } else if (arg.matches("(?s)--[^=]+=.*")) {
                    int endArg = arg.indexOf('=');
                    OptionParserState parsedOption = parseState.onStartOption(arg, arg.substring(2, endArg));
                    parseState = parsedOption.onArgument(arg.substring(endArg + 1));
                } else if (arg.matches("(?s)-[^=]=.*")) {
                    OptionParserState parsedOption = parseState.onStartOption(arg, arg.substring(1, 2));
                    parseState = parsedOption.onArgument(arg.substring(3));
                } else {
                    assert arg.matches("(?s)-[^-].*");
                    String option = arg.substring(1);
                    if (optionsByString.containsKey(option)) {
                        OptionParserState parsedOption = parseState.onStartOption(arg, option);
                        parseState = parsedOption.onStartNextArg();
                    } else {
                        String option1 = arg.substring(1, 2);
                        OptionParserState parsedOption;
                        if (optionsByString.containsKey(option1)) {
                            parsedOption = parseState.onStartOption("-" + option1, option1);
                            if (parsedOption.getHasArgument()) {
                                parseState = parsedOption.onArgument(arg.substring(2));
                            } else {
                                parseState = parsedOption.onComplete();
                                for (int i = 2; i < arg.length(); i++) {
                                    String optionStr = arg.substring(i, i + 1);
                                    parsedOption = parseState.onStartOption("-" + optionStr, optionStr);
                                    parseState = parsedOption.onComplete();
                                }
                            }
                        } else {
                            if (allowUnknownOptions) {
                                // if we are allowing unknowns, just pass through the whole arg
                                parsedOption = parseState.onStartOption(arg, option);
                                parseState = parsedOption.onComplete();
                            } else {
                                // We are going to throw a CommandLineArgumentException below, but want the message
                                // to reflect that we didn't recognise the first char (i.e. the option specifier)
                                parsedOption = parseState.onStartOption("-" + option1, option1);
                                parseState = parsedOption.onComplete();
                            }
                        }
                    }
                }
            } else {
                parseState = parseState.onNonOption(arg);
            }
        }
        parseState.onCommandLineEnd();
        return parsedCommandLine;
    }

    public CommandLineParser allowMixedSubcommandsAndOptions() {
        allowMixedOptions = true;
        return this;
    }

    public CommandLineParser allowUnknownOptions() {
        allowUnknownOptions = true;
        return this;
    }

    /**
     * Specifies that the given set of options are mutually-exclusive. Only one of the given options will be selected.
     * The parser ignores all but the last of these options.
     */
    public CommandLineParser allowOneOf(String... options) {
        Set<CommandLineOption> commandLineOptions = new HashSet<CommandLineOption>();
        for (String option : options) {
            commandLineOptions.add(optionsByString.get(option));
        }
        for (CommandLineOption commandLineOption : commandLineOptions) {
            commandLineOption.groupWith(commandLineOptions);
        }
        return this;
    }

    /**
     * Prints a usage message to the given stream.
     *
     * @param out The output stream to write to.
     */
    @SuppressWarnings("NullAway")
    public void printUsage(Appendable out, int widthHint) {
        // sort options before grouping
        Set<CommandLineOption> commandLineOptions = new TreeSet<>(new OptionComparator());
        commandLineOptions.addAll(optionsByString.values());
        // map options to their categories
        Map<OptionCategory, List<RenderedCommandLineOption>> categoryToOptions = new EnumMap<>(OptionCategory.class);
        for (OptionCategory category : OptionCategory.values()) {
            categoryToOptions.put(category, new ArrayList<>());
        }
        for (CommandLineOption option : commandLineOptions) {
            categoryToOptions.get(option.getCategory()).add(RenderedCommandLineOption.from(option));
        }
        // calculate column widths for option name and description
        int nameColumnWidth = categoryToOptions.values().stream().flatMap(List::stream).mapToInt(o -> o.getName().length()).max().orElse(0) + // account for two extra spaces before each option plus an extra space between the longest option and its description
        3;
        int descriptionColumnWidth = Math.max(30, widthHint - nameColumnWidth);
        // print hint about end signal
        Formatter formatter = new Formatter(out);
        printRenderedOption(formatter, "--", nameColumnWidth, "Signals the end of built-in options. Parses subsequent parameters as tasks or task options only.", descriptionColumnWidth);
        // print each category and its options
        for (OptionCategory category : OptionCategory.values()) {
            List<RenderedCommandLineOption> options = categoryToOptions.get(category);
            if (options == null || options.isEmpty()) {
                continue;
            }
            // print category name
            String categoryName = category.getDisplayName();
            if (!categoryName.isEmpty()) {
                printCategory(formatter, categoryName);
            }
            // print option name and description
            for (RenderedCommandLineOption option : options) {
                String name = option.getName();
                String description = option.getDescription();
                printRenderedOption(formatter, "  " + name, nameColumnWidth, description, descriptionColumnWidth);
            }
        }
        formatter.flush();
    }

    private static void printRenderedOption(Formatter formatter, String name, int nameColumnWidth, String description, int descriptionColumnWidth) {
        if (description == null || description.isEmpty()) {
            printOption(formatter, name);
        } else {
            // handle multi-line descriptions and split lines that are too long for the console
            List<String> descriptionLines = Arrays.stream(description.split("\\r?\\n")).flatMap(n -> splitToLength(n, descriptionColumnWidth).stream()).collect(Collectors.toList());
            for (int i = 0; i < descriptionLines.size(); i++) {
                printOption(formatter, i == 0 ? name : "", nameColumnWidth, descriptionLines.get(i));
            }
        }
    }

    public static List<String> splitToLength(String input, int n) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        while (start < input.length()) {
            int end = Math.min(start + n, input.length());
            if (end < input.length()) {
                int lastSpace = input.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }
            lines.add(input.substring(start, end));
            start = end;
            // skip whitespace at beginning of next line
            while (start < input.length() && Character.isWhitespace(input.charAt(start))) {
                start++;
            }
        }
        return lines;
    }

    private static void printCategory(Formatter formatter, String name) {
        formatter.format("%n%s:%n", name);
    }

    private static void printOption(Formatter formatter, String name) {
        formatter.format("%s%n", name);
    }

    private static void printOption(Formatter formatter, String name, int nameColumnWidth, String description) {
        formatter.format("%-" + nameColumnWidth + "s %s%n", name, description);
    }

    // @NullMarked
    private static class RenderedCommandLineOption {

        private final String name;

        private final String description;

        private RenderedCommandLineOption(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        static RenderedCommandLineOption from(CommandLineOption option) {
            return new RenderedCommandLineOption(optionName(option), option.getDescription());
        }

        private static String optionName(CommandLineOption option) {
            Set<String> optionStrings = new TreeSet<>(new OptionStringComparator());
            optionStrings.addAll(option.getOptions());
            List<String> prefixedStrings = new ArrayList<>();
            for (String optionString : optionStrings) {
                if (optionString.length() == 1) {
                    prefixedStrings.add("-" + optionString);
                } else {
                    prefixedStrings.add("--" + optionString);
                }
            }
            String key = join(prefixedStrings, ", ");
            return key;
        }

        private static String join(Collection<?> things, String separator) {
            StringBuilder builder = new StringBuilder();
            boolean first = true;
            if (separator == null) {
                separator = "";
            }
            for (Object thing : things) {
                if (!first) {
                    builder.append(separator);
                }
                builder.append(thing.toString());
                first = false;
            }
            return builder.toString();
        }
    }

    /**
     * Defines a new option. By default, the option takes no arguments and has no description.
     *
     * @param options The options values.
     * @return The option, which can be further configured.
     */
    public CommandLineOption option(String... options) {
        for (String option : options) {
            if (optionsByString.containsKey(option)) {
                throw new IllegalArgumentException(String.format("Option '%s' is already defined.", option));
            }
            if (option.startsWith("-")) {
                throw new IllegalArgumentException(String.format("Cannot add option '%s' as an option cannot start with '-'.", option));
            }
            if (!OPTION_NAME_PATTERN.matcher(option).matches()) {
                throw new IllegalArgumentException(String.format("Cannot add option '%s' as an option can only contain alphanumeric characters or '-' or '_'.", option));
            }
        }
        CommandLineOption option = new CommandLineOption(Arrays.asList(options));
        for (String optionStr : option.getOptions()) {
            optionsByString.put(optionStr, option);
        }
        return option;
    }

    private static class OptionString {

        private final String arg;

        private final String option;

        private OptionString(String arg, String option) {
            this.arg = arg;
            this.option = option;
        }

        public String getDisplayName() {
            return arg.startsWith("--") ? "--" + option : "-" + option;
        }

        @Override
        public String toString() {
            return getDisplayName();
        }
    }

    private static abstract class ParserState {

        public abstract boolean maybeStartOption(String arg);

        boolean isOption(String arg) {
            return arg.matches("(?s)-.+");
        }

        public abstract OptionParserState onStartOption(String arg, String option);

        public abstract ParserState onNonOption(String arg);

        public void onCommandLineEnd() {
        }
    }

    private abstract class OptionAwareParserState extends ParserState {

        protected final ParsedCommandLine commandLine;

        protected OptionAwareParserState(ParsedCommandLine commandLine) {
            this.commandLine = commandLine;
        }

        @Override
        public boolean maybeStartOption(String arg) {
            return isOption(arg);
        }

        @Override
        public ParserState onNonOption(String arg) {
            commandLine.addExtraValue(arg);
            return allowMixedOptions ? new AfterFirstSubCommand(commandLine) : new AfterOptions(commandLine);
        }
    }

    private class BeforeFirstSubCommand extends OptionAwareParserState {

        private BeforeFirstSubCommand(ParsedCommandLine commandLine) {
            super(commandLine);
        }

        @Override
        public OptionParserState onStartOption(String arg, String option) {
            OptionString optionString = new OptionString(arg, option);
            CommandLineOption commandLineOption = optionsByString.get(option);
            if (commandLineOption == null) {
                if (allowUnknownOptions) {
                    return new UnknownOptionParserState(arg, commandLine, this);
                } else {
                    throw new CommandLineArgumentException(String.format("Unknown command-line option '%s'.", optionString));
                }
            }
            return new KnownOptionParserState(optionString, commandLineOption, commandLine, this);
        }
    }

    private class AfterFirstSubCommand extends OptionAwareParserState {

        private AfterFirstSubCommand(ParsedCommandLine commandLine) {
            super(commandLine);
        }

        @Override
        public OptionParserState onStartOption(String arg, String option) {
            CommandLineOption commandLineOption = optionsByString.get(option);
            if (commandLineOption == null) {
                return new UnknownOptionParserState(arg, commandLine, this);
            }
            return new KnownOptionParserState(new OptionString(arg, option), commandLineOption, commandLine, this);
        }
    }

    private static class AfterOptions extends ParserState {

        private final ParsedCommandLine commandLine;

        private AfterOptions(ParsedCommandLine commandLine) {
            this.commandLine = commandLine;
        }

        @Override
        public boolean maybeStartOption(String arg) {
            return false;
        }

        @Override
        public OptionParserState onStartOption(String arg, String option) {
            return new UnknownOptionParserState(arg, commandLine, this);
        }

        @Override
        public ParserState onNonOption(String arg) {
            commandLine.addExtraValue(arg);
            return this;
        }
    }

    private static class MissingOptionArgState extends ParserState {

        private final OptionParserState option;

        private MissingOptionArgState(OptionParserState option) {
            this.option = option;
        }

        @Override
        public boolean maybeStartOption(String arg) {
            return isOption(arg);
        }

        @Override
        public OptionParserState onStartOption(String arg, String option) {
            return this.option.onComplete().onStartOption(arg, option);
        }

        @Override
        public ParserState onNonOption(String arg) {
            return option.onArgument(arg);
        }

        @Override
        public void onCommandLineEnd() {
            option.onComplete();
        }
    }

    private static abstract class OptionParserState {

        public abstract ParserState onStartNextArg();

        public abstract ParserState onArgument(String argument);

        public abstract boolean getHasArgument();

        public abstract ParserState onComplete();
    }

    private static class KnownOptionParserState extends OptionParserState {

        private final OptionString optionString;

        private final CommandLineOption option;

        private final ParsedCommandLine commandLine;

        private final ParserState state;

        private final List<String> values = new ArrayList<String>();

        private KnownOptionParserState(OptionString optionString, CommandLineOption option, ParsedCommandLine commandLine, ParserState state) {
            this.optionString = optionString;
            this.option = option;
            this.commandLine = commandLine;
            this.state = state;
        }

        @Override
        public ParserState onArgument(String argument) {
            if (!getHasArgument()) {
                throw new CommandLineArgumentException(String.format("Command-line option '%s' does not take an argument.", optionString));
            }
            if (argument.length() == 0) {
                throw new CommandLineArgumentException(String.format("An empty argument was provided for command-line option '%s'.", optionString));
            }
            values.add(argument);
            return onComplete();
        }

        @Override
        public ParserState onStartNextArg() {
            if (option.getAllowsArguments() && values.isEmpty()) {
                return new MissingOptionArgState(this);
            }
            return onComplete();
        }

        @Override
        public boolean getHasArgument() {
            return option.getAllowsArguments();
        }

        @Override
        public ParserState onComplete() {
            if (getHasArgument() && values.isEmpty()) {
                throw new CommandLineArgumentException(String.format("No argument was provided for command-line option '%s' with description: '%s'", optionString, option.getDescription()));
            }
            ParsedCommandLineOption parsedOption = commandLine.addOption(optionString.option, option);
            if (values.size() + parsedOption.getValues().size() > 1 && !option.getAllowsMultipleArguments()) {
                throw new CommandLineArgumentException(String.format("Multiple arguments were provided for command-line option '%s'.", optionString));
            }
            for (String value : values) {
                parsedOption.addArgument(value);
            }
            for (CommandLineOption otherOption : option.getGroupWith()) {
                commandLine.removeOption(otherOption);
            }
            return state;
        }
    }

    private static class UnknownOptionParserState extends OptionParserState {

        private final ParserState state;

        private final String arg;

        private final ParsedCommandLine commandLine;

        private UnknownOptionParserState(String arg, ParsedCommandLine commandLine, ParserState state) {
            this.arg = arg;
            this.commandLine = commandLine;
            this.state = state;
        }

        @Override
        public boolean getHasArgument() {
            return true;
        }

        @Override
        public ParserState onStartNextArg() {
            return onComplete();
        }

        @Override
        public ParserState onArgument(String argument) {
            return onComplete();
        }

        @Override
        public ParserState onComplete() {
            commandLine.addExtraValue(arg);
            return state;
        }
    }

    private static final class OptionComparator implements Comparator<CommandLineOption> {

        @Override
        public int compare(CommandLineOption option1, CommandLineOption option2) {
            String min1 = Collections.min(option1.getOptions(), new OptionStringComparator());
            String min2 = Collections.min(option2.getOptions(), new OptionStringComparator());
            // Group opposite option pairs together
            min1 = min1.startsWith(DISABLE_OPTION_PREFIX) ? min1.substring(DISABLE_OPTION_PREFIX.length()) + "-" : min1;
            min2 = min2.startsWith(DISABLE_OPTION_PREFIX) ? min2.substring(DISABLE_OPTION_PREFIX.length()) + "-" : min2;
            return new CaseInsensitiveStringComparator().compare(min1, min2);
        }
    }

    private static final class CaseInsensitiveStringComparator implements Comparator<String> {

        @Override
        public int compare(String option1, String option2) {
            int diff = option1.compareToIgnoreCase(option2);
            if (diff != 0) {
                return diff;
            }
            return option1.compareTo(option2);
        }
    }

    private static final class OptionStringComparator implements Comparator<String> {

        @Override
        public int compare(String option1, String option2) {
            boolean short1 = option1.length() == 1;
            boolean short2 = option2.length() == 1;
            if (short1 && !short2) {
                return 1;
            }
            if (!short1 && short2) {
                return -1;
            }
            return new CaseInsensitiveStringComparator().compare(option1, option2);
        }
    }
}

/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.cli;

/**
 * Categories for build options. Used in the help output.
 */
// @NullMarked
enum OptionCategory {

    HELP("Help"),
    LOGGING("Logging"),
    CONSOLE("Console"),
    CONFIGURATION("Configuration"),
    EXECUTION("Execution"),
    PERFORMANCE("Performance"),
    SECURITY("Security"),
    DIAGNOSTICS("Diagnostics"),
    DAEMON("Daemon"),
    DEVELOCITY("Develocity"),
    OTHER("");

    private final String displayName;

    OptionCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.cli;

class ParsedCommandLine {

    private final Map<String, ParsedCommandLineOption> optionsByString = new HashMap<String, ParsedCommandLineOption>();

    private final Set<String> presentOptions = new HashSet<String>();

    private final Set<String> removedOptions = new HashSet<String>();

    private final List<String> extraArguments = new ArrayList<String>();

    ParsedCommandLine(Iterable<CommandLineOption> options) {
        for (CommandLineOption option : options) {
            ParsedCommandLineOption parsedOption = new ParsedCommandLineOption();
            for (String optionStr : option.getOptions()) {
                optionsByString.put(optionStr, parsedOption);
            }
        }
    }

    @Override
    public String toString() {
        return String.format("options: %s, extraArguments: %s, removedOptions: %s", quoteAndJoin(presentOptions), quoteAndJoin(extraArguments), quoteAndJoin(removedOptions));
    }

    private String quoteAndJoin(Iterable<String> strings) {
        StringBuilder output = new StringBuilder();
        boolean isFirst = true;
        for (String string : strings) {
            if (!isFirst) {
                output.append(", ");
            }
            output.append("'");
            output.append(string);
            output.append("'");
            isFirst = false;
        }
        return output.toString();
    }

    /**
     * Returns true if the given option is present in this command-line.
     *
     * @param option The option, without the '-' or '--' prefix.
     * @return true if the option is present.
     */
    public boolean hasOption(String option) {
        option(option);
        return presentOptions.contains(option);
    }

    /**
     * Returns true if the given option was present in this command-line,
     * but was removed because another option appeared later that replaces it.
     *
     * @param option The option, without the '-' or '--' prefix.
     * @return true if the option was present.
     */
    public boolean hadOptionRemoved(String option) {
        option(option);
        return removedOptions.contains(option);
    }

    /**
     * See also {@link #hasOption}.
     *
     * @param logLevelOptions the options to check
     * @return true if any of the passed options is present
     */
    public boolean hasAnyOption(Collection<String> logLevelOptions) {
        for (String option : logLevelOptions) {
            if (hasOption(option)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the value of the given option.
     *
     * @param option The option, without the '-' or '--' prefix.
     * @return The option. never returns null.
     */
    public ParsedCommandLineOption option(String option) {
        ParsedCommandLineOption parsedOption = optionsByString.get(option);
        if (parsedOption == null) {
            throw new IllegalArgumentException(String.format("Option '%s' not defined.", option));
        }
        return parsedOption;
    }

    public List<String> getExtraArguments() {
        return extraArguments;
    }

    void addExtraValue(String value) {
        extraArguments.add(value);
    }

    ParsedCommandLineOption addOption(String optionStr, CommandLineOption option) {
        ParsedCommandLineOption parsedOption = Objects.requireNonNull(optionsByString.get(optionStr));
        presentOptions.addAll(option.getOptions());
        return parsedOption;
    }

    void removeOption(CommandLineOption option) {
        for (String optionStr : option.getOptions()) {
            if (presentOptions.remove(optionStr)) {
                // Only keep track of removed options that were present in the command line
                removedOptions.add(optionStr);
            }
        }
    }
}

/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.cli;

class ParsedCommandLineOption {

    private final List<String> values = new ArrayList<String>();

    public String getValue() {
        if (!hasValue()) {
            throw new IllegalStateException("Option does not have any value.");
        }
        if (values.size() > 1) {
            throw new IllegalStateException("Option has multiple values.");
        }
        return values.get(0);
    }

    public List<String> getValues() {
        return values;
    }

    public void addArgument(String argument) {
        values.add(argument);
    }

    public boolean hasValue() {
        return !values.isEmpty();
    }
}

/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.cli;

class ProjectPropertiesCommandLineConverter extends AbstractPropertiesCommandLineConverter {

    @Override
    protected String getPropertyOption() {
        return "P";
    }

    @Override
    protected String getPropertyOptionDetailed() {
        return "project-prop";
    }

    @Override
    protected String getPropertyOptionDescription() {
        return "Sets a project property for the build script (for example, -Pmyprop=myvalue).";
    }

    @Override
    protected OptionCategory getCategory() {
        return OptionCategory.CONFIGURATION;
    }
}

/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.cli;

class SystemPropertiesCommandLineConverter extends AbstractPropertiesCommandLineConverter {

    @Override
    protected String getPropertyOption() {
        return "D";
    }

    @Override
    protected String getPropertyOptionDetailed() {
        return "system-prop";
    }

    @Override
    protected String getPropertyOptionDescription() {
        return "Sets a JVM system property (for example, -Dmyprop=myvalue).";
    }

    @Override
    protected OptionCategory getCategory() {
        return OptionCategory.CONFIGURATION;
    }
}

/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.internal.file;

class PathTraversalChecker {

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase(Locale.US).contains("windows");

    /**
     * Checks the entry name for path traversal vulnerable sequences.
     *
     * This code is used for path traversal, ZipSlip and TarSlip detection.
     *
     * <b>IMPLEMENTATION NOTE</b>
     * We do it this way instead of the way recommended in <a href="https://snyk.io/research/zip-slip-vulnerability"></a>
     * for performance reasons, calling {@link File#getCanonicalPath()} is too expensive.
     *
     * @throws IllegalArgumentException if the entry contains vulnerable sequences
     */
    public static String safePathName(String name) {
        if (isUnsafePathName(name)) {
            throw new IllegalArgumentException(format("'%s' is not a safe archive entry or path name.", name));
        }
        return name;
    }

    public static boolean isUnsafePathName(String name) {
        if (name.isEmpty()) {
            return true;
        }
        if (IS_WINDOWS && name.contains(":")) {
            return true;
        }
        if (name.startsWith("/") || name.startsWith("\\")) {
            return true;
        }
        return containsDirectoryNavigation(name);
    }

    /**
     * We want to treat both '/' and '\' as path separators on all OSes.
     *
     * @param name the original path name
     * @return the path name with all separators replaced with the OS file separator
     */
    private static String osIndependentPath(String name) {
        if (File.separatorChar == '\\') {
            return name.replace('/', File.separatorChar);
        } else if (File.separatorChar == '/') {
            return name.replace('\\', File.separatorChar);
        } else {
            // Throw an error here, as we would want to add this separator to our list
            // rather than passing it through unmodified
            throw new IllegalStateException("Unknown file separator: " + File.separatorChar);
        }
    }

    private static boolean containsDirectoryNavigation(String name) {
        List<String> names = buildNamesList(name);
        for (String part : names) {
            if (part.equals("..")) {
                return true;
            }
            if (IS_WINDOWS) {
                // Directories with dots at the end will have them removed by win32 compatibility
                // We don't know what paths might be directories, so just ban any occurrence of dots at the end
                if (!part.equals(".") && part.endsWith(".")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> buildNamesList(String name) {
        // We run this through File then toPath, as `name` is primarily used with new File(...) calls elsewhere
        // This ensures a consistent parsing/understanding of the path
        Path path = new File(osIndependentPath(name)).toPath();
        List<String> names = new ArrayList<>(path.getNameCount());
        for (Path part : path) {
            names.add(part.toString());
        }
        return names;
    }
}

/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.internal.file.locking;

class ExclusiveFileAccessManager {

    public static final String LOCK_FILE_SUFFIX = ".lck";

    private final int timeoutMs;

    private final int pollIntervalMs;

    public ExclusiveFileAccessManager(int timeoutMs, int pollIntervalMs) {
        this.timeoutMs = timeoutMs;
        this.pollIntervalMs = pollIntervalMs;
    }

    public <T> T access(File exclusiveFile, Callable<T> task) throws Exception {
        final File lockFile = new File(exclusiveFile.getParentFile(), exclusiveFile.getName() + LOCK_FILE_SUFFIX);
        File lockFileDirectory = lockFile.getParentFile();
        if (!lockFileDirectory.mkdirs() && (!lockFileDirectory.exists() || !lockFileDirectory.isDirectory())) {
            throw new RuntimeException("Could not create parent directory for lock file " + lockFile.getAbsolutePath());
        }
        RandomAccessFile randomAccessFile = null;
        FileChannel channel = null;
        try {
            long expiry = getTimeMillis() + timeoutMs;
            FileLock lock = null;
            while (lock == null && getTimeMillis() < expiry) {
                randomAccessFile = new RandomAccessFile(lockFile, "rw");
                channel = randomAccessFile.getChannel();
                lock = channel.tryLock();
                if (lock == null) {
                    maybeCloseQuietly(channel);
                    maybeCloseQuietly(randomAccessFile);
                    Thread.sleep(pollIntervalMs);
                }
            }
            if (lock == null) {
                throw new RuntimeException("Timeout of " + timeoutMs + " reached waiting for exclusive access to file: " + exclusiveFile.getAbsolutePath());
            }
            try {
                return task.call();
            } finally {
                lock.release();
                maybeCloseQuietly(channel);
                channel = null;
                maybeCloseQuietly(randomAccessFile);
                randomAccessFile = null;
            }
        } finally {
            maybeCloseQuietly(channel);
            maybeCloseQuietly(randomAccessFile);
        }
    }

    private long getTimeMillis() {
        return System.nanoTime() / (1000L * 1000L);
    }

    private static void maybeCloseQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignore) {
                //
            }
        }
    }
}

/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.util.internal;

// @NullMarked
class ArgumentsSplitter {

    /**
     * Splits the arguments string (for example, a program command line) into a collection.
     * Only supports space-delimited and/or quoted command line arguments. This currently does not handle escaping characters such as quotes.
     *
     * @param arguments the arguments, for example command line args.
     * @return separate command line arguments.
     */
    public static List<String> split(String arguments) {
        List<String> commandLineArguments = new ArrayList<String>();
        Character currentQuote = null;
        StringBuilder currentOption = new StringBuilder();
        boolean hasOption = false;
        for (int index = 0; index < arguments.length(); index++) {
            char c = arguments.charAt(index);
            if (currentQuote == null && Character.isWhitespace(c)) {
                if (hasOption) {
                    commandLineArguments.add(currentOption.toString());
                    hasOption = false;
                    currentOption.setLength(0);
                }
            } else if (currentQuote == null && (c == '"' || c == '\'')) {
                currentQuote = c;
                hasOption = true;
            } else if (currentQuote != null && c == currentQuote) {
                currentQuote = null;
            } else {
                currentOption.append(c);
                hasOption = true;
            }
        }
        if (hasOption) {
            commandLineArguments.add(currentOption.toString());
        }
        return commandLineArguments;
    }
}

/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.util.internal;

// @NullMarked
final class WrapperCredentials {

    // @Nullable
    private final String token;

    // @Nullable
    private final String basicUserInfo;

    private WrapperCredentials(/* // @Nullable */ String token, /* // @Nullable */ String basicUserInfo) {
        this.token = token;
        this.basicUserInfo = basicUserInfo;
    }

    public static WrapperCredentials fromToken(String token) {
        return new WrapperCredentials(Objects.requireNonNull(token, "token"), null);
    }

    public static WrapperCredentials fromBasicUserInfo(String basicUserInfo) {
        return new WrapperCredentials(null, Objects.requireNonNull(basicUserInfo, "basicUserInfo"));
    }

    public static WrapperCredentials fromUsernamePassword(String username, String password) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        return fromBasicUserInfo(username + ':' + password);
    }

    // @Nullable
    public static WrapperCredentials findCredentials(URI distributionUrl, Function<? super String, ? extends /* // @Nullable */ String> propertyProvider) {
        Objects.requireNonNull(distributionUrl, "distributionUrl");
        Objects.requireNonNull(propertyProvider, "propertyProvider");
        String token = tryGetProperty(distributionUrl.getHost(), "wrapperToken", propertyProvider);
        if (token != null) {
            return fromToken(token);
        }
        return findBasicCredentials(distributionUrl, propertyProvider);
    }

    // @Nullable
    private static WrapperCredentials findBasicCredentials(URI distributionUrl, Function<? super String, ? extends /* // @Nullable */ String> propertyProvider) {
        String host = distributionUrl.getHost();
        String username = tryGetProperty(host, "wrapperUser", propertyProvider);
        String password = tryGetProperty(host, "wrapperPassword", propertyProvider);
        if (username != null && password != null) {
            return fromUsernamePassword(username, password);
        }
        String userInfo = distributionUrl.getUserInfo();
        return userInfo != null ? fromBasicUserInfo(userInfo) : null;
    }

    // @Nullable
    private static String tryGetProperty(/* // @Nullable */ String host, String key, Function<? super String, ? extends /* // @Nullable */ String> propertyProvider) {
        if (host != null) {
            String hostEscaped = host.replace('.', '_').toLowerCase(Locale.ROOT);
            String hostProperty = propertyProvider.apply("gradle." + hostEscaped + '.' + key);
            if (hostProperty != null) {
                return hostProperty;
            }
        }
        return propertyProvider.apply("gradle." + key);
    }

    // @Nullable
    public String token() {
        return token;
    }

    private static Map.Entry<String, String> mapEntry(String key, String value) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    public Map./* // @Nullable */ Entry<String, String> usernameAndPassword() {
        if (basicUserInfo == null) {
            return null;
        }
        int usernameEnd = basicUserInfo.indexOf(':');
        return usernameEnd >= 0 ? mapEntry(basicUserInfo.substring(0, usernameEnd), basicUserInfo.substring(usernameEnd + 1)) : null;
    }

    // @Nullable
    public String username() {
        Map.Entry<String, String> combined = usernameAndPassword();
        return combined != null ? combined.getKey() : null;
    }

    public String authorizationTypeDisplayName() {
        return token != null ? "Bearer Token" : "Basic";
    }

    public Map.Entry<String, String> authorizationHeader() {
        return mapEntry("Authorization", authorizationHeaderValue());
    }

    private String authorizationHeaderValue() {
        if (token != null) {
            return "Bearer " + token;
        } else if (basicUserInfo != null) {
            return "Basic " + Base64.getEncoder().encodeToString(basicUserInfo.getBytes(StandardCharsets.UTF_8));
        } else {
            throw new AssertionError("Internal error: Unexpected credentials state.");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WrapperCredentials that = (WrapperCredentials) o;
        return Objects.equals(token, that.token) && Objects.equals(basicUserInfo, that.basicUserInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token, basicUserInfo);
    }

    @Override
    public String toString() {
        return "WrapperCredentials{" + (token != null ? "<TOKEN>" : "password for " + username()) + '}';
    }
}

/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.util.internal;

/**
 * Converts a wrapper distribution url to a URI.
 */
class WrapperDistributionUrlConverter {

    /**
     * Converts the given distribution url to a URI.
     * <p>
     * If the url is relative, it is resolved against the given file root.
     * Otherwise, the URI is created from the url.
     *
     * @param distributionUrl The distribution url.
     * @param fileRoot The root directory to resolve relative urls against.
     * @return The URI.
     * @throws URISyntaxException If the url is not a valid URI.
     */
    public static URI convertDistributionUrl(String distributionUrl, File fileRoot) throws URISyntaxException {
        URI source = new URI(distributionUrl);
        if (source.getScheme() == null) {
            //  No scheme means someone passed a relative url.
            //  In our context only file relative urls make sense.
            return new File(fileRoot, source.getSchemeSpecificPart()).toURI();
        } else {
            return source;
        }
    }
}

/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.wrapper;

class BootstrapMainStarter {

    public void start(String[] args, File gradleHome) throws Exception {
        File gradleJar = findLauncherJar(gradleHome);
        if (gradleJar == null) {
            throw new RuntimeException(String.format("Could not locate the Gradle launcher JAR in Gradle distribution '%s'.", gradleHome));
        }
        // The URLClassloader will also include the jars listed in the launcher jar's
        // Class-Path manifest attributes as candidates for loading classes.
        URLClassLoader contextClassLoader = new URLClassLoader(new URL[] { gradleJar.toURI().toURL() }, ClassLoader.getSystemClassLoader().getParent());
        Thread.currentThread().setContextClassLoader(contextClassLoader);
        Class<?> mainClass = contextClassLoader.loadClass("org.gradle.launcher.GradleMain");
        Method mainMethod = mainClass.getMethod("main", String[].class);
        mainMethod.invoke(null, new Object[] { args });
        ((Closeable) contextClassLoader).close();
    }

    static File findLauncherJar(File gradleHome) {
        File libDirectory = new File(gradleHome, "lib");
        if (libDirectory.exists() && libDirectory.isDirectory()) {
            File[] launcherJars = libDirectory.listFiles(new FilenameFilter() {

                @Override
                public boolean accept(File dir, String name) {
                    return name.matches("gradle-launcher-.*\\.jar");
                }
            });
            if (launcherJars != null && launcherJars.length == 1) {
                return launcherJars[0];
            }
        }
        return null;
    }
}

/*
 * Copyright 2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.wrapper;

class Download implements IDownload {

    public static final String UNKNOWN_VERSION = "0";

    public static final int DEFAULT_NETWORK_TIMEOUT_MILLISECONDS = 10 * 1000;

    private static final int BUFFER_SIZE = 10 * 1024;

    private static final int PROGRESS_CHUNK = 1024 * 1024;

    private final Logger logger;

    private final String appName;

    private final String appVersion;

    private final DownloadProgressListener progressListener;

    private final Map<String, String> systemProperties;

    private final int networkTimeout;

    public Download(Logger logger, String appName, String appVersion) {
        this(logger, null, appName, appVersion, convertSystemProperties(System.getProperties()));
    }

    public Download(Logger logger, String appName, String appVersion, int networkTimeout) {
        this(logger, null, appName, appVersion, convertSystemProperties(System.getProperties()), networkTimeout);
    }

    private static Map<String, String> convertSystemProperties(Properties properties) {
        Map<String, String> result = new HashMap<String, String>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue() == null ? null : entry.getValue().toString());
        }
        return result;
    }

    public Download(Logger logger, DownloadProgressListener progressListener, String appName, String appVersion, Map<String, String> systemProperties) {
        this(logger, progressListener, appName, appVersion, systemProperties, DEFAULT_NETWORK_TIMEOUT_MILLISECONDS);
    }

    public Download(Logger logger, DownloadProgressListener progressListener, String appName, String appVersion, Map<String, String> systemProperties, int networkTimeout) {
        this.logger = logger;
        this.appName = appName;
        this.appVersion = appVersion;
        this.systemProperties = systemProperties;
        this.progressListener = new DefaultDownloadProgressListener(logger, progressListener);
        this.networkTimeout = networkTimeout;
        configureProxyAuthentication();
    }

    private void configureProxyAuthentication() {
        if (systemProperties.get("http.proxyUser") != null || systemProperties.get("https.proxyUser") != null) {
            // Only an authenticator for proxies needs to be set. Basic authentication is supported by directly setting the request header field.
            Authenticator.setDefault(new ProxyAuthenticator(systemProperties));
        }
    }

    public void sendHeadRequest(URI uri) throws Exception {
        URL safeUrl = safeUri(uri).toURL();
        int responseCode = -1;
        try {
            HttpURLConnection conn = (HttpURLConnection) safeUrl.openConnection();
            conn.setRequestMethod("HEAD");
            addAuthentication(uri, conn);
            conn.setRequestProperty("User-Agent", calculateUserAgent());
            conn.setConnectTimeout(networkTimeout);
            conn.setReadTimeout(networkTimeout);
            conn.connect();
            responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new RuntimeException("HEAD request to " + safeUrl + " failed: response code (" + responseCode + ")");
            }
        } catch (IOException e) {
            throw new RuntimeException("HEAD request to " + safeUrl + " failed: response code (" + responseCode + "), timeout (" + networkTimeout + "ms)", e);
        }
    }

    @Override
    public void download(URI address, File destination) throws Exception {
        destination.getParentFile().mkdirs();
        downloadInternal(address, destination);
    }

    private void downloadInternal(URI address, File destination) throws Exception {
        OutputStream out = null;
        URLConnection conn;
        InputStream in = null;
        URL safeUrl = safeUri(address).toURL();
        try {
            out = new BufferedOutputStream(new FileOutputStream(destination));
            // No proxy is passed here as proxies are set globally using the HTTP(S) proxy system properties. The respective protocol handler implementation then makes use of these properties.
            conn = safeUrl.openConnection();
            addAuthentication(address, conn);
            final String userAgentValue = calculateUserAgent();
            conn.setRequestProperty("User-Agent", userAgentValue);
            conn.setConnectTimeout(networkTimeout);
            conn.setReadTimeout(networkTimeout);
            // Check HTTP response code before downloading
            if (conn instanceof HttpURLConnection) {
                HttpURLConnection httpConn = (HttpURLConnection) conn;
                int responseCode = httpConn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Server returned HTTP response code: " + responseCode + " for URL: " + safeUrl);
                }
            }
            in = conn.getInputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            int numRead;
            int totalLength = conn.getContentLength();
            long downloadedLength = 0;
            long progressCounter = 0;
            while ((numRead = in.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Download was interrupted.");
                }
                downloadedLength += numRead;
                progressCounter += numRead;
                if (progressCounter / PROGRESS_CHUNK > 0 || downloadedLength == totalLength) {
                    progressCounter = progressCounter - PROGRESS_CHUNK;
                    progressListener.downloadStatusChanged(address, totalLength, downloadedLength);
                }
                out.write(buffer, 0, numRead);
            }
        } catch (SocketTimeoutException e) {
            throw new IOException("Downloading from " + safeUrl + " failed: timeout (" + networkTimeout + "ms)", e);
        } finally {
            logger.log("");
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }

    /**
     * Create a safe URI from the given one by stripping out user info.
     *
     * @param uri Original URI
     * @return a new URI with no user info
     */
    static URI safeUri(URI uri) {
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to parse URI", e);
        }
    }

    private void addAuthentication(URI address, URLConnection connection) throws IOException {
        WrapperCredentials credentials = WrapperCredentials.findCredentials(address, systemProperties::get);
        if (credentials == null) {
            return;
        }
        if (!"https".equals(address.getScheme())) {
            logger.log("WARNING Using HTTP " + credentials.authorizationTypeDisplayName() + " Authentication over an insecure connection to download the Gradle distribution. Please consider using HTTPS.");
        }
        Map.Entry<String, String> authHeader = credentials.authorizationHeader();
        connection.setRequestProperty(authHeader.getKey(), authHeader.getValue());
    }

    private String calculateUserAgent() {
        String javaVendor = systemProperties.get("java.vendor");
        String javaVersion = systemProperties.get("java.version");
        String javaVendorVersion = systemProperties.get("java.vm.version");
        String osName = systemProperties.get("os.name");
        String osVersion = systemProperties.get("os.version");
        String osArch = systemProperties.get("os.arch");
        return String.format("%s/%s (%s;%s;%s) (%s;%s;%s)", appName, appVersion, osName, osVersion, osArch, javaVendor, javaVersion, javaVendorVersion);
    }

    private static class ProxyAuthenticator extends Authenticator {

        private final Map<String, String> systemProperties;

        private ProxyAuthenticator(Map<String, String> systemProperties) {
            this.systemProperties = systemProperties;
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            if (getRequestorType() == RequestorType.PROXY) {
                // Note: Do not use getRequestingProtocol() here, which is "http" even for HTTPS proxies.
                String protocol = getRequestingURL().getProtocol();
                String proxyUser = systemProperties.get(protocol + ".proxyUser");
                if (proxyUser != null) {
                    String proxyPassword = systemProperties.get(protocol + ".proxyPassword");
                    if (proxyPassword == null) {
                        proxyPassword = "";
                    }
                    return new PasswordAuthentication(proxyUser, proxyPassword.toCharArray());
                }
            }
            return super.getPasswordAuthentication();
        }
    }

    private static class DefaultDownloadProgressListener implements DownloadProgressListener {

        private final Logger logger;

        private final DownloadProgressListener delegate;

        private int previousDownloadPercent;

        public DefaultDownloadProgressListener(Logger logger, DownloadProgressListener delegate) {
            this.logger = logger;
            this.delegate = delegate;
            this.previousDownloadPercent = 0;
        }

        @Override
        public void downloadStatusChanged(URI address, long contentLength, long downloaded) {
            // If the total size of distribution is known, but there's no advanced progress listener, provide extra progress information
            if (contentLength > 0 && delegate == null) {
                appendPercentageSoFar(contentLength, downloaded);
            }
            if (contentLength != downloaded) {
                logger.append(".");
            }
            if (delegate != null) {
                delegate.downloadStatusChanged(address, contentLength, downloaded);
            }
        }

        private void appendPercentageSoFar(long contentLength, long downloaded) {
            try {
                int currentDownloadPercent = 10 * (calculateDownloadPercent(contentLength, downloaded) / 10);
                if (currentDownloadPercent != 0 && previousDownloadPercent != currentDownloadPercent) {
                    logger.append(String.valueOf(currentDownloadPercent)).append('%');
                    previousDownloadPercent = currentDownloadPercent;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private int calculateDownloadPercent(long totalLength, long downloadedLength) {
            return Math.min(100, Math.max(0, (int) ((downloadedLength / (double) totalLength) * 100)));
        }
    }
}

/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.wrapper;

interface DownloadProgressListener {

    /**
     * Reports the current progress of the download
     *
     * @param address       distribution url
     * @param contentLength the content length of the distribution, or -1 if the content length is not known.
     * @param downloaded    the total amount of currently downloaded bytes
     */
    void downloadStatusChanged(URI address, long contentLength, long downloaded);
}

/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.wrapper;

class GradleUserHomeLookup {

    public static final String DEFAULT_GRADLE_USER_HOME = System.getProperty("user.home") + "/.gradle";

    public static final String GRADLE_USER_HOME_PROPERTY_KEY = "gradle.user.home";

    public static final String GRADLE_USER_HOME_ENV_KEY = "GRADLE_USER_HOME";

    public static File gradleUserHome() {
        String gradleUserHome;
        if ((gradleUserHome = System.getProperty(GRADLE_USER_HOME_PROPERTY_KEY)) != null) {
            return new File(gradleUserHome);
        }
        if ((gradleUserHome = System.getenv(GRADLE_USER_HOME_ENV_KEY)) != null) {
            return new File(gradleUserHome);
        }
        return new File(DEFAULT_GRADLE_USER_HOME);
    }
}

/*
 * Copyright 2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.wrapper;

interface IDownload {

    void download(URI address, File destination) throws Exception;
}

/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.wrapper;

class Install {

    public static final String DEFAULT_DISTRIBUTION_PATH = "wrapper/dists";

    public static final String SHA_256 = ".sha256";

    public static final int DEFAULT_NETWORK_RETRIES = 0;

    public static final int DEFAULT_NETWORK_RETRY_BACK_OFF_MS = 500;

    private static final int BROKEN_ZIP_RETRIES = 3;

    private final Logger logger;

    private final IDownload download;

    private final PathAssembler pathAssembler;

    private final ExclusiveFileAccessManager exclusiveFileAccessManager = new ExclusiveFileAccessManager(120000, 200);

    public Install(Logger logger, IDownload download, PathAssembler pathAssembler) {
        this.logger = logger;
        this.download = download;
        this.pathAssembler = pathAssembler;
    }

    public File createDist(final WrapperConfiguration configuration) throws Exception {
        final URI distributionUrl = configuration.getDistribution();
        final PathAssembler.LocalDistribution localDistribution = pathAssembler.getDistribution(configuration);
        final File distDir = localDistribution.getDistributionDir();
        final File localZipFile = localDistribution.getZipFile();
        return exclusiveFileAccessManager.access(localZipFile, () -> {
            final File markerFile = new File(localZipFile.getParentFile(), localZipFile.getName() + ".ok");
            if (distDir.isDirectory() && markerFile.isFile()) {
                InstallCheck installCheck = verifyDistributionRoot(distDir, distDir.getAbsolutePath());
                if (installCheck.isVerified()) {
                    return installCheck.gradleHome;
                }
                // Distribution is invalid. Try to reinstall.
                System.err.println(installCheck.failureMessage);
                markerFile.delete();
            }
            fetchDistribution(localZipFile, distributionUrl, distDir, configuration);
            InstallCheck installCheck = verifyDistributionRoot(distDir, Download.safeUri(distributionUrl).toASCIIString());
            if (installCheck.isVerified()) {
                setExecutablePermissions(installCheck.gradleHome);
                markerFile.createNewFile();
                localZipFile.delete();
                return installCheck.gradleHome;
            }
            // Distribution couldn't be installed.
            throw new RuntimeException(installCheck.failureMessage);
        });
    }

    private void fetchDistribution(File localZipFile, URI distributionUrl, File distDir, WrapperConfiguration configuration) throws Exception {
        List<URI> distributionUrls = configuration.getDistributionUrls();
        for (int index = 0; index < distributionUrls.size(); index++) {
            URI currentUrl = distributionUrls.get(index);
            try {
                fetchDistributionFrom(localZipFile, currentUrl, distributionUrl, distDir, configuration);
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                if (index + 1 >= distributionUrls.size()) {
                    throw e;
                }
                localZipFile.delete();
                deleteLocalTopLevelDirs(distDir);
                logger.log("Download from " + Download.safeUri(currentUrl) + " failed. Trying the next download location.");
                logger.log("Reason: " + e.getMessage());
            }
        }
        throw new IllegalStateException("No distribution download locations configured.");
    }

    private void fetchDistributionFrom(File localZipFile, URI distributionUrl, URI originalDistributionUrl, File distDir, WrapperConfiguration configuration) throws Exception {
        String distributionSha256Sum = configuration.getDistributionSha256Sum();
        boolean failed = false;
        boolean originalLocation = distributionUrl.equals(originalDistributionUrl);
        int retries = originalLocation ? BROKEN_ZIP_RETRIES : 1;
        do {
            try {
                boolean needsDownload = !localZipFile.isFile() || failed;
                if (needsDownload) {
                    forceFetch(localZipFile, distributionUrl, originalLocation ? configuration.getRetries() : 0, configuration.getRetryBackOffMs());
                }
                deleteLocalTopLevelDirs(distDir);
                verifyDownloadChecksum(Download.safeUri(distributionUrl).toASCIIString(), localZipFile, distributionSha256Sum);
                unzipLocal(localZipFile, distDir);
                InstallCheck installCheck = verifyDistributionRoot(distDir, Download.safeUri(distributionUrl).toASCIIString());
                if (!installCheck.isVerified()) {
                    throw new RuntimeException(installCheck.failureMessage);
                }
                failed = false;
            } catch (ZipException e) {
                if (originalLocation && retries >= BROKEN_ZIP_RETRIES && distributionSha256Sum == null) {
                    distributionSha256Sum = fetchDistributionSha256Sum(configuration, localZipFile);
                }
                failed = true;
                retries--;
                if (retries <= 0) {
                    throw new RuntimeException("Downloaded distribution file " + localZipFile + " is no valid zip file.");
                }
            }
        } while (failed);
    }

    private String fetchDistributionSha256Sum(WrapperConfiguration configuration, File localZipFile) {
        URI distribution = configuration.getDistribution();
        try {
            URI distributionUrl = distribution.resolve(distribution.getPath() + SHA_256);
            File tmpZipFile = new File(localZipFile.getParentFile(), localZipFile.getName() + SHA_256);
            forceFetch(tmpZipFile, distributionUrl, configuration.getRetries(), configuration.getRetryBackOffMs());
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(tmpZipFile.toPath()), StandardCharsets.UTF_8))) {
                return reader.readLine();
            }
        } catch (Exception e) {
            logger.log("Could not fetch hash for " + Download.safeUri(distribution) + ".");
            logger.log("Reason: " + e.getMessage());
            return null;
        }
    }

    private void unzipLocal(File localZipFile, File distDir) throws IOException {
        try {
            unzip(localZipFile, distDir);
        } catch (IOException e) {
            logger.log("Could not unzip " + localZipFile.getAbsolutePath() + " to " + distDir.getAbsolutePath() + ".");
            logger.log("Reason: " + e.getMessage());
            throw e;
        }
    }

    private void deleteLocalTopLevelDirs(final File distDir) {
        List<File> topLevelDirs = listDirs(distDir);
        for (File dir : topLevelDirs) {
            logger.log("Deleting directory " + dir.getAbsolutePath());
            deleteDir(dir);
        }
    }

    private void forceFetch(File localTargetFile, URI distributionUrl, int networkRetries, int networkRetryBackOffMs) throws Exception {
        // negative retry parameter values will be handled as the defaults
        networkRetries = networkRetries >= 0 ? networkRetries : DEFAULT_NETWORK_RETRIES;
        networkRetryBackOffMs = networkRetryBackOffMs >= 0 ? networkRetryBackOffMs : DEFAULT_NETWORK_RETRY_BACK_OFF_MS;
        logger.log(String.format("Fetching distribution%s.", networkRetries <= 0 ? "" : String.format(" (retrying %d times, with an initial back off of %d ms)", networkRetries, networkRetryBackOffMs)));
        int attempts = networkRetries + 1;
        long currentBackOffMs = networkRetryBackOffMs;
        Exception lastException = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                File tempDownloadFile = new File(localTargetFile.getParentFile(), localTargetFile.getName() + ".part");
                Files.deleteIfExists(tempDownloadFile.toPath());
                logger.log("Downloading " + Download.safeUri(distributionUrl));
                download.download(distributionUrl, tempDownloadFile);
                Files.move(tempDownloadFile.toPath(), localTargetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException ioException) {
                lastException = ioException;
                logger.log(String.format("Attempt %d/%d failed. Reason: %s", attempt, attempts, ioException.getMessage()));
                if (attempt < attempts) {
                    Thread.sleep(currentBackOffMs);
                    currentBackOffMs *= 2;
                }
            }
        }
        throw lastException;
    }

    static String calculateSha256Sum(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream fis = Files.newInputStream(file.toPath())) {
            int n = 0;
            byte[] buffer = new byte[4096];
            while (n != -1) {
                n = fis.read(buffer);
                if (n > 0) {
                    md.update(buffer, 0, n);
                }
            }
        }
        byte[] byteData = md.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte byteDatum : byteData) {
            String hex = Integer.toHexString(0xff & byteDatum);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private InstallCheck verifyDistributionRoot(File distDir, String distributionDescription) {
        List<File> dirs = listDirs(distDir);
        if (dirs.isEmpty()) {
            return InstallCheck.failure(format("Gradle distribution '%s' does not contain any directories. Expected to find exactly 1 directory.", distributionDescription));
        }
        if (dirs.size() != 1) {
            return InstallCheck.failure(format("Gradle distribution '%s' contains too many directories. Expected to find exactly 1 directory.", distributionDescription));
        }
        File gradleHome = dirs.get(0);
        if (BootstrapMainStarter.findLauncherJar(gradleHome) == null) {
            return InstallCheck.failure(format("Gradle distribution '%s' does not appear to contain a Gradle distribution.", distributionDescription));
        }
        return InstallCheck.success(gradleHome);
    }

    private void verifyDownloadChecksum(String sourceUrl, File localZipFile, String expectedSum) throws Exception {
        if (expectedSum == null) {
            return;
        }
        // if a SHA-256 hash sum has been defined in gradle-wrapper.properties, verify it here
        String actualSum = calculateSha256Sum(localZipFile);
        if (expectedSum.equalsIgnoreCase(actualSum)) {
            return;
        }
        localZipFile.delete();
        String message = format("Verification of Gradle distribution failed!%n" + "%n" + "Your Gradle distribution may have been tampered with.%n" + "Confirm that the 'distributionSha256Sum' property in your gradle-wrapper.properties file is correct and you are downloading the wrapper from a trusted source.%n" + "%n" + "Distribution Url: %s%n" + "Download Location: %s%n" + "Expected checksum: '%s'%n" + "Actual checksum:   '%s'%n" + "Visit https://gradle.org/release-checksums/ to verify the checksums of official distributions. If your build uses a custom distribution, see with its provider.", sourceUrl, localZipFile.getAbsolutePath(), expectedSum, actualSum);
        throw new RuntimeException(message);
    }

    @SuppressWarnings("MixedMutabilityReturnType")
    private List<File> listDirs(File distDir) {
        if (!distDir.exists()) {
            return emptyList();
        }
        File[] files = distDir.listFiles();
        if (files == null) {
            return emptyList();
        }
        List<File> dirs = new ArrayList<>();
        for (File file : files) {
            if (file.isDirectory()) {
                dirs.add(file);
            }
        }
        return dirs;
    }

    private void setExecutablePermissions(File gradleHome) {
        if (isWindows()) {
            return;
        }
        File gradleCommand = new File(gradleHome, "bin/gradle");
        String errorMessage = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("chmod", "755", gradleCommand.getCanonicalPath());
            Process p = pb.start();
            if (p.waitFor() != 0) {
                BufferedReader is = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
                Formatter stdout = new Formatter();
                String line;
                while ((line = is.readLine()) != null) {
                    stdout.format("%s%n", line);
                }
                errorMessage = stdout.toString();
            }
        } catch (IOException e) {
            errorMessage = e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errorMessage = e.getMessage();
        }
        if (errorMessage != null) {
            logger.log("Could not set executable permissions for: " + gradleCommand.getAbsolutePath());
        }
    }

    private boolean isWindows() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.US);
        return osName.contains("windows");
    }

    private boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) {
                        return false;
                    }
                }
            }
        }
        // The directory is now empty so delete it
        return dir.delete();
    }

    private void unzip(File zip, File dest) throws IOException {
        try (ZipFile zipFile = new ZipFile(zip)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File destFile = new File(dest, PathTraversalChecker.safePathName(entry.getName()));
                if (entry.isDirectory()) {
                    destFile.mkdirs();
                    continue;
                }
                try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(destFile.toPath()))) {
                    copyInputStream(zipFile.getInputStream(entry), outputStream);
                }
            }
        }
    }

    private void copyInputStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = in.read(buffer)) >= 0) {
            out.write(buffer, 0, len);
        }
        in.close();
        out.close();
    }

    private static class InstallCheck {

        private final File gradleHome;

        private final String failureMessage;

        private static InstallCheck failure(String message) {
            return new InstallCheck(null, message);
        }

        private static InstallCheck success(File gradleHome) {
            return new InstallCheck(gradleHome, null);
        }

        private InstallCheck(File gradleHome, String failureMessage) {
            this.gradleHome = gradleHome;
            this.failureMessage = failureMessage;
        }

        private boolean isVerified() {
            return gradleHome != null;
        }
    }
}

/*
 * Copyright 2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.wrapper;

class Logger implements Appendable {

    private final boolean quiet;

    public Logger(boolean quiet) {
        this.quiet = quiet;
    }

    public void log(String message) {
        if (!quiet) {
            System.out.println(message);
        }
    }

    @Override
    public Appendable append(CharSequence csq) {
        if (!quiet) {
            System.out.append(csq);
        }
        return this;
    }

    @Override
    public Appendable append(CharSequence csq, int start, int end) {
        if (!quiet) {
            System.out.append(csq, start, end);
        }
        return this;
    }

    @Override
    public Appendable append(char c) {
        if (!quiet) {
            System.out.append(c);
        }
        return this;
    }
}

/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.wrapper;

/**
 * Parses JSON without external runtime dependencies.
 *
 * <p>Objects preserve member order, numbers are represented by {@link BigDecimal}, duplicate
 * object keys are rejected, and nesting is limited to protect Wrapper startup.</p>
 */
final class MinimalJsonParser {

    private static final int MAX_DEPTH = 64;

    private MinimalJsonParser() {
    }

    /**
     * Parses one complete JSON value.
     *
     * @param input the JSON text
     * @return the parsed JSON value
     * @throws IllegalArgumentException if the input is malformed
     */
    static Object parse(String input) {
        return new Parser(input).parse();
    }

    private static final class Parser {

        private final String input;

        private int offset;

        private Parser(String input) {
            this.input = input;
            if (!input.isEmpty() && input.charAt(0) == '\ufeff') {
                offset = 1;
            }
        }

        private Object parse() {
            skipWhitespace();
            Object value = parseValue(0);
            skipWhitespace();
            if (offset != input.length()) {
                throw error("Unexpected trailing content");
            }
            return value;
        }

        private Object parseValue(int depth) {
            if (depth > MAX_DEPTH) {
                throw error("Maximum nesting depth exceeded");
            }
            if (offset >= input.length()) {
                throw error("Expected a value");
            }
            char current = input.charAt(offset);
            switch(current) {
                case '{':
                    return parseObject(depth + 1);
                case '[':
                    return parseArray(depth + 1);
                case '"':
                    return parseString();
                case 't':
                    expectLiteral("true");
                    return Boolean.TRUE;
                case 'f':
                    expectLiteral("false");
                    return Boolean.FALSE;
                case 'n':
                    expectLiteral("null");
                    return null;
                default:
                    if (current == '-' || isDigit(current)) {
                        return parseNumber();
                    }
                    throw error("Unexpected character '" + current + "'");
            }
        }

        private Map<String, Object> parseObject(int depth) {
            offset++;
            skipWhitespace();
            Map<String, Object> result = new LinkedHashMap<>();
            if (consume('}')) {
                return result;
            }
            while (true) {
                if (offset >= input.length() || input.charAt(offset) != '"') {
                    throw error("Expected an object key");
                }
                String key = parseString();
                if (result.containsKey(key)) {
                    throw error("Duplicate object key '" + key + "'");
                }
                skipWhitespace();
                expect(':');
                skipWhitespace();
                result.put(key, parseValue(depth));
                skipWhitespace();
                if (consume('}')) {
                    return result;
                }
                expect(',');
                skipWhitespace();
            }
        }

        private List<Object> parseArray(int depth) {
            offset++;
            skipWhitespace();
            List<Object> result = new ArrayList<>();
            if (consume(']')) {
                return result;
            }
            while (true) {
                result.add(parseValue(depth));
                skipWhitespace();
                if (consume(']')) {
                    return result;
                }
                expect(',');
                skipWhitespace();
            }
        }

        private String parseString() {
            offset++;
            StringBuilder result = new StringBuilder();
            while (offset < input.length()) {
                char current = input.charAt(offset++);
                if (current == '"') {
                    return result.toString();
                }
                if (current == '\\') {
                    if (offset >= input.length()) {
                        throw error("Unterminated escape sequence");
                    }
                    char escaped = input.charAt(offset++);
                    switch(escaped) {
                        case '"':
                        case '\\':
                        case '/':
                            result.append(escaped);
                            break;
                        case 'b':
                            result.append('\b');
                            break;
                        case 'f':
                            result.append('\f');
                            break;
                        case 'n':
                            result.append('\n');
                            break;
                        case 'r':
                            result.append('\r');
                            break;
                        case 't':
                            result.append('\t');
                            break;
                        case 'u':
                            result.append(parseUnicodeEscape());
                            break;
                        default:
                            throw error("Invalid escape sequence '\\" + escaped + "'");
                    }
                } else {
                    if (current < 0x20) {
                        throw error("Unescaped control character in string");
                    }
                    result.append(current);
                }
            }
            throw error("Unterminated string");
        }

        private char parseUnicodeEscape() {
            if (offset + 4 > input.length()) {
                throw error("Incomplete Unicode escape");
            }
            int value = 0;
            for (int i = 0; i < 4; i++) {
                char current = input.charAt(offset++);
                int digit = Character.digit(current, 16);
                if (digit < 0) {
                    throw error("Invalid Unicode escape");
                }
                value = (value << 4) | digit;
            }
            return (char) value;
        }

        private BigDecimal parseNumber() {
            int start = offset;
            consume('-');
            if (consume('0')) {
                if (offset < input.length() && isDigit(input.charAt(offset))) {
                    throw error("Leading zero in number");
                }
            } else {
                requireDigit();
                consumeDigits();
            }
            if (consume('.')) {
                requireDigit();
                consumeDigits();
            }
            if (consume('e') || consume('E')) {
                if (!consume('+')) {
                    consume('-');
                }
                requireDigit();
                consumeDigits();
            }
            try {
                return new BigDecimal(input.substring(start, offset));
            } catch (NumberFormatException e) {
                throw error("Invalid number");
            }
        }

        private void consumeDigits() {
            while (offset < input.length() && isDigit(input.charAt(offset))) {
                offset++;
            }
        }

        private void requireDigit() {
            if (offset >= input.length() || !isDigit(input.charAt(offset))) {
                throw error("Expected a digit");
            }
        }

        private void expectLiteral(String literal) {
            if (!input.regionMatches(offset, literal, 0, literal.length())) {
                throw error("Expected '" + literal + "'");
            }
            offset += literal.length();
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                throw error("Expected '" + expected + "'");
            }
        }

        private boolean consume(char expected) {
            if (offset < input.length() && input.charAt(offset) == expected) {
                offset++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (offset < input.length()) {
                char current = input.charAt(offset);
                if (current != ' ' && current != '\t' && current != '\r' && current != '\n') {
                    return;
                }
                offset++;
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException("Invalid JSON at offset " + offset + ": " + message);
        }

        private static boolean isDigit(char value) {
            return value >= '0' && value <= '9';
        }
    }
}

/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.wrapper;

/**
 * Loads user-level mirror rules and resolves ordered distribution download candidates.
 *
 * <p>Mirror results precede the original distribution URL, which is always retained as the final
 * fallback. Rules that require a checksum are skipped when no checksum is configured.</p>
 */
final class MirrorConfiguration {

    /**
     * Default configuration file name within the Gradle user home.
     */
    static final String FILE_NAME = "gradle-wrapper-neo.json";

    /**
     * Environment variable that overrides the configuration file location.
     */
    static final String CONFIGURATION_ENV = "GRADLE_WRAPPER_NEO_CONFIG";

    private static final int MAX_FILE_SIZE = 1024 * 1024;

    private static final Set<String> ROOT_FIELDS = fields("version", "mirrors");

    private static final Set<String> MIRROR_FIELDS = fields("enabled", "pattern", "replacement", "requireChecksum");

    private final List<Mirror> mirrors;

    private MirrorConfiguration(List<Mirror> mirrors) {
        this.mirrors = Collections.unmodifiableList(new ArrayList<>(mirrors));
    }

    /**
     * Returns a configuration that resolves only the original distribution URL.
     * @return an empty mirror configuration
     */
    static MirrorConfiguration empty() {
        return new MirrorConfiguration(Collections.emptyList());
    }

    /**
     * Loads the effective configuration file for a Gradle user home.
     *
     * @param gradleUserHome the effective Gradle user home
     * @return the parsed mirror configuration, or an empty configuration when the file is absent
     */
    static MirrorConfiguration load(File gradleUserHome) {
        File file = configurationFile(gradleUserHome, System.getenv(CONFIGURATION_ENV));
        return loadFile(file);
    }

    /**
     * Resolves the configuration file from the default home and an optional environment override.
     *
     * @param gradleUserHome the effective Gradle user home
     * @param configuredPath the environment override, or {@code null}
     * @return the effective configuration file
     */
    static File configurationFile(File gradleUserHome, String configuredPath) {
        if (configuredPath == null) {
            return new File(gradleUserHome, FILE_NAME);
        }
        if (configuredPath.isEmpty()) {
            throw new IllegalArgumentException("Environment variable " + CONFIGURATION_ENV + " must not be empty.");
        }
        File file = new File(configuredPath);
        if (!file.isAbsolute()) {
            throw new IllegalArgumentException("Environment variable " + CONFIGURATION_ENV + " must contain an absolute path: " + configuredPath);
        }
        return file;
    }

    private static MirrorConfiguration loadFile(File file) {
        if (!file.exists()) {
            return empty();
        }
        if (!file.isFile()) {
            throw new RuntimeException("Mirror configuration is not a file: " + file);
        }
        try {
            if (file.length() > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("Mirror configuration exceeds " + MAX_FILE_SIZE + " bytes.");
            }
            byte[] content = Files.readAllBytes(file.toPath());
            if (content.length > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("Mirror configuration exceeds " + MAX_FILE_SIZE + " bytes.");
            }
            String json = new String(content, StandardCharsets.UTF_8);
            return parse(json);
        } catch (IOException | RuntimeException e) {
            throw new RuntimeException("Could not load mirror configuration from '" + file + "'.", e);
        }
    }

    /**
     * Parses and validates a Wrapper Neo mirror configuration.
     *
     * @param json the JSON configuration text
     * @return the validated mirror configuration
     */
    static MirrorConfiguration parse(String json) {
        Map<String, Object> root = object(MinimalJsonParser.parse(json), "root");
        rejectUnknownFields(root, ROOT_FIELDS, "root");
        BigDecimal version = number(required(root, "version", "root"), "root.version");
        if (version.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("Unsupported mirror configuration version: " + version);
        }
        List<Mirror> mirrors = new ArrayList<>();
        if (root.containsKey("mirrors")) {
            Object configuredMirrors = root.get("mirrors");
            List<Object> entries = array(configuredMirrors, "root.mirrors");
            for (int index = 0; index < entries.size(); index++) {
                Mirror mirror = parseMirror(entries.get(index), "root.mirrors[" + index + "]");
                if (mirror != null) {
                    mirrors.add(mirror);
                }
            }
        }
        return new MirrorConfiguration(mirrors);
    }

    /**
     * Resolves mirror candidates for a distribution URL.
     *
     * @param source the original distribution URL
     * @param checksumProvided whether the distribution has an expected SHA-256 checksum
     * @return distinct candidate URLs in attempt order, ending with {@code source}
     */
    List<URI> resolve(URI source, boolean checksumProvided) {
        LinkedHashSet<URI> result = new LinkedHashSet<>();
        for (Mirror mirror : mirrors) {
            URI resolved = mirror.resolve(source, checksumProvided);
            if (resolved != null && !resolved.equals(source)) {
                result.add(resolved);
            }
        }
        result.add(source);
        return new ArrayList<>(result);
    }

    private static Mirror parseMirror(Object value, String path) {
        Map<String, Object> mirror = object(value, path);
        rejectUnknownFields(mirror, MIRROR_FIELDS, path);
        if (!optionalBoolean(mirror, "enabled", true, path)) {
            return null;
        }
        String patternText = string(required(mirror, "pattern", path), path + ".pattern");
        Pattern pattern;
        try {
            pattern = Pattern.compile(patternText);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid regular expression at " + path + ".pattern: " + e.getDescription(), e);
        }
        String replacement = string(required(mirror, "replacement", path), path + ".replacement");
        boolean requireChecksum = optionalBoolean(mirror, "requireChecksum", true, path);
        return new Mirror(pattern, replacement, requireChecksum);
    }

    private static Object required(Map<String, Object> object, String field, String path) {
        if (!object.containsKey(field)) {
            throw new IllegalArgumentException("Missing required field " + path + "." + field + ".");
        }
        return object.get(field);
    }

    private static boolean optionalBoolean(Map<String, Object> object, String field, boolean defaultValue, String path) {
        if (!object.containsKey(field)) {
            return defaultValue;
        }
        Object value = object.get(field);
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException(path + "." + field + " must be a boolean.");
        }
        return (Boolean) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String path) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException(path + " must be an object.");
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(Object value, String path) {
        if (!(value instanceof List)) {
            throw new IllegalArgumentException(path + " must be an array.");
        }
        return (List<Object>) value;
    }

    private static String string(Object value, String path) {
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(path + " must be a string.");
        }
        return (String) value;
    }

    private static BigDecimal number(Object value, String path) {
        if (!(value instanceof BigDecimal)) {
            throw new IllegalArgumentException(path + " must be a number.");
        }
        return (BigDecimal) value;
    }

    private static void rejectUnknownFields(Map<String, Object> object, Set<String> allowedFields, String path) {
        for (String field : object.keySet()) {
            if (!allowedFields.contains(field)) {
                throw new IllegalArgumentException("Unknown field " + path + "." + field + ".");
            }
        }
    }

    private static Set<String> fields(String... values) {
        Set<String> result = new HashSet<>();
        Collections.addAll(result, values);
        return Collections.unmodifiableSet(result);
    }

    private static final class Mirror {

        private final Pattern pattern;

        private final String replacement;

        private final boolean requireChecksum;

        private Mirror(Pattern pattern, String replacement, boolean requireChecksum) {
            this.pattern = pattern;
            this.replacement = replacement;
            this.requireChecksum = requireChecksum;
        }

        private URI resolve(URI source, boolean checksumProvided) {
            if (requireChecksum && !checksumProvided) {
                return null;
            }
            Matcher matcher = pattern.matcher(source.toASCIIString());
            if (!matcher.matches()) {
                return null;
            }
            String resolvedText;
            try {
                resolvedText = matcher.replaceFirst(replacement);
            } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                throw new IllegalArgumentException("Invalid mirror replacement '" + replacement + "'.", e);
            }
            URI resolved;
            try {
                resolved = URI.create(resolvedText);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Mirror replacement produced an invalid URI: " + resolvedText, e);
            }
            if (!resolved.isAbsolute() || !"https".equalsIgnoreCase(resolved.getScheme()) || resolved.getHost() == null || resolved.getFragment() != null) {
                throw new IllegalArgumentException("Mirror replacement must produce an absolute HTTPS URI without a fragment: " + resolvedText);
            }
            return resolved;
        }
    }
}

/*
 * Copyright 2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.wrapper;

class PathAssembler {

    public static final String GRADLE_USER_HOME_STRING = "GRADLE_USER_HOME";

    public static final String PROJECT_STRING = "PROJECT";

    private final File gradleUserHome;

    private final File projectDirectory;

    public PathAssembler(File gradleUserHome, File projectDirectory) {
        this.gradleUserHome = gradleUserHome;
        this.projectDirectory = projectDirectory;
    }

    /**
     * Determines the local locations for the distribution to use given the supplied configuration.
     */
    public LocalDistribution getDistribution(WrapperConfiguration configuration) {
        String baseName = getDistName(configuration.getDistribution());
        String distName = removeExtension(baseName);
        String rootDirName = rootDirName(distName, configuration);
        File distDir = new File(getBaseDir(configuration.getDistributionBase()), configuration.getDistributionPath() + "/" + rootDirName);
        File distZip = new File(getBaseDir(configuration.getZipBase()), configuration.getZipPath() + "/" + rootDirName + "/" + baseName);
        return new LocalDistribution(distDir, distZip);
    }

    private String rootDirName(String distName, WrapperConfiguration configuration) {
        String urlHash = getHash(Download.safeUri(configuration.getDistribution()).toASCIIString());
        return distName + "/" + urlHash;
    }

    /**
     * This method computes a hash of the provided {@code string}.
     * <p>
     * The algorithm in use by this method is as follows:
     * <ol>
     *    <li>Compute the MD5 value of the UTF-8 {@code string}.</li>
     *    <li>Truncate leading zeros (i.e., treat the MD5 value as a number).</li>
     *    <li>Convert to base 36 (the characters {@code 0-9a-z}).</li>
     * </ol>
     */
    @SuppressWarnings("StringCharset")
    private String getHash(String string) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] bytes = string.getBytes("UTF-8");
            messageDigest.update(bytes);
            return new BigInteger(1, messageDigest.digest()).toString(36);
        } catch (Exception e) {
            throw new RuntimeException("Could not hash input string.", e);
        }
    }

    private String removeExtension(String name) {
        int p = name.lastIndexOf(".");
        if (p < 0) {
            return name;
        }
        return name.substring(0, p);
    }

    private String getDistName(URI distUrl) {
        String path = distUrl.getPath();
        int p = path.lastIndexOf("/");
        if (p < 0) {
            return path;
        }
        return path.substring(p + 1);
    }

    private File getBaseDir(String base) {
        if (base.equals(GRADLE_USER_HOME_STRING)) {
            return gradleUserHome;
        } else if (base.equals(PROJECT_STRING)) {
            return projectDirectory;
        } else {
            throw new RuntimeException("Base: " + base + " is unknown");
        }
    }

    public static class LocalDistribution {

        private final File distZip;

        private final File distDir;

        public LocalDistribution(File distDir, File distZip) {
            this.distDir = distDir;
            this.distZip = distZip;
        }

        /**
         * Returns the location to install the distribution into.
         */
        public File getDistributionDir() {
            return distDir;
        }

        /**
         * Returns the location to install the distribution ZIP file to.
         */
        public File getZipFile() {
            return distZip;
        }
    }
}

/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.wrapper;

class PropertiesFileHandler {

    private static final String SYSTEM_PROP_PREFIX = "systemProp.";

    private static final String JVMARGS_PROP_KEY = "org.gradle.jvmargs";

    private static final String DEBUG_PROP_KEY = "org.gradle.debug";

    private static final String DEBUG_PROP_VALUE = "true";

    public static Map<String, String> getSystemProperties(File propertiesFile) {
        if (!propertiesFile.isFile()) {
            return Collections.emptyMap();
        }
        Properties properties = loadProperties(propertiesFile);
        Map<String, String> systemProperties = new HashMap<String, String>();
        for (Object argument : properties.keySet()) {
            if (argument.toString().startsWith(SYSTEM_PROP_PREFIX)) {
                String key = argument.toString().substring(SYSTEM_PROP_PREFIX.length());
                if (key.length() > 0) {
                    systemProperties.put(key, properties.get(argument).toString());
                }
            }
        }
        return Collections.unmodifiableMap(systemProperties);
    }

    public static List<String> getJvmArgs(File propertiesFile) {
        if (!propertiesFile.isFile()) {
            return Collections.emptyList();
        }
        Properties properties = loadProperties(propertiesFile);
        List<String> jvmArgs = new ArrayList<String>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            if (JVMARGS_PROP_KEY.equals(entry.getKey())) {
                Object jvmArgsPropValue = entry.getValue();
                if (jvmArgsPropValue instanceof String) {
                    jvmArgs.addAll(ArgumentsSplitter.split((String) jvmArgsPropValue));
                }
            } else if (DEBUG_PROP_KEY.equals(entry.getKey()) && DEBUG_PROP_VALUE.equals(entry.getValue())) {
                jvmArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005");
            }
        }
        return Collections.unmodifiableList(jvmArgs);
    }

    private static Properties loadProperties(File propertiesFile) {
        Properties properties = new Properties();
        try {
            FileInputStream inStream = new FileInputStream(propertiesFile);
            try {
                properties.load(inStream);
            } finally {
                inStream.close();
            }
        } catch (IOException e) {
            throw new RuntimeException("Error when loading properties file=" + propertiesFile, e);
        }
        return properties;
    }
}

/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.wrapper;

class WrapperConfiguration {

    private URI distribution;

    private String distributionBase = PathAssembler.GRADLE_USER_HOME_STRING;

    private String distributionPath = Install.DEFAULT_DISTRIBUTION_PATH;

    private String distributionSha256Sum;

    private String zipBase = PathAssembler.GRADLE_USER_HOME_STRING;

    private String zipPath = Install.DEFAULT_DISTRIBUTION_PATH;

    private int networkTimeout = Download.DEFAULT_NETWORK_TIMEOUT_MILLISECONDS;

    private boolean validateDistributionUrl = true;

    private int retries = Install.DEFAULT_NETWORK_RETRIES;

    private int retryBackOffMs = Install.DEFAULT_NETWORK_RETRY_BACK_OFF_MS;

    private MirrorConfiguration mirrorConfiguration = MirrorConfiguration.empty();

    public URI getDistribution() {
        return distribution;
    }

    public void setDistribution(URI distribution) {
        this.distribution = distribution;
    }

    List<URI> getDistributionUrls() {
        return mirrorConfiguration.resolve(distribution, distributionSha256Sum != null);
    }

    void setMirrorConfiguration(MirrorConfiguration mirrorConfiguration) {
        this.mirrorConfiguration = mirrorConfiguration;
    }

    public String getDistributionBase() {
        return distributionBase;
    }

    public void setDistributionBase(String distributionBase) {
        this.distributionBase = distributionBase;
    }

    public String getDistributionPath() {
        return distributionPath;
    }

    public void setDistributionPath(String distributionPath) {
        this.distributionPath = distributionPath;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetryBackOffMs(int retryBackOffMs) {
        this.retryBackOffMs = retryBackOffMs;
    }

    public int getRetryBackOffMs() {
        return retryBackOffMs;
    }

    public String getDistributionSha256Sum() {
        return distributionSha256Sum;
    }

    public void setDistributionSha256Sum(String distributionSha256Sum) {
        this.distributionSha256Sum = distributionSha256Sum;
    }

    public String getZipBase() {
        return zipBase;
    }

    public void setZipBase(String zipBase) {
        this.zipBase = zipBase;
    }

    public String getZipPath() {
        return zipPath;
    }

    public void setZipPath(String zipPath) {
        this.zipPath = zipPath;
    }

    public int getNetworkTimeout() {
        return networkTimeout;
    }

    public void setNetworkTimeout(int networkTimeout) {
        this.networkTimeout = networkTimeout;
    }

    public boolean getValidateDistributionUrl() {
        return validateDistributionUrl;
    }

    public void setValidateDistributionUrl(boolean validateDistributionUrl) {
        this.validateDistributionUrl = validateDistributionUrl;
    }
}

/*
 * Copyright 2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.wrapper;

class WrapperExecutor {

    public static final String DISTRIBUTION_URL_PROPERTY = "distributionUrl";

    public static final String DISTRIBUTION_BASE_PROPERTY = "distributionBase";

    public static final String DISTRIBUTION_PATH_PROPERTY = "distributionPath";

    public static final String DISTRIBUTION_SHA_256_SUM = "distributionSha256Sum";

    public static final String ZIP_STORE_BASE_PROPERTY = "zipStoreBase";

    public static final String ZIP_STORE_PATH_PROPERTY = "zipStorePath";

    public static final String NETWORK_TIMEOUT_PROPERTY = "networkTimeout";

    public static final String VALIDATE_DISTRIBUTION_URL = "validateDistributionUrl";

    public static final String RETRIES_PROPERTY = "retries";

    public static final String RETRY_BACK_OFF_PROPERTY = "retryBackOffMs";

    private final Properties properties;

    private final File propertiesFile;

    private final WrapperConfiguration config = new WrapperConfiguration();

    public static File wrapperPropertiesForProjectDirectory(File projectDir) {
        return new File(projectDir, "gradle/wrapper/gradle-wrapper.properties");
    }

    public static WrapperExecutor forProjectDirectory(File projectDir) {
        return new WrapperExecutor(wrapperPropertiesForProjectDirectory(projectDir), new Properties());
    }

    public static WrapperExecutor forWrapperPropertiesFile(File propertiesFile) {
        if (!propertiesFile.exists()) {
            throw new RuntimeException(String.format("Wrapper properties file '%s' does not exist.", propertiesFile));
        }
        return new WrapperExecutor(propertiesFile, new Properties());
    }

    WrapperExecutor(File propertiesFile, Properties properties) {
        this.properties = properties;
        this.propertiesFile = propertiesFile;
        if (propertiesFile.exists()) {
            try {
                loadProperties(propertiesFile, properties);
                config.setDistribution(WrapperDistributionUrlConverter.convertDistributionUrl(readDistroUrl(), propertiesFile.getParentFile()));
                config.setDistributionBase(getProperty(DISTRIBUTION_BASE_PROPERTY, config.getDistributionBase()));
                config.setDistributionPath(getProperty(DISTRIBUTION_PATH_PROPERTY, config.getDistributionPath()));
                config.setDistributionSha256Sum(getProperty(DISTRIBUTION_SHA_256_SUM, config.getDistributionSha256Sum(), false));
                config.setZipBase(getProperty(ZIP_STORE_BASE_PROPERTY, config.getZipBase()));
                config.setZipPath(getProperty(ZIP_STORE_PATH_PROPERTY, config.getZipPath()));
                config.setNetworkTimeout(getProperty(NETWORK_TIMEOUT_PROPERTY, config.getNetworkTimeout()));
                config.setValidateDistributionUrl(getProperty(VALIDATE_DISTRIBUTION_URL, config.getValidateDistributionUrl()));
                config.setRetries(getProperty(RETRIES_PROPERTY, config.getRetries()));
                config.setRetryBackOffMs(getProperty(RETRY_BACK_OFF_PROPERTY, config.getRetryBackOffMs()));
            } catch (Exception e) {
                throw new RuntimeException(String.format("Could not load wrapper properties from '%s'.", propertiesFile), e);
            }
        }
    }

    private String readDistroUrl() {
        if (properties.getProperty(DISTRIBUTION_URL_PROPERTY) == null) {
            reportMissingProperty(DISTRIBUTION_URL_PROPERTY);
        }
        return getProperty(DISTRIBUTION_URL_PROPERTY);
    }

    private static void loadProperties(File propertiesFile, Properties properties) throws IOException {
        InputStream inStream = new FileInputStream(propertiesFile);
        try {
            properties.load(inStream);
        } finally {
            inStream.close();
        }
    }

    /**
     * Returns the distribution which this wrapper will use. Returns null if no wrapper meta-data was found in the specified project directory.
     */
    public URI getDistribution() {
        return config.getDistribution();
    }

    /**
     * Returns the configuration for this wrapper.
     */
    public WrapperConfiguration getConfiguration() {
        return config;
    }

    public void execute(String[] args, Install install, BootstrapMainStarter bootstrapMainStarter) throws Exception {
        File gradleHome = install.createDist(config);
        bootstrapMainStarter.start(args, gradleHome);
    }

    private String getProperty(String propertyName) {
        return getProperty(propertyName, null, true);
    }

    private String getProperty(String propertyName, String defaultValue) {
        return getProperty(propertyName, defaultValue, true);
    }

    private int getProperty(String propertyName, int defaultValue) {
        return Integer.parseInt(getProperty(propertyName, String.valueOf(defaultValue)));
    }

    private boolean getProperty(String propertyName, boolean defaultValue) {
        return Boolean.parseBoolean(getProperty(propertyName, String.valueOf(defaultValue)));
    }

    private String getProperty(String propertyName, String defaultValue, boolean required) {
        String value = properties.getProperty(propertyName);
        if (value != null) {
            return value;
        }
        if (defaultValue != null) {
            return defaultValue;
        }
        if (required) {
            return reportMissingProperty(propertyName);
        } else {
            return null;
        }
    }

    private String reportMissingProperty(String propertyName) {
        throw new RuntimeException(String.format("No value with key '%s' specified in wrapper properties file '%s'.", propertyName, propertiesFile));
    }
}

/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// package org.gradle.wrapper.neo;

/**
 * Compiles the source-based Wrapper into a cached JAR and relaunches it when the source changes.
 *
 * <p>The launcher supplies the project home, source file, and target JAR as absolute system
 * properties. A file lock serializes cache updates across concurrent Wrapper processes.</p>
 */
final class Bootstrap {

    private static final String MAIN_CLASS_NAME = "GradleWrapperNeo";

    private static final String TEMPORARY_JAR_PREFIX = "gradle-wrapper-neo-";

    private static final String CLASSES_DIR_NAME = "classes";

    private static final String LOCK_FILE_NAME = "lock";

    private static final String BOOTSTRAP_PROPERTY = "gradle.wrapper.neo.bootstrap";

    /**
     * System property containing the absolute project directory used as the Wrapper application home.
     */
    public static final String APP_HOME_PROPERTY = "org.gradle.wrapper.neo.app-home";

    /**
     * System property containing the absolute path to {@code GradleWrapperNeo.java}.
     */
    public static final String SOURCE_FILE_PROPERTY = "org.gradle.wrapper.neo.source-file";

    /**
     * System property containing the absolute path to the cached Wrapper JAR.
     */
    public static final String JAR_FILE_PROPERTY = "org.gradle.wrapper.neo.jar-file";

    private static final String MANIFEST_SOURCE_SHA256 = "Gradle-Wrapper-Neo-Source-SHA256";

    private Bootstrap() {
    }

    /**
     * Ensures the cached Wrapper JAR matches the configured source and relaunches it when needed.
     *
     * @param args the command-line arguments forwarded to the Wrapper
     * @param mainClass the class used to locate the currently running code
     * @return {@code true} when the caller must stop because another Wrapper process was launched
     * @throws Exception if the source cannot be compiled, cached, or launched
     */
    public static boolean handle(String[] args, Class<?> mainClass) throws Exception {
        Path appHome = appHome();
        Path sourceFile = sourceFile();
        Path targetJar = jarFile();
        if (!Files.isDirectory(appHome)) {
            throw new RuntimeException("Application home directory '" + appHome + "' does not exist.");
        }
        if (!Files.isRegularFile(sourceFile)) {
            throw new RuntimeException("Wrapper source file '" + sourceFile + "' does not exist.");
        }
        if (Boolean.getBoolean(BOOTSTRAP_PROPERTY)) {
            Path stagingClassesDir = codeSource(mainClass);
            if (!Files.isDirectory(stagingClassesDir)) {
                throw new RuntimeException("Bootstrap classes directory '" + stagingClassesDir + "' does not exist.");
            }
            int exitCode;
            try {
                withLock(targetJar.getParent(), () -> {
                    if (!isCurrent(targetJar, sourceFile)) {
                        Path classesDir = classesDir(targetJar);
                        recreateDirectory(classesDir);
                        copyDirectory(stagingClassesDir, classesDir);
                        writeJar(sourceFile, classesDir, targetJar);
                    }
                    return null;
                });
                exitCode = launchJar(targetJar, appHome, sourceFile, targetJar, args);
            } finally {
                deleteRecursively(stagingClassesDir);
                deleteIfEmpty(stagingClassesDir.getParent());
            }
            System.exit(exitCode);
            return true;
        }
        Path currentJar = codeSource(mainClass);
        if (!Files.isRegularFile(currentJar)) {
            throw new RuntimeException("Cached wrapper JAR '" + currentJar + "' does not exist.");
        }
        if (!isExpectedJar(currentJar, targetJar, sourceFile)) {
            throw new RuntimeException("Running wrapper JAR '" + currentJar + "' does not match configured JAR '" + targetJar + "'.");
        }
        if (isCurrent(currentJar, sourceFile)) {
            return false;
        }
        Path classesDir = classesDir(targetJar);
        Files.createDirectories(targetJar.getParent());
        Path nextJar = Files.createTempFile(targetJar.getParent(), TEMPORARY_JAR_PREFIX, ".jar");
        Path launchJar;
        try {
            launchJar = withLock(targetJar.getParent(), () -> {
                if (isCurrent(targetJar, sourceFile)) {
                    return targetJar;
                }
                compileSource(sourceFile, classesDir);
                writeJar(sourceFile, classesDir, nextJar);
                return installReplacement(nextJar, targetJar);
            });
        } catch (Exception e) {
            Files.deleteIfExists(nextJar);
            throw e;
        }
        int exitCode;
        try {
            exitCode = launchJar(launchJar, appHome, sourceFile, targetJar, args);
        } finally {
            Files.deleteIfExists(nextJar);
        }
        System.exit(exitCode);
        return true;
    }

    private static Path codeSource(Class<?> mainClass) {
        URI location;
        try {
            location = mainClass.getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        if (!location.getScheme().equals("file")) {
            throw new RuntimeException(String.format("Cannot determine classpath for wrapper Jar from codebase '%s'.", location));
        }
        try {
            return Paths.get(location);
        } catch (NoClassDefFoundError e) {
            return new File(location.getPath()).toPath();
        }
    }

    private static Path requireAbsolutePath(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty()) {
            throw new RuntimeException("Missing required system property: " + name);
        }
        Path path = Paths.get(value);
        if (!path.isAbsolute()) {
            throw new RuntimeException("System property " + name + " must be an absolute path: " + value);
        }
        return path.normalize();
    }

    /**
     * Returns the project directory containing the Wrapper configuration.
     *
     * @return the normalized absolute application home
     */
    public static Path appHome() {
        return requireAbsolutePath(APP_HOME_PROPERTY);
    }

    static Path sourceFile() {
        return requireAbsolutePath(SOURCE_FILE_PROPERTY);
    }

    static Path jarFile() {
        return requireAbsolutePath(JAR_FILE_PROPERTY);
    }

    private static Path classesDir(Path targetJar) {
        return targetJar.getParent().resolve(CLASSES_DIR_NAME);
    }

    private static <T> T withLock(Path cacheDirectory, LockedAction<T> action) throws Exception {
        Files.createDirectories(cacheDirectory);
        try (FileChannel channel = FileChannel.open(cacheDirectory.resolve(LOCK_FILE_NAME), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock ignored = channel.lock()) {
            return action.execute();
        }
    }

    private static boolean isExpectedJar(Path currentJar, Path targetJar, Path sourceFile) throws Exception {
        Path normalizedCurrentJar = currentJar.toAbsolutePath().normalize();
        Path normalizedTargetJar = targetJar.toAbsolutePath().normalize();
        if (normalizedCurrentJar.equals(normalizedTargetJar)) {
            return true;
        }
        Path currentParent = normalizedCurrentJar.getParent();
        Path targetParent = normalizedTargetJar.getParent();
        String currentName = normalizedCurrentJar.getFileName().toString();
        return currentParent != null && currentParent.equals(targetParent) && currentName.startsWith(TEMPORARY_JAR_PREFIX) && currentName.endsWith(".jar") && isCurrent(currentJar, sourceFile);
    }

    private static boolean isCurrent(Path jarFile, Path sourceFile) throws Exception {
        try (JarFile jar = new JarFile(jarFile.toFile())) {
            Manifest manifest = jar.getManifest();
            if (manifest == null) {
                return false;
            }
            Attributes attributes = manifest.getMainAttributes();
            return sha256(sourceFile).equals(attributes.getValue(MANIFEST_SOURCE_SHA256));
        } catch (IOException e) {
            return false;
        }
    }

    private static void compileSource(Path sourceFile, Path classesDir) throws Exception {
        recreateDirectory(classesDir);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new RuntimeException("Could not compile " + sourceFile + ". A JDK with the system Java compiler is required.");
        }
        List<String> options = new ArrayList<>();
        if (SourceVersion.latestSupported().compareTo(SourceVersion.RELEASE_8) > 0) {
            options.add("--release");
            options.add("8");
        } else {
            options.add("-source");
            options.add("8");
            options.add("-target");
            options.add("8");
        }
        options.add("-Xlint:-options");
        options.add("-encoding");
        options.add("UTF-8");
        options.add("-d");
        options.add(classesDir.toString());
        options.add(sourceFile.toString());
        int result = compiler.run(null, null, null, options.toArray(new String[0]));
        if (result != 0) {
            throw new RuntimeException("Could not compile " + sourceFile + " with the system Java compiler.");
        }
    }

    private static void recreateDirectory(Path directory) throws IOException {
        deleteRecursively(directory);
        Files.createDirectories(directory);
    }

    private static void writeJar(Path sourceFile, Path classesDir, Path targetJar) throws Exception {
        Files.createDirectories(targetJar.getParent());
        Path tempJar = Files.createTempFile(targetJar.getParent(), targetJar.getFileName().toString() + ".", ".tmp");
        try {
            Manifest manifest = new Manifest();
            Attributes attributes = manifest.getMainAttributes();
            attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
            attributes.put(Attributes.Name.MAIN_CLASS, MAIN_CLASS_NAME);
            attributes.putValue(MANIFEST_SOURCE_SHA256, sha256(sourceFile));
            try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(tempJar), manifest)) {
                addClasses(output, classesDir);
            }
            moveReplacing(tempJar, targetJar);
        } finally {
            Files.deleteIfExists(tempJar);
        }
    }

    private static void addClasses(JarOutputStream output, Path classesDir) throws IOException {
        Files.walkFileTree(classesDir, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String entryName = classesDir.relativize(file).toString().replace(File.separatorChar, '/');
                output.putNextEntry(new JarEntry(entryName));
                Files.copy(file, output);
                output.closeEntry();
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void copyDirectory(Path sourceDirectory, Path targetDirectory) throws IOException {
        Files.walkFileTree(sourceDirectory, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(targetDirectory.resolve(sourceDirectory.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, targetDirectory.resolve(sourceDirectory.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static Path installReplacement(Path sourceJar, Path targetJar) {
        try {
            moveReplacing(sourceJar, targetJar);
            return targetJar;
        } catch (IOException e) {
            return sourceJar;
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static int launchJar(Path launchJar, Path appHome, Path sourceFile, Path targetJar, String[] args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.addAll(forwardedJvmArguments(ManagementFactory.getRuntimeMXBean().getInputArguments(), appHome, sourceFile, targetJar));
        command.add("-jar");
        command.add(launchJar.toString());
        for (String arg : args) {
            command.add(arg);
        }
        return run(command);
    }

    static List<String> forwardedJvmArguments(List<String> inputArguments, Path appHome, Path sourceFile, Path jarFile) {
        List<String> result = new ArrayList<>();
        for (String inputArgument : inputArguments) {
            if (!isSystemPropertyArgument(inputArgument, BOOTSTRAP_PROPERTY) && !inputArgument.startsWith("-Dorg.gradle.wrapper.neo.")) {
                result.add(inputArgument);
            }
        }
        result.add("-D" + APP_HOME_PROPERTY + "=" + appHome);
        result.add("-D" + SOURCE_FILE_PROPERTY + "=" + sourceFile);
        result.add("-D" + JAR_FILE_PROPERTY + "=" + jarFile);
        return result;
    }

    private static boolean isSystemPropertyArgument(String inputArgument, String property) {
        String option = "-D" + property;
        return inputArgument.equals(option) || inputArgument.startsWith(option + "=");
    }

    private static String javaExecutable() {
        Path executable = Paths.get(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java");
        if (Files.isRegularFile(executable)) {
            return executable.toString();
        }
        return isWindows() ? "java.exe" : "java";
    }

    private static int run(List<String> command) throws Exception {
        Process process = new ProcessBuilder(command).inheritIO().start();
        return process.waitFor();
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new DigestInputStream(Files.newInputStream(file), digest)) {
            byte[] buffer = new byte[8192];
            while (input.read(buffer) >= 0) {
                // Drain the source stream into the digest.
            }
        }
        byte[] hash = digest.digest();
        StringBuilder result = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            result.append(Character.forDigit((b >>> 4) & 0xf, 16));
            result.append(Character.forDigit(b & 0xf, 16));
        }
        return result.toString();
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteIfEmpty(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        try {
            Files.delete(directory);
        } catch (IOException ignored) {
            // Directory is not empty or cannot be removed; leaving it behind is harmless.
        }
    }

    private static boolean isWindows() {
        return File.separatorChar == '\\';
    }

    @FunctionalInterface
    private interface LockedAction<T> {

        T execute() throws Exception;
    }
}
