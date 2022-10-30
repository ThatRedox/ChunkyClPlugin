use crate::rt::math::Vector3;
use crate::rt::octree::Octree;
use crate::rt::ray::{IntersectionRecord, Ray};

pub mod math;
pub mod octree;
pub mod ray;
pub mod aabb;

pub struct PathTracer<'a> {
   pub octree: Octree<'a>,
}

impl <'a> PathTracer<'a> {
   pub fn trace(&self, origin: Vector3, direction: Vector3, seed: u64) -> Vector3 {
      let record = self.octree.closest_intersection(Ray {
         origin,
         direction
      });

      match record {
         None => Vector3::new_zero(),
         Some(record) => Vector3::new_fill(record.distance),
      }
   }
}
