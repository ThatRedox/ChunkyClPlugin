package chunkycl;

import static java.lang.Math.PI;
import static org.jocl.CL.*;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.apache.commons.math3.util.FastMath;
import org.jocl.*;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

import se.llbit.chunky.PersistentSettings;
import se.llbit.chunky.block.Block;
import se.llbit.chunky.chunk.BlockPalette;
import se.llbit.chunky.renderer.scene.*;
import se.llbit.chunky.resources.Texture;
import se.llbit.chunky.world.Material;
import se.llbit.log.Log;
import se.llbit.math.*;
import se.llbit.math.primitive.Primitive;
import se.llbit.math.primitive.TexturedTriangle;
import se.llbit.util.TaskTracker;

public class GpuRayTracer {
    private cl_mem octreeDepth = null;
    private cl_mem octreeData = null;
    private cl_mem voxelLength = null;
    private cl_mem transparentArray = null;
    private cl_mem transparentLength = null;
    private cl_mem blockTextures = null;
    private cl_mem blockData = null;
    private cl_mem grassTextures = null;
    private cl_mem foliageTextures = null;
    private cl_mem sunIndex = null;
    private cl_mem skyTexture = null;
    private cl_mem entityData = null;
    private cl_mem entityTrigs = null;
    private cl_mem bvhTextures = null;

    private int skyTextureResolution = 128;
    private final float[] skyImage = new float[skyTextureResolution * skyTextureResolution * 4];

    private cl_program program;
    private cl_kernel kernel;

    private cl_context context;
    private cl_command_queue commandQueue;

    public final int[] version;
    public final cl_device_id[] devices;

    private final String[] grassBlocks = new String[]{"minecraft:grass_block", "minecraft:grass",
            "minecraft:tall_grass", "minecraft:fern", "minecraft:sugarcane"};
    private final String[] foliageBlocks = new String[]{"minecraft:oak_leaves", "minecraft:dark_oak_leaves",
            "minecraft:jungle_leaves", "minecraft:acacia_leaves", "minecraft_vines"};

    private final String[] constantTintNames = new String[]{"minecraft:birch_leaves", "minecraft:spruce_leaves",
            "minecraft:lily_pad"};
    private final int[] constantTintColors = new int[]{0xFF80A755, 0xFF619961, 0xFF208030};

    private static String programSource;

    private static GpuRayTracer tracer = null;

    @SuppressWarnings("deprecation")
    private GpuRayTracer() {
        // The platform, device type and device number
        final long deviceType = CL_DEVICE_TYPE_ALL;
        final int deviceIndex = PersistentSettings.settings.getInt("clDevice", 0);

        // Load program source
        InputStream programStream = GpuRayTracer.class.getClassLoader().getResourceAsStream("rayTracer.cl");
        assert programStream != null;
        Scanner s = new Scanner(programStream).useDelimiter("\\A");
        programSource = s.hasNext() ? s.next() : "";

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

        // Create the program
        program = clCreateProgramWithSource(context, 1, new String[] {programSource},
                null, null);

        // Build the program
        try {
            clBuildProgram(program, 0, null, null, null, null);
        } catch (CLException e) {
            if (e.getStatus() == CL_BUILD_PROGRAM_FAILURE) {
                // Obtain the length of the string that will be queried
                long[] size = new long[1];
                clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, 0, null, size);

                // Create a buffer of the appropriate size and fill it with the info
                byte[] buffer = new byte[(int)size[0]];
                clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, buffer.length, Pointer.to(buffer), null);

                // Create a string from the buffer (excluding the trailing \0 byte)
                System.err.println(new String(buffer, 0, buffer.length-1));
            }

