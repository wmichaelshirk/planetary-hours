# TODO

## High latitudes (low priority — St. Louis is unaffected)
`PlanetaryHourCalculator.calculateHours` asks suncalc for "the next rise" and "the next set" after local midnight,
independently.

- **Sunset after midnight** (e.g. Reykjavik or Fairbanks near the June solstice): "the next set after midnight" is
  the *previous* evening's sunset, so sunset < sunrise and the day length goes negative.
- **Polar day / polar night**: there is no rise or set. suncalc searches up to 365 days ahead by default, so it may
  return a date weeks away instead of null, and the `?: 6:00` fallback never kicks in.

**Fix:**
1. Chain the events: sunrise → the next set after *that sunrise* → the next rise after *that set*.
2. Limit each suncalc search to ~1 day (`.oneDay()` / `.limit(...)`) so "no event" comes back as null.
3. Choose a rule for days with no sunrise or sunset. Tradition doesn't define one (planetary hours are "temporal
   hours" from Mediterranean latitudes). Conventions borrowed from high-latitude Islamic prayer times:
   - *Nearest latitude*: use the sunrise/sunset of the nearest latitude (~65°) where they still occur.
   - *Equal hours*: twelve 60-minute hours from a fixed time.

**Test:** add Fairbanks (64.84, -147.72) on 2026-06-21 and Tromsø (69.65, 18.96) on 2026-12-21 to
`PlanetaryHourCalculatorTest`, asserting the hours are contiguous, positive-length, and in Chaldean order.
