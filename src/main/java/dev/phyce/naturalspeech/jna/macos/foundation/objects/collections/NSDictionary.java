package dev.phyce.naturalspeech.jna.macos.foundation.objects.collections;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import dev.phyce.naturalspeech.jna.macos.foundation.Foundation;

public interface NSDictionary {

    NativeLong idClass = Foundation.FOUNDATION.objc_getClass("NSDictionary");

    Pointer selDictionaryWithContentsOfFile = Foundation.FOUNDATION.sel_registerName("dictionaryWithContentsOfFile:");
    Pointer selObjectForKey = Foundation.FOUNDATION.sel_registerName("objectForKey:");
}
