# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Run

This is a Gradle 9.6.1 project using Fabric Loom. It targets Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.155.2+26.2, and Java 25. On Windows, use the checked-in wrapper:

```powershell
# Compile, process resources, run checks/tests, and produce the remapped mod JAR
.\gradlew.bat build

# Compile main Java sources only
.\gradlew.bat compileJava

# Launch a development Minecraft client (runtime files go under run/)
.\gradlew.bat runClient

# Run all tests/checks
.\gradlew.bat check
.\gradlew.bat test

# Run one test class or method once tests exist
.\gradlew.bat test --tests "fully.qualified.TestClass"
.\gradlew.bat test --tests "fully.qualified.TestClass.methodName"

# Remove generated build output
.\gradlew.bat clean
```

There is currently no `src/test` tree, no explicit test dependency, and no separate formatter or lint task. `build` is the primary verification command. The development client depends on Minecraft assets and the remote NetEase API, so network access may be required.

## Architecture

This is a client-only Fabric mod under `dev.naominet.listclient`. `fabric.mod.json` declares no Fabric entrypoint; lifecycle and game hooks are installed entirely through the client mixins listed in `src/main/resources/listclient.mixins.json`.

### Bootstrap and event flow

- `MixinMinecraft` invokes `ListClient.start()` after the `Minecraft` constructor and `ListClient.stop()` when Minecraft stops.
- `ListClient.start()` initializes `ModuleManager` and `CommandManager`, registers itself with the event bus, and plays the startup sound. Shutdown saves module configuration.
- Mixins translate Minecraft activity into the internal event types: keyboard input, outgoing packets, player motion updates, and HUD rendering. `MixinMouseHandler` instead updates shared `MouseData` used by draggable HUD elements.
- `EventManager` is a synchronous reflection-based exact-class dispatcher. Handlers are methods annotated with `@EventTarget` and exactly one `Event` parameter. Enabled modules register themselves; disabled modules unregister themselves.
- Event cancellation is honored by the originating cancellable mixin. Keep Minecraft calls and event objects aligned when changing method mappings for a new game version.

### Modules, values, and commands

- `ModuleManager.initialize()` is the explicit registry for all modules. New modules must be added there; package discovery is not automatic.
- `Module` owns enabled state, key binding, HUD coordinates/dragging, suffix text, and a list of configurable `Value<?>` objects. `setEnable()` controls event registration and invokes lifecycle callbacks after game loading finishes.
- Module settings use `Option`, `Numbers`, and `Mode`, all derived from `Value`. Their MessagePack type tags must remain compatible with `Module.read()` and `ModuleManager.read()`.
- `CommandManager` registers the dot-prefixed `.toggle` and `.bind` commands. Outgoing chat packets are intercepted by `MixinConnection`; commands may be bypassed by the `NoCommands` module.
- The package name `comamnd` is intentionally misspelled in the existing source namespace. Use that spelling for imports unless performing a coordinated package migration.

### Rendering and UI

- `MixinHud` emits `EventRender2D`; enabled render modules consume it through the event bus. `RenderUtils` contains the Minecraft GUI drawing helpers.
- `Interface` is enabled by default and owns the main HUD, enabled-module list, and the floating music mini-player. HUD module coordinates are edited by dragging while the chat screen is open through `MixinChatScreen`.
- `MusicPlayer` is an entry-style module bound to `M`: enabling it opens `MusicPlayerScreen` and then releases its enabled state. Playback/session state remains in `MusicPlayer`, while audio is process-wide through `NcmAudioPlayer.INSTANCE` and the mini-player is rendered by `Interface`.
- `MusicPlayerScreen` is the full custom screen. Shared UI state belongs in `MusicPlayer` so opening and closing the screen does not reset playback, API, queue, login, or image-cache state.

### Persistence and network integration

- `FileManager` writes MessagePack data to `List/config.dll` relative to the game working directory. The format begins with `"MZ"`, then serialized module records. Preserve field order or add explicit migration logic when changing it. Although `FileManager.read()` exists, the current startup path does not call it.
- `NCMAPI` talks to `https://music.naominet.dev` asynchronously and marshals completion callbacks back to Minecraft's client thread. It persists the NetEase cookie separately at `List/ncm_cookie.txt`.
- Dynamic remote images are registered as Minecraft textures through `DynamicImageUtils`; audio streaming is handled by `NcmAudioPlayer`. UI mutations and texture registration must stay on the Minecraft/render thread.

## Source Boundaries

Edit source under `src/main/java` and assets/configuration under `src/main/resources`. Treat `build/`, `.gradle/`, `run/`, root-level decompiled `net/` classes, and compiled `dev/` classes as generated or runtime artifacts, not source. Runtime user data under `run/` and `List/` should not be used as fixtures or committed as implementation changes.
