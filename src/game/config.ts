/**
 * DUMMY SURFERS BY FSK — Central Game Config
 * Every tunable parameter lives here. No magic numbers elsewhere.
 */

export const GameConfig = {
  // ── Player movement ────────────────────────────────────────────────
  BASE_SPEED: 8, // world units (meters) per second at run start
  MAX_SPEED: 22,
  ACCEL_FACTOR: 0.00028, // speed = base + (max-base) * (1 - e^(-dist*accel))
  LANE_WIDTH: 2.5,
  LANE_SWITCH_DURATION: 0.15, // seconds, ease-out
  JUMP_STRENGTH: 4.0, // world units of jump apex
  JUMP_DURATION: 0.6,
  SUPERJUMP_STRENGTH_MULT: 2.1,
  SUPERJUMP_DURATION_MULT: 1.3,
  SLIDE_DURATION: 0.5,
  SLIDE_HEIGHT_RATIO: 0.4,
  PLAYER_HEIGHT: 1.75,
  PLAYER_HALF_WIDTH: 0.42,
  FAST_FALL_GRAVITY: 34, // swipe-down slam in air

  // ── Camera / projection ────────────────────────────────────────────
  VANISHING_POINT_Y: 0.38, // fraction of screen height
  PLAYER_BASE_Y: 0.78, // fraction of screen height where ground meets player
  FOCAL_LENGTH: 9.0, // perspective strength: scale(z) = f / (f + z)
  VIEW_DISTANCE: 68,
  CAMERA_FOLLOW: 0.42, // how much camera x follows player lane (parallax)
  CAMERA_LERP: 0.1,
  NEAR_MISS_SHAKE: 5,
  CRASH_SHAKE: 16,

  // ── Spawning / world ───────────────────────────────────────────────
  SEGMENT_LENGTH: 25,
  FIRST_SAFE_METERS: 45, // no obstacles at run start
  SLEEPER_SPACING: 1.7,
  COIN_SPAWN_CHANCE: 0.72,
  POWERUP_INTERVAL: 26, // seconds between power-up spawns (± jitter)
  MIN_REACTION_GAP: 0.62, // seconds of travel between consecutive patterns
  MIN_PATTERN_GAP: 9, // world units
  MAX_COINS_PER_RUN_BUFFER: 140,

  // ── Difficulty phases (distance in meters, obstacle gap range) ────
  PHASES: [
    { until: 200, speedPct: 0.55, gapMin: 34, gapMax: 52, weights: 'easy' },
    { until: 800, speedPct: 0.66, gapMin: 24, gapMax: 40, weights: 'easy' },
    { until: 2000, speedPct: 0.78, gapMin: 15, gapMax: 26, weights: 'medium' },
    { until: 4000, speedPct: 0.88, gapMin: 11, gapMax: 19, weights: 'hard' },
    { until: Infinity, speedPct: 0.97, gapMin: 9, gapMax: 15, weights: 'hard' },
  ],

  // ── Scoring ────────────────────────────────────────────────────────
  COIN_VALUE: 10,
  POWERUP_SCORE: 50,
  NEAR_MISS_DISTANCE: 1.05, // lateral units
  NEAR_MISS_SCORE: 25,
  DISTANCE_SCORE_PER_METER: 1,
  MULTIPLIER_MILESTONES: [
    { at: 1000, mult: 2 },
    { at: 2500, mult: 4 },
  ],
  X2_POWERUP_DOUBLE: true,

  // ── Power-ups (base durations; upgrades add +3s per level) ────────
  POWERUPS: {
    magnet: { duration: 18, color: '#ef4444', label: 'MAGNET' },
    x2: { duration: 22, color: '#f59e0b', label: 'SCORE x2' },
    shield: { duration: 12, color: '#2dd4bf', label: 'SHIELD' },
    boost: { duration: 10, color: '#a3e635', label: 'BOOST' },
    superjump: { duration: 15, color: '#f97316', label: 'SUPER JUMP' },
  } as Record<string, { duration: number; color: string; label: string }>,
  BOOST_SPEED_MULT: 1.32,
  MAGNET_RANGE_Z: 24,
  SHIELD_INVULN: 1.5,

  // ── Trains ─────────────────────────────────────────────────────────
  TRAIN_CAR_LENGTH: 6.4,
  TRAIN_WIDTH: 2.05,
  TRAIN_HEIGHT: 2.35,
  MOVING_TRAIN_REL_SPEED: 4.2, // same-direction slower trains
  APPROACH_TRAIN_SPEED: 9, // oncoming trains
  TRAIN_HORN_DISTANCE: 46,

  // ── Chaser ─────────────────────────────────────────────────────────
  CHASER_START_TIME: 4.5,
  CHASER_NEARMISS_TIME: 2.8,
  CHASER_Z: -3.4,

  // ── Virtual resolution guards ──────────────────────────────────────
  MIN_LOGICAL_WIDTH: 560,
  MAX_LOGICAL_WIDTH: 980,
} as const;

export type PowerUpType = 'magnet' | 'x2' | 'shield' | 'boost' | 'superjump';

export const POWERUP_TYPES: PowerUpType[] = ['magnet', 'x2', 'shield', 'boost', 'superjump'];

/** Upgrade costs per level (3 levels each), by power-up key. */
export const UPGRADE_COSTS: Record<string, number[]> = {
  magnet: [300, 700, 1500],
  x2: [300, 700, 1500],
  shield: [250, 600, 1300],
  boost: [250, 600, 1300],
  superjump: [350, 800, 1600],
};

export const TRAIL_COSTS: Record<string, number> = {
  none: 0,
  gold: 200,
  fire: 600,
  rainbow: 1200,
};

export const SAVE_KEY = 'dummySurfers.save.v1';
export const GAME_VERSION = '1.0.0';
