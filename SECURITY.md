# KeepG Security Manual

## Security goals

KeepG is designed as a privacy-first Android gallery with two distinct protection layers:

1. **In-app locks** protect selected photos or device albums from being displayed in KeepG until the configured authentication succeeds.
2. **Vault encryption** creates an AES-GCM encrypted copy in app-private storage using a key generated and retained by Android Keystore.

These layers solve different problems. An in-app lock does **not** make an original MediaStore file invisible to another gallery application. For confidentiality against other apps, create a Vault copy and remove the original through Android's system media controls.

## Authentication modes

### Device credential

KeepG uses AndroidX `BiometricPrompt` and requests a strong biometric or the device credential. KeepG never receives the user's fingerprint, face template, PIN, pattern, or device password. Android performs the authentication and returns success or failure.

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

## Threat model

### Defended against

- Casual access to protected items inside KeepG without authentication.
- Plaintext disclosure of Vault copies from app-private storage.
- Direct recovery of KeepG passwords from stored password strings; plaintext passwords are not stored.
- Normal Android cloud backup of KeepG's database and Vault directory.
- Accidental app-level network upload by the current classifier; the app has no Internet permission.

### Not fully defended against

- A rooted or compromised device controlling the OS/runtime.
- Screenshots or screen recording after legitimate unlock.
- Another app reading an **original** MediaStore photo when only an in-app KeepG lock was applied.
- Physical attacks outside Android Keystore guarantees.
- Incorrect smart/person classification.
- Weak user-chosen passwords.

## Safe operating procedure

1. Keep Android updated and configure a strong device credential.
2. Prefer device authentication for frequently accessed protected albums.
3. Use a unique password of at least 12 characters when password mode is required.
4. For genuinely sensitive photos, create an encrypted Vault copy.
5. Verify the Vault import completed before deleting the original.
6. Delete the original through Android's trusted system UI if it must disappear from other gallery apps.
7. Do not export decrypted Vault previews to shared storage unless disclosure is intended.
8. Before selling or transferring the device, uninstall KeepG and perform the manufacturer's secure reset.

## Key-loss behavior

The Vault key is tied to Android Keystore. Uninstalling KeepG normally removes its Keystore entry and app-private files. Copying `.kgv` files to another device without a supported key migration mechanism does not make them decryptable. Keep independent backups of irreplaceable photos before moving originals exclusively into a destructive future Vault workflow.

## Reporting a vulnerability

Do not post exploit details for an unpatched vulnerability in a public issue. Contact the repository owner privately through an available GitHub security reporting channel when enabled. Include the affected commit, Android version/device, reproduction steps, impact, and safe proof-of-concept data.
