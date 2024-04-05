package dev.thatredox.chunkynative.common.state;

import dev.thatredox.chunkynative.util.Reflection;
import se.llbit.chunky.renderer.scene.sky.SimulatedSky;
import se.llbit.chunky.renderer.scene.sky.Sky;
import se.llbit.chunky.renderer.scene.sky.Sun;
import se.llbit.chunky.resources.Texture;
import se.llbit.math.Matrix3;
import se.llbit.math.Vector3;
import se.llbit.math.Vector4;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SkyState {
    private final Texture skymap;
    private final Texture[] skybox;
    private final String skymapFileName;
    private final String[] skyboxFileName;
    private final Matrix3 rotation;
    private final boolean mirrored;
    private final double skylightModifier;
    private final List<Vector4> gradient;
    private final Vector3 color;
    private final Sky.SkyMode mode;
    private final SimulatedSky simulatedSkyMode;
    private final double horizonOffset;
    private final double sunIntensity;
    private final double sunAzimuth;
    private final double sunAltitude;
    private final boolean drawSun;
    private final Vector3 sunColor;

    public SkyState(Sky sky, Sun sun) {
        skymap = Reflection.getFieldValue(sky, "skymap", Texture.class);
        skybox = Reflection.getFieldValue(sky, "skybox", Texture[].class).clone();
        skymapFileName = Reflection.getFieldValue(sky, "skymapFileName", String.class);
        skyboxFileName = Reflection.getFieldValue(sky, "skyboxFileName", String[].class).clone();
        mirrored = Reflection.getFieldValue(sky, "mirrored", Boolean.class);
        skylightModifier = Reflection.getFieldValue(sky, "skyLightModifier", Double.class);
        List<?> gradientList = Reflection.getFieldValue(sky, "gradient", List.class);
        gradient = gradientList.stream().map(i -> new Vector4((Vector4) i)).collect(Collectors.toList());
        color = new Vector3(Reflection.getFieldValue(sky, "color", Vector3.class));
        mode = Reflection.getFieldValue(sky, "mode", Sky.SkyMode.class);
        simulatedSkyMode = Reflection.getFieldValue(sky, "simulatedSkyMode", SimulatedSky.class);
        horizonOffset = Reflection.getFieldValue(sky, "horizonOffset", Double.class);
        sunIntensity = sun.getIntensity();
        sunAzimuth = sun.getAzimuth();
        sunAltitude = sun.getAltitude();
        drawSun = sun.drawTexture();
        sunColor = new Vector3(sun.getColor());

        rotation = new Matrix3();
        Matrix3 rot = Reflection.getFieldValue(sky, "rotation", Matrix3.class);
        Reflection.copyPublic(rot, rotation);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SkyState skyState = (SkyState) o;

        if (!Reflection.equalsPublic(skyState.rotation, rotation)) return false;
        if (mirrored != skyState.mirrored) return false;
        if (Double.compare(skyState.skylightModifier, skylightModifier) != 0) return false;
        if (Double.compare(skyState.horizonOffset, horizonOffset) != 0) return false;
        if (Double.compare(skyState.sunIntensity, sunIntensity) != 0) return false;
        if (Double.compare(skyState.sunAzimuth, sunAzimuth) != 0) return false;
        if (Double.compare(skyState.sunAltitude, sunAltitude) != 0) return false;
        if (drawSun != skyState.drawSun) return false;
        if (!Objects.equals(skymap, skyState.skymap)) return false;
        if (!StateUtil.equals(skybox, skyState.skybox, Objects::equals)) return false;
        if (!Objects.equals(skymapFileName, skyState.skymapFileName)) return false;
        if (!StateUtil.equals(skyboxFileName, skyState.skyboxFileName, Objects::equals)) return false;
        if (!StateUtil.equals(gradient, skyState.gradient, StateUtil::equals)) return false;
        if (!StateUtil.equals(color, skyState.color)) return false;
        if (mode != skyState.mode) return false;
        if (!Objects.equals(simulatedSkyMode, skyState.simulatedSkyMode)) return false;
        if (!StateUtil.equals(sunColor, skyState.sunColor)) return false;

        return true;
    }
}
