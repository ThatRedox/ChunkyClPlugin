use crate::rt::math::{Float, Vector3};


pub struct Aabb {
    pub min: Vector3,
    pub max: Vector3,
}

impl Aabb {
    pub fn new(xmin: Float, xmax: Float, ymin: Float, ymax: Float, zmin: Float, zmax: Float) -> Aabb {
        Aabb {
            min: Vector3::new(xmin, ymin, zmin),
            max: Vector3::new(xmax, ymax, zmax),
        }
    }

    pub fn quick_intersect(&self, origin: Vector3, inv_dir: Vector3) -> Option<Float> {
        // TODO: Vectorize

        let t1s = (self.min - origin) * inv_dir;
        let t2s = (self.max - origin) * inv_dir;

        let tmins = Vector3::min(t1s, t2s);
        let tmaxs = Vector3::max(t1s, t2s);

        let tmin = Float::max(tmins.x, Float::max(tmins.y, tmins.z));
        let tmax = Float::min(tmaxs.x, Float::min(tmaxs.x, tmaxs.z));

        if tmax < tmin {
            None
        } else {
            Some(tmin)
        }
    }

    pub fn exit(&self, origin: Vector3, inv_dir: Vector3) -> Float {
        let t1s = (self.min - origin) * inv_dir;
        let t2s = (self.max - origin) * inv_dir;
        let tmaxs = Vector3::max(t1s, t2s);
        Float::min(tmaxs.x, Float::min(tmaxs.y, tmaxs.z))
    }
}
