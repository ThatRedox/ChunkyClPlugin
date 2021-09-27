package chunkycl.renderer.scene;

import chunkycl.renderer.RendererInstance;
import static org.jocl.CL.*;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jocl.*;
import se.llbit.chunky.resources.Texture;

public class ClTextureArray {
    private cl_mem texture = null;
    private final IntArrayList textures = new IntArrayList();
    private int texSize = -1;

    public int addTexture(Texture tex) {
        int index = textures.size();
        textures.addAll(IntList.of(tex.getData()));
        return index;
    }

    public cl_mem get() {
        if (texture != null && textures.size() == texSize) return texture;
        if (texture != null) clReleaseMemObject(texture);

        texSize = textures.size();
        texture = ImageArrays.createUintArray(textures.toIntArray());
        return texture;
    }

    public void release() {
        clReleaseMemObject(texture);
        texture = null;
    }
}