            throw e;
        }

        // Create the kernel
        kernel = clCreateKernel(program, "rayTracer", null);

        // Preallocate sky texture
        cl_image_format format = new cl_image_format();
        format.image_channel_data_type = CL_FLOAT;
        format.image_channel_order = CL_RGBA;

        cl_image_desc desc = new cl_image_desc();
        desc.image_type = CL_MEM_OBJECT_IMAGE2D;
        desc.image_width = skyTextureResolution;
        desc.image_height = skyTextureResolution;

        skyTexture = clCreateImage(context, CL_MEM_READ_ONLY,
                format, desc, null, null);
    }

    public static GpuRayTracer getTracer() {
        if (tracer == null) {
            synchronized (GpuRayTracer.class) {
                if (tracer == null) {
                    tracer = new GpuRayTracer();
                }
            }
        }
        return tracer;
    }

    @SuppressWarnings("unchecked")
    public void load(Scene scene, TaskTracker.Task renderTask) {
        Octree octree;
        int[] treeData;

        // Free opencl memory if applicable
        if (this.octreeData != null) {
            clReleaseMemObject(this.octreeData);
            clReleaseMemObject(this.voxelLength);
            clReleaseMemObject(this.transparentArray);
            clReleaseMemObject(this.transparentLength);
            clReleaseMemObject(this.blockTextures);
            clReleaseMemObject(this.blockData);
            clReleaseMemObject(this.grassTextures);
            clReleaseMemObject(this.foliageTextures);
            clReleaseMemObject(this.sunIndex);
            clReleaseMemObject(this.entityData);
            clReleaseMemObject(this.entityTrigs);
            clReleaseMemObject(this.bvhTextures);
        }

        if (renderTask != null) {
            renderTask.update("Loading Octree into GPU", 4, 0);
        }

        // Obtain octree through reflection
        try {
            Field worldOctree = scene.getClass().getDeclaredField("worldOctree");
            worldOctree.setAccessible(true);
            octree = (Octree) worldOctree.get(scene);

            Field worldOctreeImplementation = octree.getClass().getDeclaredField("implementation");
            worldOctreeImplementation.setAccessible(true);
            PackedOctree packedWorldOctree = (PackedOctree) worldOctreeImplementation.get(octree);

            Field worldOctreeTreeData = packedWorldOctree.getClass().getDeclaredField("treeData");
            worldOctreeTreeData.setAccessible(true);
            treeData = (int[]) worldOctreeTreeData.get(packedWorldOctree);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return;
        }

        // Load bounds into memory
        this.octreeDepth = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int, Pointer.to(new int[] {octree.getDepth()}), null);

        // Load octree into texture memory for performance reasons
        // Octree data taken from packed octree is turned into a 8192 x (x) image
        // Data is loaded into the rgba channels to maximize efficiency
        cl_image_format format = new cl_image_format();
        format.image_channel_data_type = CL_SIGNED_INT32;
        format.image_channel_order = CL_RGBA;

        int[] treeDataCopy = new int[(treeData.length/8192 + 1) * 8192];
        System.arraycopy(treeData, 0, treeDataCopy, 0, treeData.length);

        cl_image_desc desc = new cl_image_desc();
        desc.image_type = CL_MEM_OBJECT_IMAGE2D;
        desc.image_width = Math.min(treeData.length/4, 8192);
        desc.image_height = treeData.length / 8192 / 4 + 1;

        this.octreeData = clCreateImage(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                format, desc, Pointer.to(treeDataCopy), null);

        this.voxelLength = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int, Pointer.to(new int[] {treeData.length}), null);

        if (renderTask != null) {
            renderTask.update("Loading blocks into GPU", 4, 1);
        }

        // Create transparent block table
        List<Integer> transparentList = new LinkedList<>();
        List<Block> blockPalette;
        BlockPalette palette = scene.getPalette();

        // Get block palette through reflection
        try {
            Field blockPaletteList = palette.getClass().getDeclaredField("palette");
            blockPaletteList.setAccessible(true);
            blockPalette = (List<Block>) blockPaletteList.get(palette);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return;
        }

        // Build transparent block list
        for (int i = 0; i < blockPalette.size(); i++) {
            if (palette.get(i).invisible)
                transparentList.add(i);
        }

        // Convert transparent block list into array
        int[] transparent = new int[transparentList.size()];
        for (int i = 0; i < transparent.length; i++) {
            transparent[i] = transparentList.remove(0);
        }

        // Load transparent block list onto gpu as array.
        // Size is relatively small so there is no need to load it as a texture
        this.transparentArray = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_int * transparent.length,
                Pointer.to(transparent), null);

        this.transparentLength = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, Sizeof.cl_int,
                Pointer.to(new int[] {transparent.length}), null);

        if (renderTask != null) {
            renderTask.update("Loading Block Textures into GPU", 4, 2);
        }

        // Load biome and foliage tinting
        // Fetch the textures with reflection
        int bounds = 1 << octree.getDepth();

        int[] grassTexture = new int[bounds * bounds * 4];
        for (int i = 0; i < bounds*2; i++) {
            for (int j = 0; j < bounds*2; j++) {
                float[] color = scene.getGrassColor(j-bounds, i-bounds);
                grassTexture[i*bounds*2 + j] = (int)(256*color[0]) << 16 | (int)(256*color[1]) << 8 | (int)(256*color[2]);
            }
        }

        int[] foliageTexture = new int[bounds * bounds * 4];
        for (int i = 0; i < bounds*2; i++) {
            for (int j = 0; j < bounds*2; j++) {
                float[] color = scene.getFoliageColor(j-bounds, i-bounds);
                foliageTexture[i*bounds*2 + j] = (int)(256*color[0]) << 16 | (int)(256*color[1]) << 8 | (int)(256*color[2]);
            }
        }

        format.image_channel_data_type = CL_UNSIGNED_INT32;
        format.image_channel_order = CL_RGBA;
        desc.image_type = CL_MEM_OBJECT_IMAGE2D;
        desc.image_width = bounds / 2;
        desc.image_height = bounds * 2L;

        this.grassTextures = clCreateImage(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, format, desc,
                Pointer.to(grassTexture), null);
        this.foliageTextures = clCreateImage(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, format, desc,
                Pointer.to(foliageTexture), null);

        // Load all block textures into GPU texture memory
        // Load block texture data directly into an array which is dynamically sized for non-full blocks
        Texture stoneTexture = blockPalette.get(palette.stoneId).getTexture(0);
        int[] blockTexturesArray = new int[stoneTexture.getData().length * blockPalette.size()];
        int[] blockIndexesArray = new int[blockPalette.size() * 4];
        int index = 0;
        for (int i = 0; i < blockPalette.size(); i++) {
            Block block = blockPalette.get(i);
            Texture texture = block.getTexture(0);
            int[] textureData = texture.getData();

            // Resize array if necessary
            if (index + textureData.length > blockTexturesArray.length) {
                int[] tempCopyArray = new int[blockTexturesArray.length];
                System.arraycopy(blockTexturesArray, 0, tempCopyArray, 0, blockTexturesArray.length);
                blockTexturesArray = new int[blockTexturesArray.length + 4*textureData.length];
                System.arraycopy(tempCopyArray, 0, blockTexturesArray, 0, tempCopyArray.length);
            }

            // Add block texture data
            blockIndexesArray[i * 4] = index;

            // Check if block is tinted
            String blockName = blockPalette.get(i).name;
            if (Arrays.stream(constantTintNames).anyMatch(blockName::equals)) {
                int tintIndex = 0;
                for (int j = 0; j < constantTintNames.length; j++) {
                    if (constantTintNames[j].equals(blockName)) {
                        tintIndex = j;
                        break;
                    }
                }
                int tintColor = constantTintColors[tintIndex];

                // TODO: Simplify this with existing function?
                double tA = FastMath.pow((0xFF & (tintColor >>> 24)) / 256.0, Scene.DEFAULT_GAMMA);
                double tR = FastMath.pow((0xFF & (tintColor >>> 16)) / 256.0, Scene.DEFAULT_GAMMA);
                double tG = FastMath.pow((0xFF & (tintColor >>> 8)) / 256.0, Scene.DEFAULT_GAMMA);
                double tB = FastMath.pow((0xFF & tintColor) / 256.0, Scene.DEFAULT_GAMMA);

                // Multiply the texture by the tint
                for (int j = 0; j < textureData.length; j++) {
                    int blockColor = textureData[j];

                    int bA = 0xFF & (blockColor >>> 24);
                    int bR = 0xFF & (blockColor >>> 16);
                    int bG = 0xFF & (blockColor >>> 8);
                    int bB = 0xFF & blockColor;

                    int color = (int)(bA * tA) << 24 | (int)(bR * tR) << 16 | (int)(bG * tG) << 8 | (int)(bB * tB);
                    blockTexturesArray[index + j] = color;
                }
            } else {
                System.arraycopy(textureData, 0, blockTexturesArray, index, textureData.length);
            }
            index += textureData.length;

            // Include block information in auxiliary array
            blockIndexesArray[i*4 + 1] = (int) (block.emittance * scene.getEmitterIntensity() * 256);
            blockIndexesArray[i*4 + 2] = (int) (block.specular * 256);

            // Biome tint flag
            if (Arrays.stream(grassBlocks).anyMatch(blockName::equals)) {
                blockIndexesArray[i*4 + 3] = 1;
            } else if (Arrays.stream(foliageBlocks).anyMatch(blockName::equals)) {
                blockIndexesArray[i*4 + 3] = 2;
            } else {
                blockIndexesArray[i*4 + 3] = 0;
            }

            // x = index, y/256 = emittance, z/256 = specular
        }

        // Add the sun
        int[] textureData = Sun.texture.getData();
        // Resize array if necessary
        if (index + textureData.length > blockTexturesArray.length) {
            int[] tempCopyArray = new int[blockTexturesArray.length];
            System.arraycopy(blockTexturesArray, 0, tempCopyArray, 0, blockTexturesArray.length);
            blockTexturesArray = new int[blockTexturesArray.length + 4*textureData.length];
            System.arraycopy(tempCopyArray, 0, blockTexturesArray, 0, tempCopyArray.length);
        }
        System.arraycopy(textureData, 0, blockTexturesArray, index, textureData.length);
        int sunIndex = index;
        index += textureData.length;


        // Copy block texture data into fitted array to prevent Segfaults
        int[] blockTexturesArrayCopy = new int[(blockTexturesArray.length/8192/3 + 1) * 8192 * 3];
        System.arraycopy(blockTexturesArray, 0, blockTexturesArrayCopy, 0, blockTexturesArray.length);

        // Load arrays as images.
        format.image_channel_data_type = CL_UNSIGNED_INT32;
        format.image_channel_order = CL_RGBA;

        desc.image_type = CL_MEM_OBJECT_IMAGE2D;
        desc.image_width = Math.min(blockTexturesArray.length/4, 8192);
        desc.image_height = blockTexturesArray.length / 8192 / 4 + 1;

        blockTextures = clCreateImage(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, format, desc,
                Pointer.to(blockTexturesArrayCopy), null);

        format.image_channel_data_type = CL_SIGNED_INT32;
        format.image_channel_order = CL_RGBA;

        desc.image_type = CL_MEM_OBJECT_IMAGE1D;
        desc.image_width = blockIndexesArray.length / 4;
        blockData = clCreateImage(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, format, desc,
                Pointer.to(blockIndexesArray), null);

        this.sunIndex = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int, Pointer.to(new int[] {sunIndex}), null);

        if (renderTask != null) {
            renderTask.update("Loading BVH", 4, 3);
        }

        BVH mainBvh, actorBvh;
        try {
            Field mainBvhField = scene.getClass().getDeclaredField("bvh");
            mainBvhField.setAccessible(true);
            mainBvh = (BVH) mainBvhField.get(scene);

            Field actorBvhField = scene.getClass().getDeclaredField("actorBvh");
            actorBvhField.setAccessible(true);
            actorBvh = (BVH) actorBvhField.get(scene);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
            return;
        }

        FloatArrayList entityArray = new FloatArrayList();
        FloatArrayList entityTrigs = new FloatArrayList();
        IntArrayList entityTextures = new IntArrayList();
        Map<Material, Integer> materialIndexes = new IdentityHashMap<>();

        entityArray.add(0); // Second child index
        entityArray.add(0); // 0 primitives
        packAabb(new AABB(FastMath.min(mainBvh.getRoot().bb.xmin, actorBvh.getRoot().bb.xmin),
                          FastMath.max(mainBvh.getRoot().bb.xmax, actorBvh.getRoot().bb.xmax),
                          FastMath.min(mainBvh.getRoot().bb.ymin, actorBvh.getRoot().bb.ymin),
                          FastMath.max(mainBvh.getRoot().bb.ymax, actorBvh.getRoot().bb.ymax),
                          FastMath.min(mainBvh.getRoot().bb.zmin, actorBvh.getRoot().bb.zmin),
                          FastMath.max(mainBvh.getRoot().bb.zmax, actorBvh.getRoot().bb.zmax)), entityArray);

        packBvh(mainBvh.getRoot(), entityArray, entityTrigs, entityTextures, materialIndexes);
        entityArray.set(0, entityArray.size());
        packBvh(actorBvh.getRoot(), entityArray, entityTrigs, entityTextures, materialIndexes);

        format.image_channel_data_type = CL_FLOAT;
        format.image_channel_order = CL_RGBA;

        desc.image_type = CL_MEM_OBJECT_IMAGE2D;
        desc.image_width = 8192;
        desc.image_height = entityArray.size() / 8192 / 4 + 1;

        float[] entityArrayFloats = new float[(entityArray.size()/8192 + 1) * 8192];
        entityArray.toArray(entityArrayFloats);

        this.entityData = clCreateImage(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, format, desc,
                Pointer.to(entityArrayFloats), null);

        desc.image_height = entityTrigs.size() / 8192 / 4 + 1;
        float[] entityTrigsArray = new float[(entityTrigs.size()/8192 + 1) * 8192];
        entityTrigs.toArray(entityTrigsArray);

        this.entityTrigs = clCreateImage(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, format, desc,
                Pointer.to(entityTrigsArray), null);

        format.image_channel_data_type = CL_UNSIGNED_INT32;
        format.image_channel_order = CL_RGBA;

        desc.image_height = entityTextures.size() / 8192 / 4 + 1;
        int[] entityTexturesArray = new int[(entityTextures.size()/8192 + 1) * 8192];
        entityTextures.toArray(entityTexturesArray);

        this.bvhTextures = clCreateImage(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, format, desc,
                Pointer.to(entityTexturesArray), null);

        if (renderTask != null) {
            renderTask.update("Loading GPU", 4, 4);
        }
    }

    /** Generate sky. If mode is true = Nishita, false = Preetham */
    public void generateSky(Scene scene) {
        Ray ray = new Ray();
        Sky sky = scene.sky();

        // Get skycache resolution through reflection
        try {
            Field skyCache = sky.getClass().getDeclaredField("skyCache");
            skyCache.setAccessible(true);
            SkyCache cache = (SkyCache) skyCache.get(sky);
            skyTextureResolution = cache.getSkyResolution();
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }

        SimulatedSky simSky = sky.getSimulatedSky();
        boolean simulated = sky.getSkyMode() == Sky.SkyMode.SIMULATED;

        for (int i = 0; i < skyTextureResolution; i++) {
            for (int j = 0; j < skyTextureResolution; j++) {
                double theta = ((double) i / skyTextureResolution) * 2 * PI;
                double phi = ((double) j / skyTextureResolution) * PI - PI/2;
                double r = FastMath.cos(phi);
                ray.d.set(FastMath.cos(theta) * r, FastMath.sin(phi), FastMath.sin(theta) * r);

                Vector3 color;
                if (simulated) {
                    color = simSky.calcIncidentLight(ray);
                } else {
                    sky.getSkyDiffuseColorInner(ray);
                    color = new Vector3(ray.color.x, ray.color.y, ray.color.z);
                }

                skyImage[(j*skyTextureResolution + i) * 4 + 0] = (float) color.x;
                skyImage[(j*skyTextureResolution + i) * 4 + 1] = (float) color.y;
                skyImage[(j*skyTextureResolution + i) * 4 + 2] = (float) color.z;
            }
        }

        clEnqueueWriteImage(commandQueue, skyTexture, CL_TRUE, new long[] {0, 0, 0},
                new long[] {skyTextureResolution, skyTextureResolution, 1}, 0, 0,
                Pointer.to(skyImage), 0, null, null);
    }

    public float[] rayTrace(Vector3 origin, float[] rayDirs, float[] rayJitter, Random random, int rayDepth, boolean preview, Scene scene, int drawDepth, boolean drawEntities) {
        // Load if necessary
        if (octreeData == null) {
            load(scene, null);
        }

        // Results array
        float[] rayRes = new float[rayDirs.length];

        float[] rayPos = new float[3];
        rayPos[0] = (float) origin.x;
        rayPos[1] = (float) origin.y;
        rayPos[2] = (float) origin.z;

        Sun sun = scene.sun();
        float[] sunPos = new float[3];
        sunPos[0] = (float) (FastMath.cos(sun.getAzimuth()) * FastMath.cos(sun.getAltitude()));
        sunPos[1] = (float) (FastMath.sin(sun.getAltitude()));
        sunPos[2] = (float) (FastMath.sin(sun.getAzimuth()) * FastMath.cos(sun.getAltitude()));

        // Transfer arguments to GPU memory
        cl_mem clRayPos = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_float * rayPos.length, Pointer.to(rayPos), null);
        cl_mem clRayDepth = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int, Pointer.to(new int[] {rayDepth}), null);
        cl_mem clSunPos = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_float * 3, Pointer.to(sunPos), null);
        cl_mem clPreview = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int, Pointer.to(new int[] {preview ? 1 : 0}), null);
        cl_mem clSunIntensity = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_float, Pointer.to(new float[] {(float) sun.getIntensity()}), null);
        cl_mem clDrawDepth = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int, Pointer.to(new int[] {drawDepth}), null);
        cl_mem clDrawEntities = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int, Pointer.to(new int[] {drawEntities ? 1 : 0}), null);
        cl_mem clRayDirs = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_float * rayDirs.length, Pointer.to(rayDirs), null);
        cl_mem clRayJitter = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_float * rayJitter.length, Pointer.to(rayJitter), null);
        cl_mem clRayRes = clCreateBuffer(context, CL_MEM_WRITE_ONLY,
                (long) Sizeof.cl_float * rayRes.length, null, null);
        cl_mem clSeed = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int, Pointer.to(new int[]{random.nextInt()}), null);

        // Set the arguments
        cl_mem[] arguments = {clRayPos, clRayDirs, clRayJitter, octreeDepth, octreeData, voxelLength, transparentArray, transparentLength,
                blockTextures, blockData, clSeed, clRayDepth, clPreview, clSunPos, sunIndex, clSunIntensity, skyTexture, grassTextures, foliageTextures,
                entityData, entityTrigs, bvhTextures, clDrawEntities, clDrawDepth, clRayRes};
        for (int i = 0; i < arguments.length; i++) {
            clSetKernelArg(kernel, i, Sizeof.cl_mem, Pointer.to(arguments[i]));
        }

        // Execute the program
        clEnqueueNDRangeKernel(commandQueue, kernel, 1, null, new long[]{rayRes.length/3},
                null, 0, null, null);

        // Get the results
        try {
            clEnqueueReadBuffer(commandQueue, clRayRes, CL_TRUE, 0, (long) Sizeof.cl_float * rayRes.length,
                    Pointer.to(rayRes), 0, null, null);
        } catch (CLException e) {
            throw e;
        }

        // Clean up
        cl_mem[] releases = {clRayPos, clRayDepth, clSunPos, clPreview, clSunIntensity, clDrawDepth, clDrawEntities, clRayDirs, clRayJitter, clSeed, clRayRes};
        Arrays.stream(releases).forEach(CL::clReleaseMemObject);

        return rayRes;
    }

    public float[] rayTrace(Vector3 origin, Random random, int rayDepth, boolean preview, Scene scene, int drawDepth, boolean drawEntities, RayTraceCache cache) {
        // Load if necessary
        if (octreeData == null) {
            load(scene, null);
        }

        // Results array
        float[] rayRes = new float[cache.length];

        float[] rayPos = new float[3];
        rayPos[0] = (float) origin.x;
        rayPos[1] = (float) origin.y;
        rayPos[2] = (float) origin.z;

        Sun sun = scene.sun();
        float[] sunPos = new float[3];
        sunPos[0] = (float) (FastMath.cos(sun.getAzimuth()) * FastMath.cos(sun.getAltitude()));
        sunPos[1] = (float) (FastMath.sin(sun.getAltitude()));
        sunPos[2] = (float) (FastMath.sin(sun.getAzimuth()) * FastMath.cos(sun.getAltitude()));

        // Transfer arguments to GPU memory
        cl_mem clRayPos = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_float * rayPos.length, Pointer.to(rayPos), null);
        cl_mem clRayDepth = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int, Pointer.to(new int[] {rayDepth}), null);
        cl_mem clSunPos = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_float * 3, Pointer.to(sunPos), null);
        cl_mem clPreview = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int, Pointer.to(new int[] {preview ? 1 : 0}), null);
        cl_mem clSunIntensity = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_float, Pointer.to(new float[] {(float) sun.getIntensity()}), null);
        cl_mem clDrawDepth = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int, Pointer.to(new int[] {drawDepth}), null);
        cl_mem clDrawEntities = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int, Pointer.to(new int[] {drawEntities ? 1 : 0}), null);
        cl_mem clSeed = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int, Pointer.to(new int[]{random.nextInt()}), null);

        // Set the arguments
        cl_mem[] arguments = {clRayPos, cache.clRayDirs, cache.clRayJitter, octreeDepth, octreeData, voxelLength, transparentArray, transparentLength,
                blockTextures, blockData, clSeed, clRayDepth, clPreview, clSunPos, sunIndex, clSunIntensity, skyTexture, grassTextures, foliageTextures,
                entityData, entityTrigs, bvhTextures, clDrawEntities, clDrawDepth, cache.clRayRes};
        for (int i = 0; i < arguments.length; i++) {
            clSetKernelArg(kernel, i, Sizeof.cl_mem, Pointer.to(arguments[i]));
        }

        // Execute the program
        clEnqueueNDRangeKernel(commandQueue, kernel, 1, null, new long[]{rayRes.length/3},
                null, 0, null, null);

        // Get the results
        try {
            clEnqueueReadBuffer(commandQueue, cache.clRayRes, CL_TRUE, 0, (long) Sizeof.cl_float * rayRes.length,
                    Pointer.to(rayRes), 0, null, null);
        } catch (CLException e) {
            throw e;
        }

        // Clean up
        cl_mem[] releases = {clRayPos, clRayDepth, clSunPos, clPreview, clSunIntensity, clDrawDepth, clDrawEntities, clSeed};
        Arrays.stream(releases).forEach(CL::clReleaseMemObject);

        return rayRes;
    }

    public RayTraceCache createCache(float[] rayDirs, float[] rayJitter) {
        return new RayTraceCache(rayDirs, rayJitter);
    }

    public class RayTraceCache {
        protected cl_mem clRayDirs;
        protected cl_mem clRayJitter;
        protected cl_mem clRayRes;
        protected int length;

        protected RayTraceCache(float[] rayDirs, float[] rayJitter) {
            clRayDirs = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                    (long) Sizeof.cl_float * rayDirs.length, Pointer.to(rayDirs), null);
            clRayJitter = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                    (long) Sizeof.cl_float * rayJitter.length, Pointer.to(rayJitter), null);
            clRayRes = clCreateBuffer(context, CL_MEM_WRITE_ONLY,
                    (long) Sizeof.cl_float * rayDirs.length, null, null);

            this.length = rayDirs.length;
        }

        public void release() {
            clReleaseMemObject(clRayDirs);
            clReleaseMemObject(clRayJitter);
            clReleaseMemObject(clRayRes);
        }
    }

    private void packBvh(BVH.Node node, FloatArrayList data, FloatArrayList trigs, IntArrayList textures, Map<Material, Integer> indexes) {
        int index = data.size();
        data.add(0);    // Next node/primitive location
        data.add(0);    // Num primitives
        packAabb(node.bb, data);

        if (node instanceof BVH.Group) {
            data.set(index + 1, 0);         // No primitives
            packBvh(((BVH.Group) node).child1, data, trigs, textures, indexes); // First child
            data.set(index, data.size());   // Second child
            packBvh(((BVH.Group) node).child2, data, trigs, textures, indexes);
        } else if (node instanceof  BVH.Leaf) {
            Primitive[] primitives = node.primitives;
            data.set(index + 1, primitives.length);
            data.set(index, -trigs.size()); // Primitives index (*-1) to reduce array lookup
            packPrimitives(primitives, trigs, textures, indexes);
        } else if (node == null) {
            data.set(index, index+8);
        }
    }

    private void packPrimitives(Primitive[] primitives, FloatArrayList trigs, IntArrayList textures, Map<Material, Integer> indexes) {
        for (Primitive prim : primitives) {
            if (prim instanceof TexturedTriangle) {
                TexturedTriangle triangle = (TexturedTriangle) prim;
                trigs.add(0);   // Textured triangle = ID 0
                packAabb(triangle.bounds, trigs);   // 1-6
                trigs.add((float) triangle.e1.x);   // 7
                trigs.add((float) triangle.e1.y);
                trigs.add((float) triangle.e1.z);
                trigs.add((float) triangle.e2.x);   // 10
                trigs.add((float) triangle.e2.y);
                trigs.add((float) triangle.e2.z);
                trigs.add((float) triangle.o.x);    // 13
                trigs.add((float) triangle.o.y);
                trigs.add((float) triangle.o.z);
                trigs.add((float) triangle.n.x);    // 16
                trigs.add((float) triangle.n.y);
                trigs.add((float) triangle.n.z);
                trigs.add((float) reflectTriangleDouble(triangle, "t1u"));   // 19
                trigs.add((float) reflectTriangleDouble(triangle, "t1v"));
                trigs.add((float) reflectTriangleDouble(triangle, "t2u"));   // 21
                trigs.add((float) reflectTriangleDouble(triangle, "t2v"));
                trigs.add((float) reflectTriangleDouble(triangle, "t3u"));   // 23
                trigs.add((float) reflectTriangleDouble(triangle, "t3v"));
                trigs.add(triangle.doubleSided ? 1 : 0);    // 25
                trigs.add((float) triangle.material.getTexture(0).getWidth());  // 26
                trigs.add((float) triangle.material.getTexture(0).getHeight()); // 27
                trigs.add(triangle.material.emittance);     // 28

                if (!indexes.containsKey(triangle.material)) {
                    indexes.put(triangle.material, textures.size());
                    textures.addAll(IntList.of(triangle.material.getTexture(0).getData()));
                }

                trigs.add(indexes.get(triangle.material));  // 29
            }
        }
    }

    void packAabb(AABB box, FloatArrayList array) {
        array.add((float) box.xmin);
        array.add((float) box.xmax);
        array.add((float) box.ymin);
        array.add((float) box.ymax);
        array.add((float) box.zmin);
        array.add((float) box.zmax);
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

    /** Get a long(array) from OpenCL
     * Based on code from https://github.com/gpu/JOCLSamples/
     * List of available parameter names: https://www.khronos.org/registry/OpenCL/sdk/1.2/docs/man/xhtml/clGetDeviceInfo.html
     *
     * @param device Device to query
     * @param paramName Parameter to query
     * @param numValues Number of values to query
     */
    public static long[] getSizes(cl_device_id device, int paramName, int numValues) {
        // The size of the returned data has to depend on
        // the size of a size_t, which is handled here
        ByteBuffer buffer = ByteBuffer.allocate(
                numValues * Sizeof.size_t).order(ByteOrder.nativeOrder());
        clGetDeviceInfo(device, paramName, Sizeof.size_t * numValues,
                Pointer.to(buffer), null);
        long values[] = new long[numValues];
        if (Sizeof.size_t == 4) {
            for (int i=0; i<numValues; i++) {
                values[i] = buffer.getInt(i * Sizeof.size_t);
            }
        }
        else {
            for (int i=0; i<numValues; i++) {
                values[i] = buffer.getLong(i * Sizeof.size_t);
            }
        }
        return values;
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
        clGetDeviceInfo(device, paramName, Sizeof.cl_int * numValues, Pointer.to(values), null);
        return values;
    }

    private static double reflectTriangleDouble(TexturedTriangle triangle, String field) {
        try {
            Field f = triangle.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return (double) f.get(triangle);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.error(e);
            return 0;
        }
    }
}
