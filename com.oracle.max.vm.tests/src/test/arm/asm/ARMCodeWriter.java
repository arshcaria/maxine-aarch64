package test.arm.asm;

import java.io.*;

import com.oracle.max.asm.*;

public class ARMCodeWriter {

    public static boolean debug = false;
    private int[] instructions;
    private byte[] byteVersion;
    private int totalInstructions;

    public ARMCodeWriter(Buffer codeBuffer) {
        totalInstructions = codeBuffer.position() / 4;
        byteVersion = null;
        instructions = new int[totalInstructions];
        for (int j = 0; j < totalInstructions; j++) {
            instructions[j] = codeBuffer.getInt(j * 4);
        }
    }

    public ARMCodeWriter(byte[] codeBuffer) {
        totalInstructions = codeBuffer.length / 4;
        instructions = null;
        byteVersion = codeBuffer;

    }

    public static void enableDebug() {
        debug = true;
    }

    public static void disableDebug() {
        debug = false;
    }

    private void log(String msg) {
        if (debug) {
            System.out.println(msg);
        }
    }

    public static String preAmble(String returnType, String listOfTypes, String listOfValues) {
        String val = new String(returnType + " (*pf)(");
        val += listOfTypes + ") = (" + returnType + "(*))(code);\n";
        val += "print_uart0(\"Changed!\");\n";
        val += "(*pf)(" + listOfValues + ");\n";
        val += "asm volatile(\"forever: b forever\");\n";
        return val;

    }/*
      * void (*pf)(int) = (void (*))(code); print_uart0("changed test.c!\n"); (*pf)(1); // Need to change this to
      * something related to the test itself asm volatile("forever: b forever");
      */

    public void createStaticCodeStubsFile(String functionPrototype, byte[] stubs, int entryPoint) {
        try {

            PrintWriter writer = new PrintWriter("codebuffer.c", "UTF-8");
            writer.println("unsigned char codeArray[" + ((totalInstructions + 1) * 4 + stubs.length) + "] __attribute__((aligned(0x1000))) = { \n");
            // writer.println("void c_entry() {");
            log("unsigned char code[" + ((totalInstructions + 1) * 4 + stubs.length) + "] __attribute__((aligned(0x1000))) ;\n");
            log("void c_entry() {");
            // long xxx = 0xe30090f0; // r9 240
            // int val;
            for (int i = 0; i < stubs.length; i += 4) {
                // val = i;
                /*
                 * writer.println("codeArray[" + i + " + 3] = " + stubs[i] + ";"); writer.println("codeArray[" + i +
                 * " + 2] = " + stubs[i+1] + ";"); writer.println("codeArray[" + i + " + 1] = " + stubs[i+2] + ";");
                 * writer.println("codeArray[" + i + " + 0] = " + stubs[i+3] + ";");
                 */
                //writer.println("0x" + Integer.toHexString(stubs[i + 3]) + ", " + "0x" + Integer.toHexString(stubs[i + 2]) + ", " + "0x" + Integer.toHexString(stubs[i + 1]) + ", " + "0x" +
                                //Integer.toHexString(stubs[i]) + ",\n");
                writer.println("0x" + Integer.toHexString(stubs[i ]) + ", " + "0x" + Integer.toHexString(stubs[i + 1]) + ", " + "0x" + Integer.toHexString(stubs[i + 2]) + ", " + "0x" +
                        Integer.toHexString(stubs[i+3]) + ",\n");
            }
            writer.println("0xea, 0xff, 0xff, 0xfe  };\n");
            /*
             * for (int i = stubs.length/4; i < (totalInstructions+ stubs.length/4); i++) { xxx =
             * instructions[i-stubs.length/4]; val = (i-stubs.length/4) * 4+stubs.length; writer.println("codeArray[" +
             * val + "] = " + (xxx & 0xff) + ";"); log("codeArray[" + val + "] = 0x" + Long.toString(xxx & 0xff, 16) +
             * ";"); val = val + 1; writer.println("code[" + val + "] = " + ((xxx >> 8) & 0xff) + ";"); log("codeArray["
             * + val + "] = 0x" + Long.toString((xxx >> 8) & 0xff, 16) + ";"); val = val + 1; writer.println("code[" +
             * val + "] = " + ((xxx >> 16) & 0xff) + ";"); log("codeArray[" + val + "] = 0x" + Long.toString((xxx >> 16)
             * & 0xff, 16) + ";"); val = val + 1; writer.println("code[" + val + "] = " + ((xxx >> 24) & 0xff) + ";");
             * log("codeArray[" + val + "] = 0x" + Long.toString((xxx >> 24) & 0xff, 16) + ";"); }
             */
            /*
             * writer.println("codeArray[" + stubs.length + "] = " + 0xfe + ";"); writer.println("codeArray[" +
             * stubs.length + "+1] = " + 0xff + ";"); writer.println("codeArray[" + stubs.length + "+2] = " + 0xff +
             * ";"); writer.println("codeArray[" + stubs.length + "+3] = " + 0xea + ";");
             */
            writer.println("unsigned char *code = codeArray + " + entryPoint + ";");
            writer.println("void c_entry() {");
            writer.print(functionPrototype);
            writer.close();
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
        }
    }

