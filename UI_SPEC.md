# UI Specification

- Minimalist deep-indigo background with a compact midnight-blue capsule header.
- Youthful, high-energy color system: electric aqua, fresh mint/lime, sunny tangerine, vivid coral-pink, and saturated periwinkle-purple.
- Layered wave-shaped category cards use bright, luminous gradients while keeping document photos crisp and readable.
- Selected family member uses a vivid violet capsule; member-edit and add-member dialogs use deep blue-violet capsules instead of black.
- Avatar placeholders rotate through the same bright palette.
- Card selection smoothly expands the selected category and compresses other cards.
- Use meaningful spring/easing motion, restrained offsets, and opacity transitions.
- Physical document-card treatment: soft grounded shadow, a narrow pearlescent edge/rim, restrained directional sheen, and subtle top-edge highlight.
- Keep imported document photos crisp and legible; visual sheen must remain low-opacity and must not obscure identity text.
- Preserve the existing 3D front/back flip, drag-to-reorder lift/tilt, holographic drop feedback, and photo import interactions.
- Keep controls compact and content legible; avoid generic rounded rectangles replacing the requested wave shapes.

## Strong skeuomorphic card treatment

- The selected visual direction is strong skeuomorphism with physical interaction, while retaining the deep-indigo canvas, vivid aqua/mint/tangerine/coral/periwinkle palette, and wave-shaped category language.
- Document cards should read as tangible layered objects: a defined pearlescent rim, visible lower bevel, dark contact edge, soft ambient/spot shadows, and a restrained directional surface sheen.
- Pressing compresses the card slightly and lowers its elevation; long-press reorder lifts and enlarges it, increases the shadow, and uses restrained tilt with a subtle angle-following reflection.
- Keep document imagery crisp. Never place bright holographic or glossy effects over identity text; reserve the stronger holographic treatment for the existing drop-feedback state.
- Preserve all existing import, flip, drag-and-drop, reorder, and thumbnail-to-preview interactions. Use native Compose drawing and animation primitives; do not add a visual-effects dependency for these treatments.
