# Security Policy

## Supported versions

Khata is pre-1.0. Only the latest tagged release receives security fixes.

## Reporting a vulnerability

Please **do not** open a public issue for security problems.

Report privately via GitHub's [private vulnerability reporting](https://github.com/priyendu7/khata/security/advisories/new). Include affected version, device/Android version, and steps to reproduce.

The most sensitive areas are the encrypted database and its Keystore-held key, the app lock (biometric/device credential, app-only PIN and recovery code, `FLAG_SECURE`), SMS handling (permissions, the receiver, and anything that could leak raw SMS text), custom parser rules (which must stay declarative data — a rule that can execute code, or hang the app with a pathological pattern, is a vulnerability), CSV import/export, and anything that would add network access.

You should get an acknowledgement within a week. Fixes will be credited in the release notes unless you prefer otherwise.
