package dev.thatredox.chunkynative.opencl.renderer;

import static org.jocl.CL.*;
import org.jocl.*;

import se.llbit.log.Log;

import java.io.*;
import java.util.*;

public class KernelLoader {
    private KernelLoader() {}

    /**
     * Load the program in the jar resources.
     */
    public static cl_program loadProgram(cl_context context, cl_device_id[] devices) {
        // Load kernel
        String kernel = readResourceFile("kernel/include/rayTracer.cl");
        cl_program renderKernel = clCreateProgramWithSource(context, 1, new String[] { kernel }, null, null);

        // Search for include headers
        HashMap<String, cl_program> headerFiles = new HashMap<>();
        readHeaders(kernel, headerFiles);

        // Load headers
        boolean newHeaders = true;
        while (newHeaders) {
            newHeaders = false;
            HashMap<String, cl_program> newHeaderFiles = new HashMap<>();
            for (Map.Entry<String, cl_program> header : headerFiles.entrySet()) {
                if (header.getValue() == null && header.getKey().endsWith(".h")) {
                    String headerFile = readResourceFile("kernel/include/" + header.getKey());
                    header.setValue(clCreateProgramWithSource(context, 1, new String[] {headerFile}, null, null));
                    readHeaders(headerFile, newHeaderFiles);
                }
            }

            for (String header : newHeaderFiles.keySet()) {
                newHeaders |= headerFiles.putIfAbsent(header, null) == null;
            }
        }

        String[] includeNames = headerFiles.keySet().toArray(new String[0]);
        cl_program[] includePrograms = new cl_program[includeNames.length];
        Arrays.setAll(includePrograms, i -> headerFiles.get(includeNames[i]));

        int code = clCompileProgram(renderKernel, devices.length, devices, "-cl-std=CL1.2 -Werror",
                includePrograms.length, includePrograms, includeNames, null, null);
        if (code != CL_SUCCESS) {
            throw new RuntimeException("Program build failed with error code: " + code);
        }

        cl_program prog = clLinkProgram(context, devices.length, devices, "", 1,
                new cl_program[] { renderKernel }, null, null, null);

//        long[] sizes = new long[10];
//        long[] test = new long[1];
//        clGetProgramInfo(prog, CL_PROGRAM_BINARY_SIZES, Sizeof.size_t*10L, Pointer.to(sizes), test);
//
//        byte[] buffer = new byte[(int) sizes[0]];
//        Pointer ptr = Pointer.to(buffer);
//        clGetProgramInfo(prog, CL_PROGRAM_BINARIES, Sizeof.size_t, Pointer.to(ptr), test);
//        File saveFile = new File(savePath);
//        try (BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(saveFile))) {
//            os.write(buffer);
//        } catch (IOException e) {
//            System.out.println("Error Saving CL binary");
//        }

        return prog;
    }

    protected static void readHeaders(String kernel, HashMap<String, cl_program> headerFiles) {
        Arrays.stream(kernel.split("\\n")).filter(line -> line.startsWith("#include")).forEach(line -> {
            String stripped = line.substring("#include".length()).trim();
            String header = stripped.substring(1, stripped.length() - 1);
            headerFiles.putIfAbsent(header, null);
        });
    }

    protected static String readResourceFile(String file) {
        InputStream fileStream = KernelLoader.class.getClassLoader().getResourceAsStream(file);
        if (fileStream == null) {
            Log.error(String.format("Error loading ChunkyCl, file \"%s\" does not exist.", file));
            throw new IllegalStateException(String.format("File \"%s\" does not exist.", file));
        }
        Scanner s = new Scanner(fileStream).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
}
