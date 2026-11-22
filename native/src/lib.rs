//! Native routines used by Ferrum through the Java Foreign Function & Memory API.
//!
//! Every exported function uses the C ABI and only plain integers and raw addresses, so the
//! Java side can bind them without any glue code. Buffers are always owned by Java; this
//! library never keeps a pointer to them after a call returns.

mod crypt;
mod zlib;

/// Version of the exported interface. The Java side refuses to use a library that reports a
/// different value, which protects against stale extracted copies.
const ABI_VERSION: u32 = 3;

#[unsafe(no_mangle)]
pub extern "C" fn ferrum_abi_version() -> u32 {
    ABI_VERSION
}
