using System;
using System.IO;
using System.Speech;

using System.Speech.Synthesis;
using System.Speech.AudioFormat;

public class WSAPI5
{
	public static void Main() {
		// Stdout is used to output audio bytes
		// Stderr is used to synchronize
		// Stdin is used to read Speak commands in the format <name>\n<text>\n
		// Stdin also accepts !LIST, lists available voices in the format <name>\n<gender>\n
		Stream stdout = Console.OpenStandardOutput();

		SpeechSynthesizer synth = new SpeechSynthesizer();
		SpeechAudioFormatInfo format = new SpeechAudioFormatInfo(22050, (AudioBitsPerSample) 16, (AudioChannel) 1);
		MemoryStream audioStream = new MemoryStream();
		// Set synth to output audio bytes to our stream
		synth.SetOutputToAudioStream(audioStream, format);

		String voiceName = null;
		// line 1: voiceName (or !LIST)
		while ((voiceName = Console.ReadLine()) != null ) {

			if (voiceName.Equals("!LIST")) {
				GetInstalledVoices();
				continue;
			}

			// line 2: Spoken Text
			String text = Console.ReadLine();	

			if (voiceName.Length == 0 || text.Length == 0) {
				Console.Error.WriteLine("EXCEPTION:Invalid Speech Format");
				continue;
			}

			try {
				synth.SelectVoice(voiceName);
			} catch (ArgumentException) {
				Console.Error.WriteLine("EXCEPTION:Invalid Voice Name");
				continue;
			}

			// Capture AudioStream from SAPI5
			audioStream.SetLength(0);
			synth.Speak(text);
			byte[] bytearray = audioStream.ToArray();

			// Stream audio bytes to Java
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