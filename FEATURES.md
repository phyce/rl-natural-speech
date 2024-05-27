# Features

show friends only/friends volume boost

show built in windows/osx sapi
show sapi4 / gary gilbert voice
show right click options on chat channel buttons


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

## 1.3.0
 - Added `Friends only` mode
 - Added `Friend volume boost` option
 - Added numerical abbreviations (k, m, b, t)
 - Added `Use common abbreviations` option
 - Added `Use for dialogs` option to enable abbreviations for NPC dialogs
 - Added Microsoft Speech Api 4 (Windows)
 - Added Microsoft Speech Api 5 (Built-in Windows)
 - Added MacOS Text-to-speech (Built-in MacOS)
 - Added ability to skip NPC dialogs //////////////////
 - Added Voice Hub tab and moved all the voice packs to it
 - Added right click options to chat channel buttons
 - `Global default NPC voice` is now `Global NPC voice` and will override any NPC voice regardless of pre-set voices
 - `Fade distant sound` option now has dynamic volume updating
 - `Shortened phrases` are now `Abbreviations`
 - `Abbreviations` field is now `Custom abbreviations`
 - Reworked volume settings
 - Improved performance
 - Fixed issue where master volume was not being applied to all audio sources
 - Fixed having multiple instances never using more than one
 - Duplicate system messages won't be played every time anymore
 - Fixed issue where certain dialogs were not working (eg Spirit Tree)
 - NPC Dialogs now have an individual queue for NPC + Player dialog messages


## 1.2.4
- Fixed issue with shortened phrases using special characters not working

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