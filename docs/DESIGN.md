# Design notes

Siderea is used outdoors, at night, often one-handed and with cold fingers. These notes record what was
taken from the UI reference images and what was deliberately left out.

**Rule for references:** take the feel, never the pixels. No brand, logo, icon, photograph or exact colour
value from a reference appears in Siderea. Every colour, shape and glyph here is our own.

## What the references showed, and what we kept

### Reference 1: soft journal / calendar / sunset-data screens (light)

| Observed | Used as |
|---|---|
| Very rounded cards (≈ 20–28 dp radius), generous padding, hairline separation instead of heavy shadows | `SideriaShapes` 12/20/28 dp, `SideriaCard` with a 1 dp outline and no elevation |
| Small, widely tracked, upper-case mono labels above values (`TODAY'S SUNSET`, `TIME TO DESTINATION`) | `Siderea.text.caption`: 11 sp mono, +1.2 sp tracking, always upper-cased |
| One big mono numeral carrying a screen (`7:44`), small mono units beside it | `readoutLarge` / `readout` styles: numbers are mono so digits never jitter while a value changes |
| Pill buttons for state changes (`Pause`, `Stop`) under a status sheet that says what is happening in words (`Listening`, elapsed time) | `PillButton`, and the UX rule that every long operation has a plain-language state line |
| Calendar / stacked-photo gallery of days | Informs the Sessions screen (M2): grouped by night, thumbnails, frame counts |
| A vertical rolling selector with the chosen row emphasised | Candidate for the mode / interval pickers (M1–M2) |

Not taken: the light theme, the pastel gradients, the polaroid imagery.

### Reference 2: bracketing mode (dark)

| Observed | Used as |
|---|---|
| A top row of small pills: flash, RAW, the active mode in amber with a subtitle, histogram, overflow | `StatusPill` and the "one row of key values" rule for the viewfinder |
| Viewfinder inside a rounded container with a thumbnail filmstrip along its bottom edge and the selected thumbnail outlined in the accent colour | Exposure-bracket / frame-review strip (M1 histogram, M2 timelapse review) |
| `3 · 5 · 7` as a tiny segmented control, and a range / step stepper with chevrons | `SegmentedPill` (already in `:core:ui`) |
| A readout strip under the preview: `ISO · SS · EV`, mono value over caps label, inside a rounded bar | `ReadoutBar` / `ReadoutCell` (used today on the home screen) |
| Large white circular shutter, last-photo thumbnail on the left, outlined amber mode pill on the right | Shutter row layout for M1 |
| Warm amber on near-black | Our accent: a muted amber (`#F2A93B`), plus a pure-red night-vision palette |

### Reference 3: pro camera with ruler dials

| Observed | Used as |
|---|---|
| A single status pill at the top (resolution, ratio, format, white balance) with settings on the left | Same pattern; one line, never more |
| Horizontal tick rulers for shutter and EV overlaid at the bottom of the preview, current value large above a centre index | The signature manual control from M1: a ruler dial with haptic detents on every tick |
| Readouts below the shutter with an underlined caps caption | `ReadoutCell` |
| `M · S · I` mode letters next to the shutter | Maps onto Android 16 hybrid auto-exposure: manual / shutter-priority / ISO-priority |
| A ring-shaped control with an orange arc | Focus and white-balance dials (M1) |

## Decisions that come from the use case, not the references

* **Dark only, true black.** `#000000` background so OLED pixels are off and night adaptation is kept.
* **One accent per palette.** Amber by default. A second palette, red mode, uses only red hues
  (no green or blue channel dominance, blue ≤ 60 %) because red light least disturbs dark adaptation.
* **Contrast is tested, not eyeballed.** `PaletteContrastTest` asserts WCAG AA (4.5:1 text) for every
  text/background pair in both palettes, so red mode cannot silently become unreadable.
* **State is never colour alone.** Capability chips carry a word (`YES`, `LIMITED`, `NO`) and a shape
  (filled / outlined / flat).
* **Touch targets.** 56 dp for primary controls, 48 dp minimum for anything else.
* **Motion.** Cross-fades only (120–220 ms). Nothing slides or bounces in a dark field.
* **Fonts.** The platform sans and monospace families. No downloaded fonts, so the app works offline on
  first launch and nothing licensed is bundled.

## Tokens

All tokens live in `core/ui/.../theme/`:

| File | Contents |
|---|---|
| `Palette.kt` | `NightAmberPalette`, `NightRedPalette` |
| `Typography.kt` | Material typography plus `SideriaTextStyles` (readout, caption) |
| `Dimens.kt` | spacing (4 dp grid), touch targets, shapes, motion |
| `Haptics.kt` | `detent()`, `select()`, `toggle()`, `confirm()`, `reject()`, gated by the haptics setting |
| `Contrast.kt` | WCAG contrast maths used by the tests |

## Logo

A thin circle (the lens / aperture), one concentric arc inside it (a star trail) that ends in a single dot
(the star). Monochrome first: the ring and trail are one colour, the star is the only accent. Drawn on a
512-unit grid; `branding/generate_assets.py` is the single source for the SVGs and the Android adaptive-icon
layers (foreground, background, monochrome for themed icons), and `SideriaMark.kt` mirrors the same numbers.
The wordmark's letters are paths, so it renders identically everywhere without a font.
