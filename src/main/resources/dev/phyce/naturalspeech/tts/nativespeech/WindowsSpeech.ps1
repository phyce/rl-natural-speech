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
    [Console]::Out.WriteLine('VOICE' + $tab + $info.Name + $tab + $info.Gender)
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
