//! One-shot zlib compression and decompression backed by `zlib-rs`.
//!
//! The streams are created once per network connection and reset before every call, which
//! avoids reallocating the (fairly large) deflate state for each packet.

use core::slice;

use zlib_rs::{Deflate, DeflateFlush, Inflate, InflateFlush, Status};

/// Returned when the input is not a valid zlib stream.
const ERR_DATA: i64 = -1;
/// Returned when the output buffer is too small to hold the compressed stream.
const ERR_BUFFER: i64 = -2;

const WINDOW_BITS: u8 = 15;

/// # Safety
///
/// `address` must be zero or point to `len` readable bytes.
unsafe fn input<'a>(address: usize, len: usize) -> &'a [u8] {
    if len == 0 {
        &[]
    } else {
        unsafe { slice::from_raw_parts(address as *const u8, len) }
    }
}

/// # Safety
///
/// `address` must be zero or point to `len` writable bytes that do not overlap the input.
unsafe fn output<'a>(address: usize, len: usize) -> &'a mut [u8] {
    if len == 0 {
        &mut []
    } else {
        unsafe { slice::from_raw_parts_mut(address as *mut u8, len) }
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn ferrum_zlib_compress_bound(len: usize) -> usize {
    zlib_rs::compress_bound(len)
}

/// Creates a deflate stream with the given zlib level (`-1` selects the default level).
#[unsafe(no_mangle)]
pub extern "C" fn ferrum_deflater_new(level: i32) -> *mut Deflate {
    Box::into_raw(Box::new(Deflate::new(level, true, WINDOW_BITS)))
}

/// # Safety
///
/// `deflater` must come from [`ferrum_deflater_new`] and must not be used afterwards.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_deflater_free(deflater: *mut Deflate) {
    if !deflater.is_null() {
        drop(unsafe { Box::from_raw(deflater) });
    }
}

/// Compresses `src_len` bytes into a complete zlib stream and returns its length, or
/// [`ERR_BUFFER`] if `dst_cap` is smaller than the compressed size.
///
/// # Safety
///
/// `deflater` must be a live stream that is not used concurrently, and both buffers must be
/// valid for their lengths.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_deflate(
    deflater: *mut Deflate,
    src: usize,
    src_len: usize,
    dst: usize,
    dst_cap: usize,
) -> i64 {
    let deflater = unsafe { &mut *deflater };
    deflater.reset();

    let result = deflater.compress(
        unsafe { input(src, src_len) },
        unsafe { output(dst, dst_cap) },
        DeflateFlush::Finish,
    );
    match result {
        Ok(Status::StreamEnd) => deflater.total_out() as i64,
        _ => ERR_BUFFER,
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn ferrum_inflater_new() -> *mut Inflate {
    Box::into_raw(Box::new(Inflate::new(true, WINDOW_BITS)))
}

/// # Safety
///
/// `inflater` must come from [`ferrum_inflater_new`] and must not be used afterwards.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_inflater_free(inflater: *mut Inflate) {
    if !inflater.is_null() {
        drop(unsafe { Box::from_raw(inflater) });
    }
}

/// Decompresses a zlib stream into at most `dst_cap` bytes and returns how many bytes were
/// written, or [`ERR_DATA`] if the stream is malformed.
///
/// Like `java.util.zip.Inflater`, decompression simply stops once the output is full; the
/// caller compares the result with the length it expects.
///
/// # Safety
///
/// `inflater` must be a live stream that is not used concurrently, and both buffers must be
/// valid for their lengths.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_inflate(
    inflater: *mut Inflate,
    src: usize,
    src_len: usize,
    dst: usize,
    dst_cap: usize,
) -> i64 {
    let inflater = unsafe { &mut *inflater };
    inflater.reset(true);

    let result = inflater.decompress(
        unsafe { input(src, src_len) },
        unsafe { output(dst, dst_cap) },
        InflateFlush::Finish,
    );
    match result {
        Ok(_) => inflater.total_out() as i64,
        Err(_) => ERR_DATA,
    }
}
