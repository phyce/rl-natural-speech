# Natural Speech - speaks using the voices built into Windows.
#
# Windows only exposes speech through .NET/COM, which Java cannot call directly, so this script
# acts as the bridge. It is started once and left running: startup costs ~500ms, after which each
# message takes ~10-30ms.
#
# PROTOCOL
#   on startup, one line per installed voice, then READY:
#     VOICE <tab> name <tab> gender <tab> culture
#     READY
#   then one request per line on stdin, one response per line on stdout:
#     request   voiceNameBase64 <tab> textBase64
#     response  AUDIO <tab> pcmBase64     raw PCM, 22050Hz 16bit mono
#               ERROR <tab> message       could not speak, the process stays running
#
# WHY BASE64
#   The text being spoken is written by other players. It is passed on stdin and base64 encoded so
#   that it is only ever data - it is never placed on a command line and never interpolated into
#   this script, so there is nothing a player can type that this script would execute. It also
#   keeps tabs and newlines in chat from breaking the line framing, and keeps binary audio off a
#   stdout stream that PowerShell would otherwise corrupt.

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech

$tab = [char]9
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(
  22050,
  [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,
  [System.Speech.AudioFormat.AudioChannel]::Mono)

foreach ($installed in $synth.GetInstalledVoices()) {
  if ($installed.Enabled) {
    $info = $installed.VoiceInfo
    [Console]::Out.WriteLine('VOICE' + $tab + $info.Name + $tab + $info.Gender + $tab + $info.Culture.Name)
  }
}
[Console]::Out.WriteLine('READY')
[Console]::Out.Flush()

while ($true) {
  $line = [Console]::In.ReadLine()
  if ($null -eq $line) { break }
  if ($line.Length -eq 0) { continue }

  try {
    $fields = $line.Split($tab)
    $voice = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($fields[0]))
    $text = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($fields[1]))

    if ($voice.Length -gt 0) { $synth.SelectVoice($voice) }

    $stream = New-Object System.IO.MemoryStream
    $synth.SetOutputToAudioStream($stream, $format)
    $synth.Speak($text)
    # release the stream before reading it, the tail of the clip can still be buffered otherwise
    $synth.SetOutputToNull()

    [Console]::Out.WriteLine('AUDIO' + $tab + [Convert]::ToBase64String($stream.ToArray()))
    $stream.Dispose()
  }
  catch {
    $message = $_.Exception.Message.Replace([char]13, [char]32).Replace([char]10, [char]32)
    [Console]::Out.WriteLine('ERROR' + $tab + $message)
  }
  [Console]::Out.Flush()
}
