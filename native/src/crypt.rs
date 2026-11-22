//! AES-128/CFB8, the cipher of Minecraft's encrypted connections.
//!
//! Encryption is inherently serial: every byte feeds into the next block. Decryption is not,
//! since each keystream byte only depends on the previous 16 ciphertext bytes, which are all
//! known up front. Decryption therefore encrypts many shift-register states at once, letting
//! AES-NI or the ARMv8 crypto extensions work on several blocks in parallel.

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
    Encrypt(Encryptor),
    Decrypt { cipher: Aes128, register: [u8; 16] },
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
        Mode::Encrypt(Encryptor::new(&Array(*key), &Array(*iv)))
    } else {
        Mode::Decrypt { cipher: Aes128::new(&Array(*key)), register: *iv }
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
        Mode::Encrypt(encryptor) => encryptor.encrypt(data),
        Mode::Decrypt { cipher, register } => decrypt(cipher, register, data),
    }
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
