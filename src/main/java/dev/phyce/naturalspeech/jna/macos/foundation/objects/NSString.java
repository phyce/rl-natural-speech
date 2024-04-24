
package dev.phyce.naturalspeech.jna.macos.foundation.objects;

import com.sun.jna.Pointer;
import com.sun.jna.platform.mac.CoreFoundation;
import dev.phyce.naturalspeech.jna.macos.foundation.util.Foundation;
import java.nio.charset.StandardCharsets;
import lombok.NonNull;

public interface NSString {

	ID idClass = Foundation.objc_getClass("NSString");

	SEL selString = Foundation.sel_registerName("string");
	SEL selInitWithBytesLengthEncoding = Foundation.sel_registerName("initWithBytes:length:encoding:");
	SEL selStringByAppendingString = Foundation.sel_registerName("stringByAppendingString:");

	long constNSUTF16LittleEndianStringEncoding = 0x94000100L;


	static @NonNull String getJavaString(@NonNull ID self) {
		if (self.isNil()) {
			return "nil";
		}
		CoreFoundation.CFStringRef cfString = new CoreFoundation.CFStringRef(new Pointer(self.longValue()));

		return cfString.stringValue();
	}

	static ID alloc(String javaString) {
		if (javaString.isEmpty()) {
			return Foundation.objc_msgSend(idClass, selString);
		}

		byte[] utf16Bytes = javaString.getBytes(StandardCharsets.UTF_16LE);

		return
			Foundation.objc_msgSend(
				Foundation.objc_msgSend(idClass, NSObject.selAlloc),
				selInitWithBytesLengthEncoding,
				utf16Bytes,
				utf16Bytes.length,
				constNSUTF16LittleEndianStringEncoding
			);
	}

	static ID allocStringByAppendingString(ID leftNSString, ID rightNSString) {
		return Foundation.objc_msgSend(leftNSString, selStringByAppendingString, rightNSString);
	}
}
