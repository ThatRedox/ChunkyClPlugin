use std::ops::{Add, Div, Mul, Sub};

pub type Float = f32;

pub const EPSILON: Float = 0.000005;
pub const OFFSET: Float = 0.0001;

#[derive(Copy, Clone)]
pub struct Vector3 {
    pub x: Float,
    pub y: Float,
    pub z: Float,
}

impl Add for Vector3 {
    type Output = Vector3;
    fn add(self, rhs: Self) -> Self::Output {
        Vector3 {
            x: self.x + rhs.x,
            y: self.y + rhs.y,
            z: self.z + rhs.z,
        }
    }
}

impl Sub for Vector3 {
    type Output = Vector3;
    fn sub(self, rhs: Self) -> Self::Output {
        Vector3 {
            x: self.x - rhs.x,
            y: self.y - rhs.y,
            z: self.z - rhs.z,
        }
    }
}

impl Mul for Vector3 {
    type Output = Vector3;
    fn mul(self, rhs: Self) -> Self::Output {
        Vector3 {
            x: self.x * rhs.x,
            y: self.y * rhs.y,
            z: self.z * rhs.z,
        }
    }
}

impl Mul<Float> for Vector3 {
    type Output = Vector3;
    fn mul(self, rhs: Float) -> Self::Output {
        Vector3 {
            x: self.x * rhs,
            y: self.y * rhs,
            z: self.z * rhs,
        }
    }
}

impl Div<Float> for Vector3 {
    type Output = Vector3;
    fn div(self, rhs: Float) -> Self::Output {
        let inv = 1.0 / rhs;
        self * inv
    }
}

impl Vector3 {
    pub fn new(x: Float, y: Float, z: Float) -> Vector3 {
        Vector3 { x, y, z }
    }

    pub fn new_zero() -> Vector3 {
        Vector3::new(0.0, 0.0, 0.0)
    }

    pub fn new_fill(v: Float) -> Vector3 {
        Vector3::new(v, v, v)
    }

    pub fn inverse(&self) -> Vector3 {
        Vector3::new(1.0/self.x, 1.0/self.y, 1.0/self.z)
    }

    pub fn min(a: Vector3, b: Vector3) -> Vector3 {
        Vector3 {
            x: Float::min(a.x, b.x),
            y: Float::min(a.y, b.y),
            z: Float::min(a.z, b.z),
        }
    }

    pub fn max(a: Vector3, b: Vector3) -> Vector3 {
        Vector3 {
            x: Float::max(a.x, b.x),
            y: Float::max(a.y, b.y),
            z: Float::max(a.z, b.z),
        }
    }
}
