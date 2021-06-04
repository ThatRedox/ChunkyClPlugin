package chunkycl;

import static org.jocl.CL.*;

import org.jocl.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.Scanner;

public class KernelLoader {
    private KernelLoader() {}

    /**
     * Load the program in the jar resources.
     */
    public static cl_program loadProgram(cl_context context, cl_device_id[] devices) {
        File[] includeFiles = getResourceFolderFiles("kernel/include/");
        String[] includeNames = new String[includeFiles.length];
        cl_program[] includes = new cl_program[includeFiles.length];
        for (int i = 0; i < includeFiles.length; i++) {
            includeNames[i] = includeFiles[i].getName();
            String program = readFile(includeFiles[i]);
            includes[i] = clCreateProgramWithSource(context, 1, new String[] { program }, null, null);
        }

        String kernel = readFile(getResourceFile("kernel/rayTracer.cl"));
        cl_program program = clCreateProgramWithSource(context, 1, new String[] { kernel }, null, null);
        int code = clCompileProgram(program, devices.length, devices, "",
                includes.length, includes, includeNames, null, null);
        if (code != CL_SUCCESS) {
            throw new RuntimeException("Program build failed with error code: " + code);
        }

        return clLinkProgram(context, devices.length, devices, "", 1,
                new cl_program[] { program }, null, null, null);
    }

    protected static File[] getResourceFolderFiles(String folder) {
        ClassLoader loader = KernelLoader.class.getClassLoader();
        URL url = loader.getResource(folder);
        String path = url.getPath();
        return new File(path).listFiles();
    }

    protected static File getResourceFile(String file) {
        ClassLoader loader = KernelLoader.class.getClassLoader();
        URL url = loader.getResource(file);
        String path = url.getPath();
        return new File(path);
    }

    protected static String readFile(File file) {
        try (Scanner scanner = new Scanner(file)) {
            return scanner.hasNext() ? scanner.useDelimiter("\\A").next() : "";
        } catch (FileNotFoundException e) {
            return null;
        }
    }
}
