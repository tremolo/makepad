//! mpmux's direct calls into its `MakepadActivity`, from any thread: the QR scanner, the APK
//! updater, and the system clipboard read or written at once. An app with a `Cx` at hand uses
//! `Cx::copy_to_clipboard` and `Cx::read_clipboard` instead; these serve code that has none
//! (a worker thread) or must have the text before it returns.
//!
//! The activity is `makepad_android_state`'s; the calling thread is attached to the VM on its
//! first call and detached when it ends.
use makepad_android_state::{get_activity, get_java_vm};
use makepad_jni_sys::{self as jni_sys, jvalue, registration as reg};
use std::{cell::OnceCell, ffi::CStr};

struct Attached(*mut jni_sys::JavaVM);
impl Drop for Attached {
    fn drop(&mut self) {
        unsafe {
            ((**self.0).DetachCurrentThread.unwrap())(self.0);
        }
    }
}

thread_local! {
    /// Set when this module attached the thread: it detaches the thread when the thread ends.
    static ATTACHED: OnceCell<Attached> = const { OnceCell::new() };
}

/// This thread's JNI environment and the activity; `None` before both exist.
unsafe fn env_and_activity() -> Option<(*mut jni_sys::JNIEnv, jni_sys::jobject)> {
    let vm = get_java_vm();
    let activity = get_activity();
    if vm.is_null() || activity.is_null() {
        return None;
    }
    let mut env: *mut jni_sys::JNIEnv = std::ptr::null_mut();
    if ((**vm).GetEnv.unwrap())(vm, &mut env as *mut _ as _, jni_sys::JNI_VERSION_1_6) != 0 {
        if ((**vm).AttachCurrentThread.unwrap())(vm, &mut env, std::ptr::null_mut()) != 0 {
            return None;
        }
        ATTACHED.with(|attached| {
            let _ = attached.set(Attached(vm));
        });
    }
    (!env.is_null()).then_some((env, activity))
}

/// Clears a pending Java exception, logging it; whether there was one.
unsafe fn take_exception(env: *mut jni_sys::JNIEnv) -> bool {
    if ((**env).ExceptionCheck.unwrap())(env) == 0 {
        return false;
    }
    ((**env).ExceptionDescribe.unwrap())(env);
    ((**env).ExceptionClear.unwrap())(env);
    true
}

unsafe fn new_string(env: *mut jni_sys::JNIEnv, text: &str) -> jni_sys::jstring {
    let utf16: Vec<u16> = text.encode_utf16().collect();
    ((**env).NewString.unwrap())(env, utf16.as_ptr(), utf16.len() as jni_sys::jsize)
}

/// Calls the activity's `name` (a void method); an error when it is missing or threw.
unsafe fn call_void(name: &CStr, sig: &CStr, args: &[jvalue]) -> Result<(), String> {
    let (env, activity) =
        env_and_activity().ok_or_else(|| "the Android activity is not available".to_string())?;
    call_void_on(env, activity, name, sig, args)
}

unsafe fn call_void_on(
    env: *mut jni_sys::JNIEnv,
    activity: jni_sys::jobject,
    name: &CStr,
    sig: &CStr,
    args: &[jvalue],
) -> Result<(), String> {
    let class = ((**env).GetObjectClass.unwrap())(env, activity);
    let method = ((**env).GetMethodID.unwrap())(env, class, name.as_ptr(), sig.as_ptr());
    ((**env).DeleteLocalRef.unwrap())(env, class);
    if method.is_null() {
        take_exception(env);
        return Err(format!("MakepadActivity.{} is missing", name.to_string_lossy()));
    }
    ((**env).CallVoidMethodA.unwrap())(env, activity, method, args.as_ptr());
    if take_exception(env) {
        return Err(format!("MakepadActivity.{} failed", name.to_string_lossy()));
    }
    Ok(())
}

/// Puts `content` on the system clipboard (`MakepadActivity.copyToClipboard`).
///
/// # Safety
/// Calls into Java; any thread may call.
pub unsafe fn to_java_copy_to_clipboard(content: String) {
    let Some((env, activity)) = env_and_activity() else { return };
    let content = new_string(env, &content);
    if content.is_null() {
        take_exception(env);
        return;
    }
    let _ = call_void_on(env, activity, c"copyToClipboard", c"(Ljava/lang/String;)V", &[jvalue { l: content }]);
    ((**env).DeleteLocalRef.unwrap())(env, content);
}

/// The system clipboard's text (`MakepadActivity.pasteFromClipboard`), empty when it holds
/// none or the activity cannot be asked.
///
/// # Safety
/// Calls into Java; any thread may call.
pub unsafe fn to_java_paste_from_clipboard() -> String {
    let Some((env, activity)) = env_and_activity() else { return String::new() };
    let text = reg::object(env, activity, c"pasteFromClipboard", c"()Ljava/lang/String;", &[]);
    if take_exception(env) || text.is_null() {
        if !text.is_null() {
            ((**env).DeleteLocalRef.unwrap())(env, text);
        }
        return String::new();
    }
    let out = reg::string(env, text);
    ((**env).DeleteLocalRef.unwrap())(env, text);
    out
}

/// mpmux extension hook: start the Android QR scanner (`MakepadActivity.startMpmuxQrScanner`).
///
/// # Safety
/// Calls into Java; any thread may call.
pub unsafe fn to_java_start_mpmux_qr_scanner() {
    if let Err(error) = call_void(c"startMpmuxQrScanner", c"()V", &[]) {
        crate::error!("mpmux QR scanner: {error}");
    }
}

/// mpmux extension hook: ask the Android activity to check for an APK update manifest
/// (`MakepadActivity.checkMpmuxSelfUpdate`). The Java side owns user-visible errors and update
/// validation.
///
/// # Safety
/// Calls into Java; any thread may call.
pub unsafe fn to_java_check_mpmux_self_update(manifest_url: &str) -> Result<(), String> {
    let (env, activity) =
        env_and_activity().ok_or_else(|| "the Android activity is not available".to_string())?;
    let manifest_url = new_string(env, manifest_url);
    if manifest_url.is_null() {
        take_exception(env);
        return Err("invalid update manifest URL string".to_string());
    }
    let result = call_void_on(
        env,
        activity,
        c"checkMpmuxSelfUpdate",
        c"(Ljava/lang/String;)V",
        &[jvalue { l: manifest_url }],
    );
    ((**env).DeleteLocalRef.unwrap())(env, manifest_url);
    result
}

/// `MakepadNative.onAndroidIntentUrl`: the activity stored a pairing link it was handed (a QR
/// scan or an intent) where the app looks for it; wake the app so it does. An app-side native
/// the HAL does not register: ART finds it by this exported name.
///
/// # Safety
/// Called by ART with the arguments of its Java declaration.
#[no_mangle]
pub unsafe extern "C" fn Java_dev_makepad_android_MakepadNative_onAndroidIntentUrl(
    _env: *mut jni_sys::JNIEnv,
    _class: jni_sys::jclass,
    _url: jni_sys::jstring,
) {
    crate::thread::SignalToUI::set_ui_signal();
}
