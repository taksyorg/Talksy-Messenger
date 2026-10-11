<div align="center">

# Talksy Bot API

**Build bots, group moderators and Mini Apps for the Talksy messenger.**

`Bot API v2` &nbsp;·&nbsp; `HTTP + JSON` &nbsp;·&nbsp; `Long polling & webhooks` &nbsp;·&nbsp; `Group moderation`

[Quick start](#quick-start) · [Core concepts](#core-concepts) · [Updates](#updates) · [Method reference](#method-reference) · [Moderation](#group-moderation) · [Errors](#errors) · [Limits](#limits) · [Upgrading](#upgrading-a-server)

</div>

---

## Table of contents

1. [Overview](#overview)
2. [Quick start](#quick-start)
3. [Core concepts](#core-concepts)
4. [Updates](#updates)
5. [Method reference](#method-reference)
   - [Bot information](#bot-information)
   - [Sending messages](#sending-messages)
   - [Sending files](#sending-files)
   - [Editing and deleting](#editing-and-deleting)
   - [Buttons and keyboards](#buttons-and-keyboards)
   - [Group moderation](#group-moderation)
   - [Chat and member information](#chat-and-member-information)
   - [Webhooks](#webhooks)
   - [Mini Apps](#mini-apps)
6. [Example bots](#example-bots)
7. [Errors](#errors)
8. [Limits](#limits)
9. [Security](#security)
10. [Compatibility with v1](#compatibility-with-v1)
11. [Known limitations](#known-limitations)
12. [Upgrading a server](#upgrading-a-server)

---

## Overview

A Talksy bot is a regular account flagged as a bot. Your code runs **on your own machine or server** and talks to the Talksy server over HTTPS. The bot never runs inside the messenger.

```
 ┌──────────┐    messages     ┌────────────────┐   getUpdates / webhook   ┌────────────┐
 │  Users   │ ──────────────▶ │ Talksy server  │ ───────────────────────▶ │  Your bot  │
 │ (the app)│ ◀────────────── │   (Bot API)    │ ◀─────────────────────── │   (code)   │
 └──────────┘     replies     └────────────────┘   sendMessage, ban, ...  └────────────┘
```

What bots can do:

| Area | Capabilities |
| --- | --- |
| **Conversations** | Receive and send text in private chats and groups, reply to specific messages |
| **Files** | Send photos, videos, audio, voice messages, documents and animations |
| **Interface** | Inline buttons (text, link, Mini App, callback), persistent keyboard under the input field |
| **Message control** | Edit and delete their own messages, bulk-delete messages in groups |
| **Moderation** | Ban, unban, kick, mute, unmute and warn members with automatic punishments |
| **Events** | Members joining, leaving, being muted or banned |
| **Mini Apps** | Full web interfaces opened inside the messenger |

---

## Quick start

### 1. Create a bot

Open **BotFather** in the app, create a bot and copy its token. A token looks like this:

```
123456:AbCdEfGhIjKlMnOpQrStUvWxYz0123456789
```

> **⚠️ Keep it secret.** Anyone with the token fully controls the bot. If it leaks, regenerate it in the bot's settings.

### 2. Check that it works

```bash
export BASE="https://your-server.example"
export TOKEN="123456:AbCdEfGhIjKlMnOpQrStUvWxYz0123456789"

curl "$BASE/bot$TOKEN/getMe"
```

```json
{ "ok": true, "result": { "id": 123456, "name": "My Bot", "username": "@my_bot", "is_bot": true } }
```

### 3. Write an echo bot

```python
import os, requests

BASE  = os.environ["TALKSY_API_BASE"].rstrip("/")
API   = f"{BASE}/bot{os.environ['TALKSY_BOT_TOKEN']}"

def call(method, **params):
    data = requests.post(f"{API}/{method}", json=params, timeout=40).json()
    if not data.get("ok"):
        raise RuntimeError(data.get("error"))
    return data["result"]

offset = 0
while True:
    for update in call("getUpdates", offset=offset, timeout=25):
        offset = update["update_id"] + 1
        msg = update.get("message")
        if msg and msg.get("text"):
            call("sendMessage", chat_id=msg["chat"]["id"], text=f"You said: {msg['text']}")
```

Send your bot a message in the app and it answers. Ready-made bots are described in [Example bots](#example-bots).

---

## Core concepts

### Request format

Every method is available at

```
{BASE}/bot{TOKEN}/{method}
```

Parameters may be sent as a **JSON body**, as **form fields** (needed for file uploads) or in the **query string**. Methods accept both `GET` and `POST`.

### Response format

```jsonc
// success
{ "ok": true, "result": { ... } }

// failure
{ "ok": false, "error": "Human-readable message", "error_code": 403 }
```

> **ℹ️ Note** — a few older methods (`setKeyboard`, `setWebhook`, `deleteWebhook`) answer with just `{ "ok": true }` and do not include `error_code` on failure.

### Chat identifiers

| Chat type | `chat_id` | Example |
| --- | --- | --- |
| Private chat | the **user id** (positive) | `4821` |
| Group | **minus** the group id (negative) | `-17` |
| Channel | **minus** the channel id (negative) | `-23` |

The `chat.type` field in updates is `private`, `group` or `channel`.

### Member statuses

`owner` · `administrator` · `member` · `restricted` (muted) · `kicked` (banned) · `left`

### Who may a bot moderate?

Moderation methods require the bot to be an **administrator** of the group. A bot can never act on the group **owner**, on **other administrators**, or on **itself**. Add the bot as an administrator from *My groups → Add bot*.

---

## Updates

Bots receive events through **long polling** (`getUpdates`) or **webhooks** (`setWebhook`). Every update has an `update_id` and exactly one event object.

```json
{ "update_id": 98213, "message": { ... } }
```

### `message`

A message sent to the bot in a private chat, or by any member in a group the bot belongs to. Messages sent by bots are not delivered to other bots.

```json
{
  "update_id": 98213,
  "message": {
    "message_id": 5521,
    "date": 1791640230,
    "text": "/ban @spammer 1d advertising",
    "chat": { "id": -17, "type": "group", "title": "Talksy Team", "username": "talksy_team" },
    "from": { "id": 4821, "name": "Dany", "username": "dany", "is_bot": false },
    "reply_to_message_id": 5519,
    "media": { "type": "image", "url": "/media/9f3c.jpg", "filename": "photo.jpg", "size": 48213 },
    "encrypted": false
  }
}
```

| Field | Description |
| --- | --- |
| `message_id` | Id of the message **within its chat type** (private and group ids are separate sequences) |
| `chat.id` | Private chat: the sender's user id. Group: negative group id |
| `reply_to_message_id` | Present when the message is a reply (private chats) |
| `media` | Present when a file is attached: `type` is `image`, `video`, `audio`, `voice` or `file` |
| `encrypted` | `true` when a group message uses end-to-end encryption. `text` is empty because the bot cannot read it |

### `callback_query`

A user pressed an inline button created with `callback_data`.

```json
{
  "update_id": 98214,
  "callback_query": {
    "id": "311",
    "from": { "id": 4821, "name": "Dany", "username": "dany", "is_bot": false },
    "data": "confirm:order:42",
    "message": { "message_id": 5530, "chat": { "id": 4821, "type": "private" }, "text": "Confirm the order?" }
  }
}
```

Answer within **4 seconds** with [`answerCallbackQuery`](#answercallbackquery) so the user sees feedback.

### `chat_member`

A membership change in a group the bot belongs to: someone joined, left, was kicked, banned, muted or unmuted.

```json
{
  "update_id": 98215,
  "chat_member": {
    "chat": { "id": -17, "type": "group", "title": "Talksy Team" },
    "from": { "id": 4821, "name": "Dany", "username": "dany", "is_bot": false },
    "user": { "id": 9120, "name": "Alex", "username": "alex", "is_bot": false },
    "old_status": "left",
    "new_status": "member",
    "until_date": 0,
    "date": 1791640300
  }
}
```

`from` is the person who caused the change (for a join it is the user themselves). `until_date` is a Unix timestamp for temporary mutes and bans, or `0`.

### Polling semantics

- `getUpdates` returns updates with `update_id >= offset`, oldest first.
- Passing `offset = last_update_id + 1` **confirms** everything before it; confirmed updates are deleted.
- Unconfirmed updates are kept for **3 days**.
- With `timeout` the server holds the request open (up to 25 s) until something arrives.

---

## Method reference

All parameters are required unless marked *optional*.

### Bot information

#### `getMe`

Returns the bot's own account.

| Result field | Description |
| --- | --- |
| `id`, `name` | Bot account id and display name |
| `username` | Handle, returned **with** a leading `@` |
| `is_bot` | Always `true` |

#### `resolveUsername`

Finds a user by handle. Useful for commands such as `/ban @user`.

| Parameter | Description |
| --- | --- |
| `username` | Handle with or without `@` |

**Result:** `{ "id", "name", "username", "is_bot" }`

---

### Sending messages

#### `sendMessage`

| Parameter | Type | Description |
| --- | --- | --- |
| `chat_id` | integer | Private user id, or negative group id |
| `text` | string | 1–4096 characters |
| `reply_markup` | object | *Optional.* Inline keyboard, see [Buttons](#buttons-and-keyboards) |
| `reply_to_message_id` | integer | *Optional.* Private chats only; must belong to the same conversation |
| `parse_mode` | string | *Optional.* `HTML` strips tags and shows plain text. Rich formatting is [not yet rendered](#known-limitations) |

**Result:**

```json
{ "message_id": 5530, "chat": { "id": 4821, "type": "private" }, "text": "Hello!", "date": 1791640400 }
```

**Typical errors:** `403` user blocked the bot, bot is not a member of the group, or only administrators may post (channels and *admins-only* groups); `404` user or group not found.

```bash
curl -X POST "$BASE/bot$TOKEN/sendMessage" -H "Content-Type: application/json" \
  -d '{"chat_id": 4821, "text": "Hello from the Bot API 👋"}'
```

---

### Sending files

| Method | Sent as | `media.type` |
| --- | --- | --- |
| `sendPhoto` | picture | `image` |
| `sendVideo` | video | `video` |
| `sendAnimation` | looping video | `video` |
| `sendAudio` | audio file | `audio` |
| `sendVoice` | voice message | `voice` |
| `sendDocument` | any file | `file` |

| Parameter | Description |
| --- | --- |
| `chat_id` | Target chat |
| file | One of: multipart field `file` · `file_base64` with `filename` · a server path such as `/media/…` in the field named after the method (`photo`, `video`, …) |
| `caption` | *Optional.* Up to 1024 characters |
| `reply_markup` | *Optional.* Inline keyboard |
| `duration` | *Optional.* Seconds, for `sendVoice` |

```bash
# multipart upload
curl -X POST "$BASE/bot$TOKEN/sendDocument" \
  -F chat_id=4821 -F caption="Monthly report" -F file=@report.pdf
```

Files are encrypted on the server's disk. Maximum size is **50 MB**.

---

### Editing and deleting

A bot can edit and delete **its own** messages.

#### `editMessageText`

| Parameter | Description |
| --- | --- |
| `chat_id`, `message_id` | The bot's message |
| `text` | New text (1–4096 characters) |
| `reply_markup` | *Optional.* Omit to keep current buttons; send `{}` to remove them |

The message shows an *edited* mark. In private chats the change appears live.

#### `editMessageReplyMarkup`

Replaces or removes only the buttons.

| Parameter | Description |
| --- | --- |
| `chat_id`, `message_id` | The bot's message |
| `reply_markup` | New keyboard; omit or send `{}` to remove |

#### `deleteMessage`

| Parameter | Description |
| --- | --- |
| `chat_id`, `message_id` | Message to delete |

Private chats: own messages only. Groups: own messages, or **any** member's message when the bot is an administrator (messages of the owner and administrators are protected).

#### `purgeChatMessages`

Bulk clean-up in a group. *Requires administrator.*

| Parameter | Description |
| --- | --- |
| `chat_id` | Group |
| `limit` | *Optional.* 1–200, default 50 |
| `user_id` | *Optional.* Delete only this member's messages |

**Result:** `{ "deleted": 12 }`

---

### Buttons and keyboards

#### Inline keyboard (`reply_markup`)

Buttons appear under a message.

```json
{
  "inline_keyboard": [
    [ { "text": "✅ Confirm", "callback_data": "confirm:42" },
      { "text": "❌ Cancel",  "callback_data": "cancel:42" } ],
    [ { "text": "Open website", "url": "https://example.com" } ],
    [ { "text": "Open shop", "mini_app": true } ]
  ]
}
```

| Button type | Fields | Behaviour |
| --- | --- | --- |
| Text | `text` | Sends the button's text back to the bot as a normal message |
| Link | `text`, `url` | Opens the link (`http://` or `https://`) |
| Mini App | `text`, `mini_app: true` | Opens the bot's [Mini App](#mini-apps) |
| Callback | `text`, `callback_data` | Sends a `callback_query` update to the bot, no message appears in the chat |

Limits: up to **12 rows** of **6 buttons**; button text up to 64 characters; `callback_data` up to **64 bytes**.

#### `answerCallbackQuery`

| Parameter | Description |
| --- | --- |
| `callback_query_id` | The `id` from the `callback_query` update |
| `text` | *Optional.* Up to 200 characters, shown to the user |
| `show_alert` | *Optional.* `true` shows a dialog instead of a brief toast |

The client waits up to 4 seconds for the answer.

#### `setKeyboard`

A persistent menu displayed **under the input field** in the bot's private chat. Pressing a button sends its text as a message. It stays hidden until the user taps **Start**, and collapses while the system keyboard is open.

```json
{ "buttons": [["Catalog", "Balance"], ["Help"]] }
```

Up to **8 rows** of **4 buttons**, each label up to 32 characters. Send `{ "buttons": null }` to remove the menu.

---

### Group moderation

*All methods in this section require the bot to be an administrator of the group.* Durations are given either as `duration` (seconds from now) or `until_date` (Unix timestamp). Omit both for a permanent action.

| Method | Parameters | Result |
| --- | --- | --- |
| `banChatMember` | `chat_id`, `user_id`, `duration` / `until_date`, `reason` *(opt.)* | `{ "banned": true, "until_date": 0 }` |
| `unbanChatMember` | `chat_id`, `user_id` | `{ "unbanned": true }` |
| `kickChatMember` | `chat_id`, `user_id` | `{ "kicked": true }` |
| `restrictChatMember` | `chat_id`, `user_id`, `duration` / `until_date`, `reason` *(opt.)* | `{ "muted": true, "until_date": 1791726000 }` |
| `unmuteChatMember` | `chat_id`, `user_id` | `{ "unmuted": true }` |
| `warnChatMember` | `chat_id`, `user_id`, `reason`, `limit`, `action`, `action_duration` *(opt.)* | `{ "warns": 2, "limit": 3, "action": null }` |
| `unwarnChatMember` | `chat_id`, `user_id` | `{ "warns": 1 }` |
| `getChatWarns` | `chat_id`, `user_id` | `{ "count": 2, "warns": [ { "reason", "date", "by_id" } ] }` |
| `resetChatWarns` | `chat_id`, `user_id` | `true` |

**What each action does**

- **Ban** removes the member and blocks re-joining and posting until it expires. Bans can be issued in advance for someone who is not (yet) a member.
- **Kick** removes the member without a ban; they may return.
- **Mute** (`restrictChatMember`) keeps the member in the group but prevents posting. Passing `can_send_messages: true` lifts the mute.
- **Warn** records a warning. When the count reaches `limit` (default **3**, range 1–20) the server applies `action`: `mute` (default), `ban`, `kick` or `none`, for `action_duration` seconds (default **86400** = 1 day, `0` = permanent). Warnings are then reset.

Enforcement happens **on the server**, not in the bot: muted users receive an error when they try to post, and banned users cannot rejoin through any join route.

```bash
# mute a member for 30 minutes
curl -X POST "$BASE/bot$TOKEN/restrictChatMember" -H "Content-Type: application/json" \
  -d '{"chat_id": -17, "user_id": 9120, "duration": 1800, "reason": "flood"}'
```

---

### Chat and member information

| Method | Parameters | Result |
| --- | --- | --- |
| `getChat` | `chat_id` | Group: `id`, `type`, `title`, `username`, `description`, `members_count`. Private: `id`, `type`, `name`, `username`, `is_bot` |
| `getChatMember` | `chat_id`, `user_id` | `{ "user": {…}, "status": "restricted", "until_date": 1791726000, "warns": 1 }` |
| `getChatMembers` | `chat_id`, `limit` *(≤200)*, `offset` *(opt.)* | Array of member objects. *Requires administrator* |
| `getChatAdministrators` | `chat_id` | Array of member objects for the owner and administrators |
| `getChatMemberCount` | `chat_id` | Integer |

---

### Webhooks

Instead of polling, the server can `POST` every update to your URL.

| Method | Parameters |
| --- | --- |
| `setWebhook` | `url` — must start with `http://` or `https://` |
| `deleteWebhook` | — |

The request body is `{ "update_id": 98213, "<event>": { … } }`, where `<event>` is `message`, `callback_query` or `chat_member`. Delivery is best effort with a 5-second timeout and no retries.

> **ℹ️ Note** — webhook requests are not signed. Use an HTTPS URL with a long secret path (for example `/hook/9d3f7a…`) and reject everything else.

---

### Mini Apps

#### `setMiniApp`

Registers the web application opened by the **Open Mini App** button.

| Parameter | Description |
| --- | --- |
| `url` | `https://…` (plain `http://` is allowed only for `localhost` during development) |
| `name` | *Optional.* Title shown to the user |
| `permissions` | *Optional.* Subset of `profile`, `wallet_read`, `wallet_pay`, `wallet_transfer`, `users_search`, `send_message`. `profile` is always included |

Each Mini App session is bound to one user and one bot on the server and expires after one hour. A Mini App can never choose which user it acts on, and any call outside its granted permissions is rejected. See `MINI_APPS.md` for the JavaScript bridge.

---

## Example bots

Both bots use only the `requests` library.

| File | Purpose |
| --- | --- |
| `talksy_bot.py` | Minimal starter: `/start`, `/help`, `/ping`, `/time`, echo, persistent menu |
| `talksy_mod_bot.py` | Full group moderator |

Configuration for both:

```bash
pip install requests
export TALKSY_API_BASE="https://your-server.example"   # no /bot… suffix, no trailing path
export TALKSY_BOT_TOKEN="123456:SECRET"
python3 talksy_mod_bot.py
```

On start the moderator checks the address, the token and that the server supports Bot API v2, and explains any problem it finds.

### Moderator commands

Available to group administrators. Targets are `@username` or a numeric id. Durations: `30m`, `2h`, `1d`, `1w` (omit for permanent).

| Command | Effect |
| --- | --- |
| `/ban @user [duration] [reason]` | Ban |
| `/unban @user` | Lift a ban |
| `/kick @user` | Remove without a ban |
| `/mute @user [duration] [reason]` | Mute |
| `/unmute @user` | Lift a mute |
| `/warn @user [reason]` | Warning; punishment at the limit |
| `/unwarn @user` | Remove the latest warning |
| `/warns @user` | List warnings |
| `/purge N [@user]` | Delete the last *N* messages, optionally only one member's |
| `/info @user` | Status and warnings |
| `/help` | Command list |

### Automatic moderation

Applied to non-administrators and configurable at the top of the script:

| Setting | Default | Effect |
| --- | --- | --- |
| `ANTI_LINKS` | on | Deletes messages with links and issues a warning |
| `BAD_WORDS` | sample list | Deletes messages containing listed words and warns |
| `FLOOD_MESSAGES` / `FLOOD_SECONDS` | 6 / 8 | Mutes for 5 minutes when exceeded |
| `WARN_LIMIT`, `WARN_ACTION`, `WARN_ACTION_SECONDS` | 3, `mute`, 1 day | Punishment at the warning limit |
| `WELCOME` | greeting text | Message sent when someone joins |

---

## Errors

| HTTP | Meaning | Typical cause |
| --- | --- | --- |
| `400` | Invalid request | Missing or malformed parameter, text too long, bad `reply_markup` |
| `401` | Invalid token | Wrong or regenerated token |
| `403` | Forbidden | Bot is not an administrator, target is protected, user blocked the bot, bot not in the group |
| `404` | Not found | Unknown user, group or message |

> **🔎 Troubleshooting: "Expecting value: line 1 column 1"** — the server answered with a non-JSON page. The address is wrong, or the server does not have Bot API v2 installed (an old server answers `405` to `POST getUpdates` and `404` to new methods). Check `TALKSY_API_BASE`, then follow [Upgrading a server](#upgrading-a-server).

---

## Limits

| Item | Limit |
| --- | --- |
| Message text | 4096 characters |
| Caption | 1024 characters |
| File size | 50 MB |
| Inline keyboard | 12 rows × 6 buttons, button text ≤ 64 characters |
| `callback_data` | 64 bytes |
| Persistent keyboard | 8 rows × 4 buttons, label ≤ 32 characters |
| `getUpdates` | `limit` ≤ 100, `timeout` ≤ 25 s |
| Unconfirmed updates | kept 3 days |
| Callback answer window | 4 s |
| `purgeChatMessages` | 200 messages per call |
| Warning limit | 1–20 |

---

## Security

- **Treat the token like a password.** Keep it in an environment variable, never in source control or screenshots. Regenerate it in the bot's settings if it leaks.
- **Run bots with the least privilege.** Add moderators as administrators only to the groups they manage.
- **Never trust user text as a command source** without checking the sender's status with `getChatMember`.
- **Messages are encrypted on the server** with a server-side key so that bots can read them. Group messages that members send with end-to-end encryption are unreadable to bots by design.
- **Secure webhooks** with HTTPS and an unguessable path, since requests are not signed.

---

## Compatibility with v1

Bot API v2 keeps every v1 address and method name. Differences worth knowing:

| Topic | v1 | v2 |
| --- | --- | --- |
| `chat.id` in updates | Mistakenly contained the **bot's own id**, so replies went nowhere | The **user's id** in private chats, a negative id in groups |
| `getUpdates` | `GET` only, messages only | `GET` and `POST`, all event kinds |
| `update_id` | Message id | Own counter, always higher than any previous value; old pending messages are not replayed |
| Errors | `error` only | `error` and `error_code` |
| Webhook body | Message only | `update_id` plus the event under its kind |

Bots that already answered using `message["from"]["id"]` keep working unchanged.

---

## Known limitations

| Area | Status |
| --- | --- |
| Rich text (bold, italic, code, links in text) | Not rendered yet; `parse_mode: "HTML"` strips tags |
| Callback buttons in **groups** | The client shows inline buttons in private chats only |
| Live refresh of edited or deleted **group** messages | Appears after reopening the group |
| Reading end-to-end encrypted group messages | Not possible; updates carry `encrypted: true` |
| Replies to a message in groups | Not supported, so moderation targets are given by `@username` or id |
| Polls, quizzes, payments, command menu, inline mode | Not available yet |

---

## Upgrading a server

Bot API v2 ships as a module that plugs into `server.py` the same way as the other server modules. Each script checks everything in memory first, writes a backup, and stops without changes if something does not match.

```bash
# 1. Optional: bot profile photos
python3 patch_talksy_server_bot_avatar.py /path/to/server.py

# 2. Bot API v2: groups, moderation, callbacks, media
python3 patch_talksy_server_botapi_v2.py  /path/to/server.py
```

Add `--dry-run` to either command to check without writing. Then **restart the server**. The console must print:

```
BOTAPI V2 LOADED
```

### Android client

```bash
python3 patch_talksy_bot_ux.py            /path/to/talksy-android-updated   # Start button, menu under input, bot photo
python3 patch_talksy_client_callbacks.py  /path/to/talksy-android-updated   # callback buttons
```

Rebuild the app in Android Studio afterwards.

---

<div align="center">

**Talksy Bot API v2** &nbsp;·&nbsp; documentation

</div>
