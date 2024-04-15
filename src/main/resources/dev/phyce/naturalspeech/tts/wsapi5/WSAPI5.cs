using System;
using System.IO;
using System.Speech;

using System.Speech.Synthesis;
using System.Speech.AudioFormat;

public class WSAPI5
{
	public static void Main() {
		Stream stdout = Console.OpenStandardOutput();

		SpeechSynthesizer synth = new SpeechSynthesizer();
		SpeechAudioFormatInfo format = new SpeechAudioFormatInfo(22050, (AudioBitsPerSample) 16, (AudioChannel) 1);
		MemoryStream audioStream = new MemoryStream();
		synth.SetOutputToAudioStream(audioStream, format);

		String voiceName = null;
		while ((voiceName = Console.ReadLine()) != null ) {

			if (voiceName.Equals("!LIST")) {
				GetInstalledVoices();
				continue;
			}

			String text = Console.ReadLine();	

			if (voiceName.Length == 0 || text.Length == 0) continue;

			try {
				synth.SelectVoice(voiceName);
			} catch (ArgumentException) {
				Console.Error.WriteLine("EXCEPTION:Invalid Voice Name");
				continue;
			}

			audioStream.SetLength(0);
			synth.Speak(text);
			byte[] bytearray = audioStream.ToArray();
			stdout.Write(bytearray, 0, bytearray.Length);
			
			// We use the error stream to signal end of buffer
			Console.Error.WriteLine("END_OUT");
		}

		stdout.Close();
	}

    public static void GetInstalledVoices()
    {
		SpeechSynthesizer speak = new SpeechSynthesizer();
		foreach (InstalledVoice voice in speak.GetInstalledVoices()) {
			try {
				// Some voices are listed but not speakable
				speak.SelectVoice(voice.VoiceInfo.Name);
			} catch (ArgumentException) {
				continue;
			}

			Console.Error.WriteLine(voice.VoiceInfo.Name);
			Console.Error.WriteLine(voice.VoiceInfo.Gender);
		}
		Console.Error.WriteLine("END_OUT");
    }
}