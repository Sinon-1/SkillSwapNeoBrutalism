# SkillSwap Campus

Peer-to-peer skill-swap desktop app for students (Java Swing + PostgreSQL).
Students list what they can teach and want to learn, get matched, book sessions, chat, share reels, rate each other and earn badges.

## Architecture

```
 Desktop app (Java Swing, runs on every student's computer)
        |  JDBC (PostgreSQL protocol)
        v
 PostgreSQL database (shared: accounts, posts, reels, bookings, chat)
```

There is no separate application server yet: each desktop app talks to PostgreSQL directly
(see **Security notes** before deploying to real users).

## Video & voice calls

Open a chat in **Messages** and press **Video call** or **Voice call** (the other person must be online).
They get a ringing window with Accept / Decline; during a call you can mute, turn the camera on/off and end the call.
Call history ("Video call (2:31)", "Missed video call") is added to the chat.

* **Setup** (ring / decline / hang up) goes through the shared PostgreSQL database (table `call_signals`) or the LAN host.
* **Picture and sound** go straight from computer to computer over TCP, so both computers must be on the same network
  (campus Wi-Fi/LAN) and the firewall must allow SkillSwap the first time it asks.
* **Sound** uses plain Java Sound (no install). **Camera** uses ffmpeg: run `scripts/get-ffmpeg` once. Without ffmpeg or a camera the call still works as a voice call.
* Use headphones on laptops, otherwise the speakers echo into the microphone.

## Group chat, group calls, emotes and raise hand

* **Group chat** - in **Messages** press **+ Group**, name it and tick at least 2 people. Groups appear in the chat list next to normal chats
  (tag "GROUP"). Messages can be edited / unsent like normal ones, other people's names show above their bubbles, and notices
  ("Ana created the group", "Group video call ended (12:30)") appear as green tags. **Members** lets anyone add people or leave;
  the creator can also rename the group and remove people. Group messages are normal rows in `messages` with `to_user = 'g:<id>'`;
  the groups themselves are in `chat_groups` / `chat_group_members` (created automatically).
* **Group video call** - press **Group call** in a group chat (up to 6 people, online members only). If nobody is in the call yet, the other
  online members get a ringing window with **Join call**; if a call is already running you simply join it, like in Messenger: the group chat shows a yellow **Call in progress (3 of 6): Ana, Ben** strip with a **Join** button, the header button turns into **Join call**,
  and the chat list tags the group "IN A CALL". Joining a running call never rings anybody; when you are in it the button turns into **Back to call**. A full call (6 people) shows **Call is full**.
  (Behind the scenes everyone in a call sends a small "still running" signal every 5 seconds to the group members who are not in it yet, and a "left" signal when they hang up.)
  Everybody connects directly to everybody (no server), exactly like 1-to-1 calls, so use headphones and stay on the same network.
  A green border shows who is speaking; a red mic icon shows who is muted.
* **Emotes** - press **EMOTES** during any call (1-to-1 or group) and pick Good, Love it, Haha, Wow, Got it!, Confused, Great job or Yay!.
  The sticker floats up from your picture on everybody's screen. (Drawn in code, so no emoji font is needed.)
* **Raise hand** - press **RAISE HAND**: a yellow hand appears on your picture and in the top bar, numbered in the order hands went up, so a tutor
  can call on students in turn. Press **LOWER HAND** to put it down.

### Booking modes

When booking a session you choose **In person**, **Offline** or **Online**.
* **In person** - meet on campus; *Details* shows the time and reminds you to settle the spot by chat.
* **Offline** - no live meeting; *Message* opens the chat with the other person.
* **Online** - tick **Request a video call** to hold the session as an in-app video call. The tutor sees the request (tag "VIDEO CALL") and, once confirmed, either of you can press **Video call** on the booking (or in the chat) to ring the other person.

## Moderation

Sign in with the seeded moderator account **campusmod** (password `demo`) to open the moderator-only **Reports & Moderation** console.
This account intentionally has no student features (Feed, Search, Bookings, Messages, Profile) - only report review, ban/unban and logout.
Moderators can dismiss reports, ban an account with a reason, see all banned accounts, and unban them.
A banned account is signed out on its next refresh and cannot sign in again; all of its open reports are closed when it is banned.
Existing databases get the `campusmod` account and the extra moderation columns automatically when the app connects.

## Project layout

