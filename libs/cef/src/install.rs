//! Fetch and unpack a CEF distribution at runtime, for an app that loads
//! CEF without building against it (`MAKEPAD_CEF_RUNTIME_DIST`): the newest
//! stable `minimal` build for this platform from the CEF index, checked
//! against its sha1, unpacked under a root directory the app chooses, with
//! a pointer file naming the current one (the same layout the build script
//! keeps in `local/cef-prebuilt`). Hand the result to [`crate::set_dist_dir`].
//!
//! Blocking and slow (a 300 MB download): call it off the UI thread.

#[path = "../build_support/cef_dist.rs"]
#[allow(dead_code)]
mod cef_dist;

use std::path::{Path, PathBuf};

/// This build's platform in the CEF index (`linux64`, `windows64`, …).
pub fn platform() -> Option<&'static str> {
    match (std::env::consts::OS, std::env::consts::ARCH) {
        ("macos", "aarch64") => Some("macosarm64"),
        ("macos", "x86_64") => Some("macosx64"),
        ("linux", "x86_64") => Some("linux64"),
        ("linux", "aarch64") => Some("linuxarm64"),
        ("windows", "x86_64") => Some("windows64"),
        ("windows", "aarch64") => Some("windowsarm64"),
        _ => None,
    }
}

/// The distribution installed under `root`, if any.
pub fn installed(root: &Path) -> Option<PathBuf> {
    cef_dist::Pointer::read(root, platform()?)
}

/// Install the newest stable minimal distribution under `root` (or keep
/// the one installed); `report` hears progress ("120 of 321 MB" and the
/// like). The downloaded archive is removed once it is unpacked.
pub fn install(
    root: &Path,
    report: impl Fn(&str) + Send + Sync + 'static,
) -> Result<PathBuf, String> {
    let platform = platform().ok_or("CEF is not built for this platform")?;
    cef_dist::set_archive_type("minimal");
    cef_dist::set_reporter(Box::new(report));
    let dir = cef_dist::ensure_dist(root, platform, None, false)?;
    if let Ok(entries) = std::fs::read_dir(root) {
        for entry in entries.flatten() {
            if entry.file_name().to_string_lossy().ends_with(".tar.bz2") {
                let _ = std::fs::remove_file(entry.path());
            }
        }
    }
    Ok(dir)
}
