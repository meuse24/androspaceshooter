# Repository Guidelines

## Project Structure & Module Organization
- Single Android module under `app`; gameplay code in `app/src/main/java/info/meuse24/game/game` (engine, config, loop), entities in `app/src/main/java/info/meuse24/game/entities`, Compose UI overlays in `app/src/main/java/info/meuse24/game/ui`, theming in `app/src/main/java/info/meuse24/game/ui/theme`.
- Android resources (colors, themes, icons) sit in `app/src/main/res`; manifest in `app/src/main/AndroidManifest.xml` defines the `info.meuse24.game` namespace and locks portrait.
- Unit tests mirror packages in `app/src/test/java`; instrumentation tests live in `app/src/androidTest/java` and target the same package structure.
- Build scripts: root `build.gradle.kts` and `settings.gradle.kts` configure the workspace; module-specific settings in `app/build.gradle.kts`.

## Build, Test, and Development Commands
- Prefer the wrapper (`./gradlew` on Unix, `./gradlew.bat` on Windows) to match the repo's Gradle version.
- Build debug APK: `./gradlew assembleDebug`; install on a connected device/emulator: `./gradlew installDebug`.
- Run unit tests on JVM: `./gradlew test`.
- Run instrumentation/UI tests on a device/emulator: `./gradlew connectedAndroidTest` (ensure a device is available).
- Static checks: `./gradlew lint` for Android lint; use Android Studio's Code > Reformat to keep Kotlin style consistent.

## Coding Style & Naming Conventions
- Kotlin code follows standard style: 4-space indent, `camelCase` for vars/functions, `PascalCase` for classes and Compose `@Composable` functions, and `SCREAMING_SNAKE_CASE` for constants.
- Game loop runs on a SurfaceView thread; keep state mutation in the engine, and UI as thin overlay. Favor immutable values for draw/update inputs.
- Resource files use `snake_case` (e.g., `ic_launcher.xml`, `colors.xml`); package names stay lowercase (`info.meuse24.game`).

## Testing Guidelines
- Unit tests use JUnit4 (`testImplementation(libs.junit)`); instrumentation uses AndroidX test runner with `AndroidJUnit4`.
- Name tests after the subject (`MainActivityTest`, `GameEngineTest`); prefer assertion names that describe behavior.
- Cover spawn/timing math in engine with deterministic seeds; for UI/Compose tests rely on semantics matchers instead of text when possible.
- Run `./gradlew test` for fast checks before PRs; add `connectedAndroidTest` when UI logic changes.

## Commit & Pull Request Guidelines
- No visible Git history here; default to Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`) with imperative, present-tense summaries.
- PRs should include a concise description, linked issue/feature ID, and screenshots or screen recordings for UI-visible changes (emulator is fine).
- List validation steps in the PR (e.g., `./gradlew test`, `./gradlew connectedAndroidTest`) and call out any TODOs or follow-ups.

## Security & Configuration Tips
- Keep `local.properties` local (SDK paths, keystores); never commit secrets. Use `gradle.properties` or environment variables for non-checked-in configs.
- `proguard-rules.pro` is present but not enabled for debug; add rules alongside new libraries when minify is toggled on.
- Maintain dependency versions via the version catalog (`libs.versions.toml` if present); update the Compose BOM together to avoid mix-matched artifacts.

## Gameplay Tuning Cheatsheet
- Core knobs in `app/src/main/java/info/meuse24/game/game/GameConfig.kt`: player size/follow speed, bullet speed/rate, meteor size/speed ramp, spawn intervals.
- Explosion styling in `spawnExplosion()` inside `GameEngine`: particle count, speed, lifetime, color.
- Meteor silhouette randomness in `app/src/main/java/info/meuse24/game/entities/Meteor.kt` via sides count and `jitter`; player shape in `Player.draw`.
- Visual/audio extras: screen shake and synthetic SFX are triggered in `GameEngine` (laser, explosion, player hit). Highscore persists via `HighscoreRepository` and is shown in the HUD/overlays.
- Performance: ship/meteor visuals are pre-rendered to bitmaps in `GameEngine.initSprites()` and reused; adjust sprite variant count and sizes there for memory/FPS trade-offs. Background stars/planets draw in `GameEngine.draw` with lightweight primitives.
- Shockwave special: configurable in `GameConfig` (cooldown/duration/radius/push). UI button and cooldown bar in `GameScreen`; activation flows through `GameView.triggerShockwave()` into `GameEngine.activateShockwave()`, which slows/knocks back or destroys nearby meteors and draws an expanding ring.

## Game Engine Review and Performance Optimizations

### Zusammenfassende Analyse

Das Projekt ist gut strukturiert und folgt bewährten Mustern für die Android-Spieleentwicklung. Die Trennung der Spiellogik (`GameEngine`) von der Rendering-Schicht (`GameView`) ist sauber und macht den Code wartbar. Die Verwendung einer zentralen `GameConfig`-Klasse zur Feinabstimmung der Spielmechanik ist ein klares Plus, da sie schnelle Anpassungen ohne tiefgreifende Code-Änderungen ermöglicht.

Die `GameEngine`-Klasse ist das Herzstück des Spiels und verwaltet den Spielzustand, alle Spielobjekte (Spieler, Meteore, Kugeln), Kollisionen, Spezialeffekte wie Bildschirmerschütterungen und die Schockwelle sowie die Hintergrunddarstellung.

Die Leistung wurde bereits an entscheidenden Stellen berücksichtigt, insbesondere durch das Vor-Rendern der Sprites in `Bitmap`-Objekte. Dies ist eine wesentliche Optimierung, die das wiederholte Zeichnen komplexer Formen in jeder Frame überflüssig macht.

### Implementierte Leistungsoptimierungen

Basierend auf einer detaillierten Code-Analyse wurden die folgenden Performance-Optimierungen in der `GameEngine`-Klasse vorgenommen, um die Effizienz zu steigern und die Belastung des Garbage Collectors (GC) zu reduzieren:

1.  **Wiederverwendung von `Paint`-Objekten in `spawnExplosion`:**
    *   **Vorher:** Für jedes Partikel einer Explosion wurde ein neues `Paint`-Objekt erstellt, was zu hohem GC-Druck führte.
    *   **Nachher:** Ein Pool von `Paint`-Objekten wird nun einmalig beim Initialisieren der Engine erstellt und diese Objekte werden wiederverwendet. Die Farben werden zur Laufzeit angepasst, was die Objekt-Allokation während des Spiels minimiert.

2.  **Verwendung von indexbasierten Schleifen anstelle von `forEach`:**
    *   **Vorher:** In leistungskritischen Funktionen wie `draw`, `detectCollisions`, `updateStars`, `drawStars`, `drawPlanets`, `createFlameSprites` und `createMeteorSprite` wurden `forEach`-Schleifen verwendet. Diese erzeugen intern Iteratoren, die zu unnötigen Objekt-Allokationen und GC-Druck führen können.
    *   **Nachher:** Alle identifizierten `forEach`-Schleifen wurden durch effizientere, indexbasierte `for`-Schleifen ersetzt. Dies vermeidet die Iterator-Allokation pro Frame/Aufruf und verbessert die allgemeine Laufzeit-Performance und Speicherverwaltung.

Diese Optimierungen tragen dazu bei, eine stabilere und flüssigere Framerate zu gewährleisten, insbesondere in Szenarien mit vielen gleichzeitig aktiven Spielobjekten oder auf Geräten mit begrenzten Ressourcen.