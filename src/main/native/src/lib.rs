use std::mem;
use jni::JNIEnv;
use jni::objects::{JClass, JObject};
use jni::sys::{jint, jintArray, jlong};
use crate::ffi_guard::FfiGuard;
use crate::resource_palette::ResourcePalette;

mod resource_palette;
mod ffi_guard;

#[allow(clippy::assertions_on_constants)]
const _: () = assert!(usize::BITS <= u64::BITS, "The world is on fire!!!");

#[no_mangle]
pub extern "system" fn Java_dev_thatredox_chunkynative_rust_export_RustResourcePalette_create(_env: JNIEnv, _class: JClass) -> jlong{
    let palette = ResourcePalette::new();
    let guard = FfiGuard::create(Box::new(palette));
    guard.to_address() as jlong
}

#[no_mangle]
pub extern "system" fn Java_dev_thatredox_chunkynative_rust_export_RustResourcePalette_drop(_env: JNIEnv, _obj: JObject, address: jlong) {
    let guard: FfiGuard<ResourcePalette> = FfiGuard::new(address as usize);
    // Safety: This is synchronized and checked on the Java side.
    unsafe {
        guard.drop_object();
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_thatredox_chunkynative_rust_export_RustResourcePalette_put_1resource(env: JNIEnv, _obj: JObject, address: jlong, resource: jintArray) -> jint {
    let mut guard: FfiGuard<ResourcePalette> = FfiGuard::new(address as usize);

    let resource_len = env.get_array_length(resource).unwrap();
    let mut v = vec![0; resource_len as usize];
    env.get_int_array_region(resource, 0, v.as_mut()).unwrap();

    // Safety: i32 -> u32
    let v: Vec<u32> = unsafe {
        let mut v = mem::ManuallyDrop::new(v);
        Vec::from_raw_parts(
            v.as_mut_ptr() as *mut u32,
            v.len(),
            v.capacity()
        )
    };

    match guard.put(v) {
        Ok(index) => index,
        Err(err) => {
            let _ = env.throw_new("java/lang/RuntimeException", err);
            -1
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_thatredox_chunkynative_rust_export_RustResourcePalette_test_1impl(_env: JNIEnv, _obj: JObject, address: jlong) {
    let guard: FfiGuard<ResourcePalette> = FfiGuard::new(address as usize);

    for resource in &guard.resources {
        println!("{:?}", resource);
    }
}
