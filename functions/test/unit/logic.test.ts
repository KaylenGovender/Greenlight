import * as assert from "node:assert/strict";
import { firstName, generatePairingCode, matchLink, matchMessage, normalizePairingCode, otherMember, swipeDocId } from "../../src/logic";

describe("pairing codes", () => {
  it("generates ABCD-1234 shaped codes without I or O", () => {
    for (let i = 0; i < 500; i++) {
      const code = generatePairingCode();
      assert.match(code, /^[A-HJ-NP-Z]{4}-[0-9]{4}$/);
    }
  });

  it("normalizes user input", () => {
    assert.equal(normalizePairingCode("abcd7392"), "ABCD-7392");
    assert.equal(normalizePairingCode(" abcd-7392 "), "ABCD-7392");
    assert.equal(normalizePairingCode("ABCD 7392"), "ABCD-7392");
  });

  it("rejects malformed codes", () => {
    assert.equal(normalizePairingCode("ABC-7392"), null);
    assert.equal(normalizePairingCode("1234ABCD"), null);
    assert.equal(normalizePairingCode(42), null);
    assert.equal(normalizePairingCode(undefined), null);
  });
});

describe("couple helpers", () => {
  it("finds the other member", () => {
    assert.equal(otherMember(["a", "b"], "a"), "b");
    assert.equal(otherMember(["a", "b"], "b"), "a");
    assert.equal(otherMember(["a", "b"], "c"), null);
    assert.equal(otherMember(["a"], "a"), null);
  });

  it("builds deterministic swipe ids", () => {
    assert.equal(swipeDocId("movie_603", "uidA"), "movie_603_uidA");
  });

  it("uses first names", () => {
    assert.equal(firstName("Sarah Connor"), "Sarah");
    assert.equal(firstName(""), "Your partner");
    assert.equal(firstName(undefined), "Your partner");
  });
});

describe("match notifications", () => {
  it("words watch and restaurant matches per the spec", () => {
    assert.deepEqual(matchMessage("watch", "Sarah", "The Fall Guy"), {
      title: "✨ It's a match!",
      body: "You and Sarah both want to watch The Fall Guy.",
    });
    assert.deepEqual(matchMessage("restaurant", "Sarah", "Pizza Palace"), {
      title: "🍽️ Restaurant match!",
      body: "You and Sarah both want to try Pizza Palace.",
    });
  });

  it("deep-links to the match", () => {
    assert.equal(matchLink("watch", "tv_1399"), "greenlight://match/watch/tv_1399");
  });
});

import { cleanGroupName, generateGroupCode, groupMatchMessage, isMajority, normalizeGroupCode } from "../../src/logic";

describe("friend groups", () => {
  it("generates permanent K7M-2QX codes without ambiguous characters", () => {
    for (let i = 0; i < 500; i++) assert.match(generateGroupCode(), /^[A-HJKMNP-Z2-9]{3}-[A-HJKMNP-Z2-9]{3}$/);
  });

  it("normalizes group codes and rejects the wrong shape", () => {
    assert.equal(normalizeGroupCode("k7m2qx"), "K7M-2QX");
    assert.equal(normalizeGroupCode(" K7M 2QX "), "K7M-2QX");
    assert.equal(normalizeGroupCode("K7M-2Q"), null);
    assert.equal(normalizeGroupCode("ABCD-1234"), null);
    assert.equal(normalizeGroupCode("O0I-1LX"), null); // ambiguous characters never appear in codes
  });

  it("uses a strict majority, so couples need both partners", () => {
    assert.equal(isMajority(1, 2), false);
    assert.equal(isMajority(2, 2), true);
    assert.equal(isMajority(2, 3), true);
    assert.equal(isMajority(2, 4), false);
    assert.equal(isMajority(3, 4), true);
    assert.equal(isMajority(1, 1), false); // a group of one can't match with itself
  });

  it("cleans group names", () => {
    assert.equal(cleanGroupName("  Braai   Crew "), "Braai Crew");
    assert.equal(cleanGroupName(""), null);
    assert.equal(cleanGroupName("x".repeat(41)), null);
  });

  it("words group matches neutrally with counts", () => {
    assert.deepEqual(groupMatchMessage("activity", "Braai Crew", "Escape Room Durban", 3, 5), {
      title: "🎉 Group match in Braai Crew!",
      body: "3 of 5 want to do Escape Room Durban.",
    });
  });
});

describe("links", () => {
  it("tags match links with the group so the app can switch to it", () => {
    assert.equal(matchLink("activity", "ChIJx", "g1"), "greenlight://match/activity/ChIJx?g=g1");
  });
});

describe("remote app settings", () => {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { cleanSettings, DEFAULT_SETTINGS, readSettings, SettingsError } = require("../../src/logic");

  it("fills gaps from the current settings", () => {
    const s = cleanSettings({ defaults: { radiusKm: 10 } });
    assert.equal(s.defaults.radiusKm, 10);
    assert.equal(s.defaults.planDurationMin, DEFAULT_SETTINGS.defaults.planDurationMin);
    assert.deepEqual(s.features, DEFAULT_SETTINGS.features);
  });

  it("rejects out-of-range numbers instead of silently clamping", () => {
    assert.throws(() => cleanSettings({ defaults: { planStartHour: 25 } }), SettingsError);
    assert.throws(() => cleanSettings({ cache: { imageDays: 0 } }), SettingsError);
  });

  it("keeps the default distance on the slider and at least one tab on", () => {
    assert.throws(() => cleanSettings({ defaults: { radiusKm: 7 } }), /must be one of the distance steps/);
    assert.equal(cleanSettings({ radiusStepsKm: [7, 2, 7, 5], defaults: { radiusKm: 7 } }).radiusStepsKm.join(","), "2,5,7");
    assert.throws(() => cleanSettings({ features: { watch: false, eat: false, do: false } }), /At least one/);
  });

  it("only keeps known streaming services and requires announcement text", () => {
    assert.deepEqual(cleanSettings({ services: { disabled: ["disney", "hbo", "disney"] } }).services.disabled, ["disney"]);
    assert.throws(() => cleanSettings({ announcement: { enabled: true, text: " " } }), /announcement/);
  });

  it("reads broken stored settings back as the defaults", () => {
    assert.deepEqual(readSettings({ defaults: { radiusKm: 999 } }), DEFAULT_SETTINGS);
    assert.deepEqual(readSettings(undefined), DEFAULT_SETTINGS);
  });
});
