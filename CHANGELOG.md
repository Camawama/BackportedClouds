# Changelog

## 1.0.8

- With VS Genesis, planets of the overworld's kind have clouds from afar (`PlanetCloudWrap`, "Clouds on VS Genesis planets from afar", on). Earth and worlds made like it (Multiworld's overworlds, `/planet create earthlike`) show the whole cloud texture wrapped round them from space and from other worlds' skies: once round the equator, its full height up to about 85 degrees either side by Mercator's projection, thinning out before the poles, where a tile can't reach without a pinch. The projection is baked into 64 square cells a face, so the pixels stay lined up with the cube. The clouds are lit by the planet's own sun, drift as the world's do, sit under Genesis' glow, and cast shadows on the day side ("Cloud shadows on VS Genesis planets", on). Each world has its own part of the texture. Storms from Space Events are baked into the same cells (`PlanetCloudWrap.storms`), white as storms are from space: a cell's alpha is how thick its cloud is, thickest at a storm's heart, which also throws the darkest shadow, and ordinary clouds are a little thinner, so a storm is the brightest thing on the planet. An eclipse's shadow falls on the clouds as well as the ground: they are drawn before Genesis' shadows.
- Clouds are drawn only on planets of the overworld's kind: the layer on the planet below no longer appears over the moon or Mars. A body is of that kind when its atmosphere has Earth's colours (`genesis:overworld`).
- The clouds on a planet's night side keep a third of the light they had: at the planet's ambient tenth they were the brightest thing there.

## 1.0.5

- Clouds fade toward the horizon the way 1.21.6 and later draw them (2026-10-26). In current vanilla each cloud's opacity falls in a straight line with its distance from the camera, from full overhead to nothing at the cloud fog distance (the cloud range: 2048 blocks at 128 chunks), with no tint toward the fog colour (`rendertype_clouds.fsh`: `color.a *= 1 - linear_fog_value(distance, 0, FogCloudsEnd)`), and the far plane reaches the cloud range (`max(renderDistance * 4, cloudRange * 16)`). Until now this mod's clouds kept their renderer's own fog: vanilla's fogged them toward the fog colour at the render distance (times the distance multiplier), Embeddium's held them at full opacity out to its cloud distance (2.5 times the range, with the setting at 128), then faded them out over 64 blocks, with 30% of the fog colour mixed into every cloud; the far clouds ended at a line rather than thinning out. Now the mod draws both paths with cloud shaders of its own that fade vanilla's way (`backportedclouds:clouds` for the vanilla mesh, `backportedclouds:clouds_sodium` for Embeddium's renderer, swapped in for its own round its `render`), sets the cloud pass's fog to start 0 and end the cloud range, and extends the far plane to the range as vanilla does. In water, lava or powder snow, blinded or in darkness, in a foggy biome or a boss's world fog, the renderer's own short fog stands and the clouds fade out within it, as vanilla's do. Embeddium's mesh is now sized to the range (the square of cells that covers it) instead of the configured value as is. New config option "Fade toward the horizon" (on); off, everything is as in 1.0.4. Not under an Oculus shader pack, whose own cloud program draws the clouds. For other mods: `CloudFade.style()` says which fade the cloud pass is drawn with, so a mod that swaps the cloud shader for a copy of its own (Camafy 0.1.54's storm clouds) fades the same way. Distant Horizons: the LOD depth pass round the clouds is unchanged and runs with the new shaders; the far plane change only moves the depth it maps LODs into. Harness `clouds_fade`.
- With Distant Horizons the clouds show again (2026-10-26). DH's passes earlier in the frame leave the window's framebuffer (0) bound, and with Fabulous graphics' render targets still alive after DH switches that setting off at world load, vanilla binds nothing of its own before the clouds; so the clouds were drawn into the window and overwritten when the game's target was blitted to it, whichever renderer drew them, and there were no clouds at all with DH in (the harness showed this since 2026-10-13, with 1.0.4 as well). Before the clouds the mod now binds the game's own target where vanilla expects it (the clouds target under Fabulous graphics, else the main one; `DhCloudDepth`, with DH loaded, one log line the first time). Harness `clouds_fade` records the binding (`dhBinding`).

## 1.0.4

- Distant Horizons no longer switches the Clouds video setting off (it kept doing so in the settings). Every time DH's renderer starts (each world or dimension you enter) it sets the game's Clouds option to OFF, logging "Disabling vanilla clouds... This is done to prevent vanilla clouds from rendering on top of Distant Horizons LODs" (its `overrideVanillaGraphicsSettings` option, on by default). This mod already draws the clouds behind the LODs (above), so that override is cancelled (`DhVanillaCloudsMixin`, a by-name mixin into DH's `MinecraftClientWrapper_forge.disableVanillaClouds`; DH's other overrides are left alone). New config option "Keep the Clouds setting with Distant Horizons" (on). Set Clouds back to Fancy or Fast once; it stays.
- Clouds are now hidden behind Distant Horizons' LOD terrain when it is nearer than them, and stay in front of LODs that are farther (the clouds rendered on top of LODs instead of behind them). Distant Horizons draws its LODs into a framebuffer of its own and only copies their colour onto the game's picture, so the game's depth buffer was empty there and the clouds, drawn later, covered distant LOD mountains. Just before the clouds, the LODs' depth is written into the depth buffer (taken from DH's projection into the clouds'), and the game's own depth is put back right after, so nothing else sees it. It works whichever renderer draws the clouds (this mod's, Embeddium's, Camafy's storm-cloud shading). Distant Horizons stays optional; nothing changes without it, or with an Oculus shader pack. New config option "Hide clouds behind Distant Horizons LODs" (on).

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
