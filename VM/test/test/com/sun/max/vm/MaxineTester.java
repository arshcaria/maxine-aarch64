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
package test.com.sun.max.vm;

import java.io.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.zip.*;

import junit.framework.*;

import org.junit.internal.requests.*;
import org.junit.runner.*;
import org.junit.runner.notification.*;
import org.junit.runners.AllTests;

import sun.management.*;
import test.com.sun.max.vm.MaxineTesterConfiguration.*;

import com.sun.max.collect.*;
import com.sun.max.io.*;
import com.sun.max.lang.*;
import com.sun.max.platform.*;
import com.sun.max.program.*;
import com.sun.max.program.option.*;
import com.sun.max.vm.prototype.*;

/**
 * This class combines all the testing modes of the Maxine virtual machine into a central
 * place. It is capable of building images in various configurations and running tests
 * and user programs with the generated images.
 *
 * @author Ben L. Titzer
 * @author Doug Simon
 */
public class MaxineTester {

    private static final int PROCESS_TIMEOUT = -333;

    private static final OptionSet _options = new OptionSet();
    private static final Option<String> _outputDir = _options.newStringOption("output-dir", "maxine-tester",
                    "The output directory for the results of the maxine tester.");
    private static final Option<Integer> _imageBuildTimeOut = _options.newIntegerOption("image-build-timeout", 600,
                    "The number of seconds to wait for an image build to complete before " +
                    "timing out and killing it.");
    private static final Option<String> _javaExecutable = _options.newStringOption("java-executable", "java",
                    "The name of or full path to the Java VM executable to use. This must be a JDK 6 or greater VM.");
    private static final Option<String> _javaVMArgs = _options.newStringOption("java-vm-args", "-d64 -Xmx1g",
                    "The VM options to be used when running the Java VM.");
    private static final Option<Integer> _javaTesterTimeOut = _options.newIntegerOption("java-tester-timeout", 50,
                    "The number of seconds to wait for the in-target Java tester tests to complete before " +
                    "timing out and killing it.");
    private static final Option<Integer> _javaTesterConcurrency = _options.newIntegerOption("java-tester-concurrency", 1,
                    "The number of Java tester tests to run in parallel.");
    private static final Option<Integer> _javaRunTimeOut = _options.newIntegerOption("java-run-timeout", 50,
                    "The number of seconds to wait for the target VM to complete before " +
                    "timing out and killing it when running user programs.");
    private static final Option<Boolean> _skipOutputTests = _options.newBooleanOption("skip-output-tests", false,
                    "Skip running of the output tests.");
    private static final Option<Boolean> _skipJavaTesterTests = _options.newBooleanOption("skip-java-tester-tests", false,
                    "Skip running of the Java Tester tests.");
    private static final Option<Integer> _traceOption = _options.newIntegerOption("trace", 0,
                    "The tracing level for building the images and running the tests.");
    private static final Option<Boolean> _skipImageGen = _options.newBooleanOption("skip-image-gen", false,
                    "Skip the generation of the image, which is useful for testing the Maxine tester itself.");
    private static final Option<List<String>> _javaTesterConfigs = _options.newStringListOption("java-tester-configs",
                    MaxineTesterConfiguration.defaultJavaTesterConfigs(),
                    "A list of configurations for which to run the Java tester tests.");
    private static final Option<List<String>> _maxvmConfigList = _options.newStringListOption("maxvm-configs",
                    MaxineTesterConfiguration.defaultMaxvmOutputConfigs(),
                    "A list of configurations for which to run the Maxine output tests.");
    private static final Option<String> _javaConfigAliasOption = _options.newStringOption("java-config-alias", null,
                    "The Java tester config to use for running Java programs. Omit this option to use a separate config for Java programs.");
    private static final Option<Integer> _autoTestTimeOut = _options.newIntegerOption("auto-test-timeout", 300,
                    "The number of seconds to wait for a JUnit auto-test to complete before " +
                    "timing out and killing it.");
    private static final Option<Boolean> _skipAutoTests = _options.newBooleanOption("skip-auto-tests", false,
                    "Skip running of the JUnit auto-test classes found on the class path.");
    private static final Option<Boolean> _slowAutoTests = _options.newBooleanOption("slow-auto-tests", false,
                    "Include auto-tests known to be slow.");
    private static final Option<String> _autoTestFilter = _options.newStringOption("auto-test-filter", null,
                    "A pattern for selecting which auto-tests are run. If absent, all auto-tests on the class path are run. " +
                    "Otherwise only those whose name contains this value as a substring are run.");
    private static final Option<Boolean> _failFast = _options.newBooleanOption("fail-fast", true,
                    "Stop execution as soon as a single test fails.");
    private static final Option<File> _specjvm98Zip = _options.newFileOption("specjvm98", (File) null,
                    "Location of zipped up SpecJVM98 directory.");
    private static final Option<File> _dacapoJar = _options.newFileOption("dacapo", (File) null,
                    "Location of DaCapo JAR file.");

    private static String _javaConfigAlias = null;

    public static void main(String[] args) {
        try {
            _options.parseArguments(args);
            _javaConfigAlias = _javaConfigAliasOption.getValue();
            if (_javaConfigAlias != null) {
                ProgramError.check(MaxineTesterConfiguration._imageConfigs.containsKey(_javaConfigAlias), "Unknown Java tester config '" + _javaConfigAlias + "'");
            }
            final File outputDir = new File(_outputDir.getValue()).getAbsoluteFile();
            makeDirectory(outputDir);
            Trace.on(_traceOption.getValue());
            runAutoTests();
            buildJavaRunSchemeAndRunOutputTests();
            runJavaTesterTests();
            System.exit(reportTestResults(out()));
        } catch (Throwable throwable) {
            throwable.printStackTrace(err());
            System.exit(-1);
        }
    }

    private static final ThreadLocal<PrintStream> _out = new ThreadLocal<PrintStream>() {
        @Override
        protected PrintStream initialValue() {
            return System.out;
        }
    };
    private static final ThreadLocal<PrintStream> _err = new ThreadLocal<PrintStream>() {
        @Override
        protected PrintStream initialValue() {
            return System.err;
        }
    };

    private static PrintStream out() {
        return _out.get();
    }
    private static PrintStream err() {
        return _err.get();
    }

