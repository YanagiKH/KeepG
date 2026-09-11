# KeepG Security Manual

## Security goals

KeepG is designed as a privacy-first Android gallery with two distinct protection layers:

1. **In-app locks** protect selected photos or device albums from being displayed in KeepG until the configured authentication succeeds.
2. **Vault encryption** creates an AES-GCM encrypted copy in app-private storage using a key generated and retained by Android Keystore.

These layers solve different problems. An in-app lock does **not** make an original MediaStore file invisible to another gallery application. For confidentiality against other apps, create a Vault copy and remove the original through Android's system media controls.

## Authentication modes

### Device credential

KeepG uses AndroidX `BiometricPrompt` and requests a strong biometric or the device credential. KeepG never receives the user's fingerprint, face template, PIN, pattern, or device password. Android performs authentication and returns success or failure.

### KeepG password

Per-target KeepG passwords are never stored in plaintext. A random 128-bit salt is generated with `SecureRandom`, then the password is derived with PBKDF2-HMAC-SHA256 using 210,000 iterations and a 256-bit output. Verification uses constant-time `MessageDigest.isEqual`.

Password lock records are stored in the local Room database. Android backup is disabled for the database and shared preferences.

## Vault encryption

Vault files use GCM mode with a 128-bit authentication tag, a fresh randomized IV per file, a non-exportable Android Keystore AES key alias (`keepg-vault-aes-v1`), and app-private internal storage under `files/vault/`.

```text
[1 byte IV length][IV bytes][AES-GCM ciphertext + authentication tag]
```

Decryption fails when ciphertext or the authentication tag has been modified.

## Face and smart-classification privacy

KeepG's current classifier runs on-device. Photos are read from Android MediaStore and passed to ML Kit's on-device face detector. KeepG derives a local face descriptor from a normalized face crop plus detector geometry/classification signals. Face descriptors and grouping metadata stay in the local Room database.

KeepG does not implement identity verification and must not be used as an access-control biometric system. Automatic person groups are convenience suggestions and may produce false matches or misses.

## Editor and repair safety

Full-edition image/video/GIF editing is non-destructive. KeepG creates a new output item instead of overwriting the source. File/date repair follows the same recovered-copy model. This reduces the chance that an editing or repair failure destroys the only copy of a user's media.

Automatic background removal uses a bundled on-device segmentation model. Adjustable manual removal samples a background color and removes pixels within the user-selected tolerance. Neither operation uploads the image.

Video trimming/muting uses Android demux/remux APIs. If source tracks cannot be safely remuxed into MP4, the operation fails and the original remains untouched.

## External URL and QR handling

KeepG can inspect an unlocked image for QR/barcode content and visible text URLs. Detection runs on-device. The application only presents normalized `http://` and `https://` targets; arbitrary custom schemes are rejected.

Opening a detected URL launches the user's external browser through Android `ACTION_VIEW`. KeepG itself still declares no `INTERNET` permission and does not fetch the destination page. Users must treat QR/URL destinations as untrusted external content and review the displayed host before opening it.

## Debug logging

The optional persistent debug log is stored in app-private storage and rotates at 2 MiB. Export is always user-initiated through Android's document picker. KeepG must not log plaintext passwords, password-derived keys, Vault keys, decrypted Vault bytes, biometric material, or full face descriptor arrays.

Debug logs can still contain filenames, operation names, error messages, and timestamps. Users should review exported logs before sharing them publicly.

## Release signing boundary

Automated GitHub prerelease APKs are Android debug-key signed for installation/testing. They are not represented as production-signed store artifacts. Production signing private keys must never be committed to the repository. AAB artifacts from CI require a proper production signing/store pipeline before public store distribution.

## Threat model

### Defended against

- Casual access to protected items inside KeepG without authentication.
- Plaintext disclosure of Vault copies from app-private storage.
- Direct recovery of KeepG passwords from stored password strings; plaintext passwords are not stored.
- Normal Android cloud backup of KeepG's database and Vault directory.
- Accidental app-level network upload by the current classifier/editor; local processing does not upload media; model downloads use Internet permission (see the 0.7 boundaries below).
- Destructive source overwrite by editor/repair operations; results are written as new copies.
- Arbitrary URI-scheme launching from detected QR/text values; only HTTP(S) is exposed.

### Not fully defended against

- A rooted or compromised device controlling the OS/runtime.
- Screenshots or screen recording after legitimate unlock.
- Another app reading an **original** MediaStore photo when only an in-app KeepG lock was applied.
- Malicious/phishing content at a user-opened HTTP(S) destination.
- Physical attacks outside Android Keystore guarantees.
- Incorrect smart/person/background classification.
- Weak user-chosen passwords.
- Media payload corruption that Android cannot decode or recover.

## Safe operating procedure

1. Keep Android updated and configure a strong device credential.
2. Prefer device authentication for frequently accessed protected albums.
3. Use a unique password of at least 12 characters when password mode is required.
4. For genuinely sensitive photos, create an encrypted Vault copy.
5. Verify the Vault import completed before deleting the original.
6. Delete the original through Android's trusted system UI if it must disappear from other gallery apps.
7. Treat QR/URL results as untrusted and verify the destination host before opening.
8. Review exported debug logs before sharing them.
9. Keep irreplaceable originals until an edited/repaired copy has been verified.
10. Before selling or transferring the device, uninstall KeepG and perform the manufacturer's secure reset.

## Key-loss behavior

The Vault key is tied to Android Keystore. Uninstalling KeepG normally removes its Keystore entry and app-private files. Copying `.kgv` files to another device without a supported key migration mechanism does not make them decryptable. Keep independent backups of irreplaceable photos before moving originals exclusively into a destructive future Vault workflow.

## Reporting a vulnerability

Do not post exploit details for an unpatched vulnerability in a public issue. Contact the repository owner privately through an available GitHub security reporting channel when enabled. Include the affected commit, Android version/device, reproduction steps, impact, and safe proof-of-concept data.

## 0.7 local AI and editor boundaries

KeepG now requests `INTERNET` and network-state/foreground-download permissions for explicitly requested Hugging Face model discovery and downloads. It is no longer accurate to describe this version as having no Internet permission. There is no cloud inference or media-upload endpoint in the AI implementation. Browser/model-card links and explicit Android sharing leave the app and follow the chosen app's privacy policy.

Models are pinned to a revision and verified by SHA-256 and length before atomic installation. HF read tokens are encrypted using an Android Keystore key and are never forwarded to download redirects. Native model parsing still constitutes a trust boundary; a checksum proves object integrity, not publisher trust or absence of engine vulnerabilities.

Skills are disabled-by-default Markdown instructions. ZIP traversal, unsafe YAML and oversized files are rejected. No bundled scripts or executables run. Model-proposed actions pass a whitelist, current visible-ID authorization, one-use UI review, fresh lock checks and existing Android/password confirmations. Turning off tools, changing locks, clearing chat or backgrounding invalidates pending proposals and clears sensitive in-memory context. See [AI_MANUAL.md](AI_MANUAL.md).

GIF decode bounds are checked before native allocation. Image/GIF/video output is written as a new file with rollback of incomplete MediaStore entries. Exports are not automatically locked or encrypted. Model downloads and video export can consume substantial disk space; cancellation does not promise immediate interruption of every OEM/native call. Screen capture blocking is optional and applies to KeepG windows, not other applications.
