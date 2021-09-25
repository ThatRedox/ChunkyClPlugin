package chunkycl.renderer;

import static org.jocl.CL.*;
import org.jocl.*;

import se.llbit.chunky.PersistentSettings;
import se.llbit.log.Log;

import java.util.ArrayList;
import java.util.Arrays;

public class RendererInstance {
    public final cl_device_id[] devices;
    public final int[] version;

    public final cl_program program;
    public final cl_context context;
    public final cl_command_queue commandQueue;

    private static RendererInstance instance = null;

    public static RendererInstance get() {
        if (instance == null) {
            instance = new RendererInstance();
        }
        return instance;
    }

    private RendererInstance() {
        final long deviceType = CL_DEVICE_TYPE_ALL;
        final int deviceIndex = PersistentSettings.settings.getInt("clDevice", 0);

        // Enable exceptions
        CL.setExceptionsEnabled(true);

        // Obtain the number of platforms
        int[] numPlatformsArray = new int[1];
        clGetPlatformIDs(0, null, numPlatformsArray);
        int numPlatforms = numPlatformsArray[0];

        // Obtain all platform IDs
        cl_platform_id[] platforms = new cl_platform_id[numPlatforms];
        clGetPlatformIDs(platforms.length, platforms, null);

        // Get list of all devices
        ArrayList<cl_device_id> devices = new ArrayList<>();

        for (cl_platform_id platform : platforms) {
            // Obtain the number of devices for the platform
            int[] numDevicesArray = new int[1];
            clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
            int numDevices = numDevicesArray[0];

            // Obtain a device ID
            cl_device_id[] platformDevices = new cl_device_id[numDevices];
            clGetDeviceIDs(platform, deviceType, numDevices, platformDevices, null);
            devices.addAll(Arrays.asList(platformDevices));
        }

        // Print out all connected devices
        this.devices = devices.toArray(new cl_device_id[0]);
        System.out.println("OpenCL Devices:");
        for (int i = 0; i < devices.size(); i++) {
            System.out.println("  [" + i  + "] " + getString(devices.get(i), CL_DEVICE_NAME));
        }

        // Print out selected device
        cl_device_id device = devices.get(deviceIndex);
        System.out.println("\nUsing: " + getString(device, CL_DEVICE_NAME));

        // Initialize the context properties
        cl_context_properties contextProperties = new cl_context_properties();

        // Create a context for the selected device
        context = clCreateContext( contextProperties, 1, new cl_device_id[]{device},
                null, null, null);

        // Create a command-queue for the selected device
        cl_queue_properties properties = new cl_queue_properties();

        // Get OpenCL version
        this.version = new int[2];
        String versionString = getString(device, CL_DEVICE_VERSION);
        this.version[0] = Integer.parseInt(versionString.substring(7, 8));
        this.version[1] = Integer.parseInt(versionString.substring(9, 10));
        System.out.println("       " + versionString);

        // Create command queue with correct version
        if (this.version[0] >= 2) {
            commandQueue = clCreateCommandQueueWithProperties(
                    context, device, properties, null);
        } else {
            commandQueue = clCreateCommandQueue(
                    context, device, 0, null);
        }

        // Check if version is behind
        if (this.version[0] <= 1 && this.version[1] < 2) {
            Log.error("OpenCL 1.2+ required.");
        }

        // Build the program
        program = KernelLoader.loadProgram(context, new cl_device_id[] { device });
    }

    /** Get a string from OpenCL
     * Based on code from https://github.com/gpu/JOCLSamples/
     * List of available parameter names: https://www.khronos.org/registry/OpenCL/sdk/1.2/docs/man/xhtml/clGetDeviceInfo.html
     *
     * @param device Device to query
     * @param paramName Parameter to query
     */
    public static String getString(cl_device_id device, int paramName)
    {
        // Obtain the length of the string that will be queried
        long[] size = new long[1];
        clGetDeviceInfo(device, paramName, 0, null, size);

        // Create a buffer of the appropriate size and fill it with the info
        byte[] buffer = new byte[(int)size[0]];
        clGetDeviceInfo(device, paramName, buffer.length, Pointer.to(buffer), null);

        // Create a string from the buffer (excluding the trailing \0 byte)
        return new String(buffer, 0, buffer.length-1);
    }

    /** Get an integer(array) from OpenCL
     * Based on code from https://github.com/gpu/JOCLSamples/
     * List of available parameter names: https://www.khronos.org/registry/OpenCL/sdk/1.2/docs/man/xhtml/clGetDeviceInfo.html
     *
     * @param device Device to query
     * @param paramName Parameter to query
     * @param numValues Number of values to query
     */
    public static int[] getInts(cl_device_id device, int paramName, int numValues) {
        int[] values = new int[numValues];
        clGetDeviceInfo(device, paramName, (long) Sizeof.cl_int * numValues, Pointer.to(values), null);
        return values;
    }
}
