/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.vm;

import java.util.*;

import com.sun.max.annotate.*;
import com.sun.max.collect.*;
import com.sun.max.lang.*;
import com.sun.max.profile.*;
import com.sun.max.program.*;
import com.sun.max.unsafe.*;
import com.sun.max.util.*;
import com.sun.max.vm.VMOption.*;
import com.sun.max.vm.object.host.*;
import com.sun.max.vm.prototype.*;

/**
 * Basic VM argument handling.
 * We have to do some argument processing here, e.g. -Xms, for anything that affects
 * the bootstrap process. Since we have to work at the CString level, we only handle the
 * essential arguments and postpone everything else until the main thread body.
 * We null out the arguments that we process here so that they can easily be ignored
 * in subsequent passes.
 *
 * @author Mick Jordan
 * @author Bernd Mathiske
 * @author Ben L. Titzer
 */
public final class VMOptions {

    private static final int HELP_INDENT = 32;

    /**
     * A comparator for sorted a set of {@link VMOption}s in reverse lexicographic order of their
     * {@linkplain VMOption#_prefix prefixes}. This means that suboptions precede their parent option
     * where a suboption is an option whose prefix starts with but is not equal to the parent's prefix.
     */
    @PROTOTYPE_ONLY
    private static final Comparator<VMOption> VMOPTION_SORTER = new Comparator<VMOption>() {
        @Override
        public int compare(VMOption o1, VMOption o2) {
            return o2._prefix.compareTo(o1._prefix);
        }
    };

    /**
     * Used to collect and sort VM options as they are declared.
     */
    @PROTOTYPE_ONLY
    private static final SortedSet<VMOption> _pristinePhaseOptionsSet = new TreeSet<VMOption>(VMOPTION_SORTER);

    /**
     * Used to collect and sort VM options as they are declared.
     */
    @PROTOTYPE_ONLY
    private static final SortedSet<VMOption> _startingPhaseOptionsSet = new TreeSet<VMOption>(VMOPTION_SORTER);

    private static VMOption[] _pristinePhaseOptions;
    private static VMOption[] _startingPhaseOptions;

    private static final VMStringOption _jarOption = new VMStringOption("-jar", true, null, "Executes main class from jar file.", MaxineVM.Phase.PRISTINE) {
        @Override
        public boolean isLastOption() {
            return true;
        }
    };

    /**
     * An option to {@linkplain GlobalMetrics#report(java.io.PrintStream) report} on all global metrics gathered during execution.
     * TODO: If this option is not enabled, then the global metrics should not be gathered.
     */
    private static final VMOption _globalMetrics = new VMOption("-XX:PrintMetrics", "Report random metrics gathered during execution.", MaxineVM.Phase.STARTING) {
        @Override
        public boolean parseValue(Pointer optionValue) {
            GlobalMetrics.reset();
            return true;
        }

        @Override
        protected void beforeExit() {
            if (isPresent()) {
                GlobalMetrics.report(Log.out);
            }
        }
    };

    private static final VMOption _verboseOption = new VMOption("-verbose ", "Enables all verbose options.", MaxineVM.Phase.PRISTINE);
    private static final VMOption _timeOption = new VMOption("-XX:Time ", "Enables all timing options.", MaxineVM.Phase.PRISTINE);

    private static final VMIntOption _traceLevelOption = new VMIntOption("-XX:TraceLevel=", 0, "Enables tracing output at the specified level.", MaxineVM.Phase.PRISTINE) {
        @Override
        public boolean parseValue(Pointer optionValue) {
            Trace.on(getValue());
            return true;
        }
    };

    private static final VMOption _printConfiguration = new VMOption("-XX:PrintConfiguration", "Shows VM configuration details and exits.", MaxineVM.Phase.STARTING);
    private static final VMOption _showConfiguration = new VMOption("-XX:ShowConfiguration", "Shows VM configuration details and continues.", MaxineVM.Phase.STARTING);

