/*
 Why?

 RuneLite Plugins are not allowed to distribute binaries.

 Windows Speech API 5 can be accessed with two methods:
 1. Dynamically Linking to Windows Speech API 5 SDK for C/C++ https://www.microsoft.com/en-us/download/details.aspx?id=10121
 2. Use OS built-in dotnet assembly System.Speech

 Problem:
 1. We can't use Java-native-interface because then we'd need to distribute SAPI5 dll to users with the plugin.
    Aka, Speech 5.1 SDK Redistributable files (SpeechSDK51MSM.exe)

 2. Java cannot interface with dotnet managed assemblies, aka System.Speech.dll

    The purpose of supporting Operating System TTS is to allow NaturalSpeech to have minimal TTS capabilities
    without external dependencies.

    PowerShell is bundled with Windows and has a built-in .NET 4.0
    So we JIT compile C# into a runtime similar to Piper through PowerShell's to access System.Speech

    The CS File is in Resources, file named WSAPI5.cs

- Louis Hong 2024
*/

// Solution:
package dev.phyce.naturalspeech.tts.wsapi5;

import com.google.common.io.Resources;
import dev.phyce.naturalspeech.audio.AudioEngine;
import dev.phyce.naturalspeech.utils.OSValidator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WSAPI5Process {

	private final ByteArrayOutputStream stdoutByteStream = new ByteArrayOutputStream();

	public static WSAPI5Process start() {
		if (!OSValidator.IS_WINDOWS) {
			log.error("Attempting to starting CSharp Runtime on non-Windows.");
			return null;
		}

		try {
			return new WSAPI5Process();
		} catch (IOException e) {
			log.error("CSharp Runtime failed to launch.", e);
			return null;
		}
	}

	private WSAPI5Process() throws IOException {


		File wsapi5CSharp = extractWSAPI5CSharpFile();

		// Disable formatting for this region, so it's as concise as possible
		// @formatter:off

		// Start WSAPI5 process
		ProcessBuilder builder = new ProcessBuilder(
			"PowerShell",
			// Security Note (Louis Hong):
			// We are not passing any user input into PowerShell
			// When WSAPI5::Main ends, the powershell process ends.
			// ex: powershell -command echo "hi"

			// Unlike the recent 2024 Rust CVE-2024-24576, (example https://github.com/lpn/CVE-2024-24576.jl)
			// which demonstrated exploiting when user input is passed. ex: cmd -command echo <user_input>

			"-command",
			// Compile WSAPI5.cs using PowerShell built-in .NET4.0
			"Add-Type", "-Path", wsapi5CSharp.getAbsolutePath(), "-ReferencedAssemblies", "System,System.IO,System.Speech;",
			// Start Main Function
			"[WSAPI5]::Main();"
		);
		// @formatter:on

		Process process = builder.start();

		process.getOutputStream().write(new String("Microsoft David Desktop\n").getBytes());
		process.getOutputStream().write(new String("Hello, Natural Speech\n").getBytes());

		process.getOutputStream().close();

		AudioFormat audioFormat =
			new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
				22050.0F, // Sample Rate (per second)
				16, // Sample Size (bits)
				1, // Channels
				2, // Frame Size (bytes)
				22050.0F, // Frame Rate (same as sample rate because PCM is 1 sample per 1 frame)
				false
			);

		byte[] bytes = process.getInputStream().readAllBytes();

		System.out.println("Byte length: " + bytes.length);
		//		System.out.println("Bytes: " + Arrays.toString(bytes));

		AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(bytes), audioFormat, bytes.length);

		AudioEngine audioEngine = new AudioEngine();

		audioEngine.play("&test", stream, () -> 0f);

		// clean up the csharp file
		assert wsapi5CSharp.delete();
	}

	/**
	 * Copies WSAPI5.cs from Resources into System Temp
	 */
	private static File extractWSAPI5CSharpFile() throws IOException {

		File tempFolder = Path.of(System.getProperty("java.io.tmpdir"), "NaturalSpeech").toFile();
		boolean ignore = tempFolder.mkdir();

		String fileName = "WSAPI5_" + UUID.randomUUID().getMostSignificantBits() + ".cs";
		File tempFile = tempFolder.toPath().resolve(fileName).toFile();
		assert tempFile.createNewFile();

		try (FileWriter file = new FileWriter(tempFile)) {
			file.write(
				Resources.toString(Resources.getResource(WSAPI5Process.class, "WSAPI5.cs"), StandardCharsets.UTF_8));
		} catch (IOException e) {
			log.error("Failed to copy WSAPI5.cs to {}", tempFile);
		}

		return tempFile;
	}

}
