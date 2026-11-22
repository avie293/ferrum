//! AES-128/CFB8, the cipher of Minecraft's encrypted connections.
//!
//! Encryption is inherently serial: every byte feeds into the next block. Decryption is not,
//! since each keystream byte only depends on the previous 16 ciphertext bytes, which are all
//! known up front. Decryption therefore encrypts many shift-register states at once, letting
//! AES-NI or the ARMv8 crypto extensions work on several blocks in parallel.

#[cfg(target_arch = "x86_64")]
mod aesni;

use core::slice;

use aes::Aes128;
use aes::cipher::{Array, BlockCipherEncrypt, KeyInit, KeyIvInit};

type Encryptor = cfb8::Encryptor<Aes128>;

/// Number of blocks encrypted per batch while decrypting.
const BATCH: usize = 64;

pub struct Cfb8 {
    mode: Mode,
}

enum Mode {
    #[cfg(target_arch = "x86_64")]
    EncryptAesNi(aesni::Encryptor),
    Encrypt(Encryptor),
    Decrypt {
        cipher: Aes128,
        register: [u8; 16],
    },
}

/// Creates a cipher for one direction of a connection. Minecraft uses the shared secret as both
/// key and IV.
///
/// # Safety
///
/// `key` and `iv` must point to 16 readable bytes each.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_cfb8_new(key: usize, iv: usize, encrypt: bool) -> *mut Cfb8 {
    let key: &[u8; 16] = unsafe { &*(key as *const [u8; 16]) };
    let iv: &[u8; 16] = unsafe { &*(iv as *const [u8; 16]) };

    let mode = if encrypt {
        new_encryptor(key, iv)
    } else {
        Mode::Decrypt {
            cipher: Aes128::new(&Array(*key)),
            register: *iv,
        }
    };
    Box::into_raw(Box::new(Cfb8 { mode }))
}

/// # Safety
///
/// `cipher` must come from [`ferrum_cfb8_new`] and must not be used afterwards.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_cfb8_free(cipher: *mut Cfb8) {
    if !cipher.is_null() {
        drop(unsafe { Box::from_raw(cipher) });
    }
}

/// Encrypts or decrypts `len` bytes in place, continuing the stream of previous calls.
///
/// # Safety
///
/// `cipher` must be a live cipher that is not used concurrently, and `data` must point to `len`
/// writable bytes.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_cfb8_update(cipher: *mut Cfb8, data: usize, len: usize) {
    if len == 0 {
        return;
    }
    let cipher = unsafe { &mut *cipher };
    let data = unsafe { slice::from_raw_parts_mut(data as *mut u8, len) };

    match &mut cipher.mode {
        // SAFETY: only created after checking for AES-NI support
        #[cfg(target_arch = "x86_64")]
        Mode::EncryptAesNi(encryptor) => unsafe { encryptor.encrypt(data) },
        Mode::Encrypt(encryptor) => encryptor.encrypt(data),
        Mode::Decrypt { cipher, register } => decrypt(cipher, register, data),
    }
}

fn new_encryptor(key: &[u8; 16], iv: &[u8; 16]) -> Mode {
    #[cfg(target_arch = "x86_64")]
    if aesni::is_supported() {
        // SAFETY: support was checked right above
        return Mode::EncryptAesNi(unsafe { aesni::Encryptor::new(key, iv) });
    }
    Mode::Encrypt(Encryptor::new(&Array(*key), &Array(*iv)))
}

fn decrypt(cipher: &Aes128, register: &mut [u8; 16], data: &mut [u8]) {
    let mut blocks = [Array([0u8; 16]); BATCH];

    for chunk in data.chunks_mut(BATCH) {
        // Shift register state for byte i: the 16 ciphertext bytes before it, i.e. a sliding
        // window over the previous register followed by this chunk's ciphertext.
        for (i, block) in blocks.iter_mut().enumerate().take(chunk.len()) {
            if i < 16 {
                block[..16 - i].copy_from_slice(&register[i..]);
                block[16 - i..].copy_from_slice(&chunk[..i]);
            } else {
                block.copy_from_slice(&chunk[i - 16..i]);
            }
        }

        // The last register state must be taken before the ciphertext is overwritten
        let n = chunk.len();
        if n >= 16 {
            register.copy_from_slice(&chunk[n - 16..]);
        } else {
            register.copy_within(n.., 0);
            register[16 - n..].copy_from_slice(chunk);
        }

        let blocks = &mut blocks[..n];
        cipher.encrypt_blocks(blocks);
        for (byte, block) in chunk.iter_mut().zip(blocks.iter()) {
            *byte ^= block[0];
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn data(len: usize, seed: u32) -> Vec<u8> {
        let mut state = seed;
        (0..len)
            .map(|_| {
                state = state.wrapping_mul(1_103_515_245).wrapping_add(12_345);
                (state >> 16) as u8
            })
            .collect()
    }

    /// The streaming behaviour across calls of different sizes must not depend on the backend.
    #[test]
    fn backends_agree() {
        let key: [u8; 16] = data(16, 1).try_into().unwrap();
        let mut generic = Mode::Encrypt(Encryptor::new(&Array(key), &Array(key)));
        let mut fast = new_encryptor(&key, &key);
        let mut decryptor = Mode::Decrypt {
            cipher: Aes128::new(&Array(key)),
            register: key,
        };

        for (i, len) in [1, 15, 16, 17, 64, 65, 200, 3000, 0, 7]
            .into_iter()
            .enumerate()
        {
            let plain = data(len, i as u32);
            let (mut a, mut b) = (plain.clone(), plain.clone());
            update(&mut generic, &mut a);
            update(&mut fast, &mut b);
            assert_eq!(a, b);
            update(&mut decryptor, &mut b);
            assert_eq!(b, plain);
        }
    }

    fn update(mode: &mut Mode, data: &mut [u8]) {
        let mut cipher = Cfb8 {
            mode: core::mem::replace(
                mode,
                Mode::Encrypt(Encryptor::new(&Array([0; 16]), &Array([0; 16]))),
            ),
        };
        unsafe { ferrum_cfb8_update(&mut cipher, data.as_mut_ptr() as usize, data.len()) };
        *mode = cipher.mode;
    }
}
