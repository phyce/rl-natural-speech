package dev.phyce.naturalspeech.texttospeech.engine.windows.speechapi5;

import com.google.common.io.Resources;
import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.statics.PluginResources;
import dev.phyce.naturalspeech.utils.Platforms;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.sound.sampled.AudioFormat;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/*
 Why?

 Our goal is to allow NaturalSpeech to have minimal TTS capabilities without external dependencies (minimal mode).
 Therefore, we need operating system TTS.

 Windows Speech API 5.3 can be accessed with two methods:
	 1. Using Windows Native COM API through JNA/JNI
	 2. Use OS built-in dotnet assembly System.Speech.dll

 Problem:
	 1. Windows Native COM API stinks, it is hard to write and hard to read.
	    Also requires JNA/JNI, increasing the risk of segfaulting the JVM.

	 2. Java/C# interop is not viable, Java cannot interface with dotnet managed assemblies, aka System.Speech.dll

 Solution:
	 PowerShell is bundled with Windows and has a built-in .NET 4.0
	 So we JIT compile C# into a runtime similar to Piper through PowerShell to access System.Speech

	 The CS File is in Resources under the same package path, named WSAPI5.cs

- Louis Hong 2024
*/

@Slf4j
public class SAPI5Process {

	private static final String CONTROL_END_OUT = "END_OUT";
	private static final String CONTROL_ERROR = "EXCEPTION:";

