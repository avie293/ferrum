# Changelog

## Unreleased

### Added
- Added `config/ferrum.properties` to turn off each optimization separately
### Performance
- Sped up network packet compression by 1.5x and decompression by 2.2x by using the native `zlib-rs` instead of the JDK's zlib
- Removed the temporary heap copy vanilla makes of every compressed packet
### Compatibility
- Network compression is turned off automatically when Krypton is installed
