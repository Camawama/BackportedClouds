# Changelog

## Unreleased

- Clouds are now hidden behind Distant Horizons' LOD terrain when it is nearer than them, and stay in front of LODs that are farther (the owner: "Our clouds render on top of LODs instead of behind them"). Distant Horizons draws its LODs into a framebuffer of its own and only copies their colour onto the game's picture, so the game's depth buffer was empty there and the clouds, drawn later, covered distant LOD mountains. Just before the clouds, the LODs' depth is written into the depth buffer (taken from DH's projection into the clouds'), and the game's own depth is put back right after, so nothing else sees it. It works whichever renderer draws the clouds (this mod's, Embeddium's, Camafy's storm-cloud shading). Distant Horizons stays optional; nothing changes without it, or with an Oculus shader pack. New config option "Hide clouds behind Distant Horizons LODs" (on).

## 1.0.3

- Clouds no longer vanish when you fly above y 500 with VS Genesis. Genesis hides the clouds of its planet dimensions (the overworld included) once the camera is above y 500, all at once, as part of its sky turning to space. The clouds below now stay in view from any height. Genesis still leaves clouds out where a planet's air is too thin for them. The new "Keep clouds above the VS Genesis ceiling" config option (on) turns this off.
- With More Weathers on Camafy 0.1.36, the clouds over a storm are darkened where the storm is (Camafy draws that into the cloud shader, this mod's clouds and Embeddium's alike), so a storm shows from as far as the clouds reach.

## 1.0.2

- Clouds in the Aether now render out to the configured cloud distance. The Aether replaces cloud rendering with its own copy of the vanilla renderer (capped at the vanilla distance); the mod now hands those clouds back to the extended renderer. The Aether's "disable clouds" config option is still respected.
- The cloud distance setting now also works while an Oculus shader pack is active. Oculus draws shader-pack clouds with its own renderer, which ignored the setting. With shaders the distance is capped to the view range Oculus uses (about 29 chunks at a 16-chunk render distance), and packs that fade out, hide or replace vanilla clouds still decide what you see. The new "Extend clouds with shader packs" config option turns this off.

## 1.0.1

This update fixes the major lag issues, especially when flying above the clouds or playing in dimensions like the Aether.

- Huge performance improvements across the board. Clouds were being rendered way farther than your setting actually said, doing far more work than needed. They now render exactly as far as you configure.
- Fixed heavy lag when flying up or down (elytra, Aether gliding, etc.)
- Fixed clouds flashing invisible or looking broken while flying through the cloud layer
- Fixed stuttering during sunrise, sunset, and weather changes
- Clouds now drift in sync for everyone on a server, like in newer Minecraft versions
- Fixed clouds slowly going out of alignment far from spawn or during long sessions
- The mod no longer shows a red X next to servers that don't have it installed
- Reduced memory usage
- Fixed a rare crash related to cloud building
- The cloud distance setting now works the same in both Fancy and Fast cloud modes

## 1.0.0

- Initial release
