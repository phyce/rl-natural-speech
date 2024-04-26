# Features

### Right click option menu
Right click on any player/npc to `configure` their voice, `mute` them or `listen` exclusively to them.<br/>
![](https://mechanic.ink/img/osrs/features/right-click.png)

### Voice Explorer
Choose from over 1000 different voices for your character. 
You can use the Included voice explorer to preview a voice:<br/>
![](https://mechanic.ink/img/osrs/features/voice-explorer.png)

### Vast Customization

Explore the many customization options to fine-tune your TTS experience:<br/>
![](https://mechanic.ink/img/osrs/features/config.png)

### Spam Prevention

Intergrated with [Spam Filter](https://runelite.net/plugin-hub/show/spamfilter) and [Chat Filter](https://github.com/runelite/runelite/wiki/Chat-Filter) so you don't have to listen to spam!<br/>
![](https://mechanic.ink/img/osrs/features/spam-prevention.png)

### Commands

`::setvoice` - Use this command to set the voice of a player.<br>
**Example**: `::setvoice libritts:120 Zezima`

`::unsetvoice` - Use this command to unset a previously configured voice of a player.<br>
**Example**: `::unsetvoice Zezima`

`::checkvoice` - Use this command to check your own currently set voices or the voice of another player.<br>
**Example**: `::checkvoice zezima`

`::nslogger` - Use this to change the logging level. Available options - `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`.<br>
**Example**: `::nslogger TRACE` 




---
# Changelog

## 1.2.3
- Fixed issue with `mute others` also muted local player, and `mute self` not doing anything

## 1.2.2
 - Fixed issue with setting NPC voices

## 1.2.1
 - Re-added Dutch
 - Fixed issue where option to disable dialogs didn't work
 - Fixed issue where option to disable examine text didn't work for all examine messages

## 1.2.0
 - Added filtering by [Spam Filter](https://runelite.net/plugin-hub/show/spamfilter) and [Chat Filter](https://github.com/runelite/runelite/wiki/Chat-Filter)
 - Muting a player/npc will persist between sessions
 - Right click menu entry UI improvements, including new icons
 - Added ability to enable right-click menu only when holding the shift key
 - Configuring a voice now pre-populates field with current voice
 - `Mute-others` has been renamed to `Listen`
 - NPC Overhead text now also goes through block/allow filters
 - Removed rich text information from messages going into the TTS engine
 - Many performance, stability and codebase improvements

## 1.1.1
 - Removed a few voice packs as they need retraining
 - Added more text abbreviations

## 1.1.0

 - Added more voices: German, French, Dutch, Vietnamese, Italian, Russian
 - Added option to disable overhead text
 - Added ability to set global default voice to all NPCs
 - Added ability to set custom voice for system messages
 - Added master volume setting
 - Added more voice configuration options in the right-click menu
 - Added option to disable TTS in crowded areas
 - Changed Right-click 'TTS' menu name to 'Voice'
 - Renamed Voice Repository -> Voice Packs
 - Renamed model:id -> voice:id inside in-game voice config window
 - Fixed Engine status now shows the right information when piper is not found
 - Fixed Voice Repository now disables download when engine path is invalid
 - Fixed Text to speech engine now stops after user changes engine path 
 - Fixed MacOS now un-quarantines piper after initial attempt failed due to incorrect piper path
 - Fixed Voice Explorer search by gender
 - Fixed TTS issues with dialog text decoration
 - Fixed Piper clears task and audio queue when stopped
 - Fixed Personal voice ID not working under various circumstances
