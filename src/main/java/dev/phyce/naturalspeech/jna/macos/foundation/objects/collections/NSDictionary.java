package dev.phyce.naturalspeech.jna.macos.foundation.objects.collections;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import dev.phyce.naturalspeech.jna.macos.foundation.Foundation;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.SEL;

public interface NSDictionary {

    NativeLong idClass = Foundation.FOUNDATION.objc_getClass("NSDictionary");

    SEL selDictionaryWithContentsOfFile = Foundation.FOUNDATION.sel_registerName("dictionaryWithContentsOfFile:");
    SEL selObjectForKey = Foundation.FOUNDATION.sel_registerName("objectForKey:");
}
