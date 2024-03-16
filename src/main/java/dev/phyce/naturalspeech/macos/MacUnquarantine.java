package dev.phyce.naturalspeech.macos;

import dev.phyce.naturalspeech.utils.OSValidator;
import java.io.IOException;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;

/**
 * On macOS, browsers, like Chrome/Safari flag themselves with LSFileQuarantineEnabled in the .plist file
 * https://developer.apple.com/documentation/bundleresources/information_property_list/lsfilequarantineenabled
 *
 * For example, here is the flag in the Chromium plist:
 * https://chromium.googlesource.com/chromium/reference_builds/chrome_mac/+/refs/heads/main/Google%20Chrome.app/Contents/Info.plist#320
 *
 * This flag causes all files download by the App to have an extended-file-attribute named com.apple.quarantine
 *
 * Executables and dynamic libraries with this attribute will not be able to link or execute.
 * This is to protect users from opening an executable downloaded by the browser.
 *
 * For Natural Speech, users download the Piper executable through GitHub with the browser, so the executables are
 * flagged with com.apple.quarantine.
 *
 * Using xattr, Natural Speech removes the flags from piper and it's libraries in order to execute.
 * https://opensource.apple.com/source/xnu/xnu-1504.15.3/bsd/sys/xattr.h.auto.html
 */
@Slf4j
public class MacUnquarantine {

	public static void Unquarantine(Path piperExePath) {
		// check if OS is mac
		if (!OSValidator.IS_MAC) {
			throw new RuntimeException("Only MacOS requires unquarantining.");
		}

		// check if the piper path is valid
		if (!piperExePath.toFile().exists()) {
			log.error("Un-quarantining, but path to piper is invalid/does not exist.");
			return;
		}

		// Piper executable and dynamic libraries
		String[] exeFilenames = new String[] {
			"piper",
			"libespeak-ng.1.52.0.1.dylib",
			"libpiper_phonemize.1.2.0.dylib",
			"libonnxruntime.1.14.1.dylib"
		};

		// get the folder in order to resolve each file (not using resolveSibling for code clarity)
		Path piperFolder = piperExePath.getParent();

		// go through each file and remove the quarantine
		for (String filename : exeFilenames) {

			Path path = piperFolder.resolve(filename);

			// check if the file exists
			if (path.toFile().exists()) {

				// run xattr
				ProcessBuilder xattr = new ProcessBuilder()
					.command("xattr", "-d", "com.apple.quarantine", path.toString());

				try {
					xattr.start();
					log.trace("Successfully un-quarantined {}", path);
				} catch (IOException e) {
					log.error("Un-quarantining, but xattr crashed with {}",
						xattr.command().stream().reduce("", (a, b) -> a + " " + b));
					return;
				}
			}
			else {
				log.error("Un-quarantining, but {} piper executable is missing.", filename);
			}

		}
	}
}
