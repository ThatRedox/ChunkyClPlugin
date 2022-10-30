use crate::rt::math::{Float, Vector3};

pub struct Ray {
    pub origin: Vector3,
    pub direction: Vector3
}

pub struct IntersectionRecord {
    pub distance: Float
}
