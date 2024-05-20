package dev.phyce.naturalspeech.texttospeech.engine.macos.natives.foundation;

import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.SEL;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.ID;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.LibObjC;

public interface NSDictionary {

    ID idClass = LibObjC.objc_getClass("NSDictionary");

    SEL selDictionaryWithContentsOfFile = LibObjC.sel_registerName("dictionaryWithContentsOfFile:");
    SEL selObjectForKey = LibObjC.sel_registerName("objectForKey:");
}
