package chunkycl;

import se.llbit.math.Ray;

public class RayCl {
    enum TYPE {
        ROOT,
        DIFFUSE,
        SPECULAR
    }

    private boolean status = false;
    private boolean intersect = false;

    private final Ray ray;

    private final RayCl parent;

    private final TYPE type;

    RayCl(Ray ray, RayCl parent, TYPE type) {
        this.ray = ray;
        this.parent = parent;
        this.type = type;
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
}
