# Master of Reins

A 2D side-scrolling horse racing game built with Java Swing.

## Requirements

- Java 17 or newer.

## Download

Download this repository as a ZIP from GitHub, or clone it:

```text
git clone https://github.com/PiyadaALM/MasterOfReins.git
```

Extract the ZIP if you downloaded it.

## Run

Open a terminal in the project folder and run:

```text
java -jar MasterOfReins.jar
```

On Windows, you can also double-click `run.bat`.

Keep `MasterOfReins.jar`, `assets/`, and `asset-packs/` in the same folder. The game loads its map and images from these folders.

## How to Play

Race from left to right, pass both checkpoints in order, and reach the finish line before the other riders.

| Key | Action |
| --- | --- |
| Space | Run right; double-tap within 0.3 seconds to sprint |
| W / S or Up / Down | Move up or down |
| O (hold) | Charge stamina |
| J | Attack |
| K (hold) | Guard |
| P or Esc | Pause or resume |
| F3 | Show or hide collision debug view |

Manage stamina, avoid obstacles and water, collect items, and use the main route or the deeper bypass.

## Build

Compile the source and create the JAR:

```powershell
$sources = Get-ChildItem -Recurse src -Filter *.java | ForEach-Object { $_.FullName }
javac --release 17 -encoding UTF-8 -d build $sources
jar --create --file MasterOfReins.jar --main-class game.app.Main -C build .
```

## Notes

- The JAR contains compiled classes only. The `assets/` and `asset-packs/` folders are required at runtime.
- The game uses one Tiled map: `assets/Map/Background.tmx`.
- The project does not use an external game engine.

## Credits

Map and game code: student project.

Art and tileset assets: GandalfHardcore, REalOne, and the other asset packs included in `asset-packs/`.
