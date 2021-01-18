package chunkycl;

import se.llbit.chunky.block.Air;
import se.llbit.chunky.renderer.EmitterSamplingStrategy;
import se.llbit.chunky.renderer.WorkerState;
import se.llbit.chunky.renderer.scene.*;
import se.llbit.chunky.world.Material;
import se.llbit.math.*;

import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class RayTracerCl implements RayTracer {

    /** Extinction factor for fog rendering. */
    private static final double EXTINCTION_FACTOR = 0.04;

    private static final double fSubSurface = 0.3;

    private static boolean openClInit = false;
    private static Lock openClManage;

    private static PriorityBlockingQueue<RayCl> rtQueue;

    RayTracerCl() {
        if (!openClInit) {
            // Setup queues
            rtQueue =
                    new PriorityBlockingQueue<RayCl>(
                            11,
                            new Comparator<RayCl>() {
                                @Override
                                public int compare(RayCl o1, RayCl o2) {
                                    return o1.getRay().depth - o2.getRay().depth;
                                }
                            });

            openClManage = new ReentrantLock();

            // Load opencl stuff
        }

        openClInit = true;
    }

    /** Path trace the ray. */
    @Override
    public void trace(Scene scene, WorkerState state) {
        Ray startRay = state.ray;
        startRay.setCurrentMaterial(Air.INSTANCE);

        List<RayCl> rayProcess = Collections.synchronizedList(new ArrayList<RayCl>());

        Random random = state.random;

        // Raytrace the first ray
        RayCl rootRay = new RayCl(startRay, null, RayCl.TYPE.ROOT);
        rtAdd(rootRay, rayProcess);

        while (!rtComplete(rayProcess)) {
            // Take over processing if necessary
            if (openClManage.tryLock()) {
                try {
                    rtProcess(scene);
                } finally {
                    openClManage.unlock();
                }
            }

            // See if any rays have finished
            int i = 0;
            while (i < rayProcess.size()) {
                RayCl wrapper = rayProcess.get(i);

                if (wrapper.getStatus()) {
                    boolean hit = false;
                    Ray ray = wrapper.getRay();

                    // Check if ray has exited the scene
                    if (!wrapper.getIntersect()) {
                        if (ray.depth == 0) {
                            // Direct sky hit.
                            if (!scene.transparentSky()) {
                                scene.sky().getSkyColorInterpolated(ray);
                                scene.addSkyFog(ray);
                            }
                        } else if (ray.specular) {
                            // Indirect sky hit - specular color.
                            scene.sky().getSkySpecularColor(ray);
                            scene.addSkyFog(ray);
                        } else {
                            // Indirect sky hit - diffuse color.
                            scene.sky().getSkyColor(ray);
                        }

                        hit = true;
                    } else {
                        Material currentMat = ray.getCurrentMaterial();

                        float pSpecular = currentMat.specular;
                        double pDiffuse = ray.color.w;

                        if (pSpecular > Ray.EPSILON && random.nextFloat() < pSpecular) {
                            Ray reflected = new Ray();
                            reflected.specularReflection(ray, random);

                            if (reflected.depth < scene.getRayDepth()) {
                                rtAdd(new RayCl(reflected, wrapper, RayCl.TYPE.SPECULAR), rayProcess);
                            }
                        } else if (pDiffuse > Ray.EPSILON && random.nextFloat() < pDiffuse) {
                            if (scene.getEmittersEnabled() && currentMat.emittance > Ray.EPSILON) {
                                ray.emittance.x = ray.color.x * ray.color.x * currentMat.emittance * scene.getEmitterIntensity();
                                ray.emittance.y = ray.color.y * ray.color.y * currentMat.emittance * scene.getEmitterIntensity();
                                ray.emittance.z = ray.color.z * ray.color.z * currentMat.emittance * scene.getEmitterIntensity();
                            }

                            Ray reflected = new Ray();
                            reflected.diffuseReflection(ray, random);

                            if (reflected.depth < scene.getRayDepth()) {
                                rtAdd(new RayCl(reflected, wrapper, RayCl.TYPE.DIFFUSE), rayProcess);
                            }
                        }
                    }

                    if (hit && wrapper.getParent() != null) {
                        Ray parent = wrapper.getParent().getRay();

                        if (wrapper.getType() == RayCl.TYPE.DIFFUSE) {
                            parent.color.x = parent.color.x * (ray.color.x + ray.emittance.x);
                            parent.color.y = parent.color.y * (ray.color.y + ray.emittance.y);
                            parent.color.z = parent.color.z * (ray.color.z + ray.emittance.z);
                        } else if (wrapper.getType() == RayCl.TYPE.SPECULAR) {
                            parent.color.x = ray.color.x;
                            parent.color.y = ray.color.y;
                            parent.color.z = ray.color.z;
                        }
                    }

                    rayProcess.remove(i);
                } else {
                    i++;
                }
            }
        }
    }

    private void rtAdd(RayCl ray, List<RayCl> rays) {
        rtQueue.add(ray);
        rays.add(ray);
    }

    private void rtProcess(Scene scene) {
        RayCl ray = rtQueue.poll();

        // Check if all work is done
        if (ray == null) return;

        ray.setIntersect(PreviewRayTracer.nextIntersection(scene, ray.getRay()));
        ray.setStatus(true);
    }

    private static boolean rtComplete(List<RayCl> rays) {
        for (RayCl ray : rays) {
            if (!ray.getStatus()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Path trace the ray in this scene.
     *
     * @param firstReflection {@code true} if the ray has not yet hit the first diffuse or specular
     *     reflection
     */
    public static boolean pathTrace(
            Scene scene, Ray ray, WorkerState state, int addEmitted, boolean firstReflection) {

        boolean hit = false;
        Random random = state.random;
        List<RayCl> rays = new ArrayList<RayCl>();

        while (true) {
            if (ray.depth >= scene.getRayDepth()) {
                break;
            }

            if (!PreviewRayTracer.nextIntersection(scene, ray)) {
                if (ray.depth == 0) {
                    // Direct sky hit.
                    if (!scene.transparentSky()) {
                        scene.sky().getSkyColorInterpolated(ray);
                        scene.addSkyFog(ray);
                        hit = true;
                    }
                } else if (ray.specular) {
                    // Indirect sky hit - specular color.
                    scene.sky().getSkySpecularColor(ray);
                    scene.addSkyFog(ray);
                    hit = true;
                } else {
                    // Indirect sky hit - diffuse color.
                    scene.sky().getSkyColor(ray);
                    // Skip sky fog - likely not noticeable in diffuse reflection.
                    hit = true;
                }
                break;
            }

            Material currentMat = ray.getCurrentMaterial();
            Material prevMat = ray.getPrevMaterial();

            float pSpecular = currentMat.specular;
            double pDiffuse = ray.color.w;

            if (pSpecular > Ray.EPSILON && random.nextFloat() < pSpecular) {
                // Specular reflection.

                firstReflection = false;

                Ray reflected = new Ray();
                reflected.specularReflection(ray, random);

                if (pathTrace(scene, reflected, state, 1, false)) {
                    ray.color.x = reflected.color.x;
                    ray.color.y = reflected.color.y;
                    ray.color.z = reflected.color.z;
                    hit = true;
                }

            } else {

                if (random.nextFloat() < pDiffuse) {
                    // Diffuse reflection.

                    firstReflection = false;

                    Ray reflected = new Ray();

                    float emittance = 0;

                    Vector4 indirectEmitterColor = new Vector4(0, 0, 0, 0);

                    if (scene.getEmittersEnabled()
                            && (!scene.isPreventNormalEmitterWithSampling()
                            || scene.getEmitterSamplingStrategy() == EmitterSamplingStrategy.NONE
                            || ray.depth == 0)
                            && currentMat.emittance > Ray.EPSILON) {

                        emittance = addEmitted;
                        ray.emittance.x =
                                ray.color.x * ray.color.x * currentMat.emittance * scene.getEmitterIntensity();
                        ray.emittance.y =
                                ray.color.y * ray.color.y * currentMat.emittance * scene.getEmitterIntensity();
                        ray.emittance.z =
                                ray.color.z * ray.color.z * currentMat.emittance * scene.getEmitterIntensity();
                        hit = true;
                    } else if (scene.getEmittersEnabled()
                            && scene.getEmitterSamplingStrategy() != EmitterSamplingStrategy.NONE
                            && scene.getEmitterGrid() != null) {
                        // Sample emitter
                        boolean sampleOne = scene.getEmitterSamplingStrategy() == EmitterSamplingStrategy.ONE;
                        if (sampleOne) {
                            Grid.EmitterPosition pos =
                                    scene
                                            .getEmitterGrid()
                                            .sampleEmitterPosition((int) ray.o.x, (int) ray.o.y, (int) ray.o.z, random);
                            if (pos != null) {
                                indirectEmitterColor = sampleEmitter(scene, ray, pos, random);
                            }
                        } else {
                            for (Grid.EmitterPosition pos :
                                    scene
                                            .getEmitterGrid()
                                            .getEmitterPositions((int) ray.o.x, (int) ray.o.y, (int) ray.o.z)) {
                                indirectEmitterColor.scaleAdd(1, sampleEmitter(scene, ray, pos, random));
                            }
                        }
                    }

                    if (scene.getDirectLight()) {
                        reflected.set(ray);
                        scene.sun().getRandomSunDirection(reflected, random);

                        double directLightR = 0;
                        double directLightG = 0;
                        double directLightB = 0;

                        boolean frontLight = reflected.d.dot(ray.n) > 0;

                        if (frontLight
                                || (currentMat.subSurfaceScattering && random.nextFloat() < fSubSurface)) {

                            if (!frontLight) {
                                reflected.o.scaleAdd(-Ray.OFFSET, ray.n);
                            }

                            reflected.setCurrentMaterial(reflected.getPrevMaterial(), reflected.getPrevData());

                            getDirectLightAttenuation(scene, reflected, state);

                            Vector4 attenuation = state.attenuation;
                            if (attenuation.w > 0) {
                                double mult = QuickMath.abs(reflected.d.dot(ray.n));
                                directLightR = attenuation.x * attenuation.w * mult;
                                directLightG = attenuation.y * attenuation.w * mult;
                                directLightB = attenuation.z * attenuation.w * mult;
                                hit = true;
                            }
                        }

                        reflected.diffuseReflection(ray, random);
                        hit = pathTrace(scene, reflected, state, 0, false) || hit;
                        if (hit) {
                            ray.color.x =
                                    ray.color.x
                                            * (emittance
                                            + directLightR * scene.sun().getColor().x
                                            + (reflected.color.x + reflected.emittance.x)
                                            + (indirectEmitterColor.x));
                            ray.color.y =
                                    ray.color.y
                                            * (emittance
                                            + directLightG * scene.sun().getColor().y
                                            + (reflected.color.y + reflected.emittance.y)
                                            + (indirectEmitterColor.y));
                            ray.color.z =
                                    ray.color.z
                                            * (emittance
                                            + directLightB * scene.sun().getColor().z
                                            + (reflected.color.z + reflected.emittance.z)
                                            + (indirectEmitterColor.z));
                        } else if (indirectEmitterColor.x > Ray.EPSILON
                                || indirectEmitterColor.y > Ray.EPSILON
                                || indirectEmitterColor.z > Ray.EPSILON) {
                            hit = true;
                            ray.color.x *= indirectEmitterColor.x;
                            ray.color.y *= indirectEmitterColor.y;
                            ray.color.z *= indirectEmitterColor.z;
                        }

                    } else {
                        reflected.diffuseReflection(ray, random);

                        hit = pathTrace(scene, reflected, state, 0, false) || hit;
                        if (hit) {
                            ray.color.x =
                                    ray.color.x
                                            * (emittance
                                            + (reflected.color.x + reflected.emittance.x)
                                            + (indirectEmitterColor.x));
                            ray.color.y =
                                    ray.color.y
                                            * (emittance
                                            + (reflected.color.y + reflected.emittance.y)
                                            + (indirectEmitterColor.y));
                            ray.color.z =
                                    ray.color.z
                                            * (emittance
                                            + (reflected.color.z + reflected.emittance.z)
                                            + (indirectEmitterColor.z));
                        } else if (indirectEmitterColor.x > Ray.EPSILON
                                || indirectEmitterColor.y > Ray.EPSILON
                                || indirectEmitterColor.z > Ray.EPSILON) {
                            hit = true;
                            ray.color.x *= indirectEmitterColor.x;
                            ray.color.y *= indirectEmitterColor.y;
                            ray.color.z *= indirectEmitterColor.z;
                        }
                    }
                } else {

                    Ray transmitted = new Ray();
                    transmitted.set(ray);
                    transmitted.o.scaleAdd(Ray.OFFSET, transmitted.d);

                    if (pathTrace(scene, transmitted, state, 1, false)) {
                        ray.color.x = ray.color.x * pDiffuse + (1 - pDiffuse);
                        ray.color.y = ray.color.y * pDiffuse + (1 - pDiffuse);
                        ray.color.z = ray.color.z * pDiffuse + (1 - pDiffuse);
                        ray.color.x *= transmitted.color.x;
                        ray.color.y *= transmitted.color.y;
                        ray.color.z *= transmitted.color.z;
                        hit = true;
                    }
                }
            }

            if (hit && prevMat.isWater()) {
                // Render water fog effect.
                if (scene.getWaterVisibility() == 0) {
                    ray.color.scale(0.);
                } else {
                    double a = ray.distance / scene.getWaterVisibility();
                    double attenuation = Math.exp(-a);
                    ray.color.scale(attenuation);
                }
            }

            break;
        }
        if (!hit) {
            ray.color.set(0, 0, 0, 1);
        }

        return hit;
    }

    /**
     * Cast a shadow ray from the intersection point (given by ray) to the emitter at position pos.
     * Returns the contribution of this emitter (0 if the emitter is occluded)
     *
     * @param scene The scene being rendered
     * @param ray The ray that generated the intersection
     * @param pos The position of the emitter to sample
     * @param random RNG
     * @return The contribution of the emitter
     */
    private static Vector4 sampleEmitter(
            Scene scene, Ray ray, Grid.EmitterPosition pos, Random random) {
        Vector4 indirectEmitterColor = new Vector4();
        Ray emitterRay = new Ray();
        emitterRay.set(ray);
        // TODO Sampling a random point on the model would be better than using a random point in the
        // middle of the cube
        Vector3 target =
                new Vector3(
                        pos.x + (random.nextDouble() - 0.5) * pos.radius,
                        pos.y + (random.nextDouble() - 0.5) * pos.radius,
                        pos.z + (random.nextDouble() - 0.5) * pos.radius);
        emitterRay.d.set(target);
        emitterRay.d.sub(emitterRay.o);
        double distance = emitterRay.d.length();
        emitterRay.d.normalize();
        double indirectEmitterCoef = emitterRay.d.dot(emitterRay.n);
        if (indirectEmitterCoef > 0) {
            // Here We need to invert the material.
            // The fact that the dot product is > 0 guarantees that the ray is going away from the surface
            // it just met. This means the ray is going from the block just hit to the previous material
            // (usually air or water)
            // TODO If/when normal mapping is implemented, indirectEmitterCoef will be computed with the
            // mapped normal
            //      but the dot product with the original geometry normal will still need to be computed
            //      to ensure the emitterRay isn't going through the geometry
            Material prev = emitterRay.getPrevMaterial();
            int prevData = emitterRay.getPrevData();
            emitterRay.setPrevMaterial(emitterRay.getCurrentMaterial(), emitterRay.getCurrentData());
            emitterRay.setCurrentMaterial(prev, prevData);
            emitterRay.emittance.set(0, 0, 0);
            emitterRay.o.scaleAdd(Ray.EPSILON, emitterRay.d);
            PreviewRayTracer.nextIntersection(scene, emitterRay);
            if (emitterRay.getCurrentMaterial().emittance > Ray.EPSILON) {
                indirectEmitterColor.set(emitterRay.color);
                indirectEmitterColor.scale(emitterRay.getCurrentMaterial().emittance);
                // TODO Take fog into account
                indirectEmitterCoef *= scene.getEmitterIntensity();
                // Dont know if really realistic but offer better convergence and is better artistically
                indirectEmitterCoef /= Math.max(distance * distance, 1);
            }
        } else {
            indirectEmitterCoef = 0;
        }
        indirectEmitterColor.scale(indirectEmitterCoef);
        return indirectEmitterColor;
    }

    /** Calculate direct lighting attenuation. */
    public static void getDirectLightAttenuation(Scene scene, Ray ray, WorkerState state) {

        Vector4 attenuation = state.attenuation;
        attenuation.x = 1;
        attenuation.y = 1;
        attenuation.z = 1;
        attenuation.w = 1;
        while (attenuation.w > 0) {
            ray.o.scaleAdd(Ray.OFFSET, ray.d);
            if (!PreviewRayTracer.nextIntersection(scene, ray)) {
                break;
            }
            double mult = 1 - ray.color.w;
            attenuation.x *= ray.color.x * ray.color.w + mult;
            attenuation.y *= ray.color.y * ray.color.w + mult;
            attenuation.z *= ray.color.z * ray.color.w + mult;
            attenuation.w *= mult;
            if (ray.getPrevMaterial().isWater()) {
                if (scene.getWaterVisibility() == 0) {
                    attenuation.w = 0;
                } else {
                    double a = ray.distance / scene.getWaterVisibility();
                    attenuation.w *= Math.exp(-a);
                }
            }
        }
    }
}
