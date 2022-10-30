use crate::rt::math::Vector3;
use crate::rt::octree::Octree;

pub mod math;
pub mod octree;

pub struct PathTracer<'a> {
   pub octree: Octree<'a>,
}

impl <'a> PathTracer<'a> {
   pub fn trace(&self, origin: Vector3, direction: Vector3, seed: u64) -> Vector3 {
      direction
   }
}
