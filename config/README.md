# Development APK signing

This directory contains a **development-only, publicly accessible** signing keystore used for NocturneNote CI debug builds. The development key is intentionally NOT secret; it must never be reused for production distributions, Play Store uploads, or sensitive applications.

- Keystore: `nocturnenote-dev-debug.jks`
- Alias: `androiddebugkey`
- Password: `android`
- Certificate SHA-256: `64:46:AC:0D:78:7A:95:56:85:C8:C1:21:C5:96:48:94:2C:32:22:0D:9F:E6:63:4D:ED:0B:D3:A0:AE:D2:63:70`

Purpose: ensure each GitHub Actions debug APK uses the same certificate so later debug APKs can update earlier debug APKs (provided the application ID is unchanged). Previous Google AI Studio installations and earlier CI builds with *different signatures* cannot be updated in-place by this certificate.

For a production release create a **private** release signing key and store it in secure CI secrets instead.
