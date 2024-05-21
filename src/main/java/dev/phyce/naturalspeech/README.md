# Technical Documentation

A high-level overview of the plugin's architecture.

## Dear RuneLite Plugin Reviewer
NaturalSpeech implements machine-local text-to-speech, 
which utilizes interprocess communication and native interop with operating system API.

As a result, we understand this plugin is difficult to audit. 
We offer our condolences, and we appreciate your time.

## Overview of Text-To-Speech Engine/API Implementation

### Piper Engine
Added for realtime machine-learning text-to-speech.

- External dependency, user manual installation
- Interprocess communication using process I/O streams
- Networked ML model download (reason for our PluginHub warning)

### Windows Speech API 4 (WSAPI4)
Added for Gary Gilbert's voice, aka Microsoft Sam. 

- External dependency, user manual installation
- Interprocess communication using process I/O streams and file system I/O

### Windows Speech API 5 (WSAPI5)
Added for work-out-of-the-box experience, no setup required.

- No external dependency
- Interprocess communication using process I/O streams
- Bundled just-in-time compiled C# runtime. `WSAPI5.cs` in Resources
    - Just-in-time compiles using PowerShell's built-in NET4.0 framework

### macOS Speech 
Added for work-out-of-the-box experience, no setup required.

- No external dependency
- Interop with macOS Objective-C API using Java-Native-Access