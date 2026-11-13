# FamilyMedicalVault

Private, local-only Android family medical document vault.

## Privacy model
- No INTERNET permission.
- No MANAGE_EXTERNAL_STORAGE permission.
- Uses Android Storage Access Framework only.
- The user explicitly chooses the root folder.
- Medical documents are never uploaded by the app.

## v0.3 bridge
- Opens `.fmv` import packages created by ChatGPT or another trusted tool.
- Validates manifest, category, filename, ZIP paths, payload size and SHA-256 before import.
- Shows a Greek confirmation screen before writing anything.
- Can auto-create a missing family-member folder.
- Adds «Άνοιγμα» and «Κοινή χρήση» actions for stored documents.
- Keeps standard Android Share → Ιατρικός Φάκελος for PDF, images and text.

### .fmv format
A ZIP container with `manifest.json` and one declared payload under `payload/`.
The app treats packages as untrusted input and imports only the exact payload declared in the manifest.
