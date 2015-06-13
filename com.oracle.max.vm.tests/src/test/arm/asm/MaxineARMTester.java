package test.arm.asm;

import java.io.*;

public class MaxineARMTester {

    public static boolean DEBUG = true;
    public static boolean RESET = false;
    public static boolean DEBUGOBJECTS = false;
    public static final String ENABLE_QEMU = "max.arm.qemu";
    public static boolean ENABLE_SIMULATOR = true;
    public static final int NUM_REGS = 32;
    public static final int NUM_DP_REGS = 16;

    public enum BitsFlag {
        Bit0(0x1), Bit1(0x2), Bit2(0x4), Bit3(0x8), Bit4(0x10), Bit5(0x20), Bit6(0x40), Bit7(0x80), Bit8(0x100), Bit9(0x200), Bit10(0x400), Bit11(0x800), Bit12(0x1000), Bit13(0x2000), Bit14(0x4000), Bit15(
                        0x8000), Bit16(0x10000), Bit17(0x20000), Bit18(0x40000), Bit19(0x80000), Bit20(0x100000), Bit21(0x200000), Bit22(0x400000), Bit23(0x800000), Bit24(0x1000000), Bit25(0x2000000), Bit26(
                        0x4000000), Bit27(0x8000000), Bit28(0x10000000), Bit29(0x20000000), Bit30(0x40000000), Bit31(0x80000000), NZCBits(0xe0000000), NZCVBits(0xf0000000), Lower16Bits(0x0000ffff), Upper16Bits(
                        0xffff0000), All32Bits(0xffffffff);

        public static final BitsFlag[] values = values();
        private final long value;

        private BitsFlag(long value) {
            this.value = value;
        }

        public long value() {
            return value;
        }
    }

    private static final File qemuOutput = new File("qemu_output");
    private static final File qemuErrors = new File("qemu_errors");
    private static final File bindOutput = new File("bind_output");
    private static final File gdbOutput = new File("gdb_output");
    private static final File gdbInput = new File("gdb_input");
    private static final File gdbInputFPREGS = new File("gdb_input_fpregs");
    private static final File gdbErrors = new File("gdb_errors");
    private static final File objCopyOutput = new File("obj_copy_output");
    private static final File objCopyErrors = new File("obj_copy_errors");
    private static final File gccOutput = new File("gcc_output");
    private static final File gccErrors = new File("gcc_errors");
    private static final File asOutput = new File("as_output");
    private static final File asErrors = new File("as_errors");
    private static final File linkOutput = new File("link_output");
    private static final File linkErrors = new File("link_errors");

    private Process objectCopy;
    private Process gcc;
    private Process assembler;
    private Process assemblerEntry;
    private Process linker;
    private Process qemu;
    private Process gdb;
    private BitsFlag[] bitMasks;
    private char[] chars;
    private int[] expectRegs = new int[NUM_REGS];
    private int[] gotRegs = new int[NUM_REGS];
    private boolean[] testRegs = new boolean[NUM_REGS];
    public static int oldpos = 0;

    /*
     * arm-unknown-eabi-gcc -c -march=armv7-a -g test.c -o test.o arm-unknown-eabi-as -mcpu=cortex-a9 -g startup.s -o
     * startup.o arm-unknown-eabi-as -mcpu=cortex-a9 -g asm_entry.s -o asm_entry.o arm-unknown-eabi-ld -T test.ld test.o
     * startup.o asm_entry.o -o test.elf arm-unknown-eabi-objcopy -O binary test.elf test.bin qemu-system-arm -cpu
     * cortex-a9 -M versatilepb -m 128M -nographic -s -S -kernel test.bin
     */

    public void reset() {
        if (RESET) {
            cleanFiles();
        }
        cleanProcesses();
    }

    public void cleanFiles() {
        deleteFile(qemuOutput);
        deleteFile(qemuErrors);
        deleteFile(bindOutput);
        deleteFile(gdbOutput);
        deleteFile(gdbErrors);
        deleteFile(objCopyOutput);
        deleteFile(objCopyErrors);
        deleteFile(gccOutput);
        deleteFile(gccErrors);
        deleteFile(asOutput);
        deleteFile(asErrors);
        deleteFile(linkOutput);
        deleteFile(linkErrors);
    }

