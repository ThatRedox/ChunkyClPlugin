package chunkycl;

import se.llbit.math.Ray;
import se.llbit.math.Vector2;
import se.llbit.math.Vector4;

public class RayCl {
    enum TYPE {
        ROOT,
        DIFFUSE,
        SPECULAR,
        TRANSMITTED
    }

    private boolean status = false;
    private boolean intersect = false;

    private final Ray ray;

    private final RayCl parent;

    private final TYPE type;

    private int addEmitted;

    private float emittance = 0;

    private Vector4 indirectEmitterColor = null;

    private Vector2 imageCoords = null;

    RayCl(Ray ray, RayCl parent, TYPE type, int addEmitted) {
        this.ray = ray;
        this.parent = parent;
        this.type = type;
        this.addEmitted = addEmitted;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public boolean getStatus() {
        return status;
    }

    public void setIntersect(boolean intersect) {
        this.intersect = intersect;
    }

    public boolean getIntersect() {
        return intersect;
    }

    public Ray getRay() {
        return ray;
    }

    public RayCl getParent() {
        return parent;
    }

    public TYPE getType() {
        return type;
    }

    public int getAddEmitted() {
        return addEmitted;
    }

    public void setAddEmitted(int addEmitted) {
        this.addEmitted = addEmitted;
    }

    public void setIndirectEmitterColor(Vector4 indirectEmitterColor) {
        this.indirectEmitterColor = indirectEmitterColor;
    }

    public Vector4 getIndirectEmitterColor() {
        return indirectEmitterColor;
    }

    public float getEmittance() {
        return emittance;
    }

    public void setEmittance(float emittance) {
        this.emittance = emittance;
    }

    public void setImageCoords(Vector2 coords) {
        imageCoords = coords;
    }

    public Vector2 getImageCoords() {
        return imageCoords;
    }
}
