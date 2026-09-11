# KeepG Privacy Notes

KeepG is designed to work without an account and without an application backend. Version 0.7 requests Internet access solely for user-requested model search/downloads; inference and media processing stay on the device. Hugging Face and its download hosts receive ordinary connection data such as IP address and the requested repository. No media is sent by the local AI implementation.

Data kept locally can include MediaStore identifiers and metadata, album names, user-created collection names, lock metadata, salted password hashes, smart rules, face feature descriptors, user-assigned person names, and encrypted Vault files.

Face descriptors are sensitive local metadata. KeepG uses them only for on-device organization. They are not intended for identity verification, surveillance, or remote profiling.

Removing KeepG deletes its normal app-private database, Keystore aliases, and Vault directory according to Android's application data lifecycle. Original photos stored in MediaStore remain governed by Android and are not automatically deleted when KeepG is uninstalled.

AI chat history is memory-only. Selected attachments are copied or rendered into bounded private temporary files and removed after the turn. Backgrounding or lock/permission changes clear conversation context and invalidate proposals. Models and disabled/enabled Skills are app-private persistent data; deleting app data removes them. Read tokens are Android Keystore-encrypted and can be removed in Models. Explicit Android shares and external browser links are separate user-directed disclosures.
