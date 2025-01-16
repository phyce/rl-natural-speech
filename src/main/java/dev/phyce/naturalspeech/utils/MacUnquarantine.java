package dev.phyce.naturalspeech.utils;

import java.io.IOException;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;

/*
 * On macOS, browsers, like Chrome/Safari flag themselves with LSFileQuarantineEnabled in the .plist file
 * https://developer.apple.com/documentation/bundleresources/information_property_list/lsfilequarantineenabled
 *
 * For example, here is the flag in the Chromium plist:
 * https://chromium.googlesource.com/chromium/reference_builds/chrome_mac/+/refs/heads/main/Google%20Chrome.app/Contents/Info.plist#320
 *
 * This flag causes all files downloaded by the App to have an extended-file-attribute named com.apple.quarantine
 *
 * Executables and dynamic libraries with this attribute will not be able to link or execute.
 * This is to protect users from opening an executable downloaded by the browser.
 *
 * For Natural Speech, users download the Piper executable through GitHub with the browser, so the executables are
 * flagged with com.apple.quarantine.
 *
 * Using xattr, Natural Speech removes the flags from piper and its libraries to execute.
 *
 * xattr main function:
 * https://github.com/apple-oss-distributions/file_cmds/blob/main/xattr/xattr.c#L493
 *
 * -d option invokes
 * delete_attribute(int fd, const char *filename, const char *name)
 * https://github.com/apple-oss-distributions/file_cmds/blob/main/xattr/xattr.c#L329
 *
 * which calls sys/xattr.h
 * int fremovexattr(int fd, const char *name, int options);
 * https://github.com/apple-oss-distributions/xnu/blob/main/bsd/sys/xattr.h#L98
 */
@Slf4j
public class MacUnquarantine {

	/**
	 * @param piperExePath path to the piper executable (not the folder)
	 *
	 * @return Returns if the un-quarantine was successful
	 */
	public static boolean Unquarantine(Path piperExePath) {
		// check if OS is macOS
		if (!PlatformUtil.IS_MAC) {
			log.error("Only MacOS requires un-quarantining.");
			return true; // return true because it's "un-quarantined", as Windows/Linux never needed it.
		}

		// check if the piper path is valid
		if (!piperExePath.toFile().exists()) {
			log.error("Un-quarantining, but path to piper is invalid/does not exist.");
			return false;
		}

		// Piper executable and dynamic libraries
		final String[] exeFilenames = new String[] {
			"piper",
			"libespeak-ng.1.52.0.1.dylib",
			"libpiper_phonemize.1.2.0.dylib",
			"libonnxruntime.1.14.1.dylib"
		};

		boolean success = true;

		// get the folder in order to resolve each file (not using resolveSibling for code clarity)
		final Path piperFolder = piperExePath.getParent();

		// go through each file and remove the quarantine
		for (String filename : exeFilenames) {

			final Path path = piperFolder.resolve(filename);

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
					success = false;
				}
			}
			else {
				log.error("Un-quarantining, but {} piper executable is missing.", filename);
				success = false;
			}

		}

		return success;
	}
}
