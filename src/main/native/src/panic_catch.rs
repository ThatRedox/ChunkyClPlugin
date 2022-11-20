use std::cell::RefCell;
use std::panic::UnwindSafe;
use jni::JNIEnv;
use backtrace::Backtrace;

thread_local! {
    static PANIC_BACKTRACE: RefCell<Option<Backtrace>> = RefCell::new(None);
}

pub fn panic_catch<F: FnOnce() -> R + UnwindSafe, R>(env: &JNIEnv, f: F) -> Option<R> {
    match std::panic::catch_unwind(f) {
        Ok(r) => Some(r),
        Err(_e) => {
            let bt = PANIC_BACKTRACE.with(|b| b.borrow_mut().clone())
                .map_or("Failed to get backtrace.".to_string(), |mut bt| {
                    bt.resolve();
                    format!("\n{:?}", bt)
                });
            let exception = env.find_class("java/lang/RuntimeException")
                .expect("Failed to find RuntimeException class!");
            env.throw_new(exception, bt)
                .expect("Failed to throw exception!");
            None
        }
    }
}

pub fn init() {
    std::panic::set_hook(Box::new(|_info| {
        let bt = Backtrace::new_unresolved();
        PANIC_BACKTRACE.with(move |b| b.borrow_mut().replace(bt));
    }));
}
