# Technical Documentation

A high-level overview of the plugin's architecture.

## _Dear RuneLite Plugin Reviewer_

Natural Speech uses interprocess communication and native interop with the operating system API to perform text-to-speech locally. We understand this adds complexity to the audit process.

This document is designed to be helpful for your auditing process.

Thank you for your time and work.

— Phyce, Louis Hong

# Overview of Text-To-Speech Engine/API Implementation

## Piper Engine
`texttospeech/engine/piper/PiperProcess.java`

Added for real-time local machine-learning text-to-speech.

- 🛠️External runtime, user manual installation
- ⛓️Interprocess communication using standard I/O streams
- 🛜Networked ML model download (reason for our PluginHub warning)

## Windows Speech API 4 (WSAPI4)
`texttospeech/engine/windows/speechapi4/SpeechAPI4.java`

Added for Gary Gilbert's voice, aka Microsoft Sam. 

- 🛠️External runtime, user manual installation
- ⛓️Interprocess communication using process I/O streams and file system I/O

## Windows Speech API 5.3 (WSAPI5)
`texttospeech/engine/windows/speechapi5/SAPI5Process.java`
`resources:texttospeech/engine/windows/speechapi5/WSAPI5.cs`

Added for work-out-of-the-box experience, no setup required.

- 🛠️No external dependency
- ⛓️Windows .NET 4.0 API 
  - ☢️Just-in-time compiles using PowerShell's built-in NET4.0 framework
  - Interprocess communication using standard I/O streams
  - Bundled just-in-time compiled C# runtime. `WSAPI5.cs` in Resources

☢️Significant time was invested in exploring other options. The other option is to JNI/JNA interop with Windows Native COM API. However, COM API really stinks, and may be even harder to audit for bad acting logic.<br>
_(I've written an blog about all the options explored: https://louishong.com/blog1/)_

Auditing Windows .NET 4.0 Framework C# is similar Java and the code intent is clear. 


`SAPI5Process.java` and `WSAPI5.cs` is carefully audited and commented.

## macOS Speech
`texttospeech/engine/macos/natives/objc/LibObjC.java`

Added for work-out-of-the-box experience, no setup required.

- 🛠️No external dependency
- ⛓macOS Foundation API
  - Java-Native-Access interop with Objective-C Runtime and macOS Foundation

# Plugin Specials

## `PluginEventBus`
`eventbus/PluginEventBus.java`

Entirely seperate from the client eventbus, used by speech engines to communicate with the plugin UI.
## `PluginExecutorService`
`executor/PluginExecutorService.java`

Entirely seperate from the client executor service, used by speech engines to perform async actions.

## `PluginSingletonScope`
`singleton/PluginSingletonScope.java`

Guice @Singleton via just-in-time bindings lives in the parent injector and are persistent. This interferes with hot-reloading workflow during development, and wastes client memory even after plugin is disabled/uninstalled.

> Guice JavaDoc:<br>
> Just-in-time bindings created for child injectors will be created in an ancestor injector whenever possible. This allows for scoped instances to be shared between injectors. Use explicit bindings to prevent bindings from being shared with the parent injector. 

We still want singletons, just scoped to the startup/shutdown lifecycle of the plugin; so we have a custom singleton scope. The annotation is `@PluginSingleton`, binded to scope `PluginSingletonScope`. Singletons inside this scope is cleared on `shutDown`.

## `NaturalSpeechModule`
`naturalspeech/NaturalSpeechModule.java`

NaturalSpeech just-in-time injects plugin objects into `NaturalSpeechModule`; using the `Plugin::startUp` as the bootstrap. NaturalSpeechModule reference is nullified on `shutdown` and allowed to GC. 

This allows proper hot-reloading and plugin rebooting during development and GC of plugin objects after shutdown.

## `ChatFilterPluglet` and `SpamFilterPluglet`
`spamdetection/SpamDetection.java`

Spam filtering is very important to the text-to-speech experience; it is implemented in these two plugins but not accessible to Natural Speech because their APIs are private.

We cannot use reflection because of PluginHub rules, so we copy-pasted the core filtering logic in order to perform identical filtering.

## `MacUnquanantine`
`utils/MacUnquarantine.java`

On macOS, browsers, like Chrome/Safari flag themselves with LSFileQuarantineEnabled in the .plist file: https://developer.apple.com/documentation/bundleresources/information_property_list/lsfilequarantineenabled

For example, here is the flag in the Chromium plist: https://chromium.googlesource.com/chromium/reference_builds/chrome_mac/+/refs/heads/main/Google%20Chrome.app/Contents/Info.plist#320

This flag causes all files downloaded by the App to have an extended-file-attribute named `com.apple.quarantine`. Executables and dynamic libraries with this attribute will not be able to link or execute.
This is to protect users from double-click opening an executable downloaded by the browser.

For Natural Speech, users download the Piper executable through GitHub with the browser, so the executables are
flagged with `com.apple.quarantine`.

Using macOS `xattr` executable, Natural Speech removes the flags from piper and its libraries to execute.

`xattr` main function:<br>
https://github.com/apple-oss-distributions/file_cmds/blob/main/xattr/xattr.c#L493

`-d` option invokes
```C
delete_attribute(int fd, const char *filename, const char *name)
```

https://github.com/apple-oss-distributions/file_cmds/blob/main/xattr/xattr.c#L329

which syscalls XNU function `sys/xattr.h`
```C
int fremovexattr(int fd, const char *name, int options);
```
https://github.com/apple-oss-distributions/xnu/blob/main/bsd/sys/xattr.h#L98


## `LibObjC`
`texttospeech/engine/macos/natives/objc/LibObjC.java`

For macOS, we use JNA to directly interface with the OS native Objective-C API, `Foundation`. https://developer.apple.com/documentation/foundation

Since JNA interfaces to C, but macOS API is in Objective-C, the interop is performed through the Objective-C Runtime (synonymous with the Java Runtime Environment). 
https://developer.apple.com/documentation/objectivec/objective-c_runtime?language=objc

The level of Java/Obj-C interop we perform is somewhat novel, including implementing the Obj-C Block application-binary-interface (Blocks in Obj-C is synonymous with Java closures).
https://clang.llvm.org/docs/Block-ABI-Apple.html

## Explicit Client `EventBus` Registering
Client event handlers/subscribers were partitioned into multiple objects, they explicitly subscribe to the `EventBus` in `startUp` and reliably unsubscribe on `shutDown`. We placed these event handlers objects in the package `clienteventhandlers`. 