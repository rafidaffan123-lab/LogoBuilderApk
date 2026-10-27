# logo.bin format notes
Fill from real samples only. Record SHA-256 of every sample.

## Samples
| file | sha256 | device/chipset | notes |
|------|--------|----------------|-------|

## Findings
- Header / magic:
- Image count / index table:
- Encoding (RGB565 / RGB888 / RGBA8888 / compressed?):
- Offsets, padding, trailing blocks:

## Verification
- [ ] Parse -> rewrite unchanged -> compare against original (not just re-parse own output)

## Hints (unverified, from general knowledge — confirm with samples before relying)
- Many MediaTek partition images start with a 512-byte header whose first u32 (LE) is 0x58881688,
  followed by a size and a name field. The app only flags this magic; it does not parse the payload.
- Payload pixel layout, compression and offsets: UNKNOWN. No parser exists for them.
