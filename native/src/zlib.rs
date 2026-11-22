//! One-shot zlib compression and decompression backed by `zlib-rs`.
//!
//! Streams are created once per network connection or worker thread and reset before every
//! call, which avoids reallocating the (fairly large) deflate state for each packet or chunk.

use core::slice;

use zlib_rs::{Deflate, DeflateFlush, Inflate, InflateFlush, Status};

/// Returned when the input is not a valid zlib stream.
const ERR_DATA: i64 = -1;
/// Returned when the output buffer is too small.
const ERR_BUFFER: i64 = -2;

const WINDOW_BITS: u8 = 15;
/// Window bits that select the gzip format instead of zlib, as in zlib itself.
const GZIP_WINDOW_BITS: u8 = 16 + WINDOW_BITS;

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
/// Creates a deflate stream that produces gzip instead of zlib streams.
#[unsafe(no_mangle)]
pub extern "C" fn ferrum_gzip_deflater_new(level: i32) -> *mut Deflate {
    Box::into_raw(Box::new(Deflate::new(level, true, GZIP_WINDOW_BITS)))
}

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

/// Decompresses a complete zlib stream whose size is not known in advance.
///
/// Returns the total number of bytes written once the stream has ended. If the output fills up
/// first, [`ERR_BUFFER`] is returned with the stream state kept: the caller then passes the same
/// `src` again together with the remaining space of a larger output (`dst_cap` bytes at `dst`,
/// placed directly after the bytes written so far) and `reset = false`. A stream that is
/// truncated or malformed yields [`ERR_DATA`].
///
/// # Safety
///
/// `inflater` must be a live stream that is not used concurrently, and both buffers must be
/// valid for their lengths.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_inflate_stream(
    inflater: *mut Inflate,
    src: usize,
    src_len: usize,
    dst: usize,
    dst_cap: usize,
    reset: bool,
) -> i64 {
    let inflater = unsafe { &mut *inflater };
    if reset {
        inflater.reset(true);
    }

    let consumed = inflater.total_in() as usize;
    if consumed > src_len {
        return ERR_DATA;
    }
    let src = unsafe { input(src, src_len) };
    let written_before = inflater.total_out();
    let result = inflater.decompress(
        &src[consumed..],
        unsafe { output(dst, dst_cap) },
        InflateFlush::NoFlush,
    );
    match result {
        Ok(Status::StreamEnd) => inflater.total_out() as i64,
        // A full output may still hold back data even if all input was consumed
        Ok(_) if inflater.total_out() - written_before == dst_cap as u64 => ERR_BUFFER,
        // Input exhausted before the end of the stream
        Ok(_) => ERR_DATA,
        Err(_) => ERR_DATA,
    }
}

/// Decompresses gzip data that may consist of several members, like `GZIPInputStream`.
///
/// Returns the number of bytes written, [`ERR_BUFFER`] if they do not fit into `dst_cap` (the
/// caller retries with a larger buffer) or [`ERR_DATA`] if the data is malformed. Bytes after
/// the last member that do not start another member are ignored, as the JDK does.
///
/// # Safety
///
/// Both buffers must be valid for their lengths.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_gunzip(
    src: usize,
    src_len: usize,
    dst: usize,
    dst_cap: usize,
) -> i64 {
    let mut src = unsafe { input(src, src_len) };
    let dst = unsafe { output(dst, dst_cap) };
    let mut written = 0;

    loop {
        // The state is not reused since resetting the stable API always selects the zlib format
        let mut inflater = Inflate::new(true, GZIP_WINDOW_BITS);
        let space = dst.len() - written;
        match inflater.decompress(src, &mut dst[written..], InflateFlush::Finish) {
            Ok(Status::StreamEnd) => {}
            // A full output may still hold back data even if all input was consumed
            Ok(_) if inflater.total_out() as usize == space => return ERR_BUFFER,
            Ok(_) => return ERR_DATA,
            Err(_) => return ERR_DATA,
        }
        written += inflater.total_out() as usize;
        src = &src[inflater.total_in() as usize..];

        if !src.starts_with(&[0x1f, 0x8b]) {
            return written as i64;
        }
    }
}