    public void createCodeStubsFile(byte[] stubs, int entryPoint) {
        try {

            PrintWriter writer = new PrintWriter("codebuffer.c", "UTF-8");
            writer.println("unsigned char codeArray[" + ((totalInstructions + 1) * 4 + stubs.length) + "] __attribute__((aligned(0x1000))) ;\n");
            writer.println("void c_entry() {");
            log("unsigned char code[" + ((totalInstructions + 1) * 4 + stubs.length) + "] __attribute__((aligned(0x1000))) ;\n");
            log("void c_entry() {");
            // long xxx = 0xe30090f0; // r9 240
            // int val;
            for (int i = 0; i < stubs.length; i += 4) {
                // val = i;
                writer.println("codeArray[" + i + " ] = " + stubs[i] + ";");
                writer.println("codeArray[" + i + " + 1] = " + stubs[i + 1] + ";");
                writer.println("codeArray[" + i + " + 2] = " + stubs[i + 2] + ";");
                writer.println("codeArray[" + i + " + 3] = " + stubs[i + 3] + ";");
            }
            /*
             * for (int i = stubs.length/4; i < (totalInstructions+ stubs.length/4); i++) { xxx =
             * instructions[i-stubs.length/4]; val = (i-stubs.length/4) * 4+stubs.length; writer.println("codeArray[" +
             * val + "] = " + (xxx & 0xff) + ";"); log("codeArray[" + val + "] = 0x" + Long.toString(xxx & 0xff, 16) +
             * ";"); val = val + 1; writer.println("code[" + val + "] = " + ((xxx >> 8) & 0xff) + ";"); log("codeArray["
             * + val + "] = 0x" + Long.toString((xxx >> 8) & 0xff, 16) + ";"); val = val + 1; writer.println("code[" +
             * val + "] = " + ((xxx >> 16) & 0xff) + ";"); log("codeArray[" + val + "] = 0x" + Long.toString((xxx >> 16)
             * & 0xff, 16) + ";"); val = val + 1; writer.println("code[" + val + "] = " + ((xxx >> 24) & 0xff) + ";");
             * log("codeArray[" + val + "] = 0x" + Long.toString((xxx >> 24) & 0xff, 16) + ";"); }
             */
            writer.println("codeArray[" + (stubs.length+3) +  "] = " + 0xfe + ";");
            writer.println("codeArray[" + (stubs.length + 2) +"] = " + 0xff + ";");
            writer.println("codeArray[" + (stubs.length+1) + "] = " + 0xff + ";");
            writer.println("codeArray[" + (stubs.length) + "] = " + 0xea + ";");
            writer.println("unsigned char *code = codeArray + " + entryPoint + ";");
            writer.close();
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
        }
    }

    public void createCodeFile() {
        try {
            PrintWriter writer = new PrintWriter("codebuffer.c", "UTF-8");
            writer.println("unsigned char code[" + ((totalInstructions + 1) * 4) + "] __attribute__((aligned(0x1000))) ;\n");
            writer.println("void c_entry() {");
            log("unsigned char code[" + ((totalInstructions + 1) * 4) + "] __attribute__((aligned(0x1000))) ;\n");
            log("void c_entry() {");

            long xxx = 0xe30090f0; // r9 240
            int val;
            for (int i = 0; i < totalInstructions; i++) {
                xxx = instructions[i];
                val = i * 4;
                writer.println("code[" + val + "] = " + ( xxx& 0xff) + ";");
                log("code[" + val + "] = 0x" + Long.toString((xxx ) & 0xff, 16) + ";");
                val = val + 1;
                writer.println("code[" + val + "] = " + ((xxx >> 8) & 0xff) + ";");
                log("code[" + val + "] = 0x" + Long.toString((xxx >> 8) & 0xff, 16) + ";");
                val = val + 1;
                writer.println("code[" + val + "] = " + ((xxx >> 16) & 0xff) + ";");
                log("code[" + val + "] = 0x" + Long.toString((xxx >> 16) & 0xff, 16) + ";");
                val = val + 1;

                writer.println("code[" + val + "] = " + (xxx >>24 & 0xff) + ";");
                log("code[" + val + "] = 0x" + Long.toString(xxx>>24 & 0xff, 16) + ";");



            }
            writer.println("code[" + totalInstructions * 4 + "] = " + 0x0 + ";");
            log("code[" + totalInstructions * 4 + "] = " + 0x0 + ";");
            writer.println("code[" + totalInstructions * 4 + "+1] = " + 0x0 + ";");
            log("code[" + totalInstructions * 4 + "+1] = " + 0x0 + ";");
            writer.println("code[" + totalInstructions * 4 + "+2] = " + 0x0 + ";");
            log("code[" + totalInstructions * 4 + "+2] = " + 0x0 + ";");
            writer.println("code[" + totalInstructions * 4 + "+3] = " + 0x14 + ";");
            log("code[" + totalInstructions * 4 + "+3] = " + 0x14 + ";");
            writer.close();
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
        }
    }
}
/*
 * unsigned char code[12] __attribute__((aligned(0x1000))) ; void c_entry() { code[0] = 0xff; code[1] = 0x90; code[2] =
 * 0xa0; code[3] = 0xe3; code[4] = 0xff; code[5] = 0xaf;// r10? code[6] =0x4f; code[7] = 0xe3; // do load or r9 twice
 * code[8] = 0xfe; code[9] = 0xff; code[10] = 0xff; code[11] = 0xea;
 */
