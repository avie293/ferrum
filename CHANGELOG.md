# Changelog

## Unreleased

### Added
- Added `config/ferrum.properties` to turn off each optimization separately
### Performance
- Sped up network packet compression by 1.5x and decompression by 2.2x by using the native `zlib-rs` instead of the JDK's zlib
- Removed the temporary heap copy vanilla makes of every compressed packet
- Sped up decrypting online-mode connections by 5x and encrypting them by 1.3x with a native AES/CFB8 that decrypts many blocks in parallel
- Sped up writing chunks to region files by 2.7x and reading them by 2.5x by compressing each chunk in one native call instead of streaming it through a new JDK `Deflater`/`Inflater`
### Compatibility
- Network compression and encryption are turned off automatically when Krypton is installed
