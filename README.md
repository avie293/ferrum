# Ferrum

Speeds up performance-critical game code by running it in native Rust.

Ferrum replaces selected hot paths of Minecraft with native code written in Rust, called through Java's Foreign Function & Memory API. It works **on both the client and the server**, each side benefits on its own, and it **does not change any game mechanics, worlds or the network protocol**, so players and servers without Ferrum can still connect. Features include:

- Network packets are compressed 1.5x and decompressed 2.2x faster by using `zlib-rs` instead of the JDK's zlib.
- Compressed packets are no longer copied into a temporary array first, unlike vanilla.
- Online-mode connections are decrypted 5x and encrypted 1.7x faster with a native AES/CFB8 that decrypts many blocks in parallel.
- Chunks are written to region files 2.7x and read 2.5x faster, measured on 2,445 chunks of a freshly generated world.

Native libraries are included for Windows (x64), Linux (x64, ARM64) and macOS (Intel, Apple Silicon). On any other system, or if the library cannot be loaded, the game simply uses vanilla code. Every optimization can be turned off separately in `config/ferrum.properties`, and the network optimizations turn themselves off when Krypton is installed.

The numbers above were measured with Java 25 on an AMD Ryzen 9 5900X; network compression was measured on the 1,509 NBT structure files from the Minecraft jar (43 MiB in total).

Bugs and crashes can be reported in the [issue tracker](https://github.com/avie293/avie-issues/issues) by selecting **Ferrum** as the project.
