package dev.phyce.naturalspeech.jna.macos.foundation;

import com.sun.jna.NativeLong;
import dev.phyce.naturalspeech.jna.macos.objc.ID;
import dev.phyce.naturalspeech.jna.macos.objc.LibObjC;
import dev.phyce.naturalspeech.jna.macos.objc.SEL;

public interface NSDictionary {

    ID idClass = LibObjC.objc_getClass("NSDictionary");

    SEL selDictionaryWithContentsOfFile = LibObjC.sel_registerName("dictionaryWithContentsOfFile:");
    SEL selObjectForKey = LibObjC.sel_registerName("objectForKey:");
}
