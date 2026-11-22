//! Alpha coverage preservation for cutout mipmaps, a bit-exact port of `MipmapGenerator`.
//!
//! Vanilla estimates how much of a texture passes the alpha test by sampling every texel at
//! 4x4 bilinearly interpolated points, and repeats this up to five times per mip level while
//! searching for an alpha scale. Every float operation below is performed in the same order as
//! in vanilla, so the results are identical.

use core::slice;

/// Byte offset of the alpha channel in an RGBA `NativeImage` pixel.
const ALPHA: usize = 3;

/// Relative margin around the alpha reference within which texels are always sampled.
///
/// The interpolated alpha is a convex combination of the four corner alphas, computed with a
/// relative rounding error below 1e-6. If all corners are clearly above or below the reference,
/// every sample is too, and the count is known without evaluating them.
const MARGIN: f32 = 1e-4;

/// # Safety
///
/// `pixels` must point to `width * height` RGBA pixels.
unsafe fn image<'a>(pixels: usize, width: usize, height: usize) -> &'a mut [u8] {
    unsafe { slice::from_raw_parts_mut(pixels as *mut u8, width * height * 4) }
}

/// [VanillaCopy] `MipmapGenerator#alphaTestCoverage`
fn coverage(pixels: &[u8], width: usize, height: usize, alpha_ref: f32, alpha_scale: f32) -> f32 {
    let alpha = |x: usize, y: usize| {
        (pixels[(x + y * width) * 4 + ALPHA] as f32 / 255.0 * alpha_scale).clamp(0.0, 1.0)
    };
    let below = alpha_ref * (1.0 - MARGIN);
    let above = alpha_ref * (1.0 + MARGIN);

    let mut coverage = 0.0f32;
    for y in 0..height.saturating_sub(1) {
        for x in 0..width.saturating_sub(1) {
            let a00 = alpha(x, y);
            let a10 = alpha(x + 1, y);
            let a01 = alpha(x, y + 1);
            let a11 = alpha(x + 1, y + 1);

            let texel_coverage = if a00.max(a10).max(a01).max(a11) < below {
                0.0
            } else if a00.min(a10).min(a01).min(a11) > above {
                16.0
            } else {
                sample(a00, a10, a01, a11, alpha_ref)
            };
            coverage += texel_coverage / 16.0;
        }
    }
    coverage / ((width as i32 - 1) * (height as i32 - 1)) as f32
}

fn sample(a00: f32, a10: f32, a01: f32, a11: f32, alpha_ref: f32) -> f32 {
    let mut texel_coverage = 0.0f32;
    for subsample_y in 0..4 {
        let fy = (subsample_y as f32 + 0.5) / 4.0;
        for subsample_x in 0..4 {
            let fx = (subsample_x as f32 + 0.5) / 4.0;
            let alpha = a00 * (1.0 - fx) * (1.0 - fy)
                + a10 * fx * (1.0 - fy)
                + a01 * (1.0 - fx) * fy
                + a11 * fx * fy;
            if alpha > alpha_ref {
                texel_coverage += 1.0;
            }
        }
    }
    texel_coverage
}

/// [VanillaCopy] `MipmapGenerator#alphaTestCoverage`
///
/// # Safety
///
/// `pixels` must point to `width * height` RGBA pixels.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_mip_coverage(
    pixels: usize,
    width: i32,
    height: i32,
    alpha_ref: f32,
    alpha_scale: f32,
) -> f32 {
    let (width, height) = (width as usize, height as usize);
    let pixels = unsafe { image(pixels, width, height) };
    coverage(pixels, width, height, alpha_ref, alpha_scale)
}

/// [VanillaCopy] `MipmapGenerator#scaleAlphaToCoverage`
///
/// # Safety
///
/// `pixels` must point to `width * height` RGBA pixels.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_mip_scale_alpha(
    pixels: usize,
    width: i32,
    height: i32,
    desired_coverage: f32,
    alpha_ref: f32,
    alpha_cutoff_bias: f32,
) {
    let (width, height) = (width as usize, height as usize);
    let pixels = unsafe { image(pixels, width, height) };

    let mut min_alpha_scale = 0.0f32;
    let mut max_alpha_scale = 4.0f32;
    let mut alpha_scale = 1.0f32;
    let mut best_alpha_scale = 1.0f32;
    let mut best_error = f32::MAX;

    for _ in 0..5 {
        let current_coverage = coverage(pixels, width, height, alpha_ref, alpha_scale);
        let error = (current_coverage - desired_coverage).abs();
        if error < best_error {
            best_error = error;
            best_alpha_scale = alpha_scale;
        }

        if current_coverage < desired_coverage {
            min_alpha_scale = alpha_scale;
        } else {
            if !(current_coverage > desired_coverage) {
                break;
            }
            max_alpha_scale = alpha_scale;
        }
        alpha_scale = (min_alpha_scale + max_alpha_scale) * 0.5;
    }

    for pixel in pixels.chunks_exact_mut(4) {
        let alpha = pixel[ALPHA] as f32 / 255.0;
        let alpha = (alpha * best_alpha_scale + alpha_cutoff_bias + 0.025).clamp(0.0, 1.0);
        pixel[ALPHA] = (alpha * 255.0).floor() as u8;
    }
}
