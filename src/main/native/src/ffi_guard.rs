use std::ops::{Deref, DerefMut};

pub struct FFIGuard<'a, T> {
    obj: &'a mut T,
}

impl <'a, T> Deref for FFIGuard<'a, T> {
    type Target = T;

    fn deref(&self) -> &Self::Target {
        self.obj
    }
}

impl <'a, T> DerefMut for FFIGuard<'a, T> {
    fn deref_mut(&mut self) -> &mut Self::Target {
        self.obj
    }
}

impl <'a, T> FFIGuard<'a, T> {
    pub fn new(address: usize) -> Self {
        let obj = unsafe { Box::leak(Box::from_raw(address as *mut T)) };
        Self { obj }
    }

    pub fn create(obj: Box<T>) -> Self {
        let obj = Box::leak(obj);
        Self { obj }
    }

    pub fn to_address(&self) -> usize {
        self.obj as *const T as usize
    }

    pub unsafe fn drop_object(self) {
        let obj = Box::from_raw(self.obj);
        drop(obj);
    }
}
