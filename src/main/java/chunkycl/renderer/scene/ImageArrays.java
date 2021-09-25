package chunkycl.renderer.scene;

import static org.jocl.CL.*;

import chunkycl.renderer.RendererInstance;
import org.jocl.*;

public class ImageArrays {
    private static cl_image_format fmt = null;
    private static cl_image_desc desc = null;

    public static cl_mem createIntArray(int[] buffer) {
        RendererInstance instance = RendererInstance.get();

        if (fmt == null) {
            fmt = new cl_image_format();
            fmt.image_channel_order = CL_RGBA;
        }
        fmt.image_channel_data_type = CL_SIGNED_INT32;

        if (desc == null) {
            desc = new cl_image_desc();
            desc.image_type = CL_MEM_OBJECT_IMAGE2D;
            desc.image_width = 8192;
        }
        desc.image_height = buffer.length / 8192 / 4 + 1;

        return clCreateImage(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                fmt, desc, Pointer.to(buffer), null);
    }
}
