use std::borrow::Borrow;
use std::{mem, slice};
use jni::JNIEnv;
use jni::sys::jintArray;

pub struct ResourcePalette {
    pub resources: Vec<Vec<u32>>,
}

impl ResourcePalette {
    pub fn new() -> Self {
        ResourcePalette { resources: vec![] }
    }

    pub fn put(&mut self, resource: Vec<u32>) -> usize {
        let index = self.resources.len();
        self.resources.push(resource);
        index
    }
}

pub struct Bvh {
    pub bvh: Vec<u32>,
}

pub struct Octree {
    pub octree: Vec<u32>,
    pub depth: u32,
    pub block_mapping: Vec<u32>,
}

impl Octree {
    pub fn as_octree(&self) -> crate::rt::octree::Octree {
        let data: &[u32] = self.octree.borrow();
        let data = unsafe {
            slice::from_raw_parts(data.as_ptr() as *const i32, data.len())
        };

        crate::rt::octree::Octree {
            tree_data: data,
            depth: self.depth,
        }
    }
}

/// Convert a vector of i32 to u32
pub fn vec_i_to_u(v: Vec<i32>) -> Vec<u32> {
    unsafe {
        let mut v = mem::ManuallyDrop::new(v);
        Vec::from_raw_parts(
            v.as_mut_ptr() as *mut u32,
            v.len(),
            v.capacity()
        )
    }
}

pub fn load_int_array(env: &JNIEnv, array: jintArray) -> Vec<i32> {
    let len = env.get_array_length(array).unwrap();
    let mut v = vec![0i32; len as usize];
    env.get_int_array_region(array, 0, v.as_mut()).unwrap();
    v
}