    private static Pointer _argv;
    private static int _argc;
    private static int _argumentStart;

    private static boolean _earlyVMExitRequested;

    private static String[] _mainClassArguments;
    private static String _mainClassName;

    private VMOptions() {
    }

    public static void printHelpForOption(String prefix, String value, String help) {
        Log.print("    ");
        Log.print(prefix);
        Log.print(value);
        Log.print(" ");
        int column = 5 + prefix.length() + value.length();
        for (; column < HELP_INDENT; column++) {
            Log.print(' ');
        }
        if (help != null) {
            // reformat the help text by wrapping the lines after column 72.
            // Strings.formatParagraphs() can't be used because allocation may not work here
            for (int j = 0; j < help.length(); j++) {
                final char ch = help.charAt(j);
                if (column > 72 && (ch == ' ' || ch == '\t')) {
                    Log.println();
                    for (int k = 0; k < HELP_INDENT; k++) {
                        Log.print(' ');
                    }
                    column = HELP_INDENT;
                } else {
                    Log.print(ch);
                    column++;
                }
            }
        }
        Log.println();
    }

    @PROTOTYPE_ONLY
    private static VMOption[] addOption(SortedSet<VMOption> options, VMOption option, Iterable<VMOption> allOptions) {
        if (option.category() == VMOption.Category.IMPLEMENTATION_SPECIFIC) {
            final String name = option._prefix.substring("-XX:".length());
            ProgramError.check(Character.isUpperCase(name.charAt(0)), "Option with \"-XX:\" prefix must start with an upper-case letter: " + option);
        }
        for (VMOption existingOption : allOptions) {
            ProgramError.check(!existingOption._prefix.equals(option._prefix), "VM option prefix is not unique: " + option._prefix);
            if (option._prefix.startsWith(existingOption._prefix)) {
                existingOption.addSuboption(option);
            } else if (existingOption._prefix.startsWith(option._prefix)) {
                option.addSuboption(existingOption);
            }
        }
        options.add(option);
        return options.toArray(new VMOption[options.size()]);
    }

    @PROTOTYPE_ONLY
    public static VMOption addOption(VMOption option, MaxineVM.Phase phase) {
        final Iterable<VMOption> allOptions = Iterables.join(_pristinePhaseOptionsSet, _startingPhaseOptionsSet);
        if (phase == MaxineVM.Phase.PRISTINE) {
            _pristinePhaseOptions = addOption(_pristinePhaseOptionsSet, option, allOptions);
        } else if (phase == MaxineVM.Phase.STARTING) {
            _startingPhaseOptions = addOption(_startingPhaseOptionsSet, option, allOptions);
        } else {
            ProgramError.unexpected("VM options for the " + phase + " phase not (yet) supported");
        }
        return option;
    }

    private static void printOptions(VMOption[] options, String label, Category category) {
        for (VMOption option : options) {
            if (option.category() == category) {
                option.printHelp();
            }
        }
    }

    private static void printOptions(String label, Category category) {
        if (label != null) {
            Log.println();
            Log.println(label);
        }
        printOptions(_pristinePhaseOptions, label, category);
        printOptions(_startingPhaseOptions, label, category);
    }

    public static void printUsage() {
        Log.println("Usage: maxvm [-options] [class | -jar jarfile]  [args...]");
        Log.println("where options include:");

        printOptions(null, Category.STANDARD);
        printOptions("Non-standard options:", Category.NON_STANDARD);
        printOptions("Maxine options:", Category.IMPLEMENTATION_SPECIFIC);
    }

    /**
     * Determines if the VM should terminate. This will be true if there was an error while parsing the VM options.
     * It may also be true if the semantics of some VM option is to print some diagnostic info and then
     * exit the VM.
     */
    public static boolean earlyVMExitRequested() {
        return _earlyVMExitRequested;
    }

