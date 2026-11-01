ANIMATED TILES
Each *_stripN.png contains N frames side by side (left to right), every frame the same size as the tile.
Recommended speed: 160 ms per frame, looping. Frame counts and sizes are listed in animations.json.
Godot: Sprite2D/AnimatedSprite2D with Hframes = N.  Unity: Sprite Editor > Slice > Grid by cell size.
GameMaker: import the *_stripN.png file, it is split into N frames automatically.
Tiled: in the tileset editor assign the frames to the tile to make an animated tile.
The same frames are also in frames/<name>/frame_1.png ... frame_N.png for tools that prefer separate images.
The .gif files are previews only.
