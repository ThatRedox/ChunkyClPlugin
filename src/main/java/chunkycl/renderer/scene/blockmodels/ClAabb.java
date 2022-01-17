package chunkycl.renderer.scene.blockmodels;

import chunkycl.renderer.scene.ClMaterial;
import chunkycl.renderer.scene.ClTextureAtlas;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import se.llbit.chunky.model.AABBModel;
import se.llbit.chunky.model.Tint;
import se.llbit.chunky.resources.Texture;
import se.llbit.math.AABB;

import java.util.concurrent.atomic.AtomicInteger;

public class ClAabb {
    public float xmin, ymin, zmin;
    public float xmax, ymax, zmax;
    public int[] materials = new int[6];
    public int flags = 0;

    public ClAabb(AABB box, Texture[] textures, Tint[] tints, AABBModel.UVMapping[] mappings, float emittance, float specular, float metalness, float roughness, ClTextureAtlas texMap, Object2IntMap<ClMaterial> materials, AtomicInteger materialCounter) {
        this.xmin = (float) box.xmin;
        this.xmax = (float) box.xmax;
        this.ymin = (float) box.ymin;
        this.ymax = (float) box.ymax;
        this.zmin = (float) box.zmin;
        this.zmax = (float) box.zmax;

        for (int i = 0; i < 6; i++) {
            Tint tint = null;
            if (tints != null) tint = tints[i];
            Texture tex = textures[i];
            AABBModel.UVMapping map = null;
            if (mappings != null) map = mappings[i];

            if (tex != null) {
                this.flags |= mapping2Flags(map) << (i * 4);
                this.materials[i] = ClMaterial.getMaterialPointer(
                        new ClMaterial(tex, tint, emittance, specular, metalness, roughness, texMap),
                        materials, materialCounter);
            } else {
                this.flags |= (0b1000) << (i * 4);
            }
        }
    }

    private static int mapping2Flags(AABBModel.UVMapping mapping) {
        int flipU =  0b0100;
        int flipV =  0b0010;
        int swapUV = 0b0001;

        if (mapping == null) {
            return 0;
        }

        switch (mapping) {
            case ROTATE_90:
                return flipV | swapUV;
            case ROTATE_180:
                return flipU | flipV;
            case ROTATE_270:
                return flipU | swapUV;
            case FLIP_U:
                return flipU;
            case FLIP_V:
                return flipV;
            default:
            case NONE:
                return 0;
        }
    }

    public int[] pack() {
        int[] out = new int[13];
        out[0] = Float.floatToIntBits(xmin);
        out[1] = Float.floatToIntBits(xmax);
        out[2] = Float.floatToIntBits(ymin);
        out[3] = Float.floatToIntBits(ymax);
        out[4] = Float.floatToIntBits(zmin);
        out[5] = Float.floatToIntBits(zmax);
        out[6] = flags;
        System.arraycopy(materials, 0, out, 7, materials.length);
        return out;
    }
}
