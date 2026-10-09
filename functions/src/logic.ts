/** Pure helpers (no Firebase imports) so they can be unit-tested without emulators. */

// Letters without I/O (easily confused with 1/0).
const LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
const DIGITS = "0123456789";
// Group codes mix letters and digits but drop the ambiguous ones (I, L, O, 0, 1).
const GROUP_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

export type GroupType = "couple" | "friends";

/** Couple pairing code: ABCD-1234, expires after 15 minutes. */
export function generatePairingCode(random: () => number = Math.random): string {
  const pick = (set: string) => set[Math.floor(random() * set.length)];
  let code = "";
  for (let i = 0; i < 4; i++) code += pick(LETTERS);
  code += "-";
  for (let i = 0; i < 4; i++) code += pick(DIGITS);
  return code;
}

/** "abcd7392", "ABCD 7392" and "abcd-7392" all become "ABCD-7392"; returns null if the shape is wrong. */
export function normalizePairingCode(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const clean = raw.toUpperCase().replace(/[^A-Z0-9]/g, "");
  if (!/^[A-Z]{4}[0-9]{4}$/.test(clean)) return null;
  return `${clean.slice(0, 4)}-${clean.slice(4)}`;
}

/** Friend-group code: K7M-2QX. Permanent until the owner resets it. */
export function generateGroupCode(random: () => number = Math.random): string {
  let code = "";
  for (let i = 0; i < 6; i++) code += GROUP_ALPHABET[Math.floor(random() * GROUP_ALPHABET.length)];
  return `${code.slice(0, 3)}-${code.slice(3)}`;
}

export function normalizeGroupCode(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const clean = raw.toUpperCase().replace(/[^A-Z0-9]/g, "");
  if (clean.length !== 6 || [...clean].some((c) => !GROUP_ALPHABET.includes(c))) return null;
  return `${clean.slice(0, 3)}-${clean.slice(3)}`;
}

export const MAX_GROUP_SIZE = 20;

export function cleanGroupName(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const name = raw.trim().replace(/\s+/g, " ");
  return name.length >= 1 && name.length <= 40 ? name : null;
}

/** A match needs more than half of the current members. For a couple that means both. */
export function isMajority(interested: number, members: number): boolean {
  return members >= 2 && interested * 2 > members;
}

export type SwipeKind = "watch" | "restaurant" | "activity";

export const SWIPE_COLLECTIONS: Record<SwipeKind, { swipes: string; matches: string }> = {
  watch: { swipes: "watchSwipes", matches: "watchMatches" },
  restaurant: { swipes: "restaurantSwipes", matches: "restaurantMatches" },
  activity: { swipes: "activitySwipes", matches: "activityMatches" },
};

/** Swipe doc ids are `${itemKey}_${uid}`. */
export function swipeDocId(itemKey: string, uid: string): string {
  return `${itemKey}_${uid}`;
}

export function otherMember(memberIds: string[], uid: string): string | null {
  if (memberIds.length !== 2 || !memberIds.includes(uid)) return null;
  return memberIds[0] === uid ? memberIds[1] : memberIds[0];
}

export function firstName(displayName: string | undefined | null): string {
  const n = (displayName ?? "").trim().split(/\s+/)[0];
  return n || "Your partner";
}

const VERB: Record<SwipeKind, string> = { watch: "watch", restaurant: "try", activity: "do" };

/** Couple wording (to one partner). */
export function matchMessage(kind: SwipeKind, partnerFirstName: string, title: string) {
  if (kind === "restaurant") {
    return { title: "🍽️ Restaurant match!", body: `You and ${partnerFirstName} both want to try ${title}.` };
  }
  if (kind === "activity") {
    return { title: "🎯 Activity match!", body: `You and ${partnerFirstName} both want to do ${title}.` };
  }
  return { title: "✨ It's a match!", body: `You and ${partnerFirstName} both want to watch ${title}.` };
}

/** Friend-group wording: neutral, with counts. */
export function groupMatchMessage(kind: SwipeKind, groupName: string, title: string, interested: number, members: number) {
  return {
    title: `🎉 Group match in ${groupName}!`,
    body: `${interested} of ${members} want to ${VERB[kind]} ${title}.`,
  };
}

export function planNoun(type: GroupType): string {
  return type === "couple" ? "date night" : "meet up";
}

export function matchLink(kind: SwipeKind, itemKey: string, groupId?: string): string {
  return `greenlight://match/${kind}/${encodeURIComponent(itemKey)}` + (groupId ? `?g=${groupId}` : "");
}

// ---------------------------------------------------------------- remote app settings

export const SERVICE_KEYS = ["netflix", "prime", "disney"] as const;

/** Everything the admin console can tune in the app. Stored in /config/app; every field is optional in the app. */
export interface AppSettings {
  announcement: { enabled: boolean; text: string; level: "info" | "success" | "warning" };
  maintenance: { enabled: boolean; message: string };
  features: { watch: boolean; eat: boolean; do: boolean; groups: boolean; shareCards: boolean };
  defaults: { radiusKm: number; planDurationMin: number; planStartHour: number; deckRoundSize: number };
  radiusStepsKm: number[];
  services: { disabled: string[] };
  categories: { hiddenFood: string[]; hiddenActivity: string[] };
  cache: { imageDays: number; tmdbHours: number; placesMemoMin: number };
}