```
skillswap/
├── pom.xml                         Maven build (PostgreSQL driver, optional JavaFX video, runnable jar)
├── database/schema.sql             Tables (incl. chat_groups, chat_group_members) (the app also creates them automatically on first connect)
├── lib/postgresql-42.7.4.jar       JDBC driver for building without Maven
├── scripts/                        run-dev, package (installer), build-no-maven  (.sh and .bat)
├── .env.example  .gitignore
└── src/main/java/campus/skillswap/
    ├── Main.java                   Entry point
    ├── config/AppConfig.java       ~/.skillswap/config.properties (DB connection, theme)
    ├── core/AppState.java          Loaded data snapshot + signed-in user
    ├── model/                      User, Post, Sess, Msg, Report, Store, Match, Group, CallSig
    ├── data/                       Database (SQL, polling, saving) + Seed (demo data)
    ├── net/LanSync.java            Host/Join over Wi-Fi/LAN (no database needed)
    ├── service/                    Stats (ratings, badges, matching), Skills (categories), Security (hashing)
    └── ui/
        ├── Theme, AppShell, Notifier       Look & feel, main window, toasts/tray/reminders
        ├── screens/                Login, Connection, Feed, Search, Certs, Bookings, Nearby, Messages, Profile, Settings
        └── media/                  Videos, Reels, VideoPlayerFX (optional JavaFX player)
```

## Connecting the database

On start the app opens the database connection screen directly. Enter the host, port, database, user and password
(for Supabase, copy these from **Project Settings > Database > Connection string**), then create an account or sign in.
The connection is remembered per computer in `~/.skillswap/config.properties`.

## Run it (development)

Requirements: JDK 17+ and Maven, and a PostgreSQL server you can reach.

```
mvn exec:java            # or scripts/run-dev.sh  /  scripts\run-dev.bat
```

No Maven? `scripts/build-no-maven.sh` (or `.bat`) compiles with plain `javac` and the jar in `lib/`
(videos then can not be played, because the in-app player needs JavaFX - use run-dev).

Enter the host, port, database, user and password. The database and all tables are created
automatically, and demo data is added if it is empty. Settings are remembered per computer in `~/.skillswap/config.properties`.
Demo accounts (password `demo`): ramos, alamag, henry, jeb, chinu, dino, jamesa, kylet, and moderator `campusmod`.

## Deploy

1. **Host the database** somewhere every student can reach (Supabase, Neon, Railway, a school server...).
   Create a database user for the app and give students the host/port/db/user/password.
2. **Build the installer** on the OS you are targeting:
   - Windows: `scripts\package.bat`  (needs the WiX Toolset for .exe/.msi, or switch to `--type app-image`)
   - macOS: `scripts/package.sh`  -> .dmg
   - Linux: `scripts/package.sh`  -> .deb
   The installer includes Java, so students install nothing else. Output is in `dist/`.
3. Students install the app, enter the database details once, and sign in.

`mvn package` alone produces `target/skillswap-campus.jar` (run with `java -jar`).

## Security notes (read before real deployment)

- Every client connects to the database directly, so every client holds the database login.
  Use a **dedicated limited database user** (not `postgres`/admin), never share your admin password, and treat
  the credentials as visible to anyone who has the app.
- Passwords are stored as SHA-256 hashes (salted only with the username). For a real launch, move to bcrypt/argon2
  and put a small backend API between the app and the database, so the DB password never leaves the server.
- Reels are stored inside PostgreSQL (BYTEA, max 60 MB each). Large volumes need object storage instead.
- Never commit `.env` or `config.properties` (both are in `.gitignore`).

## Videos: any format plays
Videos in any format (MKV, AVI, WebM, HEVC, odd MOV...) now play: the first time one is opened, SkillSwap converts it to
MP4 (H.264 + AAC) in the background and keeps the result in `~/.skillswap/cache/play`. On Windows ffmpeg is downloaded
automatically the first time it is needed (about 30 MB, once). On macOS / Linux install it first (`brew install ffmpeg` / `sudo apt install ffmpeg`).

## Videos: automatic conversion on upload (optional)
The in-app player only plays MP4 with H.264 video + AAC audio. Install ffmpeg once and SkillSwap converts every
uploaded video to that format for you (progress shows in the status bar; the original file is not changed):

* Windows: double-click `scripts\get-ffmpeg.bat` (downloads ffmpeg into `lib\ffmpeg`), or run `winget install Gyan.FFmpeg`.
* macOS / Linux: `brew install ffmpeg` / `sudo apt install ffmpeg`.

In the New post / New reel window, "Convert for in-app playback" is ticked by default when ffmpeg is found.
The file picker's Preview panel and video thumbnails also use ffmpeg when it is available.
