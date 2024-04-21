package dev.phyce.naturalspeech.jna.macos.foundation;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.ID;

@SuppressWarnings({"BooleanMethodIsAlwaysInverted", "UnusedReturnValue"})
public interface Foundation extends Library {

    Foundation FOUNDATION = Native.load("Foundation", Foundation.class);

    ID class_getInstanceVariable(NativeLong classPointer, String name);

    ID object_getIvar(NativeLong target, NativeLong ivar);

    ID objc_getClass(String className);

    ID objc_allocateClassPair(NativeLong superClass, String name, long extraBytes);

    void objc_registerClassPair(NativeLong clazz);

    ID class_createInstance(NativeLong clazz, int extraBytes);

    boolean class_addMethod(NativeLong clazz, Pointer selector, Callback callback, String types);

	ID objc_msgSend(NativeLong receiver, Pointer selector);

    ID objc_msgSend(NativeLong receiver, Pointer selector, Pointer obj);

    ID objc_msgSend(NativeLong receiver, Pointer selector, NativeLong objAddress);

    ID objc_msgSend(NativeLong receiver, Pointer selector, boolean boolArg);

    ID objc_msgSend(NativeLong receiver, Pointer selector, double doubleArg);

    // Used by NSObject.performSelectorOnMainThread
    ID objc_msgSend(NativeLong receiver, Pointer selector, Pointer selectorDst, NativeLong objAddress, boolean wait);

    // Used by NSString.fromJavaString
    ID objc_msgSend(NativeLong receiver, Pointer selector, byte[] bytes, int len, long encoding);

    Pointer sel_registerName(String selectorName);

}
