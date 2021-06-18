package chunkycl;

import static org.jocl.CL.*;

import org.jocl.*;
import se.llbit.log.Log;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

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
        ArrayList<String> headerFiles = new ArrayList<>();
        Arrays.stream(kernel.split("\\n")).filter(line -> line.startsWith("#include")).forEach(line -> {
            String stripped = line.substring("#include".length()).trim();
            headerFiles.add(stripped.substring(1, stripped.length()-1));
        });

        // Load headers
        cl_program[] includes = new cl_program[headerFiles.size()];
        for (int i = 0; i < headerFiles.size(); i++) {
            String headerFile = readResourceFile("kernel/include/" + headerFiles.get(i));
            includes[i] = clCreateProgramWithSource(context, 1, new String[] { headerFile }, null, null);
        }

        int code = clCompileProgram(renderKernel, devices.length, devices, "",
                includes.length, includes, headerFiles.toArray(new String[0]), null, null);
        if (code != CL_SUCCESS) {
            throw new RuntimeException("Program build failed with error code: " + code);
        }

        return clLinkProgram(context, devices.length, devices, "", 1,
                new cl_program[] { renderKernel }, null, null, null);
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
