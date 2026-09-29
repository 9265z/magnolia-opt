# Magnolia OPT

A client-only Fabric 26.2 mod that detects Magnolia chat games, solves common English and Minecraft-themed scrambles, identifies vanilla crafting recipes offline, and remembers confirmed answers for later rounds.

## Install

1. Use a Minecraft 26.2 Fabric profile with Fabric Loader 0.19.5 or newer.
2. Put the single `MagnoliaOPT.jar` file in that profile's `mods` folder.

The complete matching Fabric API 26.2 distribution is embedded inside this JAR. A separate Fabric API installation is not required. Fabric Loader itself must still be the selected Minecraft mod loader because it is what launches every Fabric mod.

The release filename no longer contains a version, so future updates replace the same file instead of accumulating copies. The mod never moves or deletes other files in the mods folder; managed launchers such as OneClient remain responsible for their own package state.

No server-side installation is needed.

## Use

- Run `/maggui` to open the graphical control center. `/mag gui` opens it too.
- The redesigned GUI uses a responsive sidebar on large windows and compact two-row tabs at smaller GUI scales.
- Appearance offers **Glass** (rounded translucent UI with bundled Lato SemiBold font) and **Minecraft** (square opaque UI with the native Minecraft font).
- Both styles support red, blue, purple, pink, black, and white accents, saved between launches.
- The Updates page controls launch checks and shows the installed version, newest release, and verification status.
- Game Library shows a prompt-and-answer example for every supported Magnolia chat game.
- Its master switch, auto-submit, trivia auto-answer, **Auto save answers**, and auto-welcome toggles persist between launches.
- Automation includes a persistent 0–100% submission chance, adjustable in 5% steps. A 70–80% range is recommended for less repetitive participation, but no setting guarantees avoiding server detection.
- **Auto /welcome** recognizes each Magnolia new-player banner and sends `/welcome` after a newly randomized 2–4 second delay. Multiple joins are queued independently. `/mag welcome` toggles it too.
- Turning **Auto-answer trivia** off still shows question answers, but will not submit them.
- Turning **Learn & save** off prevents server reveals, wins, and manually entered responses from being added to memory. Existing memories remain available.
- The Learning page separately controls saving from Magnolia's `Answer » ...` reveals and from the exact chat message associated with `username was first!`. Both sources are enabled by default.
- Use the Current Game section to save or forget a response, show answers in chat, or copy the best answer.
- Set up or test the OpenAI connection directly from the OpenAI section.
- When a supported prompt appears, click an answer to copy it.
- Click **FILL CHAT** to place the best answer in the chat input; press Enter yourself.
- `/mag answer` shows the latest suggestions again.
- `/mag remember <answer>` teaches the current prompt manually.
- `/mag forget` removes the current prompt from memory.
- `/mag toggle` enables or disables detection.
- `/mag stats` shows the memory count and file location.

For `CHAT GAME | Answer the question`, the mod captures the next question and automatically submits known candidates in order. It stops when Magnolia announces a winner or reveals the answer. Unknown questions are learned from Magnolia's `Answer » ...` line and answered automatically when they repeat.

Automatic submission uses the percentage selected in `/maggui` (80% by default) and skips the remaining rounds. Each answer waits between 1 and 4 seconds before submission. The delay includes random variation and increases with the answer's character count; alternate guesses receive separate delays. Answers always remain visible locally for manual copy/fill, including skipped rounds.

## OpenAI answers for unseen questions

If no key is configured, an in-game setup window appears after joining a world. Choose **GET A KEY** to open the official [OpenAI Platform API keys page](https://platform.openai.com/api-keys), then paste and save the key inside Minecraft. The field masks the key while it is displayed.

The key is stored locally at:

`config/magnolia-chat-helper/openai.json`

Open that file and paste your OpenAI API key into `apiKey`, keeping the quotation marks:

```json
{
  "enabled": true,
  "apiKey": "sk-your-key-here",
  "model": "gpt-6-luna",
  "timeoutSeconds": 7
}
```

You can still edit that file manually, then restart Minecraft or run `/mag api reload`.

- `/mag api` shows whether the key loaded, the model, and the last request status.
- `/mag api test` performs a real request without waiting for a Magnolia game. It prints either `API TEST PASSED` with the answer or the exact authentication, billing, rate-limit, connection, or timeout error.

The `OPENAI_API_KEY` environment variable can be used instead of saving a key in the file. A key in `openai.json` takes priority.

Trivia questions without a memorized answer are sent to `https://api.openai.com/v1/responses`. Requests use `gpt-6-luna` with reasoning effort `none` for low latency and set `store` to `false`. The API requires internet access, an OpenAI Platform API key, and available API credits. API usage is billed separately from a ChatGPT subscription. Never share your key or paste it into Minecraft chat.

Answers are stored locally in `config/magnolia-chat-helper/memory.json`.
GUI preferences are stored locally in `config/magnolia-chat-helper/settings.json`.

## Verified updates

Magnolia OPT checks the public [`9265z/magnolia-opt`](https://github.com/9265z/magnolia-opt) releases in the background when Minecraft launches. If a newer version exists, an in-game warning offers **UPDATE NOW** or **LATER**. Updates are never installed merely because a release was detected, and the mod never forces Minecraft to restart.

Before replacing the active `MagnoliaOPT.jar`, the updater verifies GitHub's SHA-256 asset digest, the Fabric mod id, and the version embedded in `fabric.mod.json`. A successful update becomes active after Minecraft is restarted. Update checks can be disabled on the Updates page in `/maggui`; `/mag update check` starts a manual check.

## Prompt tuning

The detector supports two-line `Unscramble the word` and `Unreverse the word` games, including multi-word Minecraft item names and exact capitalization. It also supports prompts containing `unshuffle`, `descramble`, `anagram`, `type`, `write`, or `repeat`. `Type the random text` accepts letters and numbers and preserves exact capitalization. It detects `Name the crafted item` followed by ingredient counts and `Answer the question` followed by a question ending in `?`. If Magnolia uses another format, capture the full plain-text message or a screenshot so the detector can be tuned precisely.
