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

## Project layout

```
skillswap/
├── pom.xml                         Maven build (PostgreSQL driver, optional JavaFX video, runnable jar)
├── database/schema.sql             Tables (the app also creates them automatically on first connect)
├── lib/postgresql-42.7.4.jar       JDBC driver for building without Maven
├── scripts/                        run-dev, package (installer), build-no-maven  (.sh and .bat)
├── .env.example  .gitignore
└── src/main/java/campus/skillswap/
    ├── Main.java                   Entry point
    ├── config/AppConfig.java       ~/.skillswap/config.properties (DB connection, theme)
    ├── core/AppState.java          Loaded data snapshot + signed-in user
    ├── model/                      User, Post, Sess, Msg, Report, Store, Match
    ├── data/                       Database (SQL, polling, saving) + Seed (demo data)
    ├── net/LanSync.java            Host/Join over Wi-Fi/LAN (no database needed)
    ├── service/                    Stats (ratings, badges, matching), Skills (categories), Security (hashing)
    └── ui/
        ├── Theme, AppShell, Notifier       Look & feel, main window, toasts/tray/reminders
        ├── screens/                Login, Connection, Feed, Search, Certs, Bookings, Nearby, Messages, Profile, Settings
        └── media/                  Videos, Reels, VideoPlayerFX (optional JavaFX player)
```

## Two ways to connect

On start the app asks how to connect:

1. **Host on this Wi-Fi / LAN** - no database needed. Best for testing with friends/classmates.
   One computer chooses *Host*; the app shows its address, port and a 4-digit join code.
   Everyone else on the same Wi-Fi chooses **Join a host** and enters those three values.
   Accounts, posts, bookings, chat and reels are shared while the host's app stays open.
   The host saves its data in `~/.skillswap/lan-data.bin` (videos in `~/.skillswap/lan-videos`), so it is still there next time.
   If Windows/macOS asks, allow Java through the firewall. If two people edit the same row at the same instant, the last edit wins.
2. **Shared PostgreSQL database** - for real deployment (see below).

## Run it (development)

Requirements: JDK 17+ and Maven, and a PostgreSQL server you can reach.

```
mvn exec:java            # or scripts/run-dev.sh  /  scripts\run-dev.bat
```

No Maven? `scripts/build-no-maven.sh` (or `.bat`) compiles with plain `javac` and the jar in `lib/`
(videos then can not be played, because the in-app player needs JavaFX - use run-dev).

If you pick PostgreSQL, enter host, port, database, user and password. The database and all tables are created
automatically, and demo data is added if it is empty. Settings are remembered per computer in `~/.skillswap/config.properties`.
Demo accounts (password `demo`): ramos, alamag, henry, jeb, chinu, dino, jamesa, kylet.

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

## Videos: automatic conversion (recommended)
The in-app player only plays MP4 with H.264 video + AAC audio. Install ffmpeg once and SkillSwap converts every
uploaded video to that format for you (progress shows in the status bar; the original file is not changed):

* Windows: double-click `scripts\get-ffmpeg.bat` (downloads ffmpeg into `lib\ffmpeg`), or run `winget install Gyan.FFmpeg`.
* macOS / Linux: `brew install ffmpeg` / `sudo apt install ffmpeg`.

In the New post / New reel window, "Convert for in-app playback" is ticked by default when ffmpeg is found.
The file picker's Preview panel and video thumbnails also use ffmpeg when it is available.
