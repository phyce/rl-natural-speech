package dev.phyce.naturalspeech.texttospeech.engine.macos.foundation;

import dev.phyce.naturalspeech.texttospeech.engine.macos.objc.SEL;
import dev.phyce.naturalspeech.texttospeech.engine.macos.objc.ID;
import dev.phyce.naturalspeech.texttospeech.engine.macos.objc.LibObjC;

public interface NSDictionary {

    ID idClass = LibObjC.objc_getClass("NSDictionary");

    SEL selDictionaryWithContentsOfFile = LibObjC.sel_registerName("dictionaryWithContentsOfFile:");
    SEL selObjectForKey = LibObjC.sel_registerName("objectForKey:");
}
