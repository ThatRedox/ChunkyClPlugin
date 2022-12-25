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
        String kernel = readResourceFile("kernel/rayTracer.cl");
        cl_program renderKernel = clCreateProgramWithSource(context, 1, new String[] { kernel }, null, null);

        // Search for include headers
        HashMap<String, cl_program> headerFiles = new HashMap<>();

        // Fake `opencl.h` header
        headerFiles.put("../opencl.h", clCreateProgramWithSource(context, 1, new String[] {""}, null, null));

        // Load headers
        readHeaders(kernel, headerFiles);

        boolean newHeaders = true;
        while (newHeaders) {
            newHeaders = false;
            HashMap<String, cl_program> newHeaderFiles = new HashMap<>();
            for (Map.Entry<String, cl_program> header : headerFiles.entrySet()) {
                if (header.getValue() == null && header.getKey().endsWith(".h")) {
                    String headerFile = readResourceFile("kernel/" + header.getKey());
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

        return clLinkProgram(context, devices.length, devices, "", 1,
                new cl_program[] { renderKernel }, null, null, null);
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
