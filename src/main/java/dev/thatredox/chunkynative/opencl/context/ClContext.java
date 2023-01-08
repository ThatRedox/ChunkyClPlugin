package dev.thatredox.chunkynative.opencl.context;

import jdk.jpackage.internal.Log;
import org.jocl.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.jocl.CL.*;

public class ClContext {
    public final Device device;
    public final cl_context context;
    public final cl_command_queue queue;
    public final cl_device_id[] deviceArray;

    public ClContext(Device device) {
        this.device = device;
        this.deviceArray = new cl_device_id[] { device.device };

        cl_context_properties contextProperties = new cl_context_properties();
        this.context = clCreateContext(contextProperties, 1, deviceArray,
                null, null, null);


        int[] version = device.version();
        if (version[0] >= 2) {
            cl_queue_properties queueProperties = new cl_queue_properties();
            queue = clCreateCommandQueueWithProperties(context, device.device, queueProperties, null);
        } else {
            queue = createCommandQueueOld(0);
        }

        // Check if version is behind
        if (version[0] <= 1 && version[1] < 2) {
            Log.error("OpenCL 1.2+ required.");
        }
    }

    @SuppressWarnings("deprecation")
    private cl_command_queue createCommandQueueOld(long properties) {
        return clCreateCommandQueue(context, device.device, properties, null);
    }

    /**
     * Load an OpenCL program.
     *
     * @param sourceReader  Function to read source files from filenames.
     * @param kernelName    Kernel entrypoint filename.
     * @return OpenCL program.
     */
    public cl_program loadProgram(Function<String, String> sourceReader, String kernelName) {
        // Load kernel
        String kernel = sourceReader.apply(kernelName);
        cl_program kernelProgram = clCreateProgramWithSource(context, 1, new String[] { kernel },
                null, null);

        // Search for include headers
        HashMap<String, cl_program> headerFiles = new HashMap<>();

        // Fake `opencl.h` header
        headerFiles.put("../opencl.h", clCreateProgramWithSource(context, 1, new String[] { "" },
                null, null));

        // Load headers
        readHeaders(kernel, headerFiles);

        boolean newHeaders = true;
        while (newHeaders) {
            newHeaders = false;
            HashMap<String, cl_program> newHeaderFiles = new HashMap<>();
            for (Map.Entry<String, cl_program> header : headerFiles.entrySet()) {
                if (header.getValue() == null && header.getKey().endsWith(".h")) {
                    String headerFile = sourceReader.apply(header.getKey());
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

        int code = clCompileProgram(kernelProgram, 1, deviceArray, "-cl-std=CL1.2 -Werror",
                includePrograms.length, includePrograms, includeNames, null, null);
        if (code != CL_SUCCESS) {
            throw new RuntimeException("Program build failed with error code: " + code);
        }

        return clLinkProgram(context, 1, deviceArray, "", 1,
                new cl_program[] { kernelProgram }, null, null, null);
    }

    protected static void readHeaders(String kernel, HashMap<String, cl_program> headerFiles) {
        Arrays.stream(kernel.split("\\n")).filter(line -> line.startsWith("#include")).forEach(line -> {
            String stripped = line.substring("#include".length()).trim();
            String header = stripped.substring(1, stripped.length() - 1);
            headerFiles.putIfAbsent(header, null);
        });
    }
}
