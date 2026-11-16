# Tijori

A personal, offline password manager for Android. *Tijori* means safe or strongbox.

## What it does

- **Encrypted vault** opened by one master password, with optional fingerprint or face unlock.
- **Categories** (starts with Personal, Work, Microsoft, GitHub, GPay) and **tags**.
- **Tag policies**: a tag can say "rotate every 90 days, at least 14 characters, needs a symbol".
  A password with several tags gets the strictest combination.
- **Generator** that follows the policy of the entry's tags, so a generated password always passes.
- **Rotation reminders**: a daily background check posts a notification when passwords are overdue
  or due within a week. The list has a "Due for rotation" filter.
- **Warnings** on the list for overdue, reused and rule-breaking passwords.
- **Password history**: the last 10 passwords of each entry.
- **Encrypted backup and restore** to any location the phone can reach (Drive, Downloads, USB).
- Screenshots blocked, clipboard cleared after 30 seconds, auto-lock one minute after leaving the app.
- No internet permission.

## How the vault is protected

| Piece | Choice |
|---|---|
| Payload encryption | AES-256-GCM with a random 256-bit vault key, fresh nonce on every save |
| Master password | Argon2id (64 MiB, 3 passes, Bouncy Castle) derives a key that only wraps the vault key |
| Header | Salt and cost settings are authenticated; changing them makes the file fail to open |
| Biometric unlock | An Android Keystore key, usable only after a strong biometric check, wraps a copy of the vault key |
| Storage | One file in app-private storage, written atomically; excluded from cloud backup and device transfer |

The master password is never stored. There is no recovery if it is forgotten.

The reminder job runs while the vault is locked, so it only knows *when* passwords fall due, not
which ones. Those due times are the one thing kept outside the encrypted file.

## Project layout

- `core/` is plain Kotlin with no Android code: file format, encryption, generator, policies,
  reminder rules. It has 32 unit tests, including the Argon2id test vector from RFC 9106 and a
  test that flips every byte of a vault file and checks none of them opens.
- `app/` is the Android app (Jetpack Compose).

## Build

**GitHub Actions:** push to `main`. The workflow runs the core tests, builds the release APK
(shrunk with R8) and attaches it to the run as `tijori-release-apk`.

**Android Studio:** open the folder and run the `app` configuration. Or from a terminal:

    ./gradlew :core:test
    ./gradlew :app:assembleRelease     # small APK
    ./gradlew :app:assembleDebug       # larger, quicker to build

Needs JDK 17. Minimum Android version is 8.0 (API 26).

### Sign with your own key (do this once)

Android only installs an update over an existing app when both are signed with the same key, and
uninstalling the app deletes the vault. Without a key of your own, every GitHub Actions build is
signed with a new throwaway key, so builds cannot update each other.

1. Create a key on your computer and keep the file somewhere safe. If you lose it, future builds
   cannot update the installed app.

       keytool -genkeypair -v -keystore tijori.jks -alias tijori -keyalg RSA -keysize 4096 -validity 10000

2. In the GitHub repo, under Settings, Secrets and variables, Actions, add:

   | Secret | Value |
   |---|---|
   | `TIJORI_KEYSTORE_BASE64` | output of `base64 -w0 tijori.jks` (macOS: `base64 -i tijori.jks`) |
   | `TIJORI_KEYSTORE_PASSWORD` | the keystore password |
   | `TIJORI_KEY_ALIAS` | `tijori` |
   | `TIJORI_KEY_PASSWORD` | the key password |

Never commit `tijori.jks`; `.gitignore` already excludes it.

Even with a fixed key, **export a backup before installing a new build**.

## Status

- `core` was compiled and all 32 tests pass.
- `app` has not been compiled or run on a device yet; the first build may need small fixes.
- Until it has run for a while and you have restored a backup successfully at least once, keep
  your passwords in your current password manager as well.

## Known limits

- The clipboard is cleared only if the app is still alive after 30 seconds.
- Text typed into fields lives in memory as ordinary strings until Android reclaims it.
- A phone that is rooted or infected while the vault is unlocked can read it; no app can prevent that.

## Not built yet

Autofill in other apps, 2FA codes (TOTP), passphrase generator, breach check, CSV import from
Chrome or Bitwarden.
