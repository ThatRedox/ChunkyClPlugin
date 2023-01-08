package dev.thatredox.chunkynative.opencl.context;

import org.jocl.*;
import se.llbit.chunky.PersistentSettings;
import se.llbit.log.Log;

import java.util.ArrayList;

import static org.jocl.CL.*;

public class Device {
    public final int id;
    public final cl_device_id device;
    public final cl_platform_id platform;

    public Device(int id, cl_device_id device, cl_platform_id platform) {
        this.id = id;
        this.device = device;
        this.platform = platform;
    }

    public String name() {
        return getString(CL_DEVICE_NAME);
    }

    public DeviceType type() {
        return DeviceType.get(getDeviceLongs(CL_DEVICE_TYPE, 1)[0]);
    }

    public String versionString() {
        return getString(CL_DEVICE_VERSION);
    }

    public int[] version() {
        String version = versionString();
        int[] out = new int[2];
        out[0] = Integer.parseInt(version.substring(7, 8));
        out[1] = Integer.parseInt(version.substring(9, 10));
        return out;
    }

    public double computeCapacity() {
        double freq = getInts(CL_DEVICE_MAX_CLOCK_FREQUENCY, 1)[0];
        double units = getInts(CL_DEVICE_MAX_COMPUTE_UNITS, 1)[0];
        double simd = type() == DeviceType.GPU ? 32 : 1;    // An educated guess
        return freq * units * simd / 1000.0;
    }

    public enum DeviceType {
        CPU("CPU"), GPU("GPU"), ACCELERATOR("Accelerator"),
        CUSTOM("Custom"), UNKNOWN("Unknown");

        public static DeviceType get(long type) {
            switch ((int) type) {
                case (int) CL_DEVICE_TYPE_CPU:
                    return CPU;
                case (int) CL_DEVICE_TYPE_GPU:
                    return GPU;
                case (int) CL_DEVICE_TYPE_ACCELERATOR:
                    return ACCELERATOR;
                case (int) CL_DEVICE_TYPE_CUSTOM:
                    return CUSTOM;
                default:
                    Log.infof("Unknown device type: (%d)", type);
                    return UNKNOWN;
            }
        }

        public final String name;

        DeviceType(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static Device[] getDevices() {
        final long deviceType = CL_DEVICE_TYPE_ALL;

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
        ArrayList<Device> devices = new ArrayList<>();

        for (cl_platform_id platform : platforms) {
            // Obtain the number of devices for the platform
            try {
                int[] numDevicesArray = new int[1];
                clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
                int numDevices = numDevicesArray[0];

                // Obtain a device ID
                cl_device_id[] platformDevices = new cl_device_id[numDevices];
                clGetDeviceIDs(platform, deviceType, numDevices, platformDevices, null);

                for (cl_device_id device : platformDevices) {
                    Device d = new Device(devices.size(), device, platform);
                    devices.add(d);
                }
            } catch (CLException e) {
                Log.info("Error obtaining device", e);
            }
        }

        return devices.toArray(new Device[0]);
    }

    /**
     * Get the preferred device.
     */
    public static Device getPreferredDevice() {
        int deviceIndex = PersistentSettings.settings.getInt("clDevice", 0);
        Device[] devices = getDevices();
        if (devices.length == 0) {
            throw new CLException("No OpenCL devices!");
        }
        if (deviceIndex > 0 && deviceIndex < devices.length) {
            return devices[deviceIndex];
        }
        return devices[0];
    }

    /**
     * Set the preferred device.
     */
    public static void setPreferredDevice(Device device) {
        PersistentSettings.settings.setInt("clDevice", device.id);
        PersistentSettings.save();
    }

    /**
     * Get a string from OpenCL
     * Based on code from <a href="https://github.com/gpu/JOCLSamples/">JOCL Samples</a>
     *
     * @param paramName Parameter to query
     */
    public String getString(int paramName) {
        // Obtain the length of the string that will be queried
        long[] size = new long[1];
        clGetDeviceInfo(device, paramName, 0, null, size);

        // Create a buffer of the appropriate size and fill it with the info
        byte[] buffer = new byte[(int)size[0]];
        clGetDeviceInfo(device, paramName, buffer.length, Pointer.to(buffer), null);

        // Create a string from the buffer (excluding the trailing \0 byte)
        return new String(buffer, 0, buffer.length-1);
    }

    /**
     * Get an integer(array) from OpenCL
     * Based on code from <a href="https://github.com/gpu/JOCLSamples/">JOCL Samples</a>
     *
     * @param paramName Parameter to query
     * @param numValues Number of values to query
     */
    public int[] getInts(int paramName, int numValues) {
        int[] values = new int[numValues];
        clGetDeviceInfo(device, paramName, (long) Sizeof.cl_int * numValues, Pointer.to(values), null);
        return values;
    }

    /**
     * Get a long(array) from OpenCL
     * Based on code from <a href="https://github.com/gpu/JOCLSamples/">JOCL Samples</a>
     *
     * @param paramName Parameter to query
     * @param numValues Number of values to query
     */
    public long[] getDeviceLongs(int paramName, int numValues) {
        long[] values = new long[numValues];
        clGetDeviceInfo(device, paramName, (long) Sizeof.cl_long * numValues, Pointer.to(values), null);
        return values;
    }
}