    /**
     * Runs a given runnable with all {@linkplain #out() standard} and {@linkplain #err() error} output redirect to
     * private buffers. The private buffers are then flushed to the global streams once the runnable completes.
     */
    private static void runWithSerializedOutput(Runnable runnable) {
        final PrintStream oldOut = out();
        final PrintStream oldErr = err();
        final ByteArrayPrintStream out = new ByteArrayPrintStream();
        final ByteArrayPrintStream err = new ByteArrayPrintStream();
        try {
            _out.set(out);
            _err.set(err);
            runnable.run();
        } finally {
            synchronized (oldOut) {
                out.writeTo(oldOut);
            }
            synchronized (oldErr) {
                err.writeTo(oldErr);
            }
            _out.set(oldOut);
            _err.set(oldErr);
        }
    }

    /**
     * Used for per-thread buffering of output.
     */
    static class ByteArrayPrintStream extends PrintStream {
        public ByteArrayPrintStream() {
            super(new ByteArrayOutputStream());
        }
        public void writeTo(PrintStream other) {
            try {
                ((ByteArrayOutputStream) out).writeTo(other);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void makeDirectory(File directory) {
        if (!directory.exists() && !directory.mkdirs()) {
            ProgramError.unexpected("Could not make directory " + directory);
        }
        ProgramError.check(directory.isDirectory(), "Path is not a directory: " + directory);
        copyInputFiles(directory);
    }

    private static void copyInputFiles(File directory) {
        final Set<java.lang.Package> outputTestPackages = new HashSet<java.lang.Package>();
        for (Class mainClass : MaxineTesterConfiguration._outputTestClasses) {
            outputTestPackages.add(mainClass.getPackage());
        }
        final File parent = new File(new File("VM"), "test");
        ProgramError.check(parent != null && parent.exists(), "Could not find VM/test: trying running in the root of your Maxine repository");
        for (java.lang.Package p : outputTestPackages) {
            File dir = parent;
            for (String n : p.getName().split("\\.")) {
                dir = new File(dir, n);
            }
            final File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().endsWith(".input")) {
                        try {
                            Files.copy(f, new File(directory, f.getName()));
                        } catch (FileNotFoundException e) {
                            // do nothing.
                        } catch (IOException e) {
                            // do nothing.
                        }
                    }
                }
            }
        }
    }

