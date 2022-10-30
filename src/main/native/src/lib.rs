use jni::JNIEnv;
use jni::objects::{JClass};
use jni::sys::{jint, jintArray, jlong};
use crate::ffi_guard::FFIGuard;
use crate::loading_structs::{Bvh, load_int_array, Octree, ResourcePalette, vec_i_to_u};

mod loading_structs;
mod ffi_guard;

#[allow(clippy::assertions_on_constants)]
const _: () = assert!(usize::BITS <= u64::BITS, "The world is on fire!!!");

#[no_mangle]
pub extern "system" fn Java_dev_thatredox_chunkynative_rust_export_ffi_SynchronizedRustResourcePalette_create(_env: JNIEnv, _class: JClass) -> jlong {
    let palette = ResourcePalette::new();
    let palette = FFIGuard::create(Box::new(palette));
    palette.to_address() as jlong
}

#[no_mangle]
pub extern "system" fn Java_dev_thatredox_chunkynative_rust_export_ffi_SynchronizedRustResourcePalette_drop(_env: JNIEnv, _class: JClass, address: jlong) {
    let palette: FFIGuard<ResourcePalette> = FFIGuard::new(address as usize);
    // Safety: This is synchronized and checked on the Java side.
    unsafe {
        palette.drop_object();
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_thatredox_chunkynative_rust_export_ffi_SynchronizedRustResourcePalette_put(env: JNIEnv, _class: JClass, address: jlong, resource: jintArray) -> jlong {
    let mut palette: FFIGuard<ResourcePalette> = FFIGuard::new(address as usize);

    let resource = vec_i_to_u(load_int_array(env, resource));

    palette.put(resource) as jlong
}

#[no_mangle]
pub extern "system" fn Java_dev_thatredox_chunkynative_rust_export_ffi_SynchronizedRustBvh_create(env: JNIEnv, _class: JClass, bvh: jintArray) -> jlong {
    let bvh = vec_i_to_u(load_int_array(env, bvh));

    let bvh = FFIGuard::create(Box::new(Bvh {
        bvh
    }));
    bvh.to_address() as jlong
}

#[no_mangle]
pub extern "system" fn Java_dev_thatredox_chunkynative_rust_export_ffi_SynchronizedRustBvh_drop(_env: JNIEnv, _class: JClass, address: jlong) {
    let bvh: FFIGuard<Bvh> = FFIGuard::new(address as usize);
    // Safety: This is synchronized and checked on the Java side.
    unsafe {
        bvh.drop_object();
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_thatredox_chunkynative_rust_export_ffi_SynchronizedRustOctree_create(env: JNIEnv, _class: JClass, octree: jintArray, depth: jint, block_mapping: jintArray) -> jlong {
    let octree = vec_i_to_u(load_int_array(env, octree));
    let depth = depth as u32;
    let block_mapping = vec_i_to_u(load_int_array(env, block_mapping));

    let octree = FFIGuard::create(Box::new(Octree {
        octree, depth, block_mapping,
    }));
    octree.to_address() as jlong
}

#[no_mangle]
pub extern "system" fn Java_dev_thatredox_chunkynative_rust_export_ffi_SynchronizedRustOctree_drop(_env: JNIEnv, _class: JClass, address: jlong) {
    let octree: FFIGuard<Octree> = FFIGuard::new(address as usize);
    // Safety: This is synchronized and checked on the Java side.
    unsafe {
        octree.drop_object();
    }
}
