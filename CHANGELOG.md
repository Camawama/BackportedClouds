# Changelog

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
