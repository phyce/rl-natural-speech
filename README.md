# Natural Speech [![Active Installs](http://img.shields.io/endpoint?url=https://api.runelite.net/pluginhub/shields/installs/plugin/naturalspeech)](https://runelite.net/plugin-hub/show/naturalspeech) [![Plugin Rank](http://img.shields.io/endpoint?url=https://api.runelite.net/pluginhub/shields/rank/plugin/naturalspeech)](https://runelite.net/plugin-hub)


Give everyone a voice in OldSchool RuneScape! For all characters and players, with over 1000 different voices.

### To see a list of features, check out [FEATURES.md](FEATURES.md)

Natural Speech requires an installing external tool to process text to generate speech.
At the moment there are only 2 supported TTS engines:<br/>
- Local TTS:  [Piper](https://github.com/rhasspy/piper).<br/>
- API TTS: [Elevenlabs](https://elevenlabs.io)

[![Discord](https://discord.com/api/guilds/1214848661029392405/widget.png?style=banner2)](https://discord.gg/Zg5hMYMZ6E)


## Installing  [Piper](https://github.com/rhasspy/piper) on Windows.

1. Download the binary from https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_windows_amd64.zip

2. Extract the folder somewhere on your system, for example in ```C:\piper```, so that it is accessible from ```C:\piper\piper.exe```
3. Piper should now be ready to go.

## Installing  [Piper](https://github.com/TheLouisHong/piper) on Mac.
1. Download the binary
    1. For Apple Silicon https://github.com/TheLouisHong/piper/releases/download/2024.2.25/piper_macos_aarch64.tar.gz
    2. For Intel https://github.com/TheLouisHong/piper/releases/download/2024.2.25/piper_macos_x64.tar.gz
2. Download the appropriate package and extract it for example in ```~/piper/```
3. Piper should be ready to go

## Installing  [Piper](https://github.com/rhasspy/piper) on Linux.
1. Find the appropriate version suited for your system from https://github.com/rhasspy/piper/releases/tag/2023.11.14-2
2. Download the appropriate package and extract it for example in ```~/piper/```
3. Piper should now be ready to go.


### Post installation notes

After Installing Piper you should be able to use TTS in-game once the voice pack is downloaded, and the engine has been started.

## Setting up Natural Speech
To set up Natural Speech, you will need to install a TTS engine, and download atleast one voice pack through the plugin's panel.

After intalling the plugin, open the panel by clicking the button on the side:

![](https://narration.studio/storage/runelite/naturalspeech-0.png)

You should see a plugin panel looking like the screenshot below.

![](https://narration.studio/storage/runelite/naturalspeech-1.png)

Make sure that under the `Play` and `Stop` control buttons the location of your piper installation is correct.
If it's not, piper will not work. To change it click on the browse button, and navigate to find `piper.exe` and press `Open`.

![](https://narration.studio/storage/runelite/naturalspeech-2.png)

Click `Download` on a voice pack.

![](https://narration.studio/storage/runelite/naturalspeech-3.png)


The button will get greyed out, the download will start and might take a while. Be patient.

![](https://narration.studio/storage/runelite/naturalspeech-4.png)

After it's finished downloading, **toggle the repository on**.

![](https://narration.studio/storage/runelite/naturalspeech-5.png)

Piper status should update to `Running`, which means the plugin is ready to go!

![](https://narration.studio/storage/runelite/naturalspeech-6.png)

## Setting up [Elevenlabs]([Elevenlabs](https://elevenlabs.io))

Sign up or log in to https://elevenlabs.io and create an API key in https://elevenlabs.io/app/developers/api-keys with access to Text to speech.

![](https://narration.studio/storage/runelite/naturalspeech-7.png)

Copy the key that appears on the screen that starts with `sk_`

![](https://narration.studio/storage/runelite/naturalspeech-8.png)<br/>
_Note: the API key in this screenshot is not valid. Go make your own._

Paste the API key in Natural Speech settings

![](https://narration.studio/storage/runelite/naturalspeech-9.png)<br/>
_You will get a warning when pasting the key, letting you know that using this feature will send some data to ElevenLabs. This is required for it to work._

At this point, the Natural Speech panel should show that ElevenLabs is ready to use!

![](https://narration.studio/storage/runelite/naturalspeech-10.png)