package chunkycl.renderer.scene;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import se.llbit.chunky.PersistentSettings;
import se.llbit.chunky.model.Tint;
import se.llbit.chunky.resources.Texture;
import se.llbit.log.Log;
import se.llbit.math.ColorUtil;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class ClMaterial {
    public boolean hasColorTexture;
    public boolean hasNormalEmittanceTexture;
    public boolean hasSpecularMetalnessRoughnessTexture;

    public int blockTint;

    public int textureSize;
    public int colorTexture;
    public int normalEmittanceTexture;
    public int specularMetalnessRoughnessTexture;

    public ClMaterial(Texture texture, Tint tint, float emittance, float specular, float metalness, float roughness, ClTextureAtlas texMap) {
        this.hasColorTexture = !PersistentSettings.getSingleColorTextures();
        this.hasNormalEmittanceTexture = false;
        this.hasSpecularMetalnessRoughnessTexture = false;

        if (tint != null) {
            switch (tint.type) {
                default:
                    Log.warn("Unsupported tint type " + tint.type);
                case NONE:
                    this.blockTint = 0;
                    break;
                case CONSTANT:
                    this.blockTint = ColorUtil.getRGB(tint.tint) | 0xFF000000;
                    break;
                case BIOME_FOLIAGE:
                    this.blockTint = 1 << 24;
                    break;
                case BIOME_GRASS:
                    this.blockTint = 2 << 24;
                    break;
                case BIOME_WATER:
                    this.blockTint = 3 << 24;
                    break;
            }
        } else {
            this.blockTint = 0;
        }

        this.textureSize = (texture.getWidth() << 16) | texture.getHeight();
        this.colorTexture = this.hasColorTexture ? texMap.get(texture).location : texture.getAvgColor();
        this.normalEmittanceTexture = (int) (emittance * 255.0);
        this.specularMetalnessRoughnessTexture = (int) (specular * 255.0) |
                ((int) (metalness * 255.0) << 8) |
                ((int) (roughness * 255.0) << 16);
    }

    public int[] pack() {
        int[] packed = new int[6];
        packed[0] =
                ((this.hasColorTexture ? 1 : 0) << 2) |
                ((this.hasNormalEmittanceTexture ? 1 : 0) << 1) |
                (this.hasSpecularMetalnessRoughnessTexture ? 1 : 0);
        packed[1] = this.textureSize;
        packed[2] = this.blockTint;
        packed[3] = this.colorTexture;
        packed[4] = this.normalEmittanceTexture;
        packed[5] = this.specularMetalnessRoughnessTexture;
        return packed;
    }

    public static int getMaterialPointer(ClMaterial mat, Object2IntMap<ClMaterial> materials, AtomicInteger materialCounter) {
        if (materials.containsKey(mat)) {
            return materials.getInt(mat);
        } else {
            int ptr = materialCounter.getAndIncrement();
            materials.put(mat, ptr);
            return ptr;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClMaterial that = (ClMaterial) o;
        return hasColorTexture == that.hasColorTexture && hasNormalEmittanceTexture == that.hasNormalEmittanceTexture && hasSpecularMetalnessRoughnessTexture == that.hasSpecularMetalnessRoughnessTexture && blockTint == that.blockTint && textureSize == that.textureSize && colorTexture == that.colorTexture && normalEmittanceTexture == that.normalEmittanceTexture && specularMetalnessRoughnessTexture == that.specularMetalnessRoughnessTexture;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hasColorTexture, hasNormalEmittanceTexture, hasSpecularMetalnessRoughnessTexture, blockTint, textureSize, colorTexture, normalEmittanceTexture, specularMetalnessRoughnessTexture);
    }
}
