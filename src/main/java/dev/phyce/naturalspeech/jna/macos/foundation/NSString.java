
package dev.phyce.naturalspeech.jna.macos.foundation;

import com.sun.jna.Pointer;
import com.sun.jna.platform.mac.CoreFoundation;
import dev.phyce.naturalspeech.jna.macos.objc.ID;
import dev.phyce.naturalspeech.jna.macos.objc.LibObjC;
import dev.phyce.naturalspeech.jna.macos.objc.SEL;
import java.nio.charset.StandardCharsets;
import lombok.NonNull;

public interface NSString {

	ID idClass = LibObjC.objc_getClass("NSString");

	SEL selString = LibObjC.sel_registerName("string");
	SEL selInitWithBytesLengthEncoding = LibObjC.sel_registerName("initWithBytes:length:encoding:");
	SEL selStringByAppendingString = LibObjC.sel_registerName("stringByAppendingString:");

	long constNSUTF16LittleEndianStringEncoding = 0x94000100L;


	static @NonNull String getJavaString(@NonNull ID self) {
		if (self.isNil()) {
			return "nil";
		}
		CoreFoundation.CFStringRef cfString = new CoreFoundation.CFStringRef(self);

		return cfString.stringValue();
	}

	static ID alloc(String javaString) {
		if (javaString.isEmpty()) {
			return LibObjC.objc_msgSend(idClass, selString);
		}

		byte[] utf16Bytes = javaString.getBytes(StandardCharsets.UTF_16LE);

		return
			LibObjC.objc_msgSend(
				LibObjC.objc_msgSend(idClass, NSObject.selAlloc),
				selInitWithBytesLengthEncoding,
				utf16Bytes,
				utf16Bytes.length,
				constNSUTF16LittleEndianStringEncoding
			);
	}

	static ID allocStringByAppendingString(ID leftNSString, ID rightNSString) {
		return LibObjC.objc_msgSend(leftNSString, selStringByAppendingString, rightNSString);
	}
}
