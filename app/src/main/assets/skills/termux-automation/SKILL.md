---
name: termux-automation
description: Run shell commands through the user's Termux app when no first-class tool fits — e.g. "git status in my project folder", "list downloads", "run ffmpeg on this file". Only used when the user has opted in under Settings → Advanced → Termux shell access.
---
# Termux Automation

Trigger on shell-flavoured requests that no first-class tool can answer:
"git status", "run python on …", "list files in …", "ffmpeg convert …",
"adb tap 500 800", "what's in ~/projects", "run the build script",
"`uname -a`", etc. Japanese equivalents: "シェルで…", "ターミナルで…",
"gitの状態", "ffmpegかけて".

**Do NOT use this skill for tasks a first-class tool already handles.**
Before reaching for `termux_shell_exec`, confirm none of these fit:

- Smart-home device control → `device_*` tools
- Files on the tablet storage (Downloads, Pictures) → `list_recent_*` tools
- Calendar, notifications, timers → `*_calendar_events` / `list_notifications` / `set_timer`
- Web search → `web_search` + `fetch_webpage`
- Any math / unit conversion / currency → `calculator` / `unit_converter` / `currency`

## Capability check

Before the user issues a shell request, the `termux_shell_exec` tool
either appears in your tool list or it doesn't. Three gates must all be
open:

1. Termux installed from F-Droid on this device
2. `com.termux.permission.RUN_COMMAND` granted to OpenDash
3. The user flipped `Settings → Advanced → Termux shell access` on

If the tool isn't in your list and the user asks for a shell command,
say: *"Shell access isn't enabled. You can turn it on in Settings →
Advanced → Termux shell access."* Do not try to synthesize `ls` output
from memory.

## Tool schema

```
termux_shell_exec(
  command: string,        // absolute binary path, required
  arguments?: string[],   // optional
  working_dir?: string,   // optional cwd
  timeout_ms?: integer    // optional, default 30000
)
```

The assistant MUST pass absolute binary paths — `/data/data/com.termux/files/usr/bin/git`,
not `git`. Termux's PATH is not the host PATH.

## Default flow

1. Resolve intent → pick the binary (`git`, `ls`, `python`, `ffmpeg`, `adb`, …)
2. Build the absolute path (`/data/data/com.termux/files/usr/bin/<binary>`)
3. Call `termux_shell_exec` with `command` + `arguments`
4. Report a short spoken summary of the result (never dump raw stdout
   unless the user explicitly asked)

## Examples

### Example: "git status in my project folder"

```
termux_shell_exec(
  command = "/data/data/com.termux/files/usr/bin/git",
  arguments = ["status", "--short"],
  working_dir = "/storage/emulated/0/Documents/projects/opendash"
)
```

Response (spoken): *"Three files changed, one untracked."* — do not
read the full `git status` wall of text.

### Example: "what's in my Downloads"

Prefer `list_recent_photos` / `list_recent_videos` for media; for
generic files:

```
termux_shell_exec(
  command = "/data/data/com.termux/files/usr/bin/ls",
  arguments = ["-lah"],
  working_dir = "/storage/emulated/0/Download"
)
```

Response (spoken): *"Eighteen files, most recent is `screenshot-2025-04-21.png`."*

### Example: "tap the screen at 500 800" (ADB self-control)

Only works if the user ran `adb tcpip 5555` once from a computer and
`adb connect localhost:5555` inside Termux. Otherwise the command exits
with *"no devices"*.

```
termux_shell_exec(
  command = "/data/data/com.termux/files/usr/bin/adb",
  arguments = ["shell", "input", "tap", "500", "800"]
)
```

Prefer `tap_by_text` (AccessibilityService) when the target is a
labelled button — adb shell input is only a fallback for apps where
a11y doesn't cooperate.

## Safety

- **Respect the allowlist.** If the tool returns *"Command X is not on
  the Termux allowlist"*, tell the user what's missing — do not try to
  work around it.
- **Never run destructive commands without spelling them out in the
  spoken confirmation.** If the user asks to delete something, say what
  you're about to run before calling the tool: *"I'm about to run
  `rm -rf ~/tmp`. Say yes to continue."* Wait for an affirmative.
- **Don't read back secrets.** If a command output contains tokens,
  keys, or passwords (`.env`, `git config`, `ssh-add`), summarize
  redacted: *"Your git user.email is set — I won't read the value
  out loud."*
- **Non-zero exit codes are failures.** A command that exits with
  `exit=1` is an error; say *"That didn't work — it returned exit
  code 1."* Don't claim success.

## Related skills

- `home-control` — smart-home devices (not shell)
- `quick-note` — voice notes (uses memory, not shell)
- `task-manager` — to-do items (uses memory, not shell)