    private void deleteFile(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    public void objcopy() {
        final ProcessBuilder objcopy = new ProcessBuilder("aarch64-none-elf-objcopy", "-O", "binary", "test.elf", "test.bin");
        objcopy.redirectOutput(objCopyOutput);
        objcopy.redirectError(objCopyErrors);
        try {
            objectCopy = objcopy.start();
            objectCopy.waitFor();
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
        }
    }

    public void newCompile() {
        final ProcessBuilder removeFiles = new ProcessBuilder("/bin/rm", "-rR", "test.bin", "test.elf");
        final ProcessBuilder compile = new ProcessBuilder("arm-none-eabi-gcc", "-c", "-march=armv8-a", "-mfloat-abi=hard", "-mfpu=fp-armv8", "-g", "test.c", "-o", "test.o");
        compile.redirectOutput(gccOutput);
        compile.redirectError(gccErrors);
        try {
            removeFiles.start().waitFor();
            gcc = compile.start();
            gcc.waitFor();
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
        }
    }

    public void compile() {
        final ProcessBuilder removeFiles = new ProcessBuilder("/bin/rm", "-rR", "test.bin", "test.elf");
        final ProcessBuilder compile = new ProcessBuilder("aarch64-none-elf-gcc", "-c", "-march=armv8-a", "-g", "test.c", "-o", "test.o");
        compile.redirectOutput(gccOutput);
        compile.redirectError(gccErrors);
        try {
            removeFiles.start().waitFor();
            gcc = compile.start();
            gcc.waitFor();
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
        }
    }

    public void assembleStartup() {
        final ProcessBuilder assemble = new ProcessBuilder("aarch64-none-elf-as", "-march=armv8-a","-g", "startup.s", "-o", "startup.o");
        assemble.redirectOutput(new File("as_output"));
        assemble.redirectError(new File("as_errors"));
        try {
            assembler = assemble.start();
            assembler.waitFor();
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
        }
    }

    public void assembleEntry() {
        final ProcessBuilder assemble = new ProcessBuilder("aarch64-none-elf-as", "-march=armv8-a", "-g", "asm_entry.s", "-o", "asm_entry.o");
        assemble.redirectOutput(asOutput);
        assemble.redirectError(asErrors);
        try {
            assemblerEntry = assemble.start();
            assemblerEntry.waitFor();
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
        }
    }

    public void link() {
        final ProcessBuilder link = new ProcessBuilder("aarch64-none-elf-ld", "-T", "test.ld", "test.o", "startup.o", "-o", "test.elf");
        link.redirectOutput(linkOutput);
        link.redirectError(linkErrors);
        try {
            linker = link.start();
            linker.waitFor();
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
        }
    }

    public void cleanProcesses() {
        terminateProcess(objectCopy);
        terminateProcess(gcc);
        terminateProcess(assembler);
        terminateProcess(assemblerEntry);
        terminateProcess(linker);
        terminateProcess(gdb);
        terminateProcess(qemu);
    }

    private void terminateProcess(Process process) {
        if (process != null) {
            process.destroy();
        }
    }

    private void runSimulation(boolean captureFPREGs) throws Exception {
        ProcessBuilder gdbProcess = new ProcessBuilder("arm-none-eabi-gdb");
        if (captureFPREGs) {
            gdbProcess.redirectInput(gdbInputFPREGS);
        } else {
            gdbProcess.redirectInput(gdbInput);
        }
        gdbProcess.redirectOutput(gdbOutput);
        gdbProcess.redirectError(gdbErrors);
        ProcessBuilder qemuProcess = new ProcessBuilder("qemu-system-aarch64", "-cpu", "cortex-a57", "-M", "virt", "-m", "128M", "-nographic", "-s", "-S", "-kernel", "test.bin");
        qemuProcess.redirectOutput(qemuOutput);
        qemuProcess.redirectError(qemuErrors);
        try {
            qemu = qemuProcess.start();
            while (!qemuOutput.exists()) {
                Thread.sleep(500);
            }
            do {
                ProcessBuilder bindTest = new ProcessBuilder("lsof", "-i", "TCP:1234");
                bindTest.redirectOutput(bindOutput);
                bindTest.start().waitFor();
                FileInputStream inputStream = new FileInputStream(bindOutput);
                if (inputStream.available() != 0) {
                    log("MaxineARMTester:: qemu ready");
                    break;
                } else {
                    log("MaxineARMTester: gemu not ready");
                    Thread.sleep(500);
                }
            } while (true);
            gdbProcess.start().waitFor();
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
            cleanProcesses();
            System.exit(-1);
        }
    }

    public Object[] runObjectRegisteredSimulation() throws Exception {
        runSimulation(true);
        Object[] simulatedRegisters = parseObjectRegistersToFile(gdbOutput.getName());
        return simulatedRegisters;
    }

    public int[] runRegisteredSimulation() throws Exception {
        ProcessBuilder gdbProcess = new ProcessBuilder("arm-none-eabi-gdb");
        gdbProcess.redirectInput(gdbInput);
        gdbProcess.redirectOutput(gdbOutput);
        gdbProcess.redirectError(gdbErrors);
        ProcessBuilder qemuProcess = new ProcessBuilder("qemu-system-aarch64", "-cpu", "cortex-a57", "-M", "virt", "-m", "128M", "-nographic", "-s", "-S", "-kernel", "test.bin");
        qemuProcess.redirectOutput(qemuOutput);
        qemuProcess.redirectError(qemuErrors);
        try {
            qemu = qemuProcess.start();
            while (!qemuOutput.exists()) {
                Thread.sleep(500);
            }
            do {
                ProcessBuilder bindTest = new ProcessBuilder("lsof", "-i", "TCP:1234");
                bindTest.redirectOutput(bindOutput);
                bindTest.start().waitFor();
                FileInputStream inputStream = new FileInputStream(bindOutput);
                if (inputStream.available() != 0) {
                    log("MaxineARMTester:: qemu ready");
                    break;
                } else {
                    log("MaxineARMTester: gemu not ready");
                    Thread.sleep(500);
                }
            } while (true);
            gdbProcess.start().waitFor();
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
            cleanProcesses();
            System.exit(-1);
        }
        int[] simulatedRegisters = parseRegistersToFile(gdbOutput.getName());
        return simulatedRegisters;
    }

    public void runSimulation() throws Exception {
        cleanFiles();
        ProcessBuilder gdbProcess = new ProcessBuilder("aarch64-none-elf-gdb");
        gdbProcess.redirectInput(gdbInput);
        gdbProcess.redirectOutput(gdbOutput);
        gdbProcess.redirectError(gdbErrors);
        ProcessBuilder qemuProcess = new ProcessBuilder("qemu-system-aarch64", "-cpu", "cortex-a57", "-M", "virt", "-m", "128M", "-nographic", "-s", "-S", "-kernel", "test.bin");
        qemuProcess.redirectOutput(qemuOutput);
        qemuProcess.redirectError(qemuErrors);
        try {
            qemu = qemuProcess.start();
            while (!qemuOutput.exists()) {
                Thread.sleep(500);
            }
            do {
                ProcessBuilder binder = new ProcessBuilder("lsof", "-i", "TCP:1234");
                binder.redirectOutput(bindOutput);
                binder.start().waitFor();
                FileInputStream inputStream = new FileInputStream(bindOutput);
                if (inputStream.available() != 0) {
                    log("MaxineARMTester:: qemu ready");
                    break;
                } else {
                    log("MaxineARMTester: gemu not ready");
                    Thread.sleep(500);
                }
            } while (true);
            gdb = gdbProcess.start();
            gdb.waitFor();
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
            cleanProcesses();
            System.exit(-1);
        }
        int[] simulatedRegisters = parseRegistersToFile(gdbOutput.getName());
        if (!validateRegisters(simulatedRegisters, expectRegs, testRegs)) {
            cleanProcesses();
            assert false : "Error while validating registers";
        }
    }
    public int [] runSimulationRegisters() throws Exception {
        cleanFiles();
        ProcessBuilder gdbProcess = new ProcessBuilder("aarch64-none-elf-gdb");
        gdbProcess.redirectInput(gdbInput);
        gdbProcess.redirectOutput(gdbOutput);
        gdbProcess.redirectError(gdbErrors);
        ProcessBuilder qemuProcess = new ProcessBuilder("qemu-system-aarch64", "-cpu", "cortex-a57", "-M", "virt", "-m", "128M", "-nographic", "-s", "-S", "-kernel", "test.bin");
        qemuProcess.redirectOutput(qemuOutput);
        qemuProcess.redirectError(qemuErrors);
        try {
            qemu = qemuProcess.start();
            while (!qemuOutput.exists()) {
                Thread.sleep(500);
            }
            do {
                ProcessBuilder binder = new ProcessBuilder("lsof", "-i", "TCP:1234");
                binder.redirectOutput(bindOutput);
                binder.start().waitFor();
                FileInputStream inputStream = new FileInputStream(bindOutput);
                if (inputStream.available() != 0) {
                    log("MaxineARMTester:: qemu ready");
                    break;
                } else {
                    log("MaxineARMTester: gemu not ready");
                    Thread.sleep(500);
                }
            } while (true);
            gdb = gdbProcess.start();
            gdb.waitFor();
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
            cleanProcesses();
            System.exit(-1);
        }
        int[] simulatedRegisters = parseRegistersToFile(gdbOutput.getName());
        return simulatedRegisters;
    }

    private boolean validateRegisters(int[] simRegisters, int[] expectedRegisters, boolean[] testRegisters) {
        for (int i = 0; i < NUM_REGS; i++) {
            log(i + " sim: " + simRegisters[i] + " exp: " + expectedRegisters[i] + " test: " + testRegisters[i]);
        }
        boolean result = true;
        int bitmask = 0;
        for (int i = 0; i < NUM_REGS; i++) {
            long simulatedRegister = simRegisters[i] & bitMasks[i].value();
            long expectedRegister = expectedRegisters[i];
            if (testRegisters[i]) {
                if (simulatedRegister != expectedRegister) {
                    bitmask = bitmask | (1 << i);
                    result = false;
                }
            }
        }
        if (!result) {
            for (int i = 0; i < NUM_REGS; i++) {
                System.out.println(i + " sim: " + simRegisters[i] + " exp: " + expectedRegisters[i] + " test: " + testRegisters[i]);
            }
        }
        return result;
    }

    public MaxineARMTester(int[] expected, boolean[] test, BitsFlag[] range) {
        initializeQemu();
        bitMasks = range;
        for (int i = 0; i < NUM_REGS; i++) {
            expectRegs[i] = expected[i];
            testRegs[i] = test[i];
        }
    }

    public MaxineARMTester(long[] expected, boolean[] test, BitsFlag[] range) {
        initializeQemu();
        bitMasks = range;
        int j = 0;
        for (int i = 0; i < NUM_REGS; i++) {
            if (test[i]) {
                expectRegs[j] = (int) ((expected[i] >> 32) & 0xffffffff);
                expectRegs[j + 1] = (int) (expected[i] & 0xffffffff);
                testRegs[j] = testRegs[j + 1] = test[i];
                j = +2;
            }
        }
    }
    public MaxineARMTester() {
        initializeQemu();
        for (int i = 0; i < NUM_REGS; i++) {
            testRegs[i] = false;
        }
    }

    public MaxineARMTester(String[] args) {
        initializeQemu();
        for (int i = 0; i < NUM_REGS; i++) {
            testRegs[i] = false;
        }
        for (int i = 0; i < args.length; i += 2) {
            expectRegs[Integer.parseInt(args[i])] = Integer.parseInt(args[i + 1]);
            testRegs[Integer.parseInt(args[i])] = true;
        }
    }

    private void initializeQemu() {
        ENABLE_SIMULATOR = Integer.getInteger(ENABLE_QEMU) != null && Integer.getInteger(ENABLE_QEMU) > 0 ? true : false;
    }

    private Object[] parseObjectRegistersToFile(String file) throws Exception {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line = null;
        boolean enabled = false;
        int i = 0;
        Object[] expectedValues = new Object[16 + 1 + 16 + 32];
        while ((line = reader.readLine()) != null) {
            if (line.contains("r0")) {
                enabled = true;
                line = line.substring(6, line.length());
            }
            if (!enabled) {
                continue;
            }
            String value = line.split("\\s+")[1];
            long tmp = Long.parseLong(value.substring(2, value.length()).toString(), 16);

            if (tmp > Integer.MAX_VALUE) {
                tmp = (int)(2L*Integer.MIN_VALUE + tmp);
            } else expectedValues[i] = (int)tmp;
            //expectedValues[i] = new Integer((int) Long.parseLong(value.substring(2, value.length()).toString(), 16));
            expectedValues[i] = new Integer((int)tmp);
            if (DEBUGOBJECTS) {
                System.out.println(" CORE " + i + " " + ((Integer) expectedValues[i]).intValue());
            }
            i++;
            if (line.contains("cpsr")) {
                enabled = false;
                // might want to get cpsr but we dont need it right now
                expectedValues[i] = null;
                break;
            }
        }
        while ((line = reader.readLine()) != null) {
            if (line.contains("f64")) {
                enabled = true;
            } else {
                enabled = false;
            }
            if (i >= (16 + 16 + 1)) {
                break;
            }
            if (!enabled) {
                continue;
            }
            String values[] = line.split("\\s+");
            for (int j = 0; j < values.length; j++) {
                if (values[j].equals("f64")) {
                    String doubleVal = values[j + 2];
                    String str = doubleVal.substring(0, doubleVal.length() - 1);
                    try {
                        Double tmp = new Double(str);
                        expectedValues[i++] = tmp;
                    } catch (Exception e) {
                        // we get exceptions when there is a NaN
                        // currently we just set them to null
                        if (str.equals("inf")) {
                            expectedValues[i++] = new Double(Double.POSITIVE_INFINITY);
                        } else if (str.equals("-inf")) {
                            expectedValues[i++] = new Double(Double.NEGATIVE_INFINITY);
                        } else {
                            expectedValues[i++] = new Double(Double.NaN);
                        }
                    }
                    break;
                }
            }
            if (DEBUGOBJECTS) {
                System.out.println(" DOUBLE " + (i - 1) + " " + ((Double) expectedValues[i - 1]).doubleValue());
            }
            if (i >= (16 + 16 + 1)) {
                break;
            }
        }
        while ((line = reader.readLine()) != null) {
            if (line.contains("=")) {
                enabled = true;
            } else {
                enabled = false;
            }
            if (!enabled) {
                continue;
            }
            String values[] = line.split("\\s+");
            for (int j = 0; j < values.length; j++) {

                if (values[j].equals("=")) {
                    String doubleVal = values[j + 1];
                    try {
                        Float tmp = new Float(doubleVal);
                        expectedValues[i++] = tmp;
                    } catch (Exception e) {
                        if (doubleVal.equals("inf")) {
                            expectedValues[i++] = new Float(Float.POSITIVE_INFINITY);
                        } else if (doubleVal.equals("-inf")) {
                            expectedValues[i++] = new Float(Float.NEGATIVE_INFINITY);
                        } else {
                            expectedValues[i++] = new Float(Float.NaN);
                        }
                    }
                    break;
                }
            }
            if (i == expectedValues.length) {
                break;
            }
            if (DEBUGOBJECTS) {
                System.out.println(" FLOAT " + (i - 1) + " " + ((Float) expectedValues[i - 1]).floatValue());
            }
        }
        return expectedValues;
    }

    private int[] parseRegistersToFile(String file) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line = null;
        boolean enabled = false;
        int i = 0;
        int[] expectedValues = new int[NUM_REGS];
        while ((line = reader.readLine()) != null) {
            if (line.contains(" x0")) {
                enabled = true;
                line = line.substring(6, line.length());
            }
            if (!enabled) {
                continue;
            }
            String value = line.split("\\s+")[1];

            long tmp = Long.parseLong(value.substring(2, value.length()).toString(), 16);

            if (tmp > Integer.MAX_VALUE) {
                expectedValues[i] = (int) (2L * Integer.MIN_VALUE + tmp);
            } else {
                expectedValues[i] = (int) tmp;
            }
            i++;
            if (line.contains("sp")) {
                enabled = false;
            }
        }
        return expectedValues;
    }

    public void run() throws Exception {
        assembleStartup();
        assembleEntry();
        compile();
        link();
        objcopy();
        runSimulation();
    }

    public static void enableDebug() {
        DEBUG = true;
    }

    public static void disableDebug() {
        DEBUG = false;
    }

    private void log(String msg) {
        if (DEBUG) {
            System.out.println(msg);
        }
    }

    public static void main(String[] args) throws Exception {
        MaxineARMTester tester = new MaxineARMTester(args);
        tester.run();
    }
}