    protected static void error(String errorMessage) {
        _earlyVMExitRequested = true;
        Log.print("VM program argument parsing error: ");
        Log.println(errorMessage);
        printUsage();
        MaxineVM.setExitCode(1);
    }

    protected static void error(VMOption option) {
        _earlyVMExitRequested = true;
        Log.print("Error while parsing ");
        Log.print(option.toString());
        Log.print(": ");
        option.printErrorMessage();
        Log.println();
        printUsage();
        MaxineVM.setExitCode(1);
    }

    public static String jarFile() {
        return _jarOption.getValue();
    }

    /**
     * Gets the index of the next non-empty {@linkplain #_argv command line argument} starting at a given index.
     *
     * @param start the index of the first argument to consider
     * @return the index of the first word in {@link _argv} that points to a non-empty C string or -1 if there is no
     *         such command line argument at whose index is greater than or equal to {@code index} and less than
     *         {@link #_argc}
     */
    private static int findArgument(int start) {
        if (start == -1) {
            return -1;
        }
        for (int i = start; i < _argc; i++) {
            final Pointer argument = _argv.getWord(0, i).asPointer();
            if (!argument.isZero() && !CString.length(argument).isZero()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Parses a given option whose {@linkplain VMOption#_prefix prefix} is at a given index in the command line
     * arguments.
     *
     * @param index the index of {@code option}'s prefix in the command line arguments
     * @param argument the command line argument at {@code index}
     * @param option an option whose prefix matches the beginning of {@code argument}
     * @return the index of the next command line argument not consumed by {@code option} or -1 if {@code option}
     *         {@linkplain VMOption#isLastOption() terminates} the list of options
     */
    private static int parseOption(int index, final Pointer argument, final VMOption option) {
        final int nextIndex;
        if (option.consumesNext()) {
            // this option expects a space and then its value (e.g. -classpath)
            if (findArgument(index + 1) != index + 1) {
                error(option.toString());
            }
            // parse the next argument as this option's value
            if (!option.parseValue(_argv.getWord(index + 1).asPointer())) {
                error(option.toString());
            }
            _argv.setWord(index, Word.zero());
            _argv.setWord(index + 1, Word.zero());
            nextIndex = index + 2;
        } else {
            // otherwise ask the option to parse itself
            if (!option.parse(argument)) {
                error(option.toString());
            }
            _argv.setWord(index, Word.zero());
            nextIndex = index + 1;
        }
        if (option.isLastOption()) {
            _argumentStart = nextIndex;
            return -1;
        }
        return nextIndex;
    }

    private static VMOption findVMOption(Pointer arg, VMOption[] options) {
        for (VMOption option : options) {
            if (option.matches(arg)) {
                return option;
            }
        }
        return null;
    }

    public static boolean parsePristine(int argc, Pointer argv) {
        _argv = argv;
        _argc = argc;
        _argumentStart = argc;

        int index = findArgument(1); // skip the first argument (the name of the executable)
        while (index >= 0) {
            final Pointer argument = _argv.getWord(index).asPointer();
            final VMOption option = findVMOption(argument, _pristinePhaseOptions);
            if (option != null) {
                // some option prefix matched. attempt to parse it.
                index = parseOption(index, argument, option);
                if (index < 0) {
                    break;
                }
            } else if (argument.getByte() == '-') {
                index++;
                // an option to be handled later
            } else {
                // the first non-option argument must be the main class, unless -jar
                _argumentStart = index;
                break;
            }
            index = findArgument(index);
        }
        return checkOptionsForErrors(_pristinePhaseOptions);
    }

    protected static String getArgumentString(int index) throws Utf8Exception {
        final Pointer cArgument = _argv.getWord(index).asPointer();
        if (cArgument.isZero()) {
            return null;
        }
        final String result = CString.utf8ToJava(cArgument);
        if (result.isEmpty()) {
            return null;
        }
        return result;
    }

    private static boolean checkOptionsForErrors(VMOption[] options) {
        if (_earlyVMExitRequested) {
            return false;
        }
        for (VMOption option : options) {
            if (!option.check()) {
                error(option);
                return false;
            }
        }
        return true;
    }

    /**
     * This is a reference to the initial value of {@link System#props} when the VM starts up.
     * The "magic" in {@link HostObjectAccess#hostToTarget(Object)} will ensure that this map
     * only has the properties from the host specified by {@link JDKInterceptor#REMEMBERED_PROPERTY_NAMES}.
     * The system properties parsed on the command line are stored in this map.
     * This is required so that they are available before the System class is initialized.
     */
    public static final Properties _initialSystemProperties = System.getProperties();

    public static boolean parseStarting() {
        try {
            int index = 1;
            while (index < _argumentStart) {
                final String argument = getArgumentString(index);
                if (argument == null) {
                    index++;
                } else {
                    if (argument.startsWith("-D")) {
                        parseSystemProperty(_initialSystemProperties, argument);
                        _argv.setWord(index, Word.zero());
                    } else {
                        final Pointer nextArg = _argv.getWord(index).asPointer();
                        final VMOption option = findVMOption(nextArg, _startingPhaseOptions);
                        if (option == null) {
                            error("unknown VM argument \"" + CString.utf8ToJava(nextArg) + "\"");
                        } else if (!option.parse(nextArg)) {
                            error("parsing of " + argument + " failed");
                        }
                        _argv.setWord(index, Word.zero());
                        index++;
                    }
                }
            }
        } catch (Utf8Exception utf8Exception) {
            error("UTF8 problem");
        }
        final boolean noErrorFound = checkOptionsForErrors(_startingPhaseOptions);
        if (noErrorFound) {
            if (_printConfiguration.isPresent() || _showConfiguration.isPresent()) {
                final VMConfiguration vm = VMConfiguration.target();
                Log.println("VM Configuration:");
                Log.println("  Build level: " + vm.buildLevel());
                Log.println("  Platform: " + vm.platform());
                for (VMScheme vmScheme : vm.vmSchemes()) {
                    final String specification = vmScheme.specification().getSimpleName();
                    Log.println("  " + specification.replace("Scheme", " scheme") + ": " + vmScheme.getClass().getName());
                }
                if (_printConfiguration.isPresent()) {
                    _earlyVMExitRequested = true;
                }
            }
        }
        return noErrorFound;
    }

    /**
     * Adds any system properties specified on the command line to a given properties object.
     * The command line properties override any properties in {@code properties} that have
     * the same name.
     *
     * @param properties the object to which the command line properties are added
     */
    public static void addParsedSystemProperties(Properties properties) {
        for (Map.Entry<Object, Object> entry : _initialSystemProperties.entrySet()) {
            properties.setProperty((String) entry.getKey(), (String) entry.getValue());
        }
    }

    /**
     * Parses a system property from command line argument that starts with "-D" and adds it
     * to a given properties object.
     *
     * @param properties the object to which the command line property extracted from {@code argument} is added
     * @param argument a command line argument that starts with "-D"
     */
    private static void parseSystemProperty(Properties properties, final String argument) {
        final int index = argument.indexOf('=');
        String name;
        String value = "";
        if (index < 0) {
            name = argument.substring(2); // chop off -D
        } else {
            name = argument.substring(2, index); // get the name of the option
            value = argument.substring(index + 1);
        }
        properties.setProperty(name, value);
    }

    public static String mainClassName() {
        return _mainClassName;
    }

    public static String[] mainClassArguments() {
        return _mainClassArguments;
    }

    private static void parseMainClassArguments(int argumentStart) throws Utf8Exception {
        int argumentCount = 0;

        for (int i = argumentStart; i < _argc; i++) {
            if (findArgument(i) >= 0) {
                argumentCount++;
            }
        }
        _mainClassArguments = new String[argumentCount];
        int mainClassArgumentsIndex = 0;
        for (int i = argumentStart; i < _argc; i++) {
            if (findArgument(i) >= 0) {
                _mainClassArguments[mainClassArgumentsIndex++] = getArgumentString(i);
            }
        }
    }

    /**
     * Tries to parse the next available command line argument which specifies the name of the class containing the main
     * method to be run.
     *
     * @param errorIfNotPresent specifies whether the omission of a main class argument is to be considered an
     *            {@linkplain #error(String) error}
     * @return true if the main class name argument was successfully parsed. If so, then it's now available by calling
     *         {@link #mainClassName()}.
     */
    public static boolean parseMain(boolean errorIfNotPresent) {
        try {
            if (_jarOption.isPresent()) {
                // the first argument is the first argument to the program
                parseMainClassArguments(_argumentStart);
                return true;
            }
            if (_argumentStart < _argc) {
                // the first argument is the name of the main class
                _mainClassName = getArgumentString(_argumentStart);
                parseMainClassArguments(_argumentStart + 1);
                return _mainClassName != null;
            }
            if (errorIfNotPresent) {
                error("no main class specified");
            }
        } catch (Utf8Exception utf8Exception) {
            error("UTF8 problem");
        }
        return false;
    }

    /**
     * Parse a size specification nX, where X := {K, M, G, T, P, k, m, g, t, p}.
     *
     * For backwards compatibility with HotSpot,
     * lower case letters shall have the same respective meaning as the upper case ones,
     * even though their non-colloquialized definitions would suggest otherwise.
     *
     * @param p a pointer to the C string
     * @param length the maximum length of the C string
     * @param startIndex the starting index into the C string pointed to by the first argument
     * @return the scaled value or -1 if error
     */
    protected static long parseScaledValue(Pointer p, Size length, int startIndex) {
        long result = 0L;
        boolean done = false;
        int index = startIndex;
        while (index < length.toInt()) {
            if (done) {
                // having any additional characters is an error
                return -1L;
            }
            final int character = CString.getByte(p, length, Offset.fromInt(index));
            index++;
            if ('0' <= character && character <= '9') {
                result *= 10;
                result += character - '0';
            } else {
                done = true;
                switch (character) {
                    case 'K':
                    case 'k': {
                        result *= Longs.K;
                        break;
                    }
                    case 'M':
                    case 'm': {
                        result *= Longs.M;
                        break;
                    }
                    case 'G':
                    case 'g': {
                        result *= Longs.G;
                        break;
                    }
                    case 'T':
                    case 't': {
                        result *= Longs.T;
                        break;
                    }
                    case 'P':
                    case 'p': {
                        result *= Longs.P;
                        break;
                    }
                    default: {
                        // illegal character
                        return -1L;
                    }
                }
            }
        }
        return result;
    }

    protected static int parseUnsignedInt(String string) {
        int result = 0;
        for (int i = 0; i < string.length(); i++) {
            final char ch = string.charAt(i);
            if (ch >= '0' && ch <= '9') {
                result *= 10;
                result += string.charAt(i) - '0';
            } else {
                return -1;
            }
        }
        return result;
    }

    protected static long parseUnsignedLong(String string) {
        long result = 0L;
        for (int i = 0; i < string.length(); i++) {
            final char ch = string.charAt(i);
            if (ch >= '0' && ch <= '9') {
                result *= 10L;
                result += string.charAt(i) - '0';
            } else {
                return -1L;
            }
        }
        return result;
    }

    /**
     * Calls the {@link VMOption#beforeExit()} method of each registered VM option.
     */
    public static void beforeExit() {
        for (VMOption option : _pristinePhaseOptions) {
            option.beforeExit();
        }
        for (VMOption option : _startingPhaseOptions) {
            option.beforeExit();
        }
        if (MaxineVM.isPrototyping()) {
            for (String argument : VMOption.unmatchedVMArguments()) {
                if (argument != null) {
                    ProgramWarning.message("VM argument not matched by any VM option: " + argument);
                }
            }
        }
    }
}
