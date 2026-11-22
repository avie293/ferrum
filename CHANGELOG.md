# Changelog

## Unreleased

### Added
- Added `config/ferrum.properties` to turn off each optimization separately
### Performance
- Sped up network packet compression by 1.5x and decompression by 2.2x by using the native `zlib-rs` instead of the JDK's zlib
- Removed the temporary heap copy vanilla makes of every compressed packet
- Sped up decrypting online-mode connections by 5x and encrypting them by 1.7x with a native AES/CFB8 that decrypts many blocks in parallel
- Sped up writing chunks to region files by 2.7x and reading them by 2.5x by compressing each chunk in one native call instead of streaming it through a new JDK `Deflater`/`Inflater`
- Sped up reading gzip compressed NBT files such as `level.dat`, player data and structure templates by 1.9x and writing them by 1.5x
- Sped up building the mipmaps of cutout textures such as leaves, grass and glass by 1.9x (2.7x for 128x resource packs) with pixel-identical results
- Reduced native memory use by keeping one zlib state (417 KiB) per network thread instead of one per connection, about 34 MiB less with 100 players on a server with 8 CPU threads
- Removed the two heap arrays vanilla keeps for every encrypted connection, each as large as its biggest packet
- Removed the unused 8 KiB buffer vanilla allocates for every compressed connection
- Lowered the compression buffers kept per thread from 4 MiB to 1 MiB
### Compatibility
- Network compression and encryption are turned off automatically when Krypton is installed
