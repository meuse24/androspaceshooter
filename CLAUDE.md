# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an Android vertical space shooter game built with Kotlin, Jetpack Compose for UI, and custom Canvas-based rendering for the game loop. The player controls a spaceship that automatically fires bullets at incoming meteors, with touch controls for horizontal movement and a special shockwave ability.

## Build and Run Commands

### Building
```bash
# Build debug APK
gradlew assembleDebug

# Build release APK
gradlew assembleRelease

# Clean build
gradlew clean
```

### Testing
```bash
# Run unit tests
gradlew test

# Run instrumented tests (requires connected device/emulator)
gradlew connectedAndroidTest

# Run specific test class
gradlew test --tests info.meuse24.game.ExampleUnitTest
```

### Installing
```bash
# Install debug build to connected device
gradlew installDebug
```

## Architecture

### Game Loop Architecture

The game uses a custom game loop rather than a typical Android game engine:

1. **GameView** (game/GameView.kt) - A custom SurfaceView that runs the main game thread
   - Implements `Runnable` and runs on a dedicated thread
   - Targets 60 FPS using `targetFrameNanos` and sleep-based frame pacing
   - Manages surface lifecycle (created/changed/destroyed)
   - Routes touch events to the engine

2. **GameEngine** (game/GameEngine.kt) - Core game logic and state
   - Manages all game entities (bullets, meteors, particles, player)
   - Handles collision detection, spawning logic, and score tracking
   - Renders all game objects to Canvas
   - Implements sprite generation at runtime (player, meteors, flames)
   - Manages visual effects (screen shake, shockwave, particle explosions)

3. **GameState** (game/GameState.kt) - Simple enum for state management
   - WAITING: Game hasn't started
   - RUNNING: Active gameplay
   - GAME_OVER: Game ended, showing score

### Entity System

Entities are simple data classes with update/draw methods:

- **Player** (entities/Player.kt) - Player ship with smooth follow movement
- **Meteor** (entities/Meteor.kt) - Falling asteroids with procedural shapes
- **Bullet** (entities/Bullet.kt) - Upward-moving projectiles
- **Particle** (entities/Particle.kt) - Explosion particles with lifetime decay

All entities use screen-relative sizing (fraction of screen width/height) for responsive layout.

### UI Layer

The game uses Jetpack Compose for overlays while the game canvas renders underneath:

- **GameScreen** (ui/GameScreen.kt) - Main composable that combines:
  - AndroidView wrapper for the custom GameView
  - Compose overlays for HUD, buttons, and game state screens
  - State hoisting from MainActivity

- **MainActivity** - Sets up state and event listeners
  - Uses `remember` with mutable state for score, FPS, game state
  - Implements GameEventListener to bridge engine events to Compose state

### Configuration and Tuning

**GameConfig** (game/GameConfig.kt) is a data class containing all tunable gameplay parameters:
- Fire rate, bullet speed, meteor spawn intervals
- Difficulty progression (speed ramps, spawn acceleration)
- Shockwave properties (cooldown, radius, push force)
- Entity sizes as fractions of screen dimensions

To adjust difficulty or pacing, modify values in GameConfig.

### Sound Effects

**SoundEffects** (game/SoundEffects.kt) uses Android SoundPool for low-latency audio. Sounds are generated procedurally at runtime rather than loaded from files.

### Data Persistence

**HighscoreRepository** (utils/HighscoreRepository.kt) uses SharedPreferences to persist the high score across app sessions.

## Key Implementation Details

### Frame-Rate Independent Movement

All movement uses `deltaSeconds` (time since last frame) to ensure consistent speed regardless of frame rate:
```kotlin
entity.y += speed * deltaSeconds
```

### Collision Detection

Uses circle-to-circle distance checks for bullets/meteors and circle-to-rectangle for meteor/player collisions. All done in GameEngine.detectCollisions().

### Sprite Generation

Instead of loading image assets, sprites are generated at runtime using Canvas drawing:
- Player ship: Multi-layer path drawing (wings, fuselage, cockpit)
- Meteors: Procedural polygons with random craters and cracks
- Flame sprites: Multiple frames for animation flicker

This happens in GameEngine.initSprites() during resize.

### Shockwave Mechanic

The shockwave is a special ability with cooldown:
- Player triggers via button click
- Expands outward from player position over 0.65 seconds
- Destroys small meteors, pushes large ones
- Uses easing function for smooth radius expansion
- 12-second cooldown with visual charge indicator

### Threading Model

- Main thread: Compose UI and event handling
- Game thread: Dedicated thread in GameView running the game loop
- Thread lifecycle: Created on startGame(), stopped on surfaceDestroyed()
- Uses AtomicBoolean for thread-safe loop control
