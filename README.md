# ReadVoice

A free, open-source Android app for managing your Reddit inbox and, in later
phases, listening to Reddit hands-free with the text-to-speech engine already on
your phone. No server, no ads, no paid APIs.

> **Status: Phase 1.** Login, inbox and private messages are built and unit
> tested. The app has not yet been run against a live Reddit account, because
> Reddit now requires approval before a new API app can be created. See
> [Reddit access](#reddit-access).

## What works today (Phase 1)

- **Log in with Reddit** in the system browser. The app never sees your password.
- **Inbox tab**: comment replies, post replies and mentions, each labelled by type.
- **Private tab**: private messages grouped into one conversation per person,
  shown as a read-only chat.
- **Appearance**: Black (AMOLED) and White (daylight) themes, four fonts
  including Atkinson Hyperlegible, and five text sizes.
- **Setup screen** for entering a Reddit client ID, with a format check and a
  "Check with Reddit" button.

Phase 1 is read-only: it does not post, vote, send, delete or mark anything as read.

## Roadmap

The app is being built step by step. Each phase ends with something installable
and testable.

| Phase | Scope |
| --- | --- |
| 1 (built) | Login, inbox, private messages, appearance, setup |
| 2 | Search, sort and filter your messages (on the phone) |
| 3 | Browse and search posts, subreddits and redditors |
| 4 | Read posts, comments and messages aloud with on-device text-to-speech |
| 5 | Write and reply to messages, post to a subreddit, follow profiles and subreddits |
| 6 | Delete your own messages, posts and comments, with preview and confirmation |

## Security and privacy

- **No server.** The app talks directly from your phone to Reddit. The
  developer never receives any of your data.
- **OAuth in the browser.** Login uses Reddit's authorization-code flow in a
  browser tab, with a one-time `state` value so a forged or replayed redirect
  is rejected.
- **Encrypted session.** The login token is encrypted with AES-256-GCM using a
  key held in the Android Keystore, and is revoked and wiped on logout.
- **Nothing in backups.** Android backup and device transfer are disabled for
  the app's data.
- **HTTPS only.** Cleartext traffic is blocked, and the token is attached only
  to requests for Reddit's API host.
- **Least privilege.** Phase 1 asks Reddit for `identity` and `privatemessages`
  only.
- **Messages stay in memory.** Reddit content is not written to storage, and is
  shown as plain text.
- **No tracking, no analytics, no AI training.**

## Reddit access

Reddit's API needs a client ID that identifies the app. Since November 2025
Reddit requires approval under its
[Responsible Builder Policy](https://support.reddithelp.com/hc/en-us/articles/42728983564564-Responsible-Builder-Policy)
before a new app can be created, and an approval request for ReadVoice is
being prepared.

If you already have a client ID for a Reddit **installed app** whose redirect
URI is `readvoice://oauth`, you can enter it on the app's setup screen.

## Building

Requirements: JDK 17 and the Android SDK (platform 34).

```
./gradlew assembleDebug      # build the APK
./gradlew testDebugUnitTest  # run the unit tests
./gradlew installDebug       # install on a connected phone
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Developers can skip the setup screen in debug builds by adding their client ID
to `local.properties`, which is ignored by git:

```
reddit.clientId=your_client_id
```

Release builds never contain a client ID.

## Project structure

Native Kotlin with classic Android Views, chosen to stay light on low-RAM
phones. One app module, three layers:

```
app/src/main/java/com/readvoice/app/
├── domain/   models and interfaces; no Android imports
├── data/     Reddit API, OAuth, encrypted storage, settings
├── ui/       screens and view models
└── di/       AppContainer, the one place implementations are wired together
```

The UI and data layers depend on the interfaces in `domain`, never on each
other, so each piece can be tested with fakes.

Minimum Android version: 7.0 (API 24).

## Licence

ReadVoice is released under the [MIT Licence](LICENSE).

The bundled Atkinson Hyperlegible font is copyright Braille Institute of
America, Inc. and licensed under the SIL Open Font License 1.1; see
[app/ATKINSON-OFL.txt](app/ATKINSON-OFL.txt).

ReadVoice is an independent project and is not affiliated with or endorsed by
Reddit, Inc.
