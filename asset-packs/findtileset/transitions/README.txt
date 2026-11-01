Terrain transitions: each folder joins two floors of this pack (base__over).
16 pieces per pair, one for every combination of the 4 corners (1 = 'over' terrain at that corner).
index = TL*8 + TR*4 + BL*2 + BR in <name>_sheet.png (4x4).
Tiled: open <name>.tsx, it contains a corner Wang set -> paint with the terrain brush.
Godot 4: TileSet > Terrains, mode 'Match Corners', use the corner values in <name>.json.
<name>_demo_map.png shows a map built only from these pieces.
This sampler has 1 of the transition sets; the full packs have up to 6 (see the store page).
