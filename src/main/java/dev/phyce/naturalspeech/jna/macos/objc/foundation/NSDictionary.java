package dev.phyce.naturalspeech.jna.macos.objc.foundation;

import com.sun.jna.NativeLong;
import dev.phyce.naturalspeech.jna.macos.objc.ObjC;
import dev.phyce.naturalspeech.jna.macos.objc.SEL;

public interface NSDictionary {

    NativeLong idClass = ObjC.objc_getClass("NSDictionary");

    SEL selDictionaryWithContentsOfFile = ObjC.sel_registerName("dictionaryWithContentsOfFile:");
    SEL selObjectForKey = ObjC.sel_registerName("objectForKey:");
}
