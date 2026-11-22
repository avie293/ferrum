//! CFB8 encryption with AES-NI, keeping the round keys and the shift register in SIMD registers.
//!
//! Encryption costs one AES block per byte and cannot be parallelized, so it is bound by the
//! latency of the AES instructions. The generic implementation additionally shifts the register
//! through memory for every byte; doing it in a register keeps the loop close to that limit.

use core::arch::x86_64::*;

pub struct Encryptor {
    keys: [__m128i; 11],
    register: __m128i,
}

pub fn is_supported() -> bool {
    std::is_x86_feature_detected!("aes") && std::is_x86_feature_detected!("sse4.1")
}

macro_rules! expand_round {
    ($keys:ident, $i:expr, $rcon:expr) => {
        $keys[$i] = expand_step(
            $keys[$i - 1],
            _mm_aeskeygenassist_si128::<$rcon>($keys[$i - 1]),
        );
    };
}

#[target_feature(enable = "aes,sse4.1")]
fn expand_step(key: __m128i, assist: __m128i) -> __m128i {
    let assist = _mm_shuffle_epi32::<0xff>(assist);
    let mut key = key;
    key = _mm_xor_si128(key, _mm_slli_si128::<4>(key));
    key = _mm_xor_si128(key, _mm_slli_si128::<4>(key));
    key = _mm_xor_si128(key, _mm_slli_si128::<4>(key));
    _mm_xor_si128(key, assist)
}

impl Encryptor {
    /// # Safety
    ///
    /// The CPU must support AES-NI and SSE4.1, see [`is_supported`].
    #[target_feature(enable = "aes,sse4.1")]
    pub unsafe fn new(key: &[u8; 16], iv: &[u8; 16]) -> Self {
        let mut keys = [_mm_setzero_si128(); 11];
        keys[0] = unsafe { _mm_loadu_si128(key.as_ptr().cast()) };
        expand_round!(keys, 1, 0x01);
        expand_round!(keys, 2, 0x02);
        expand_round!(keys, 3, 0x04);
        expand_round!(keys, 4, 0x08);
        expand_round!(keys, 5, 0x10);
        expand_round!(keys, 6, 0x20);
        expand_round!(keys, 7, 0x40);
        expand_round!(keys, 8, 0x80);
        expand_round!(keys, 9, 0x1b);
        expand_round!(keys, 10, 0x36);

        Self {
            keys,
            register: unsafe { _mm_loadu_si128(iv.as_ptr().cast()) },
        }
    }

    /// # Safety
    ///
    /// The CPU must support AES-NI and SSE4.1, see [`is_supported`].
    #[target_feature(enable = "aes,sse4.1")]
    pub unsafe fn encrypt(&mut self, data: &mut [u8]) {
        let k = &self.keys;
        let mut register = self.register;

        for byte in data {
            let mut block = _mm_xor_si128(register, k[0]);
            block = _mm_aesenc_si128(block, k[1]);
            block = _mm_aesenc_si128(block, k[2]);
            block = _mm_aesenc_si128(block, k[3]);
            block = _mm_aesenc_si128(block, k[4]);
            block = _mm_aesenc_si128(block, k[5]);
            block = _mm_aesenc_si128(block, k[6]);
            block = _mm_aesenc_si128(block, k[7]);
            block = _mm_aesenc_si128(block, k[8]);
            block = _mm_aesenc_si128(block, k[9]);
            block = _mm_aesenclast_si128(block, k[10]);

            let encrypted = *byte ^ _mm_cvtsi128_si32(block) as u8;
            *byte = encrypted;
            // Drop the oldest byte and append the ciphertext byte
            register = _mm_insert_epi8::<15>(_mm_srli_si128::<1>(register), encrypted as i32);
        }
        self.register = register;
    }
}

impl Drop for Encryptor {
    fn drop(&mut self) {
        for key in &mut self.keys {
            // Volatile so the round keys are really cleared and the store is not optimized away
            unsafe { core::ptr::write_volatile(key, core::mem::zeroed()) };
        }
    }
}