    private static void unzip(File zip, File destDir) {
        if (!destDir.exists() && !destDir.mkdirs()) {
            ProgramError.unexpected("Could not make directory " + destDir);
        }

        Enumeration entries;
        ZipFile zipFile;

        try {
            Trace.line(2, "Extracting contents of " + zip.getAbsolutePath() + " to " + destDir);
            zipFile = new ZipFile(zip);
            entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                final ZipEntry entry = (ZipEntry) entries.nextElement();
                final File parentDir;
                if (entry.isDirectory()) {
                    parentDir = new File(destDir, entry.getName());
                } else {
                    final String relParentDir = new File(entry.getName()).getParent();
                    if (relParentDir != null) {
                        parentDir = new File(destDir, relParentDir);
                    } else {
                        parentDir = destDir;
                    }
                }
                if (!parentDir.exists() && !parentDir.mkdirs()) {
                    ProgramError.unexpected("Could not make directory " + parentDir);
                }

                if (!entry.isDirectory()) {
                    final File destFile = new File(destDir, entry.getName());
                    Trace.line(2, "  inflating: " + entry.getName() + " ...");
                    Streams.copy(zipFile.getInputStream(entry), new FileOutputStream(destFile));
                }
            }

            zipFile.close();
        } catch (IOException ioe) {
            ProgramError.unexpected("Error extracting " + zip.getAbsolutePath() + " to " + destDir, ioe);
        }
    }


    /**
     * A map from test names to a string describing a test failure or null if a test passed.
     */
    private static final Map<String, String> _unexpectedFailures = Collections.synchronizedMap(new TreeMap<String, String>());
    private static final Map<String, String> _unexpectedPasses = Collections.synchronizedMap(new TreeMap<String, String>());

    /**
     * Adds a test result to the global set of test results.
     *
     * @param testName the unique name of the test
     * @param failure a failure message or null if the test passed
     * @return {@code true} if the result (pass or fail) of the test matches the expected result, {@code false} otherwise
     */
    private static boolean addTestResult(String testName, String failure, ExpectedResult expectedResult) {
        final boolean passed = failure == null;
        if (!expectedResult.matchesActualResult(passed)) {
            if (expectedResult == ExpectedResult.FAIL) {
                _unexpectedPasses.put(testName, failure);
            } else {
                assert expectedResult == ExpectedResult.PASS;
                _unexpectedFailures.put(testName, failure);
            }
            return false;
        }
        return true;
    }

    private static boolean addTestResult(String testName, String failure) {
        return addTestResult(testName, failure, MaxineTesterConfiguration.expectedResult(testName, null));
    }

    /**
     * Summarizes the collected test results.
     *
     * @param out where the summary should be printed. This value can be null if only the return value is of interest.
     * @return an integer that is the total of all the unexpected passes, the unexpected failures, the number of failed
     *         attempts to generate an image and the number of auto-tests subprocesses that failed with an exception
     */
    private static int reportTestResults(PrintStream out) {
        if (out != null) {
            out.println();
            out.println("== Summary ==");
        }
        int failedImages = 0;
        for (Map.Entry<String, File> entry : _generatedImages.entrySet()) {
            if (entry.getValue() == null) {
                if (out != null) {
                    out.println("Failed building image for configuration '" + entry.getKey() + "'");
                }
                failedImages++;
            }
        }

        int failedAutoTests = 0;
        for (String autoTest : _autoTestsWithExceptions) {
            if (out != null) {
                out.println("Non-zero exit status for '" + autoTest + "'");
            }
            failedAutoTests++;
        }

        if (out != null) {
            if (!_unexpectedFailures.isEmpty()) {
                out.println("Unexpected failures:");
                for (Map.Entry<String, String> entry : _unexpectedFailures.entrySet()) {
                    out.println("  " + entry.getKey() + " " + entry.getValue());
                }
            }
            if (!_unexpectedPasses.isEmpty()) {
                out.println("Unexpected passes:");
                for (String unexpectedPass : _unexpectedPasses.keySet()) {
                    out.println("  " + unexpectedPass);
                }
            }
        }

        final int exitCode = _unexpectedFailures.size() + _unexpectedPasses.size() + failedImages + failedAutoTests;
        if (out != null) {
            out.println("Exit code: " + exitCode);
        }
        return exitCode;
    }

    /**
     * A helper class for running one or more JUnit tests. This helper delegates to {@link JUnitCore} to do most of the work.
     */
    public static class JUnitTestRunner {

        static final String INCLUDE_SLOW_TESTS_PROPERTY = "includeSlowTests";

        private static Set<String> loadFailedTests(File file) {
            if (file.exists()) {
                System.out.println("Only running the tests listed in " + file.getAbsolutePath());
                try {
                    return new HashSet<String>(Iterables.toCollection(Files.readLines(file)));
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
            return null;
        }

        /**
         * Runs the JUnit tests in a given class.
         *
         * @param args an array with the following three elements:
         *            <ol>
         *            <li>The name of a class containing the JUnit test(s) to be run.</li>
         *            <li>The path of a file to which the {@linkplain Description name} of the tests that pass will be
         *            written.</li>
         *            <li>The path of a file to which the name of the tests that fail will be written. If this file
         *            already exists, then only the tests listed in the file will be run.</li>
         *            </ol>
         */
        public static void main(String[] args) throws Throwable {
            System.setErr(System.out);

            final String testClassName = args[0];
            final File passedFile = new File(args[1]);
            final File failedFile = new File(args[2]);

            final Class<?> testClass = Class.forName(testClassName);
            final Test test = AllTests.testFromSuiteMethod(testClass);

            final boolean includeSlowTests = System.getProperty(INCLUDE_SLOW_TESTS_PROPERTY) != null;

            final Set<String> failedTestNames = loadFailedTests(failedFile);
            final Runner runner = new AllTests(testClass) {
                @Override
                public void run(RunNotifier notifier) {
                    final TestResult result = new TestResult() {
                        @Override
                        protected void run(TestCase testCase) {
                            final Description description = Description.createTestDescription(testCase.getClass(), testCase.getName());
                            if (!includeSlowTests && MaxineTesterConfiguration.isSlowAutoTestCase(testCase)) {
                                System.out.println("Omitted slow test: " + description);
                                return;
                            }
                            if (failedTestNames == null || failedTestNames.contains(description.toString())) {
                                super.run(testCase);
                            }
                        }
                    };
                    result.addListener(createAdaptingListener(notifier));
                    test.run(result);
                }
            };

            final PrintStream passed = new PrintStream(new FileOutputStream(passedFile));
            final PrintStream failed = new PrintStream(new FileOutputStream(failedFile));
            final JUnitCore junit = new JUnitCore();
            junit.addListener(new RunListener() {
                boolean _failed;

                @Override
                public void testStarted(Description description) throws Exception {
                    System.out.println("running " + description);
                }

                @Override
                public void testFailure(Failure failure) throws Exception {
                    failure.getException().printStackTrace(System.out);
                    _failed = true;
                }

                @Override
                public void testFinished(Description description) throws Exception {
                    if (_failed) {
                        failed.println(description);
                    } else {
                        passed.println(description);
                    }
                    _failed = false;
                }
            });

            final Request request = new ClassRequest(testClass) {
                @Override
                public Runner getRunner() {
                    return runner == null ? super.getRunner() : runner;
                }
            };

            junit.run(request);
            passed.close();
            failed.close();
        }
    }

    /**
     * A list of the {@linkplain #runAutoTests auto-tests} that caused the Java process to exit with an exception.
     */
    private static AppendableSequence<String> _autoTestsWithExceptions = new ArrayListSequence<String>();

    /**
     * Parses a file of test names (one per line) run as part of an auto-test. The global records of test results are
     * {@linkplain #addTestResult(String, String, boolean) updated} appropriately.
     *
     * @param resultsFile the file to parse
     * @param passed specifies if the file list tests that passed or failed
     * @param unexpectedResults if non-null, then all unexpected test results are added to this set
     */
    static void parseAutoTestResults(File resultsFile, boolean passed, Set<String> unexpectedResults) {
        try {
            final Sequence<String> lines = Files.readLines(resultsFile);
            for (String line : lines) {
                final String testName = line;
                final boolean expectedResult = addTestResult(testName, passed ? null : "failed", MaxineTesterConfiguration.expectedResult(testName, null));
                if (unexpectedResults != null && !expectedResult) {
                    unexpectedResults.add("unexpectedly "  + (passed ? "passed " : "failed ") + testName);
                }
            }
        } catch (IOException ioException) {
            out().println("could not read '" + resultsFile.getAbsolutePath() + "': " + ioException);
        }
    }

    /**
     * Determines if {@linkplain #_failFast fail fast} has been requested and at least one unexpected failure has
     * occurred.
     */
    static boolean stopTesting() {
        return _failFast.getValue() && reportTestResults(null) != 0;
    }

    /**
     * Runs all the auto-tests available on the class path. An auto-test is a class whose unqualified name is "AutoTest"
     * that resides in a sub-package of the {@code test.com.sun.max} package. These classes are assumed to contain one
     * or more JUnit tests that can be run via {@link JUnitCore}.
     */
    private static void runAutoTests() {
        if (_skipAutoTests.getValue() || stopTesting()) {
            return;
        }
        final File outputDir = new File(_outputDir.getValue(), "auto-tests");

        final String filter = _autoTestFilter.getValue();
        final Set<String> autoTests = new TreeSet<String>();
        new ClassSearch() {
            @Override
            protected boolean visitClass(String className) {
                if (className.endsWith(".AutoTest")) {
                    if (filter == null || className.contains(filter)) {
                        autoTests.add(className);
                    }
                }
                return true;
            }
        }.run(Classpath.fromSystem());

        final int availableProcessors = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
        final ExecutorService autoTesterService = Executors.newFixedThreadPool(availableProcessors);
        final CompletionService<Void> autoTesterCompletionService = new ExecutorCompletionService<Void>(autoTesterService);
        for (final String autoTest : autoTests) {
            autoTesterCompletionService.submit(new Runnable() {
                public void run() {
                    if (!stopTesting()) {
                        runWithSerializedOutput(new Runnable() {
                            public void run() {
                                runAutoTest(outputDir, autoTest);
                            }
                        });
                    }
                }
            }, null);
        }
        autoTesterService.shutdown();
        try {
            autoTesterService.awaitTermination(_javaTesterTimeOut.getValue() * 2 * autoTests.size(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Runs a single {@linkplain #runAutoTests() auto-test}.
     *
     * @param outputDir where the result logs of the auto-test are to be placed
     * @param autoTest the auto-test to run
     */
    private static void runAutoTest(final File outputDir, String autoTest) {
        final File outputFile = getOutputFile(outputDir, autoTest, null);
        final File passedFile = getOutputFile(outputDir, autoTest, null, ".passed");
        final File failedFile = getOutputFile(outputDir, autoTest, null, ".failed");

        String[] systemProperties = null;
        if (_slowAutoTests.getValue()) {
            systemProperties = new String[] {JUnitTestRunner.INCLUDE_SLOW_TESTS_PROPERTY};
        }

        final String[] javaArgs = buildJavaArgs(JUnitTestRunner.class, javaVMArgs(), new String[] {autoTest, passedFile.getName(), failedFile.getName()}, systemProperties);
        final String[] command = appendArgs(new String[] {_javaExecutable.getValue()}, javaArgs);

        final PrintStream out = out();

        out.println("JUnit auto-test: Started " + autoTest);
        out.flush();
        final long start = System.currentTimeMillis();
        final int exitValue = exec(outputDir, command, null, outputFile, autoTest, _autoTestTimeOut.getValue());
        out.print("JUnit auto-test: Stopped " + autoTest);

        final Set<String> unexpectedResults = new HashSet<String>();
        parseAutoTestResults(passedFile, true, unexpectedResults);
        parseAutoTestResults(failedFile, false, unexpectedResults);

        if (exitValue != 0) {
            if (exitValue == PROCESS_TIMEOUT) {
                out.print(" (timed out)");
            } else {
                out.print(" (exit value == " + exitValue + ")");
            }
            _autoTestsWithExceptions.append(autoTest);
        }
        final long runTime = System.currentTimeMillis() - start;
        out.println(" [Time: " + NumberFormat.getInstance().format((double) runTime / 1000) + " seconds]");
        for (String unexpectedResult : unexpectedResults) {
            out.println("    " + unexpectedResult);
        }
        if (!unexpectedResults.isEmpty()) {
            out.println("    see: " + fileRef(outputFile));
        }
    }

    private static void runJavaTesterTests() {
        if (stopTesting() || _skipJavaTesterTests.getValue()) {
            return;
        }
        final List<String> javaTesterConfigs = _javaTesterConfigs.getValue();

        final ExecutorService javaTesterService = Executors.newFixedThreadPool(_javaTesterConcurrency.getValue());
        final CompletionService<Void> javaTesterCompletionService = new ExecutorCompletionService<Void>(javaTesterService);
        for (final String config : javaTesterConfigs) {
            javaTesterCompletionService.submit(new Runnable() {
                public void run() {
                    if (!stopTesting()) {
                        runWithSerializedOutput(new Runnable() {
                            public void run() {
                                runJavaTesterTests(config);
                            }
                        });
                    }
                }
            }, null);
        }

        javaTesterService.shutdown();
        try {
            javaTesterService.awaitTermination(_javaTesterTimeOut.getValue() * 2 * javaTesterConfigs.size(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static String fileRef(File file) {
        final String basePath = new File(_outputDir.getValue()).getAbsolutePath() + File.separator;
        final String path = file.getAbsolutePath();
        if (path.startsWith(basePath)) {
            return "file:" + path.substring(basePath.length());
        }
        return file.getAbsolutePath();
    }

    private static void runJavaTesterTests(String config) {
        final File imageDir = new File(_outputDir.getValue(), config);

        final PrintStream out = out();
        out.println("Java tester: Started " + config);
        if (_skipImageGen.getValue() || generateImage(imageDir, config)) {
            String nextTestOption = "-XX:TesterStart=0";
            int executions = 0;
            while (nextTestOption != null) {
                final File outputFile = getOutputFile(imageDir, "JAVA_TESTER" + (executions == 0 ? "" : "-" + executions), config);
                final JavaCommand command = new JavaCommand((Class) null);
                command.addArgument(nextTestOption);
                final int exitValue = runMaxineVM(command, imageDir, null, outputFile, _javaTesterTimeOut.getValue());
                final JavaTesterResult result = parseJavaTesterOutputFile(config, outputFile);
                final String summary = result._summary;
                nextTestOption = result._nextTestOption;
                out.print("Java tester: Stopped " + config + " - ");
                if (exitValue == 0) {
                    out.println(summary);
                } else if (exitValue == PROCESS_TIMEOUT) {
                    out.println("(timed out): " + summary);
                    out.println("  -> see: " + fileRef(outputFile));
                } else {
                    out.println("(exit = " + exitValue + "): " + summary);
                    out.println("  -> see: " + fileRef(outputFile));
                }
                executions++;
            }
        } else {
            out.println("(image build failed)");
            final File outputFile = getOutputFile(imageDir, "IMAGEGEN", config);
            out.println("  -> see: " + fileRef(outputFile));
        }
    }

    private static void buildJavaRunSchemeAndRunOutputTests() {
        if (stopTesting()) {
            return;
        }
        final String config = _javaConfigAlias == null ? "java" : _javaConfigAlias;
        final File outputDir = new File(_outputDir.getValue(), "java");
        final File imageDir = new File(_outputDir.getValue(), config);
        out().println("Building Java run scheme: started");
        if (_skipImageGen.getValue() || generateImage(imageDir, config)) {
            out().println("Building Java run scheme: OK");
            if (!_skipOutputTests.getValue()) {
                runOutputTests(outputDir, imageDir);
            }
            runSpecJVM98(outputDir, imageDir);
            runDaCapo(outputDir, imageDir);
        } else {
            out().println("Building Java run scheme: failed");
            final File outputFile = getOutputFile(imageDir, "IMAGEGEN", config);
            out().println("  -> see: " + fileRef(outputFile));
        }
    }

    private static void runDaCapo(File outputDir, File imageDir) {
        final File jarFile = _dacapoJar.getValue();
        if (jarFile == null) {
            return;
        }
        if (!jarFile.exists()) {
            out().println("Couldn't find DaCapo JAR file " + jarFile);
            return;
        }

        for (String test : MaxineTesterConfiguration._dacapoTests) {
            runDaCapoTest(outputDir, imageDir, test);
        }
    }

    private static void runDaCapoTest(File outputDir, File imageDir, String test) {
        if (stopTesting()) {
            return;
        }
        out().print(left50("Running DaCapo " + test + ": "));
        final File javaOutput = getOutputFile(outputDir, "JVM_DaCapo_" + test, null);

        final JavaCommand command = new JavaCommand(_dacapoJar.getValue());
        command.addArgument(test);
        final int javaExitValue = runJavaVM("DaCapo " + test, command, imageDir, null, javaOutput, _javaRunTimeOut.getValue());
        for (String config : _maxvmConfigList.getValue()) {
            runMaxineVMDaCapoTest(config, outputDir, imageDir, test, javaOutput, javaExitValue);
        }
        out().println();
    }

    private static void runMaxineVMDaCapoTest(String config, File outputDir, File imageDir, String test, File javaOutput, int javaExitValue) {
        final JavaCommand command = new JavaCommand(_dacapoJar.getValue());
        for (String option : MaxineTesterConfiguration.getVMOptions(config)) {
            command.addVMOption(option);
        }
        command.addArgument(test);
        final File maxvmOutput = getOutputFile(outputDir, "MAXVM_DaCapo_" + test, config);
        final int maxineExitValue = runMaxineVM(command, imageDir, null, maxvmOutput, _javaRunTimeOut.getValue());
        final String testName = "DaCapo:" + test;
        if (javaExitValue != maxineExitValue) {
            if (maxineExitValue == PROCESS_TIMEOUT) {
                final ExpectedResult expected = printFailed(testName, config);
                addTestResult(testName, String.format("timed out", maxineExitValue, javaExitValue), expected);
            } else {
                final ExpectedResult expected = printFailed(testName, config);
                addTestResult(testName, String.format("bad exit value [received %d, expected %d; see %s and %s ]", maxineExitValue, javaExitValue, fileRef(javaOutput), fileRef(maxvmOutput)), expected);
            }
        } else if (compareFiles(javaOutput, maxvmOutput, null)) {
            final ExpectedResult expected = printSuccess(testName, config);
            addTestResult(testName, null, expected);
        } else {
            final ExpectedResult expected = printFailed(testName, config);
            addTestResult(testName, String.format("output did not match [compare %s with %s ]", fileRef(javaOutput), fileRef(maxvmOutput)), expected);
        }
    }

    private static void runSpecJVM98(File outputDir, File imageDir) {
        final File specjvm98Zip = _specjvm98Zip.getValue();
        if (specjvm98Zip == null) {
            return;
        }
        if (!specjvm98Zip.exists()) {
            out().println("SpecJVM98 zip file does not exist: " + specjvm98Zip);
            return;
        }

        final File specjvm98Dir = new File(_outputDir.getValue(), "specjvm98");
        unzip(specjvm98Zip, specjvm98Dir);

        for (String test : MaxineTesterConfiguration._specjvm98Tests) {
            runSpecJVM98Test(outputDir, imageDir, specjvm98Dir, test);
        }
    }

    private static void runSpecJVM98Test(File outputDir, File imageDir, File workingDir, String test) {
        if (stopTesting()) {
            return;
        }
        out().print(left50("Running SpecJVM98 " + test + ": "));
        final File javaOutput = getOutputFile(outputDir, "JVM_SpecJVM98_" + test, null);

        final JavaCommand command = new JavaCommand("SpecApplication");
        command.addClasspath(".");
        command.addArgument(test);

        final int javaExitValue = runJavaVM("DaCapo " + test, command, imageDir, workingDir, javaOutput, _javaRunTimeOut.getValue());
        for (String config : _maxvmConfigList.getValue()) {
            runMaxineVMSpecJVM98Test(config, outputDir, imageDir, workingDir, test, javaOutput, javaExitValue);
        }
        out().println();
    }

    private static void runMaxineVMSpecJVM98Test(String config, File outputDir, File imageDir, File workingDir, String test, File javaOutput, int javaExitValue) {
        final JavaCommand command = new JavaCommand("SpecApplication");
        for (String option : MaxineTesterConfiguration.getVMOptions(config)) {
            command.addVMOption(option);
        }
        command.addArgument(test);
        command.addClasspath(".");
        final File maxvmOutput = getOutputFile(outputDir, "MAXVM_SpecJVM98_" + test, config);
        final int maxineExitValue = runMaxineVM(command, imageDir, workingDir, maxvmOutput, _javaRunTimeOut.getValue());
        final String testName = "SpecJVM98:" + test;
        if (javaExitValue != maxineExitValue) {
            if (maxineExitValue == PROCESS_TIMEOUT) {
                final ExpectedResult expected = printFailed(testName, config);
                addTestResult(testName, String.format("timed out", maxineExitValue, javaExitValue), expected);
            } else {
                final ExpectedResult expected = printFailed(testName, config);
                addTestResult(testName, String.format("bad exit value [received %d, expected %d; see %s and %s ]", maxineExitValue, javaExitValue, fileRef(javaOutput), fileRef(maxvmOutput)), expected);
            }
        } else if (compareFiles(javaOutput, maxvmOutput, MaxineTesterConfiguration._specjvm98IgnoredLinePatterns)) {
            final ExpectedResult expected = printSuccess(testName, config);
            addTestResult(testName, null, expected);
        } else {
            final ExpectedResult expected = printFailed(testName, config);
            addTestResult(testName, String.format("output did not match [compare %s with %s ]", fileRef(javaOutput), fileRef(maxvmOutput)), expected);
        }
    }

    private static void runOutputTests(final File outputDir, final File imageDir) {
        out().println("Output tests key:");
        out().println("      OK: positive test passed");
        out().println("  normal: negative test failed");
        out().println("  passed: negative test passed");
        out().println("  failed: positive test failed");
        out().println("  noluck: non-deterministic test failed");
        out().println("   lucky: non-deterministic test passed");
        for (Class mainClass : MaxineTesterConfiguration._outputTestClasses) {
            runOutputTest(outputDir, imageDir, mainClass);
        }
    }

    private static void runOutputTest(File outputDir, File imageDir, Class mainClass) {
        if (stopTesting()) {
            return;
        }
        out().print(left50("Running " + mainClass.getName() + ": "));
        final File javaOutput = getOutputFile(outputDir, "JVM_" + mainClass.getSimpleName(), null);

        final JavaCommand command = new JavaCommand(mainClass);
        for (String option : javaVMArgs()) {
            command.addVMOption(option);
        }
        command.addClasspath(System.getProperty("java.class.path"));
        final int javaExitValue = runJavaVM(mainClass.getName(), command, imageDir, null, javaOutput, _javaRunTimeOut.getValue());
        for (String config : _maxvmConfigList.getValue()) {
            runMaxineVMOutputTest(config, outputDir, imageDir, mainClass, javaOutput, javaExitValue);
        }
        out().println();
    }
    private static String left50(final String str) {
        return Strings.padLengthWithSpaces(str, 50);
    }

    private static String left16(final String str) {
        return Strings.padLengthWithSpaces(str, 16);
    }

    private static ExpectedResult printFailed(String testName, String config) {
        final ExpectedResult expectedResult = MaxineTesterConfiguration.expectedResult(testName, config);
        if (expectedResult == ExpectedResult.FAIL) {
            out().print(left16(config + ": (normal)"));
        } else if (expectedResult == ExpectedResult.NONDETERMINISTIC) {
            out().print(left16(config + ": (noluck) "));
        } else {
            out().print(left16(config + ": (failed)"));
        }
        out().flush();
        return expectedResult;
    }

    private static ExpectedResult printSuccess(String testName, String config) {
        final ExpectedResult expectedResult = MaxineTesterConfiguration.expectedResult(testName, config);
        if (expectedResult == ExpectedResult.PASS) {
            out().print(left16(config + ": OK"));
        } else if (expectedResult == ExpectedResult.NONDETERMINISTIC) {
            out().print(left16(config + ": (lucky) "));
        } else {
            out().print(left16(config + ": (passed)"));
        }
        out().flush();
        return expectedResult;
    }

    private static void runMaxineVMOutputTest(String config, File outputDir, File imageDir, Class mainClass, final File javaOutput, final int javaExitValue) {
        final JavaCommand command = new JavaCommand(mainClass);
        for (String option : MaxineTesterConfiguration.getVMOptions(config)) {
            command.addVMOption(option);
        }
        command.addClasspath(System.getProperty("java.class.path"));
        final File maxvmOutput = getOutputFile(outputDir, "MAXVM_" + mainClass.getSimpleName(), config);
        final int maxineExitValue = runMaxineVM(command, imageDir, null, maxvmOutput, _javaRunTimeOut.getValue());
        if (javaExitValue != maxineExitValue) {
            if (maxineExitValue == PROCESS_TIMEOUT) {
                final ExpectedResult expected = printFailed(mainClass.getName(), config);
                addTestResult(mainClass.getName(), String.format("timed out", maxineExitValue, javaExitValue), expected);
            } else {
                final ExpectedResult expected = printFailed(mainClass.getName(), config);
                addTestResult(mainClass.getName(), String.format("bad exit value [received %d, expected %d; see %s and %s ]", maxineExitValue, javaExitValue, fileRef(javaOutput), fileRef(maxvmOutput)), expected);
            }
        } else if (compareFiles(javaOutput, maxvmOutput, null)) {
            final ExpectedResult expected = printSuccess(mainClass.getName(), config);
            addTestResult(mainClass.getName(), null, expected);
        } else {
            final ExpectedResult expected = printFailed(mainClass.getName(), config);
            addTestResult(mainClass.getName(), String.format("output did not match [compare %s with %s ]", fileRef(javaOutput), fileRef(maxvmOutput)), expected);
        }
    }

    private static boolean compareFiles(File f1, File f2, String[] ignoredLinePatterns) {
        try {
            final BufferedReader f1Reader = new BufferedReader(new FileReader(f1));
            final BufferedReader f2Reader = new BufferedReader(new FileReader(f2));
            try {

                String line1;
                String line2;
            nextLine:
                while (true) {
                    line1 = f1Reader.readLine();
                    line2 = f2Reader.readLine();
                    if (line1 == null) {
                        if (line2 == null) {
                            return true;
                        }
                        return false;
                    }
                    if (!line1.equals(line2)) {
                        if (line2 == null) {
                            return false;
                        }
                        if (ignoredLinePatterns != null) {
                            for (String pattern : ignoredLinePatterns) {
                                if (line1.contains(pattern) && line2.contains(pattern)) {
                                    continue nextLine;
                                }
                            }
                        }
                        return false;
                    }
                }
            } finally {
                f1Reader.close();
                f2Reader.close();
            }
        } catch (IOException e) {
            return false;
        }
    }

    static class JavaTesterResult {
        final String _summary;
        final String _nextTestOption;

        JavaTesterResult(String summary, String nextTestOption) {
            _nextTestOption = nextTestOption;
            _summary = summary;
        }

    }

    private static final Pattern TEST_BEGIN_LINE = Pattern.compile("(\\d+): +(\\S+)\\s+next: '-XX:TesterStart=(\\d+)', end: '-XX:TesterEnd=(\\d+)'");

    private static JavaTesterResult parseJavaTesterOutputFile(String config, File outputFile) {
        String nextTestOption = null;
        String lastTest = null;
        String lastTestNumber = null;
        try {
            final BufferedReader reader = new BufferedReader(new FileReader(outputFile));
            final AppendableSequence<String> failedLines = new ArrayListSequence<String>();
            try {
                while (true) {
                    final String line = reader.readLine();

                    if (line == null) {
                        break;
                    }

                    final Matcher matcher = TEST_BEGIN_LINE.matcher(line);
                    if (matcher.matches()) {
                        if (lastTest != null) {
                            addTestResult(lastTest, null);
                        }
                        lastTestNumber = matcher.group(1);
                        lastTest = matcher.group(2);
                        final String nextTestNumber = matcher.group(3);
                        final String endTestNumber = matcher.group(4);
                        if (!nextTestNumber.equals(endTestNumber)) {
                            nextTestOption = "-XX:TesterStart=" + nextTestNumber;
                        } else {
                            nextTestOption = null;
                        }

                    } else if (line.contains("failed")) {
                        failedLines.append(line); // found a line with "failed"--probably a failed test
                        addTestResult(lastTest, line);
                        lastTest = null;
                        lastTestNumber = null;
                    } else if (line.startsWith("Done: ")) {
                        if (lastTest != null) {
                            addTestResult(lastTest, null);
                        }
                        lastTest = null;
                        lastTestNumber = null;
                        // found the terminating line indicating how many tests passed
                        if (failedLines.isEmpty()) {
                            assert nextTestOption == null;
                            return new JavaTesterResult(line, null);
                        }
                        break;
                    }
                }
                if (lastTest != null) {
                    addTestResult(lastTest, "never returned a result");
                    failedLines.append("\t" + lastTestNumber + ": crashed or hung the VM");
                }
                if (failedLines.isEmpty()) {
                    return new JavaTesterResult("no failures", nextTestOption);
                }
                final StringBuffer buffer = new StringBuffer("failures: ");
                for (String failed : failedLines) {
                    buffer.append("\n").append(failed);
                }
                return new JavaTesterResult(buffer.toString(), nextTestOption);
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            return new JavaTesterResult("could not open file: " + outputFile.getPath(), null);
        }
    }

    /**
     * @param workingDir if {@code null}, then {@code imageDir} is used
     */
    private static int runMaxineVM(JavaCommand command, File imageDir, File workingDir, File outputFile, int timeout) {
        String[] envp = null;
        if (OperatingSystem.current() == OperatingSystem.LINUX) {
            // Since the executable may not be in the default location, then the -rpath linker option used when
            // building the executable may not point to the location of libjvm.so any more. In this case,
            // LD_LIBRARY_PATH needs to be set appropriately.
            final Map<String, String> env = new HashMap<String, String>(System.getenv());
            String libraryPath = env.get("LD_LIBRARY_PATH");
            if (libraryPath != null) {
                libraryPath = libraryPath + File.pathSeparatorChar + imageDir.getAbsolutePath();
            } else {
                libraryPath = imageDir.getAbsolutePath();
            }
            env.put("LD_LIBRARY_PATH", libraryPath);

            final String string = env.toString();
            envp = string.substring(1, string.length() - 2).split(", ");
        }
        return exec(workingDir == null ? imageDir : workingDir, command.getExecutableCommand(imageDir.getAbsolutePath() + "/maxvm"), envp, outputFile, null, timeout);
    }

    /**
     * @param workingDir if {@code null}, then {@code imageDir} is used
     */
    private static int runJavaVM(String program, JavaCommand command, File imageDir, File workingDir, File outputFile, int timeout) {
        final String name = "Executing " + program;
        return exec(workingDir == null ? imageDir : workingDir, command.getExecutableCommand(_javaExecutable.getValue()), null, outputFile, name, timeout);
    }

    /**
     * Map from configuration names to the directory in which the image for the configuration was created.
     * If the image generation failed for a configuration, then it will have a {@code null} entry in this map.
     */
    private static final Map<String, File> _generatedImages = new HashMap<String, File>();

    public static boolean generateImage(File imageDir, String imageConfig) {
        if (_generatedImages.containsKey(imageConfig)) {
            return _generatedImages.get(imageConfig) != null;
        }
        final String[] generatorArguments = MaxineTesterConfiguration._imageConfigs.get(imageConfig);
        if (generatorArguments == null) {
            ProgramError.unexpected("unknown image configuration: " + imageConfig);
        }
        Trace.line(2, "Generating image for " + imageConfig + " configuration...");
        final String[] imageArguments = appendArgs(new String[] {"-output-dir=" + imageDir, "-trace=1"}, generatorArguments);
        final String[] javaVMArgs = appendArgs(new String[] {"-XX:CompileCommand=exclude,com/sun/max/vm/jit/JitReferenceMapEditor,fillInMaps"}, javaVMArgs());
        String[] javaArgs = buildJavaArgs(BinaryImageGenerator.class, javaVMArgs, imageArguments, null);
        javaArgs = appendArgs(new String[] {_javaExecutable.getValue()}, javaArgs);
        final File outputFile = getOutputFile(imageDir, "IMAGEGEN", imageConfig);

        final int exitValue = exec(null, javaArgs, null, outputFile, "Building " + imageDir.getName() + "/maxine.vm", _imageBuildTimeOut.getValue());
        if (exitValue == 0) {
            // if the image was built correctly, copy the maxvm executable and shared libraries to the same directory
            copyBinary(imageDir, "maxvm");
            copyBinary(imageDir, mapLibraryName("jvm"));
            copyBinary(imageDir, mapLibraryName("javatest"));
            copyBinary(imageDir, mapLibraryName("prototype"));
            copyBinary(imageDir, mapLibraryName("tele"));

            if (OperatingSystem.current() == OperatingSystem.DARWIN) {
                exec(null, new String[] {"bin/mod-macosx-javalib.sh", imageDir.getAbsolutePath(), System.getProperty("java.home")}, null, new File("/dev/stdout"), null, 5);
            }

            _generatedImages.put(imageConfig, imageDir);
            return true;
        } else if (exitValue == PROCESS_TIMEOUT) {
            out().println("(image build timed out): " + new File(imageDir, BinaryImageGenerator.getDefaultBootImageFilePath().getName()));
        }
        _generatedImages.put(imageConfig, null);
        return false;
    }

    private static String mapLibraryName(String name) {
        final String libName = System.mapLibraryName(name);
        if (OperatingSystem.current() == OperatingSystem.DARWIN && libName.endsWith(".jnilib")) {
            return Strings.chopSuffix(libName, ".jnilib") + ".dylib";
        }
        return libName;
    }

    private static void copyBinary(File imageDir, String binary) {
        final File defaultImageDir = BinaryImageGenerator.getDefaultBootImageFilePath().getParentFile();
        final File defaultBinaryFile = new File(defaultImageDir, binary);
        final File binaryFile = new File(imageDir, binary);
        try {
            Files.copy(defaultBinaryFile, binaryFile);
            binaryFile.setExecutable(true);
        } catch (IOException e) {
            ProgramError.unexpected(e);
        }
    }

    private static File getOutputFile(File outputDir, String outputFileName, String imageConfig, String suffix) {
        final String configString = imageConfig == null ? "" : "_" + imageConfig;
        final File file = new File(outputDir, outputFileName + configString + suffix);
        makeDirectory(file.getParentFile());
        return file;
    }

    private static File getOutputFile(File outputDir, String outputFileName, String imageConfig) {
        return getOutputFile(outputDir, outputFileName, imageConfig, ".stdout");
    }

    private static String[] appendArgs(String[] args, String... extraArgs) {
        String[] result = args;
        if (extraArgs.length > 0) {
            result = new String[args.length + extraArgs.length];
            System.arraycopy(args, 0, result, 0, args.length);
            System.arraycopy(extraArgs, 0, result, args.length, extraArgs.length);
        }
        return result;
    }

    private static String[] javaVMArgs() {
        final String value = _javaVMArgs.getValue();
        if (value == null) {
            return null;
        }
        final String javaVMArgs = value.trim();
        return javaVMArgs.split("\\s+");
    }

    private static String[] buildJavaArgs(Class javaMainClass, String[] vmArguments, String[] javaArguments, String[] systemProperties) {
        final LinkedList<String> cmd = new LinkedList<String>();
        addPropertiesAndVmArgs(vmArguments, systemProperties, cmd);
        cmd.add(javaMainClass.getName());
        addJavaArgs(javaArguments, cmd);
        return cmd.toArray(new String[0]);
    }

    private static String[] buildJarArgs(File jarFile, String[] vmArguments, String[] javaArguments, String[] systemProperties) {
        final LinkedList<String> cmd = new LinkedList<String>();
        addPropertiesAndVmArgs(vmArguments, systemProperties, cmd);
        cmd.add("-jar");
        cmd.add(jarFile.getAbsolutePath());
        addJavaArgs(javaArguments, cmd);
        return cmd.toArray(new String[0]);
    }
    private static void addJavaArgs(String[] javaArguments, final LinkedList<String> cmd) {
        if (javaArguments != null) {
            for (String arg : javaArguments) {
                cmd.add(arg);
            }
        }
    }
    private static void addPropertiesAndVmArgs(String[] vmArguments, String[] systemProperties, final LinkedList<String> cmd) {
        cmd.add("-classpath");
        cmd.add(System.getProperty("java.class.path"));
        addJavaArgs(vmArguments, cmd);
        if (systemProperties != null) {
            for (int i = 0; i < systemProperties.length; i++) {
                cmd.add("-D" + systemProperties[i]);
            }
        }
    }

    /**
     * Executes a command in a sub-process.
     *
     * @param workingDir the working directory of the subprocess, or {@code null} if the subprocess should inherit the
     *            working directory of the current process
     * @param command the command and arguments to be executed
     * @param env array of strings, each element of which has environment variable settings in the format
     *            <i>name</i>=<i>value</i>, or <tt>null</tt> if the subprocess should inherit the environment of the
     *            current process
     * @param outputFile the file to which stdout and stderr should be redirected or {@code null} if these output
     *            streams are to be discarded
     * @param name a descriptive name for the command or {@code null} if {@code command[0]} should be used instead
     * @param timeout the timeout in seconds
     * @return
     */
    private static int exec(File workingDir, String[] command, String[] env, File outputFile, String name, int timeout) {
        traceExec(workingDir, command);
        try {
            final Process process = Runtime.getRuntime().exec(command, env, workingDir);
            final ProcessThread processThread = new ProcessThread(outputFile, process, name != null ? name : command[0], timeout);
            final int exitValue = processThread.exitValue();
            return exitValue;
        } catch (IOException e) {
            throw ProgramError.unexpected(e);
        }
    }

    private static void traceExec(File workingDir, String[] command) {
        if (Trace.hasLevel(2)) {
            final PrintStream stream = Trace.stream();
            synchronized (stream) {
                if (workingDir == null) {
                    stream.println("Executing process in current directory");
                } else {
                    stream.println("Executing process in directory: " + workingDir);
                }
                stream.print("Command line:");
                for (String c : command) {
                    stream.print(" " + c);
                }
                stream.println();
            }
        }
    }

    /**
     * A dedicated thread to wait for the process and terminate it if it gets stuck.
     *
     * @author Ben L. Titzer
     */
    private static class ProcessThread extends Thread {

        private final Process _process;
        private final int _timeoutMillis;
        protected Integer _exitValue;
        private boolean _timedOut;
        private final InputStream _stdout;
        private final InputStream _stderr;
        private final OutputStream _stdoutTo;
        private final OutputStream _stderrTo;
        private Throwable _exception;

        private final File _stdoutToFile;
        private final File _stderrToFile;

        public ProcessThread(File outputFile, Process process, String name, int timeoutSeconds) throws FileNotFoundException {
            super(name);
            _process = process;
            _timeoutMillis = 1000 * timeoutSeconds;
            _stdout = new BufferedInputStream(_process.getInputStream());
            _stderr = new BufferedInputStream(_process.getErrorStream());

            _stdoutToFile = outputFile;
            _stdoutTo = outputFile != null ? new FileOutputStream(outputFile) : new NullOutputStream();

            if (outputFile == null) {
                _stderrTo = new NullOutputStream();
                _stderrToFile = null;
            } else {
                if (outputFile.getName().endsWith("stdout")) {
                    _stderrToFile = new File(Strings.chopSuffix(outputFile.getAbsolutePath(), "stdout") + "stderr");
                } else {
                    _stderrToFile = new File(outputFile.getAbsolutePath() + ".stderr");
                }
                _stderrTo = new FileOutputStream(_stderrToFile);
            }
        }

        private int redirect(InputStream from, OutputStream to, File toFile) throws IOException {
            final byte[] buf = new byte[1024];
            int total = 0;
            while (from.available() > 0) {
                final int count = from.read(buf);
                if (count > 0) {
                    try {
                        to.write(buf, 0, count);
                    } catch (IOException ioException) {
                        throw new IOException("IO error writing to " + (toFile == null ? "[null]" : toFile.getAbsolutePath()), ioException);
                    }
                    total += count;
                }
            }
            return total;
        }

        @Override
        public void run() {
            try {
                final long start = System.currentTimeMillis();
                while (_exitValue == null && System.currentTimeMillis() - start < _timeoutMillis) {
                    if (redirect(_stdout, _stdoutTo, _stdoutToFile) + redirect(_stderr, _stderrTo, _stderrToFile) == 0) {
                        try {
                            // wait for a few milliseconds to avoid eating too much CPU.
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            // do nothing.
                        }
                    }
                }
                if (_exitValue == null) {
                    _timedOut = true;
                    // Timed out:
                    _process.destroy();
                }
                _stdout.close();
                _stderr.close();
            } catch (IOException ioException) {
                _exception = ioException;
            }
        }

        public int exitValue() throws IOException {
            start();
            try {
                _exitValue = _process.waitFor();
            } catch (InterruptedException interruptedException) {
                // do nothing.
            }

            try {
                // Wait for redirecting thread to finish
                join();

            } catch (InterruptedException interruptedException) {
                interruptedException.printStackTrace();
            }

            _stdout.close();
            _stderr.close();

            if (_exception != null) {
                throw ProgramError.unexpected(_exception);
            }
            if (_timedOut) {
                _exitValue = PROCESS_TIMEOUT;
            }
            return _exitValue;
        }
    }
}
