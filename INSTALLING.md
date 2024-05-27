# Installing voices for Natural Speech
- #### [Installing Piper](#installing-piper)
- #### [Downloading Piper Voice Packs](#installing-piper-voice-packs)
- #### [Installing Microsoft Speech API 4 Windows (Gary Gilbert Voice)](#Installing-Microsoft-Speech-API-4-Windows)

## Installing Piper

### Installing  [Piper](https://github.com/rhasspy/piper) on Windows.

1. Download the binary https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_windows_amd64.zip

2. Extract the folder somewhere on your system, for example in ```C:\piper```, so that it is accessible from ```C:\piper\piper.exe```
3. Piper should now be ready to go.

### Installing  [Piper](https://github.com/TheLouisHong/piper) on MacOS.
1. Download the binary
    1. For Apple Silicon https://github.com/TheLouisHong/piper/releases/download/2024.2.25/piper_macos_aarch64.tar.gz
    2. For Intel https://github.com/TheLouisHong/piper/releases/download/2024.2.25/piper_macos_x64.tar.gz
2. Download the appropriate package and extract it for example in ```~/piper/```
3. Piper should be ready to go

### Installing  [Piper](https://github.com/rhasspy/piper) on Linux.
1. Find the appropriate version suited for your system from https://github.com/rhasspy/piper/releases/tag/2023.11.14-2
2. Download the appropriate package and extract it for example in ```~/piper/```
3. Piper should now be ready to go.


### Post installation notes

Make sure that in the `Advanced` section under the `Play` and `Stop` control buttons the location of your piper installation is correct.
If it's not, piper will not work. To change it click on the `browse` button.

![](https://mechanic.ink/img/osrs/natural-speech/installing/piper-browse-binary.png)

Navigate to find `piper.exe` and press `Open`. For windows this will be `piper.exe`, whereas for MacOS and Linux the binary should simply be `piper`. 

![](https://mechanic.ink/img/osrs/natural-speech/installing/piper-select-binary.png)

After Installing Piper you should be able to use TTS in-game once you install atleast one voice pack, and the engine has been started.




## Installing Piper Voice Packs

Visit the `Voice Hub` tab and download the voice packs that you like. You can right-click to remove, or to change the process count. Changing the process count allows you to run multiple instances of the same voice pack, this will allow Natural Speech to process more messages as the same time. **Be careful through, as adding too many voice packs or multiple instances per voice pack can end up consuming too much CPU and Memory.**

![](https://mechanic.ink/img/osrs/natural-speech/installing/piper-voice-hub-tab.png)

Click `Download` on a voice pack.

![](https://mechanic.ink/img/osrs/natural-speech/installing/libritts-download-start.png)

The button will get greyed out, the download will start and might take a while. Be patient.

![](https://mechanic.ink/img/osrs/natural-speech/installing/libritts-download-wait.png)


After it's finished downloading, **toggle the repository on**.

![](https://mechanic.ink/img/osrs/natural-speech/installing/libritts-download-enable.png)

Piper status should update to `Running`, which means the plugin is ready to go! You should also see the enabled voice pack status under the Piper location input in the `Advanced section`

![](https://mechanic.ink/img/osrs/natural-speech/installing/piper-status.png)

## Installing Microsoft Speech API 4 Windows

Installing Windows Speech API 4 requires installing some old Microsoft packages that are not shipped with the newer versions of their operating systems anymore.

You can get a copy here: https://github.com/TheLouisHong/Microsoft-Sam-Mary-Mike-TruVoice-WSAPI4

`SAPI4SDK.exe` is the Speech API 4 SDK and is **required**.<br/>
`spchapi.exe` is the TTS runtime and is **required**.<br/>
`msttsl.exe` is a pack of TTS voices by Microsoft.<br/>
`tv_enua.exe` is a pack of TTS voices by Lernout & Hauspie.

Apart from `SAPI4SDK.exe` and `spchapi.exe` you will need to install atleast one voice pack, `msttsl.exe` and/or `tv_enua.exe`. If you're looking Gary Gilbert's voice, you will find it in `msttsl.exe`

You will also need to download the SAPI 4 engine, which you can find here: https://github.com/TheLouisHong/natural-speech-installer/releases/tag/SpeechAPI4

Place the `sapi4.dll`, `sapi4limits.exe`, `sapi4out.exe` in your runelite directory in your user directory:
```.runelite\NaturalSpeech\sapi4out\```

Go to the `Voice Hub` tab, and click on the toggle to enable SAPI 4.

![](https://mechanic.ink/img/osrs/natural-speech/installing/mssapi4-enable.png)

Congratulations! SAPI 4 should now be ready. To use Gary Gilbert's voice, you'll need to set `microsoft:sam` as the voice ID.