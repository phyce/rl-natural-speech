# Technical Documentation

A high-level overview of the plugin's architecture.

## _Dear RuneLite Plugin Reviewer_

Natural Speech utilizes interprocess communication and native interop with the operating system API. As a result, we understand this plugin is difficult to audit.

This document is designed to be helpful for your auditing process.

Thank you for your time and work.

— Phyce, Louis Hong

## Overview of Text-To-Speech Engine/API Implementation

### Piper Engine
---
`texttospeech/engine/piper/PiperProcess.java`

Added for real-time local machine-learning text-to-speech.

- 🛠️External runtime, user manual installation
- ⛓️Interprocess communication using standard I/O streams
- 🛜Networked ML model download (reason for our PluginHub warning)

### Windows Speech API 4 (WSAPI4)
---
`texttospeech/engine/windows/speechapi4/SpeechAPI4.java`

Added for Gary Gilbert's voice, aka Microsoft Sam. 

- 🛠️External runtime, user manual installation
- ⛓️Interprocess communication using process I/O streams and file system I/O

### Windows Speech API 5.3 (WSAPI5)
---
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

### macOS Speech
---
`texttospeech/engine/macos/natives/objc/LibObjC.java`

Added for work-out-of-the-box experience, no setup required.

- 🛠️No external dependency
- ⛓macOS Foundation API
  - Java-Native-Access interop with Objective-C Runtime and macOS Foundation

## Plugin Specials

### PluginEventBus
Entirely seperate from the client eventbus, used by speech engines to communicate with the plugin UI.
### PluginExecutorService
Entirely seperate from the client executor service, used by speech engines to perform async actions.

### PluginSingletonScope
Guice @Singleton via just-in-time bindings lives in the parent injector and are persistent. This interferes with hot-reloading workflow during development, and wastes client memory even after plugin is disabled/uninstalled.

> Guice JavaDoc:<br>
> Just-in-time bindings created for child injectors will be created in an ancestor injector whenever possible. This allows for scoped instances to be shared between injectors. Use explicit bindings to prevent bindings from being shared with the parent injector. 

We still want singletons, just scoped to the startup/shutdown lifecycle of the plugin; so we have a custom singleton scope. The annotation is `@PluginSingleton`, binded to scope `PluginSingletonScope`. Singletons inside this scope is cleared on `shutDown`.

### NaturalSpeechModule
NaturalSpeech just-in-time injects plugin objects into `NaturalSpeechModule`; using the `Plugin::startUp` as the bootstrap. NaturalSpeechModule reference is nullified on `shutdown` and allowed to GC. 

This allows proper hot-reloading and plugin rebooting during development and GC of plugin objects after shutdown.