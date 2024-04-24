package dev.phyce.naturalspeech.jna.macos.foundation.objects.collections;

import com.sun.jna.NativeLong;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.SEL;
import dev.phyce.naturalspeech.jna.macos.foundation.util.Foundation;

public interface NSDictionary {

    NativeLong idClass = Foundation.objc_getClass("NSDictionary");

    SEL selDictionaryWithContentsOfFile = Foundation.sel_registerName("dictionaryWithContentsOfFile:");
    SEL selObjectForKey = Foundation.sel_registerName("objectForKey:");
}
