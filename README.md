# AndroSpaceShooter

Ein einfaches Space-Shooter-Spiel, entwickelt für Android mit Kotlin und Jetpack Compose.

## Features

*   **Prozedural generierte Grafik**: Alle Spielobjekte (Spieler, Meteore, Kraken, Partikel) werden zur Laufzeit mit Canvas-API gezeichnet, es werden keine externen Bild-Assets verwendet.
*   **Performance-Optimierungen**:
    *   **Object Pooling für Partikel**: Reduziert Garbage Collection und sorgt für flüssigere Explosionen.
    *   **Optimiertes Sound-System**: Nutzt `AudioTrack`-Pooling und einen Hintergrund-Executor, um Audio-Verzögerungen zu vermeiden und gleichzeitig Mehrfach-Sounds zu ermöglichen (Polyphonie).
*   **Dynamische Soundeffekte**:
    *   **"Wabermittel" Kraken-Loop**: Ein unheimlicher, auf- und abschwellender Ton begleitet die Space-Krake und passt sich dynamisch an ihre Geschwindigkeit an.
    *   **Spezialisierte Explosionen**: Unterscheidliche Explosionsgeräusche für Meteore und die Space-Krake.
    *   **Highscore-Sound**: Ein triumphales Arpeggio bei einem neuen Highscore.
    *   **Game Over-Sound**: Ein trauriger Abwärts-Sound, wenn kein Highscore erreicht wurde.
*   **Bonus Space-Krake**: Eine gelegentlich auftauchende Alien-Krake, die mit variabler Geschwindigkeit den Bildschirm quert. Ein Treffer bringt viele Bonuspunkte und löst eine spezielle Explosion aus.
*   **Grundlegende Game-Loop**: Update- und Render-Schleife mit FPS-Limitierung.
*   **Bildschirm-Schütteln**: Visuelles Feedback bei Kollisionen.
*   **Schockwelle**: Eine spezielle Fähigkeit des Spielers, die nahegelegene Meteore zerstört oder wegstößt.

## Kompilieren und Starten

Um das Spiel zu kompilieren und auf deinem Android-Gerät oder Emulator auszuführen, folge diesen Schritten:

1.  **Android Studio installieren**: Stelle sicher, dass du die neueste Version von [Android Studio](https://developer.android.com/studio) installiert hast.
2.  **Projekt klonen**:
    ```bash
    git clone https://github.com/meuse24/androspaceshooter.git
    cd androspaceshooter
    ```
3.  **Projekt in Android Studio öffnen**:
    *   Starte Android Studio.
    *   Wähle "Open an existing Android Studio project" oder "Open" und navigiere zum geklonten `androspaceshooter`-Verzeichnis.
4.  **Gradle Sync abwarten**: Android Studio sollte automatisch die Gradle-Dateien synchronisieren. Falls nicht, klicke auf "Sync Project with Gradle Files" (Das Elefant-Symbol in der Toolbar).
5.  **App ausführen**:
    *   Verbinde ein Android-Gerät oder starte einen Android-Emulator.
    *   Klicke auf den grünen "Run" (Play-Icon) Button in der Toolbar von Android Studio.

Das Spiel sollte auf deinem Gerät/Emulator starten.
