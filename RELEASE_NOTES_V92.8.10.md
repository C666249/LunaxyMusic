# Lunaxy Music V92.8.10

Baseline: Lunaxy Music V92.8.9.

## Home visual update

- Keeps the V92.8.9 recommendation card as **Original**, and adds four optional recommendation-card materials: **柔光极光 / 棱镜边框 / 月雾磨砂 / 深空晶体**.
- The style entry is an icon-only control aligned to the right of “为你推荐”; tapping it opens a lightweight bottom drawer.
- Style choice is persisted locally and applies to all three recommendation cards while preserving their individual accent colours.
- No real-time Gaussian/RenderEffect blur is used; the new materials rely on restrained shaders, strokes and geometry.

## Album-reactive home details

- Recent-played covers now extract their colour from the loaded artwork and render a very soft local ambient halo.
- Horizontal scrolling adds only subtle depth: the cover nearest the viewport centre stays at 1.00 scale, while side covers ease toward ~0.965 and slightly lower opacity. No 3D tilt is used.
- The home search field now uses the same album-reactive flowing halo language as the current-song row in the playback queue, keyed from the current cover accent.

## Preserved behaviour

- V92.8.9 song-title tap-to-search remains unchanged.
- Artist tap-to-search, playback, recommendation generation, source routing, cache, download and player motion code are otherwise unchanged.
