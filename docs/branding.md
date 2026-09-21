# LochSSH branding

## Palette

| Name | Hex | Use |
|---|---|---|
| Dark Loch Navy | `#0D1B2A` | Background, dark surfaces |
| Emerald Pine | `#1B4332` | Water, primary brand fill |
| Highland Amber | `#E9C46A` | Prompt marks, accents, active keys |
| Mist Gray | `#E0E1DD` | Nessy, foreground text |

Defined as resources in `res/values/colors.xml`.

## Assets

| File | Use |
|---|---|
| `drawable/ic_launcher_nessi.xml` | Standalone 48dp badge. In-app logo, about screen, docs |
| `drawable/ic_launcher_foreground.xml` | Adaptive icon foreground, art inside the 66dp safe circle |
| `drawable/ic_launcher_background.xml` | Adaptive icon background, navy with a soft radial lift |
| `drawable/ic_launcher_monochrome.xml` | Themed icon layer for Android 13 and later |
| `mipmap-anydpi-v26/ic_launcher.xml` | Adaptive icon, also used for `ic_launcher_round` |
| `drawable/ic_splash_nessi.xml` | 288dp splash icon, art inside the inner 192dp circle |
| `drawable/ic_notification.xml` | Status bar icon. Flat silhouette, the system uses the alpha only |

The splash is wired through `values-v31/themes.xml`. Devices below API 31 get no
splash screen; adding `androidx.core:core-splashscreen` would backport it.

## Image prompts

For app store banners, promotional art and high resolution icon concepts. Written
for Midjourney and DALL-E 3; drop the trailing flags for DALL-E.

### 1. Store banner (feature graphic)

> Wide cinematic illustration of a calm Scottish highland loch at blue hour. A long
> necked lake monster silhouette rises from still water on the right third, three
> humps trailing behind it, rendered as a clean flat vector shape in pale mist gray
> `#E0E1DD`. The water is deep emerald pine `#1B4332` with minimal horizontal ripple
> lines. Sky and distant hills in dark loch navy `#0D1B2A`, no clouds, no texture
> noise. Floating over the water on the left, a soft amber `#E9C46A` glow spells a
> terminal prompt `>_` with a blinking cursor block, its reflection breaking across
> the ripples. Flat vector poster art, limited four colour palette, sharp edges, no
> gradients except one subtle radial glow behind the monster, generous negative space
> in the upper left for a title. --ar 16:9 --style raw --v 6

### 2. Promotional art (hero, terminal aesthetic)

> A lake monster made of green terminal text rising out of black water, seen from the
> shore at night. Its neck and head are formed from columns of monospaced characters
> and scrolling shell output in amber `#E9C46A` and mist gray `#E0E1DD`, fading to
> emerald pine `#1B4332` where it enters the loch. Reflected light scatters on the
> water as short dashes of amber. Background is flat dark loch navy `#0D1B2A` with a
> faint scanline texture. Mood is quiet and technical rather than scary. Retro
> computing poster, flat illustration with crisp geometry, high contrast, no lens
> flare, no photographic realism. --ar 3:2 --style raw --v 6

### 3. Icon concept sheet

> App icon concept, a stylised lake monster silhouette emerging from two minimal wave
> bands, centred in a rounded square. Mist gray `#E0E1DD` creature, emerald pine
> `#1B4332` water, dark loch navy `#0D1B2A` background, a small highland amber
> `#E9C46A` terminal prompt `>_` sitting on the water below the neck. Extremely simple
> geometric shapes, thick even strokes, flat colour, no outlines, no text, no shadows,
> legible at 48 pixels. Present as a grid of four variations that differ only in the
> neck curve and the number of humps. --ar 1:1 --style raw --v 6
