<div align="center">

# Talksy Bot API

**Build bots, moderators and Mini Apps for Talksy.**

[Quick start](#quick-start) · [Updates](#updates) · [Methods](#methods) · [Limits](#limits)

</div>

---

## Overview

A Talksy bot is an account controlled by your own code. The bot receives events and responds through a simple HTTPS + JSON API.

| | |
| --- | --- |
| **Chats** | Private chats and groups |
| **Content** | Text, photos, videos, audio, voice messages, documents |
| **Interface** | Inline buttons, callback buttons, a persistent menu |
| **Moderation** | Ban, mute, kick and warn members |
| **Extras** | Webhooks and Mini Apps |

---

## Quick start

**1. Create a bot** with BotFather in the app and copy its token.

> Keep the token private. Anyone who has it controls the bot.

**2. Make your first request**

```bash
curl "https://<host>/bot<TOKEN>/getMe"
```

**3. Reply to messages**

```python
import requests

API = "https://<host>/bot<TOKEN>"

def call(method, **params):
    return requests.post(f"{API}/{method}", json=params, timeout=40).json()["result"]

offset = 0
while True:
    for update in call("getUpdates", offset=offset, timeout=25):
        offset = update["update_id"] + 1
        msg = update.get("message")
        if msg and msg.get("text"):
            call("sendMessage", chat_id=msg["chat"]["id"], text="You said: " + msg["text"])
```

---

## Basics

**Request** — `https://<host>/bot<TOKEN>/<method>` with parameters as JSON, form fields or query string. `GET` and `POST` are both accepted.

**Response**

```json
{ "ok": true, "result": { } }
{ "ok": false, "error": "Description of the problem" }
```

**Chat ids**

| Chat | `chat_id` |
| --- | --- |
| Private chat | the user's id |
| Group | the group id as a **negative** number |

---

## Updates

Receive events with `getUpdates` or [`setWebhook`](#webhooks). Each update contains an `update_id` and one event.

| Event | When it occurs |
| --- | --- |
| `message` | A user writes to the bot, or a member writes in a group the bot belongs to |
| `callback_query` | A user presses a callback button |
| `chat_member` | A member joins, leaves, is muted or banned |

```json
{
  "update_id": 1001,
  "message": {
    "message_id": 55,
    "date": 1791640230,
    "text": "Hello",
    "chat": { "id": 4821, "type": "private" },
    "from": { "id": 4821, "name": "Alex", "username": "alex", "is_bot": false }
  }
}
```

Pass `offset = last update_id + 1` to `getUpdates` to confirm processed updates.

---

## Methods

### Messages

| Method | Main parameters |
| --- | --- |
| `sendMessage` | `chat_id`, `text`, `reply_markup`, `reply_to_message_id` |
| `sendPhoto` · `sendVideo` · `sendAnimation` · `sendAudio` · `sendVoice` · `sendDocument` | `chat_id`, a file (multipart `file` or `file_base64` + `filename`), `caption`, `reply_markup` |
| `editMessageText` | `chat_id`, `message_id`, `text`, `reply_markup` |
| `editMessageReplyMarkup` | `chat_id`, `message_id`, `reply_markup` |
| `deleteMessage` | `chat_id`, `message_id` |
| `purgeChatMessages` | `chat_id`, `limit`, `user_id` *(groups, administrator only)* |

A bot edits and deletes its own messages. In groups an administrator bot can also delete members' messages.

### Buttons

**Inline keyboard** — pass as `reply_markup`:

```json
{
  "inline_keyboard": [
    [ { "text": "Confirm", "callback_data": "confirm" },
      { "text": "Cancel",  "callback_data": "cancel" } ],
    [ { "text": "Website", "url": "https://example.com" } ]
  ]
}
```

| Button | Fields | Result |
| --- | --- | --- |
| Text | `text` | Sends its text to the bot as a message |
| Link | `text`, `url` | Opens the link |
| Mini App | `text`, `mini_app: true` | Opens the bot's Mini App |
| Callback | `text`, `callback_data` | Sends a `callback_query` to the bot |

**`answerCallbackQuery`** — `callback_query_id`, `text`, `show_alert`. Answer promptly so the user gets feedback.

**`setKeyboard`** — a persistent menu under the input field, for example `{ "buttons": [["Catalog", "Help"]] }`. Send `{ "buttons": null }` to remove it.

### Moderation

For groups where the bot is an **administrator**. The group owner and administrators cannot be targeted. Set a period with `duration` (seconds) or `until_date` (Unix time); omit both for permanent.

| Method | Parameters |
| --- | --- |
| `banChatMember` | `chat_id`, `user_id`, `duration`, `reason` |
| `unbanChatMember` | `chat_id`, `user_id` |
| `kickChatMember` | `chat_id`, `user_id` |
| `restrictChatMember` *(mute)* | `chat_id`, `user_id`, `duration`, `reason` |
| `unmuteChatMember` | `chat_id`, `user_id` |
| `warnChatMember` | `chat_id`, `user_id`, `reason`, `limit` *(default 3)*, `action` *(mute · ban · kick · none)*, `action_duration` |
| `unwarnChatMember` · `getChatWarns` · `resetChatWarns` | `chat_id`, `user_id` |

When a member reaches the warning `limit`, the chosen `action` is applied automatically.

### Chats and members

| Method | Returns |
| --- | --- |
| `getMe` | The bot's account |
| `getChat` | Chat details |
| `getChatMember` | Status (`owner`, `administrator`, `member`, `restricted`, `kicked`, `left`) and warnings |
| `getChatMembers` | Member list *(administrator only)* |
| `getChatAdministrators` | Owner and administrators |
| `getChatMemberCount` | Number of members |
| `resolveUsername` | User by `@username` |

### Webhooks

`setWebhook` (`url`) delivers every update to your HTTPS endpoint as a `POST`. `deleteWebhook` returns to polling.

### Mini Apps

`setMiniApp` — `url`, `name`, `permissions`. Opens a web app inside Talksy from a Mini App button.

---

## Limits

| | |
| --- | --- |
| Message text | 4096 characters |
| Caption | 1024 characters |
| File size | 50 MB |
| Inline keyboard | 12 rows × 6 buttons |
| `callback_data` | 64 bytes |
| `getUpdates` | up to 100 updates, `timeout` up to 25 s |

## Errors

| Code | Meaning |
| --- | --- |
| `400` | Invalid request |
| `401` | Invalid token |
| `403` | Action not allowed |
| `404` | Not found |

---

<div align="center">

**Talksy Bot API**

</div>
