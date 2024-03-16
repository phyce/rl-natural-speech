# NaturalSpeech [![Plugin Installs](http://img.shields.io/endpoint?url=https://i.pluginhub.info/shields/installs/plugin/naturalspeech)](https://runelite.net/plugin-hub/show/naturalspeech) [![Plugin Rank](http://img.shields.io/endpoint?url=https://i.pluginhub.info/shields/rank/plugin/naturalspeech)](https://runelite.net/plugin-hub)
Give everyone a voice in oldschool! For all characters and players, with over different 900 voices.

Naturalspeech requires an external tool to process text to generate speech. 
At the moment the only supported TTS engine is  [Piper](https://github.com/rhasspy/piper).

[![Discord](https://discord.com/api/guilds/1214848661029392405/widget.png?style=banner2)](https://discord.gg/FYPM226s)


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
2. Download the appropriate package and extract it for example in your user folder ```~/piper/```
3. Piper should now be ready to go.


After Installing Piper you should be able to use TTS in-game once the voice model is downloaded, and the engine has been started.

## Setting up NaturalSpeech
To set up NaturalSpeech, you will need to install a TTS engine, and download atleast one voice model through the plugin's panel.

After intalling the plugin, open the panel by clicking the button on the side:

![](https://mechanic.ink/img/osrs/naturalspeech-0.png)

You should see a plugin panel looking like the screenshot below.

![](https://mechanic.ink/img/osrs/naturalspeech-1.png)

Make sure that under the `Play` and `Stop` control buttons the location of your piper installation is correct.
If it's not, piper will not work. To change it click on the browse button, and navigate to find `piper.exe` and press `Open`.

![](https://mechanic.ink/img/osrs/naturalspeech-2.png)

Click `Download` on a voice repository.

![](https://mechanic.ink/img/osrs/naturalspeech-3.png)


The button will get greyed out, the download will start and might take a while. Be patient.

![](https://mechanic.ink/img/osrs/naturalspeech-4.png)

After it's finished downloading, **toggle the repository on**.

![](https://mechanic.ink/img/osrs/naturalspeech-5.png)

Piper status should update to `Running`, which means the plugin is ready to go!

![](https://mechanic.ink/img/osrs/naturalspeech-6.png)

