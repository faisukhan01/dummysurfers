import { SAVE_KEY, GAME_VERSION } from './config';
import type { PowerUpType } from './config';
import type { MissionSave } from './missions';

export interface GameSettings {
  music: boolean;
  sfx: boolean;
  vibration: boolean;
}

export interface TotalStats {
  runs: number;
  bestDistance: number;
  totalDistance: number;
  totalCoins: number;
  totalJumps: number;
  totalSlides: number;
  totalPowerups: number;
  totalNearMisses: number;
  playSeconds: number;
}

export interface SaveData {
  v: number;
  best: number;
  totalCoins: number;
  selectedCharacter: string;
  ownedCharacters: string[];
  upgrades: Record<string, number>; // powerup key -> level 0..3
  trail: string; // 'none' | 'gold' | 'fire' | 'rainbow'
  settings: GameSettings;
  tutorialDone: boolean;
  stats: TotalStats;
  missions: MissionSave[];
  updatedAt: number;
}

export const DEFAULT_SAVE: SaveData = {
  v: 1,
  best: 0,
  totalCoins: 0,
  selectedCharacter: 'dash',
  ownedCharacters: ['dash'],
  upgrades: { magnet: 0, x2: 0, shield: 0, boost: 0, superjump: 0 },
  trail: 'none',
  settings: { music: true, sfx: true, vibration: true },
  tutorialDone: false,
  stats: {
    runs: 0,
    bestDistance: 0,
    totalDistance: 0,
    totalCoins: 0,
    totalJumps: 0,
    totalSlides: 0,
    totalPowerups: 0,
    totalNearMisses: 0,
    playSeconds: 0,
  },
  missions: [],
  updatedAt: 0,
};

/**
 * Local persistence — works 100% offline.
 * Swap this class for a Firebase-backed adapter later: the rest of the
 * game only talks to this interface.
 */
export class SaveManager {
  private data: SaveData;
  private listeners: ((d: SaveData) => void)[] = [];

  constructor() {
    this.data = this.load();
  }

  private load(): SaveData {
    if (typeof window === 'undefined') return structuredClone(DEFAULT_SAVE);
    try {
      const raw = localStorage.getItem(SAVE_KEY);
      if (!raw) return structuredClone(DEFAULT_SAVE);
      const parsed = JSON.parse(raw) as Partial<SaveData>;
      // Deep-merge over defaults so new fields survive version upgrades
      const merged: SaveData = {
        ...structuredClone(DEFAULT_SAVE),
        ...parsed,
        settings: { ...DEFAULT_SAVE.settings, ...(parsed.settings ?? {}) },
        stats: { ...DEFAULT_SAVE.stats, ...(parsed.stats ?? {}) },
        upgrades: { ...DEFAULT_SAVE.upgrades, ...(parsed.upgrades ?? {}) },
        missions: Array.isArray(parsed.missions) ? parsed.missions : [],
        ownedCharacters: Array.isArray(parsed.ownedCharacters) && parsed.ownedCharacters.length
          ? parsed.ownedCharacters
          : ['dash'],
      };
      merged.v = 1;
      return merged;
    } catch {
      return structuredClone(DEFAULT_SAVE);
    }
  }

  get(): SaveData {
    return this.data;
  }

  update(mut: (d: SaveData) => void): SaveData {
    mut(this.data);
    this.data.updatedAt = Date.now();
    this.persist();
    for (const l of this.listeners) l(this.data);
    return this.data;
  }

  addCoins(n: number) {
    this.update((d) => {
      d.totalCoins += n;
      d.stats.totalCoins += n;
    });
  }

  addBest(score: number) {
    this.update((d) => {
      d.best = Math.max(d.best, score);
    });
  }

  setUpgrades(type: PowerUpType, level: number) {
    this.update((d) => {
      d.upgrades[type] = level;
    });
  }

  setSettings(s: Partial<GameSettings>) {
    this.update((d) => {
      d.settings = { ...d.settings, ...s };
    });
  }

  reset(): SaveData {
    this.data = structuredClone(DEFAULT_SAVE);
    this.persist();
    for (const l of this.listeners) l(this.data);
    return this.data;
  }

  /** Ensure 3 starter missions exist for a brand-new save. */
  ensureMissions(factory: () => MissionSave[]) {
    if (this.data.missions.length === 0) {
      this.update((d) => {
        d.missions = factory();
      });
    }
  }

  onChange(cb: (d: SaveData) => void) {
    this.listeners.push(cb);
    return () => {
      this.listeners = this.listeners.filter((l) => l !== cb);
    };
  }

  private persist() {
    if (typeof window === 'undefined') return;
    try {
      localStorage.setItem(SAVE_KEY, JSON.stringify(this.data));
    } catch {
      // storage full / private mode — game continues session-only
    }
  }
}

export const GAME_VERSION_TAG = GAME_VERSION;
