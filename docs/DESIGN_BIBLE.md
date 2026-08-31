# Dummy Surfers — Design Bible (Subway Surfers visual clone target)

Derived from direct study of official Subway Surfers screenshots (gameplay HUD,
home screen, profile panels, new-high-score celebration). Every subsystem must
match this language. 100% original art, 100% SS look.

## 1. World palette (bright warm daylight — NO dark sunset)

| Token          | Hex       | Use |
|----------------|-----------|-----|
| SKY_TOP        | `#3FB8F5` | Vivid cyan-blue zenith |
| SKY_HIGH       | `#7FD4F7` | Upper sky |
| SKY_LOW        | `#FFE9C2` | Warm cream horizon glow |
| SUN_CORE       | `#FFFDF2` | Sun disc |
| SUN_HALO       | `#FFEDB8` | Sun glow |
| FOG            | `#FFE4BC` | Light warm haze at horizon (LIGHT, not dark) |
| GROUND_BALLAST | `#C97B5E` | Warm terracotta gravel |
| GROUND_FAR     | `#D98D6A` | Lighter distance ballast |
| PATH_CREAM     | `#F2D9A7` | Cream path patches between rails |
| PATH_ORANGE    | `#E8A25C` | Orange path patches |
| GRASS          | `#5FBF4A` | Vivid green strips beside tracks |
| SLEEPER        | `#6B4A36` | Warm brown ties |
| RAIL_TOP       | `#E8E4DA` | Shiny silver rail head |
| RAIL_SIDE      | `#B4553A` | Rust-orange rail side (SS look) |
| HAZARD_YELLOW  | `#FFC93C` | Yellow/black chevron barriers |
| HAZARD_BLACK   | `#2B2622` | Chevron black |
| CONTOUR_TEAL   | `#37B8A8` | Signal posts, teal props |
| CONTAINER_RED  | `#C4553E` | Red-brown containers/bricks |

## 2. Train liveries (bright SS metro + graffiti freight)

1. `#3E7BC0` body / `#2C5E96` shade / `#FFFFFF` band — classic blue metro w/ white stripe
2. `#F2A63B` / `#D8841F` / `#FFF3D6` — orange graffiti freight
3. `#43B45C` / `#2E8A44` / `#EAF7DC` — green metro
4. `#D94A38` / `#A83326` / `#FFE2C8` — red express
5. `#F7D23E` / `#DBAE1D` / `#3A3F6B` — yellow metro w/ navy band
6. `#8A55C9` / `#6A3DA3` / `#F2E2FF` — violet graffiti
- All trains: grey roof `#9AA0A8`, dark windows with light-blue reflection,
  yellow front face + two round headlights, colorful graffiti blobs on freight.

## 3. UI DNA

- **Font display**: Luckiest Guy (chunky comic, ALL CAPS, white w/ dark-navy
  `#24316B` outline + soft drop shadow). **Font body**: Fugaz One.
- **Score (HUD top-center)**: huge white, thick navy outline; `x2` gold star
  chip to its left when multiplier active.
- **Coin pill (top-left)**: yellow rounded pill `#FFC93C` w/ darker gold edge,
  gold coin icon w/ star emboss, brown number `#7A4A12`.
- **Pause (top-right)**: orange-red `#FF5A3C` rounded-square, white rounded bars,
  white 3px border + drop edge.
- **Power meter (bottom-center)**: white rounded frame, segmented fill (SS
  hoverboard meter style).
- **Panels**: periwinkle `#7B84D6` rounded 18px, inner slot `#4A529E`, top
  highlight rim, white ALL-CAPS labels; values in gold `#FFD23E`.
- **Currency pills**: dark navy `#2A3057` rounded-full, white numbers.
- **Primary button**: huge gold `#FFC93C` rounded, darker gold bottom edge,
  white icon + ALL-CAPS label w/ navy outline. Pressed = 96% scale + darker.
- **Secondary button**: green `#3DBB5A` glossy w/ white border.
- **Bottom tab bar** (menu): navy `#2A3057` tabs, icons + labels, active tab =
  yellow `#FFC93C` background, red `!` badges.
- **Game over**: radial rainbow burst bg (red→orange→yellow→green) + white
  speed streaks when NEW BEST; "NEW HIGH SCORE!" + giant number; confetti.
- **Menu hero**: graffiti-tag logo "DUMMY SURFERS" (tilted, spray colors),
  character center on tracks, HIGH SCORE card w/ trophy.

## 4. Characters (cartoon SS proportions)

Big head, small body, cap/beanie + hoodie/tee, sneakers. Per character:
- dash: red cap backwards + blue hoodie + jeans (Jake energy)
- nova: purple beanie + gold glasses + white tee
- tank: green beanie + orange hoodie
- kira: pink cap + violet jacket

## 5. Effects

- Coin sparkle: gold star particles. Landing: white dust puffs (SS style).
- Speed lines: white streaks. Boost: warm orange vignette (not black).
- New-best burst: radial rainbow wedges (red/orange/yellow/green) radiating
  from center + confetti — exact SS celebration.
- Menu: graffiti-tag logo "DUMMY SURFERS", character on tracks, HIGH SCORE card.