export const DEFAULT_SETTINGS: AppSettings = {
  announcement: { enabled: false, text: "", level: "info" },
  maintenance: { enabled: false, message: "" },
  features: { watch: true, eat: true, do: true, groups: true, shareCards: true },
  defaults: { radiusKm: 5, planDurationMin: 180, planStartHour: 19, deckRoundSize: 20 },
  radiusStepsKm: [2, 5, 10, 15, 20, 30, 40, 50],
  services: { disabled: [] },
  categories: { hiddenFood: [], hiddenActivity: [] },
  cache: { imageDays: 7, tmdbHours: 6, placesMemoMin: 15 },
};

/** Thrown for settings that can't be saved; the message says which field and why. */
export class SettingsError extends Error {}

const obj = (v: unknown): Record<string, unknown> => (v && typeof v === "object" && !Array.isArray(v) ? v as Record<string, unknown> : {});
const bool = (v: unknown, fallback: boolean) => (typeof v === "boolean" ? v : fallback);
function int(v: unknown, fallback: number, min: number, max: number, name: string): number {
  if (v === undefined || v === null || v === "") return fallback;
  const n = Number(v);
  if (!Number.isInteger(n) || n < min || n > max) throw new SettingsError(`${name} must be a whole number from ${min} to ${max}.`);
  return n;
}
function text(v: unknown, max: number): string {
  return typeof v === "string" ? v.trim().slice(0, max) : "";
}
function keys(v: unknown, max = 40): string[] {
  return Array.isArray(v) ? [...new Set(v.filter((x): x is string => typeof x === "string").map((x) => x.trim()).filter((x) => x && x.length <= max))] : [];
}

/**
 * Validates and normalises settings from the admin console: fills gaps with the current value
 * (or the default), clamps nothing silently (out-of-range numbers are an error), de-duplicates
 * lists, and checks cross-field rules (the default radius must be one of the slider steps).
 */
export function cleanSettings(input: unknown, current: AppSettings = DEFAULT_SETTINGS): AppSettings {
  const i = obj(input);
  const a = obj(i.announcement), m = obj(i.maintenance), f = obj(i.features), d = obj(i.defaults), s = obj(i.services), c = obj(i.categories), k = obj(i.cache);
  const level = ["info", "success", "warning"].includes(a.level as string) ? a.level as AppSettings["announcement"]["level"] : current.announcement.level;
  const steps = i.radiusStepsKm === undefined ? current.radiusStepsKm
    : [...new Set((Array.isArray(i.radiusStepsKm) ? i.radiusStepsKm : []).map(Number))].filter((n) => Number.isInteger(n)).sort((x, y) => x - y);
  if (steps.length < 2 || steps.length > 10 || steps[0] < 1 || steps[steps.length - 1] > 50) {
    throw new SettingsError("Distance steps need 2 to 10 whole numbers between 1 and 50 km.");
  }
  const out: AppSettings = {
    announcement: {
      enabled: bool(a.enabled, current.announcement.enabled),
      text: a.text === undefined ? current.announcement.text : text(a.text, 280),
      level,
    },
    maintenance: {
      enabled: bool(m.enabled, current.maintenance.enabled),
      message: m.message === undefined ? current.maintenance.message : text(m.message, 300),
    },
    features: {
      watch: bool(f.watch, current.features.watch),
      eat: bool(f.eat, current.features.eat),
      do: bool(f.do, current.features.do),
      groups: bool(f.groups, current.features.groups),
      shareCards: bool(f.shareCards, current.features.shareCards),
    },
    defaults: {
      radiusKm: int(d.radiusKm, current.defaults.radiusKm, 1, 50, "Default distance"),
      planDurationMin: int(d.planDurationMin, current.defaults.planDurationMin, 30, 720, "Default plan length"),
      planStartHour: int(d.planStartHour, current.defaults.planStartHour, 0, 23, "Default start hour"),
      deckRoundSize: int(d.deckRoundSize, current.defaults.deckRoundSize, 5, 100, "Round size"),
    },
    radiusStepsKm: steps,
    services: { disabled: s.disabled === undefined ? current.services.disabled : keys(s.disabled).filter((x) => (SERVICE_KEYS as readonly string[]).includes(x)) },
    categories: {
      hiddenFood: c.hiddenFood === undefined ? current.categories.hiddenFood : keys(c.hiddenFood),
      hiddenActivity: c.hiddenActivity === undefined ? current.categories.hiddenActivity : keys(c.hiddenActivity),
    },
    cache: {
      imageDays: int(k.imageDays, current.cache.imageDays, 1, 30, "Image cache days"),
      tmdbHours: int(k.tmdbHours, current.cache.tmdbHours, 1, 168, "Movie data cache hours"),
      placesMemoMin: int(k.placesMemoMin, current.cache.placesMemoMin, 0, 120, "Place search cache minutes"),
    },
  };
  if (out.features.watch === false && out.features.eat === false && out.features.do === false) {
    throw new SettingsError("At least one of Watch, Eat or Do must stay on.");
  }
  if (!out.radiusStepsKm.includes(out.defaults.radiusKm)) {
    throw new SettingsError(`The default distance (${out.defaults.radiusKm} km) must be one of the distance steps.`);
  }
  if (out.announcement.enabled && !out.announcement.text) throw new SettingsError("Write the announcement text, or turn it off.");
  return out;
}

/** Reads stored settings back, tolerating missing or old fields. */
export function readSettings(stored: unknown): AppSettings {
  try {
    return cleanSettings(stored ?? {}, DEFAULT_SETTINGS);
  } catch {
    return DEFAULT_SETTINGS;
  }
}
