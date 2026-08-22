# KeepG Privacy Notes

KeepG is designed to work without an account and without an application backend. The Android manifest intentionally does not request Internet access.

Data kept locally can include MediaStore identifiers and metadata, album names, user-created collection names, lock metadata, salted password hashes, smart rules, face feature descriptors, user-assigned person names, and encrypted Vault files.

Face descriptors are sensitive local metadata. KeepG uses them only for on-device organization. They are not intended for identity verification, surveillance, or remote profiling.

Removing KeepG deletes its normal app-private database, Keystore aliases, and Vault directory according to Android's application data lifecycle. Original photos stored in MediaStore remain governed by Android and are not automatically deleted when KeepG is uninstalled.
