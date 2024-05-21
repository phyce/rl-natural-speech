# Technical Documentation

A high-level overview of the plugin's architecture.

## _Dear RuneLite Plugin Reviewer_

Natural Speech utilizes interprocess communication and native interop with the operating system API. As a result, we understand this plugin is difficult to audit.

This document is designed to be helpful for your auditing process.

Thank you for your time and work.

\- Phyce, Louis Hong

## Overview of Text-To-Speech Engine/API Implementation

### Piper Engine

Added for real-time local machine-learning text-to-speech.

- 🛠️External runtime, user manual installation
- ⛓️Interprocess communication using standard I/O streams
- 🛜Networked ML model download (reason for our PluginHub warning)

### Windows Speech API 4 (WSAPI4)

Added for Gary Gilbert's voice, aka Microsoft Sam. 

- 🛠️External runtime, user manual installation
- ⛓️Interprocess communication using process I/O streams and file system I/O

### Windows Speech API 5 (WSAPI5)

Added for work-out-of-the-box experience, no setup required.

- 🛠️No external dependency
- ⛓️Windows .NET 4.0 API 
  - Interprocess communication using standard I/O streams
  - Bundled just-in-time compiled C# runtime. `WSAPI5.cs` in Resources
  - Just-in-time compiles using PowerShell's built-in NET4.0 framework


### macOS Speech

Added for work-out-of-the-box experience, no setup required.

- 🛠️No external dependency
- ⛓macOS Foundation API
  - Java-Native-Access interop with Objective-C Runtime and Foundation