	public static final AudioFormat AUDIO_FORMAT =
		new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
			22050.0F, // Sample Rate (per second)
			16, // Sample Size (bits)
			1, // Channels
			2, // Frame Size (bytes)
			22050.0F, // Frame Rate (same as sample rate because PCM is 1 sample per 1 frame)
			false
		);

	private final Process process;
	private final ByteArrayOutputStream audioStreamCapture = new ByteArrayOutputStream();

	private final BufferedWriter processStdIn;
	private final Thread processStdInThread;
	private final Thread processStdErrThread;

	@Value
	@AllArgsConstructor
	@EqualsAndHashCode
	public static class SAPI5Voice {
		String name;
		Gender gender;
	}

	private final List<SAPI5Voice> availableVoices = new ArrayList<>();

	public static SAPI5Process start() {
		if (!Platforms.IS_WINDOWS) {
			log.error("Attempting to starting CSharp Runtime on non-Windows.");
			return null;
		}

		try {
			return new SAPI5Process();
		} catch (IOException | RuntimeException e) {
			log.error("CSharp Runtime failed to launch.", e);
			return null;
		}
	}

	public void destroy() {
		processStdInThread.interrupt();
		processStdErrThread.interrupt();

		if (process != null && process.isAlive()) {
			try {
				if (processStdIn != null) processStdIn.close();
			} catch (IOException exception) {
				log.error("{} failed closing processStdIn on destroy.", this, exception);
			}
			process.destroy();
		}
	}

	private SAPI5Process() throws IOException {

		final File wsapi5CSharp = extractWSAPI5CSharpFile();

		// Disable formatting for this region, so it's as concise as possible
		// @formatter:off

		// Start WSAPI5 process
		ProcessBuilder builder = new ProcessBuilder(
			"PowerShell",
			// Security Note (Louis Hong):
			// We are not passing any user input as PowerShell commands or as arguments
			// When WSAPI5::Main ends, the powershell process end; as it is the last command to powershell.
			// ex: powershell -command echo "hi"

			// Unlike the Rust CVE-2024-24576, (example https://github.com/frostb1ten/CVE-2024-24576-PoC)
			// which demonstrated exploiting when user input is passed into the command field.
			// ex 1: powershell -command echo <user_input>
			// ex 2: (rust)
			// let output = Command::new("./test.bat")
			//                         .arg(<userinput>) <-- vulnerable
			//                         .output()
			//                         .expect("Failed to execute command");

			// https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.core/about/about_powershell_exe?view=powershell-5.1#-command
			"-command",
			// Compile WSAPI5.cs using PowerShell built-in .NET4.0
			"Add-Type", "-Path", wsapi5CSharp.getAbsolutePath(), "-ReferencedAssemblies", "System,System.IO,System.Speech;",
			// Start Main Function
			"[WSAPI5]::Main();"
		);
		// @formatter:on

		// PowerShell, with all commands defined, will start and no longer be taking any more commands.
		process = builder.start();


		if (!process.isAlive()) {

			String err = String.format("WSAPI5.cs path:%s, failed to start with error:%s",
				wsapi5CSharp.getAbsolutePath(), new String(process.getErrorStream().readAllBytes())
			);

			log.error(err);
			throw new RuntimeException(err);
		}

		// Stdin is used receive output audio bytes
		// Stderr is used to synchronize
		// Stdout is used to send audio commands in the format <name>\n<text>\n
		// Stdout also accepts !LIST, lists available voices in the format <name>\n<gender>\n

		// Begin IO with WSAPI5.cs
		processStdIn = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

		// blocking IO for !LIST to iterate available voices
		fetchAvailableVoices();

		processStdInThread =
			new Thread(this::processStdIn, String.format("[%s] WSAPI5::processStdIn Thread", this));
		processStdInThread.start();

		processStdErrThread =
			new Thread(this::processStdErr, String.format("[%s] WSAPI5::processStdErr Thread", this));
		processStdErrThread.start();

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
			file.write(Resources.toString(PluginResources.WSAPI5_CSHARP_RUNTIME, StandardCharsets.UTF_8));
		} catch (IOException e) {
			log.error("Failed to copy WSAPI5.cs to {}", tempFile);
		}

		// Delete temp files on exit
		tempFile.deleteOnExit();
		tempFolder.deleteOnExit();

		return tempFile;
	}

	public void generateAudio(String voiceName, String text, Consumer<byte[]> onComplete) {
		new Thread(() -> {
			try {
				onComplete.accept(_blockingGenerateAudio(voiceName, text));
			} catch (IOException e) {
				log.error("Failed to generate audio", e);
				onComplete.accept(null);
			}
		}).start();
	}

	@SneakyThrows(InterruptedException.class)
	@Synchronized // a call can block the next until the first it finishes, in an ordered fashion
	private byte[] _blockingGenerateAudio(String voiceName, String text) throws IOException {
		text = text.replace("\n", "").replace("\r", "");

		synchronized (audioStreamCapture) {
			try {
				audioStreamCapture.reset();

				// <name>\n
				processStdIn.write(voiceName);
				processStdIn.newLine();

				// <text>\n
				processStdIn.write(text);
				processStdIn.newLine();

				processStdIn.flush();

				// wait for stderr control message END_OUT
				audioStreamCapture.wait();

				return audioStreamCapture.toByteArray();
			} finally {
				audioStreamCapture.reset();
			}
		}
	}

	@NonNull
	public List<SAPI5Voice> getAvailableVoices() {
		return Collections.unmodifiableList(availableVoices);
	}

	/**
	 * This function must only be called before audio streaming has begun.
	 * It is blocking and synchronized.
	 */
	private void fetchAvailableVoices() throws IOException {
		synchronized (processStdIn) {
			processStdIn.write("!LIST");
			processStdIn.newLine();
			processStdIn.flush();
		}

		// The input is formatted as
		// <name>\n
		// <gender>\n
		// ... multiple entries ...
		// END_OUT
		BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
		String name;
		while (!(name = reader.readLine()).equals(CONTROL_END_OUT)) {
			Gender gender = Gender.parseString(reader.readLine());
			availableVoices.add(new SAPI5Voice(name, gender));
		}
	}


	/**
	 * StdIn captures audio output from the process
	 */
	private void processStdIn() {
		try (InputStream inputStream = process.getInputStream()) {
			byte[] data = new byte[1024];
			int nRead;
			while (!processStdInThread.isInterrupted() && (nRead = inputStream.read(data, 0, data.length)) != -1) {
				synchronized (audioStreamCapture) {
					audioStreamCapture.write(data, 0, nRead);
				}
			}
		} catch (IOException e) {
			log.error("{}: reading stdin threw", this, e);
		}
	}

	/**
	 * StdErr is used for control messages to synchronize the processes
	 * Not used for error except when prefixed with EXCEPTION:
	 */
	private void processStdErr() {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
			String line;
			while (!processStdErrThread.isInterrupted() && (line = reader.readLine()) != null) {
				if (line.startsWith(CONTROL_ERROR)) {
					log.error("[pid:{}-StdErr]:CSharp Exception: {}", process.pid(), line);
					synchronized (audioStreamCapture) {
						audioStreamCapture.notify(); // notify capture is complete
					}
				}

				if (line.equals(CONTROL_END_OUT)) {
					synchronized (audioStreamCapture) {
						audioStreamCapture.notify(); // notify capture is complete
					}
				}

				log.trace("[pid:{}-StdErr]: Audio Capture Complete, {} detected", process.pid(), CONTROL_END_OUT);
			}
		} catch (IOException e) {
			log.error("{}: readStdErr threw exception", this, e);
		}
	}

}
