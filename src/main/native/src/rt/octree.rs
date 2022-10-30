use crate::rt::aabb::Aabb;
use crate::rt::math::{Float, OFFSET, Vector3};
use crate::rt::ray::{IntersectionRecord, Ray};

pub struct Octree<'a> {
    pub tree_data: &'a[i32],
    pub depth: u32,
}

impl <'a> Octree<'a> {
    pub fn closest_intersection(&self, ray: Ray) -> Option<IntersectionRecord> {
        let mut distance = 0 as Float;

        let inv_dir = ray.direction.inverse();

        {
            // Check if we are in bounds
            let lx = (ray.origin.x as i32) >> self.depth;
            let ly = (ray.origin.y as i32) >> self.depth;
            let lz = (ray.origin.z as i32) >> self.depth;
            if lx != 0 || ly != 0 || lz != 0 {
                // Intersect with the bounds of the octree
                let octree_size = (1 << self.depth) as Float;
                let octree_box = Aabb {
                    min: Vector3::new_zero(),
                    max: Vector3::new_fill(octree_size),
                };
                let dist = octree_box.quick_intersect(ray.origin, inv_dir)?;
                if dist < 0.0 {
                    return None;
                }

                // Successful intersection
                distance += dist + OFFSET;
            }
        }

        loop {
            let pos = ray.origin + (ray.direction * distance);
            let bx = pos.x.floor() as i32;
            let by = pos.y.floor() as i32;
            let bz = pos.z.floor() as i32;

            // Check inbounds
            if (bx >> self.depth) != 0 || (by >> self.depth) != 0 || (bz >> self.depth) != 0 {
                return None;
            }

            // Read the octree
            let mut level = self.depth;
            let mut data = self.tree_data[0];
            while data > 0 {
                level -= 1;
                let lx = 1 & (bx >> level);
                let ly = 1 & (by >> level);
                let lz = 1 & (bz >> level);
                data = self.tree_data[(data + ((lx << 2) | (ly << 1) | lz)) as usize];
            }
            data = -data;
            let lx = bx >> level;
            let ly = by >> level;
            let lz = bz >> level;

            // Get block data if there is an intersection
            if data != 0 {
                return Some(IntersectionRecord { distance })
            }

            // Exit the current leaf
            let leaf_box = Aabb::new(
                (lx << level) as Float, ((lx + 1) << level) as Float,
                (ly << level) as Float, ((ly + 1) << level) as Float,
                (lz << level) as Float, ((lz + 1) << level) as Float,
            );
            distance += leaf_box.exit(pos, inv_dir) + OFFSET;
        }
    }
}
