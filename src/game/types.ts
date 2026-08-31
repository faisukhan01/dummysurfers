import type { PowerUpType } from './config';

export type Lane = -1 | 0 | 1;

export type GamePhase = 'boot' | 'menu' | 'tutorial' | 'playing' | 'dying' | 'gameover';

export type TutorialStep = 0 | 1 | 2 | 3 | null; // left, right, jump, slide

export interface HudPowerUp {
  type: PowerUpType;
  remaining: number;
  total: number;
}

export interface HudSnapshot {
  score: number;
  runCoins: number;
  distance: number;
  multiplier: number;
  speedPct: number;
  powerups: HudPowerUp[];
  shieldActive: boolean;
  tutorialStep: TutorialStep;
}

export interface RunStats {
  score: number;
  coins: number;
  distance: number;
  jumps: number;
  slides: number;
  powerups: number;
  nearMisses: number;
}

export interface RunResult extends RunStats {
  newBest: boolean;
}

export type DecoKind =
  | 'building'
  | 'skyscraper'
  | 'pole'
  | 'billboard'
  | 'lamp'
  | 'tree'
  | 'platform'
  | 'shelter'
  | 'stationSign'
  | 'bench'
  | 'signal'
  | 'graffitiWall'
  | 'bridgeGirder'
  | 'tunnelArch'
  | 'tunnelWall'
  | 'fence'
  | 'bush';

export interface Deco {
  kind: DecoKind;
  side: -1 | 1; // -1 left, 1 right
  x: number; // world x offset (units from center)
  z: number;
  h: number; // height in units
  w: number;
  variant: number; // seeded 0..1 for color/shape variety
  lit: boolean; // has lit windows
}

export type ObstacleKind = 'lowBarrier' | 'highBarrier' | 'blockade' | 'gate' | 'fenceFull';

export interface Obstacle {
  kind: ObstacleKind;
  lanes: Lane[]; // affected lanes (gate/fence = all)
  z: number;
  cleared: boolean; // for near-miss tracking
  variant: number;
}

export type TrainKind = 'parked' | 'sameDir' | 'approach';

export interface Train {
  id: number;
  lanes: Lane[];
  z: number; // z of the FRONT (closest end)
  cars: number;
  kind: TrainKind;
  speed: number; // extra world-scroll delta applied to z
  livery: number; // 0..5 color scheme index
  hornDone: boolean;
  seed: number;
}

export interface Coin {
  lane: Lane;
  x: number; // continuous x (magnet flight)
  y: number; // height above ground
  z: number;
  baseY: number;
  phase: number;
  collected: boolean;
  magnet: boolean;
}

export interface PowerUpEntity {
  type: PowerUpType;
  lane: Lane;
  z: number;
  phase: number;
  taken: boolean;
}

export type ParticleShape = 'circle' | 'spark' | 'confetti' | 'streak' | 'ring' | 'text';

export interface Particle {
  active: boolean;
  x: number; // screen-space
  y: number;
  vx: number;
  vy: number;
  g: number; // gravity px/s²
  life: number;
  maxLife: number;
  size: number;
  color: string;
  shape: ParticleShape;
  rot: number;
  vrot: number;
  text?: string;
}

export interface SegmentType {
  name: 'open' | 'urban' | 'station' | 'bridge' | 'tunnel' | 'industrial';
}
