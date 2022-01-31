package dev.thatredox.chunkynative.opencl.renderer.scene;

import dev.thatredox.chunkynative.common.export.texture.AbstractTextureLoader;
import se.llbit.chunky.PersistentSettings;
import se.llbit.chunky.model.Tint;
import se.llbit.chunky.resources.Texture;
import se.llbit.chunky.world.Material;
import se.llbit.log.Log;
import se.llbit.math.ColorUtil;

import java.util.Objects;

public class ClMaterial {
    public static final int MATERIAL_SIZE = 6;

    public boolean hasColorTexture;
    public boolean hasNormalEmittanceTexture;
    public boolean hasSpecularMetalnessRoughnessTexture;

    public int blockTint;

    public long colorTexture;
    public int normalEmittanceTexture;
    public int specularMetalnessRoughnessTexture;

    public ClMaterial(Material material, Tint tint, AbstractTextureLoader texMap) {
        this(material.texture, tint, material.emittance, material.specular, material.metalness, material.roughness, texMap);
    }

    public ClMaterial(Texture texture, Tint tint, float emittance, float specular, float metalness, float roughness, AbstractTextureLoader texMap) {
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

        this.colorTexture = this.hasColorTexture ? texMap.get(texture).get() : texture.getAvgColor();
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
        packed[1] = (int) (this.colorTexture >>> 32);
        packed[2] = this.blockTint;
        packed[3] = (int) this.colorTexture;
        packed[4] = this.normalEmittanceTexture;
        packed[5] = this.specularMetalnessRoughnessTexture;
        return packed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClMaterial that = (ClMaterial) o;
        return hasColorTexture == that.hasColorTexture && hasNormalEmittanceTexture == that.hasNormalEmittanceTexture && hasSpecularMetalnessRoughnessTexture == that.hasSpecularMetalnessRoughnessTexture && blockTint == that.blockTint && colorTexture == that.colorTexture && normalEmittanceTexture == that.normalEmittanceTexture && specularMetalnessRoughnessTexture == that.specularMetalnessRoughnessTexture;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hasColorTexture, hasNormalEmittanceTexture, hasSpecularMetalnessRoughnessTexture, blockTint, colorTexture, normalEmittanceTexture, specularMetalnessRoughnessTexture);
    }
}
