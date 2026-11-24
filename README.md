# Backported Clouds

Clouds from newer Minecraft, on Forge 1.20.1: the cloud texture of 1.21.11, clouds out to 128 chunks, and the fade
toward the horizon that 1.21.6 and later draw. Client only; servers don't need it, and it joins any server.

**Requires:** Minecraft 1.20.1, Forge 47.3.10+. **Optional:** Embeddium, Oculus, Distant Horizons, The Aether, VS Genesis
(each one below). MIT licence.

## What it does

- **The 1.21.11 cloud texture.** It sits at vanilla's own path and also at `backportedclouds:textures/environment/clouds.png`.
  Where vanilla's old texture would win the resource pack order (a pack whose `options.txt` lists `vanilla` without
  `mod_resources`), the clouds still take this mod's copy; a resource pack of your own on top keeps its clouds.
- **Far clouds.** Clouds render out to the cloud render distance (128 chunks by default), and the far plane reaches that
  far, so they aren't cut off.
- **The fade toward the horizon** (1.0.5). Each cloud's opacity falls with its distance from you, from full overhead to
  nothing at the cloud render distance, with no tint toward the fog colour, as 1.21.6 and later draw it. In water, lava,
  powder snow, blindness, darkness, a foggy biome or a boss's fog, the short fog of those places stands and the clouds
  fade out within it, as vanilla's do.
- **Clouds that drift in sync** for everyone on a server, as in newer Minecraft, and stay aligned far from spawn.

## Compatibility

- **Embeddium:** its cloud renderer draws the clouds; this mod sizes its mesh to the distance and gives it the fade.
- **Oculus:** with a shader pack the pack's own cloud program draws the clouds. The distance is capped to the view range
  Oculus uses and the fade stands down; packs that hide or replace clouds still decide what you see.
- **Distant Horizons:** clouds are hidden behind nearer LOD terrain and drawn in front of farther LODs; Distant Horizons
  no longer switches the Clouds setting off; and its own clouds over the LODs are made from this mod's cloud texture
  (1.0.7). Distant Horizons builds those when a world loads.
- **The Aether:** its clouds render out to the configured distance; its "disable clouds" option is still respected.
- **VS Genesis:**
  - Clouds stay in view above y 500, where Genesis would hide them all at once (1.0.3).
  - From high up, the planet you see below you carries a cloud layer of its own, and the world's clouds hand over to it
    in a short band as you rise (1.0.7). Only on planets of the overworld's kind, never over the Moon or Mars (1.0.8).
  - From space or another world's sky, Earth and worlds like it (Multiworld's overworlds) wear the whole cloud texture,
    thinning out before the poles and casting shadows on the day side (1.0.8). With Space Events, storms show in them as
    bright white patches. Seen in a day sky, a planet's night side takes the sky's colour under its clouds as well as
    under its ground, never black clouds (1.0.9).
- Not tested with other performance mods.

## Config

`config/backportedclouds-client.toml`, client side. Every option is on by default unless a value is shown.

| Option | Default | What it does |
| --- | --- | --- |
| Cloud render distance | `128` | How far clouds render, in chunks (2 to 128). |
| Fade toward the horizon | on | The 1.21.6+ fade. Off: clouds keep their renderer's own fog, as before 1.0.5. |
| Extend frustum | on | The far plane reaches the clouds, so distant ones aren't clipped. |
| Cloud distance multiplier | `4.0` | Only with the fade off: how much further than the render distance the fog end and far plane go (1 to 16). |
| Use this mod's cloud texture over vanilla's | on | Take this mod's texture where vanilla's old one would be on top of the pack order. |
| Extend clouds with shader packs | on | Apply the cloud distance under an Oculus shader pack (capped to Oculus' view range). |
| Hide clouds behind Distant Horizons LODs | on | Clouds behind nearer LOD terrain, in front of farther LODs. |
| Keep the Clouds setting with Distant Horizons | on | Stop Distant Horizons switching the Clouds video setting off. Set Clouds back to Fancy or Fast once; it stays. |
| Distant Horizons clouds use the cloud texture | on | Distant Horizons' own clouds over the LODs take this mod's texture. |
| Keep clouds above the VS Genesis ceiling | on | Clouds stay in view above y 500 with VS Genesis. |
| Clouds on the VS Genesis planet | on | A cloud layer on the planet below you from high up. |
| Clouds fade into the VS Genesis planet | on | The world's clouds hand over to that layer as you rise. |
| Cloud texels across a VS Genesis planet face | `0` | The planet layer's resolution; 0 sizes it to match the world's clouds at the handover height. |
| Clouds on VS Genesis planets from afar | on | Earth-like planets wear the cloud texture from space. |
| Cloud shadows on VS Genesis planets | on | Those clouds cast shadows on the day side. |

## For mod developers

- `net.camacraft.backportedclouds.CloudFade.style()` says which fade the cloud pass is drawn with (1: this mod's, 0: the
  renderer's own). A mod that swaps the cloud shader for a copy of its own can fade the same way; the standard fog
  uniforms carry the distance (start 0, end the cloud fog distance). Camafy's storm clouds do this.
- `net.camacraft.backportedclouds.PlanetBelow.current()` gives this frame's VS Genesis planet below (its centre from the
  camera, half extent, and the handover, 0 to 1); the GLSL function `planet_below_fade` in `clouds.fsh` is the fade to copy.

See `CHANGELOG.md` for the history of each version.
