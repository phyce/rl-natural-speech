
package dev.phyce.naturalspeech.jna.macos.foundation.objects;

import com.sun.jna.Pointer;
import com.sun.jna.platform.mac.CoreFoundation;
import static dev.phyce.naturalspeech.jna.macos.foundation.Foundation.FOUNDATION;
import dev.phyce.naturalspeech.jna.macos.foundation.util.Foundations;
import java.nio.charset.StandardCharsets;
import lombok.NonNull;

public interface NSString {

	ID idClass = FOUNDATION.objc_getClass("NSString");

	SEL selString = FOUNDATION.sel_registerName("string");
	SEL selInitWithBytesLengthEncoding = FOUNDATION.sel_registerName("initWithBytes:length:encoding:");
	SEL selStringByAppendingString = FOUNDATION.sel_registerName("stringByAppendingString:");

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
			return Foundations.objc_msgSend(idClass, selString);
		}

		byte[] utf16Bytes = javaString.getBytes(StandardCharsets.UTF_16LE);

		return
			Foundations.objc_msgSend(
				Foundations.objc_msgSend(idClass, NSObject.selAlloc),
				selInitWithBytesLengthEncoding,
				utf16Bytes,
				utf16Bytes.length,
				constNSUTF16LittleEndianStringEncoding
			);
	}

	static ID allocStringByAppendingString(ID leftNSString, ID rightNSString) {
		return Foundations.objc_msgSend(leftNSString, selStringByAppendingString, rightNSString);
	}
}
