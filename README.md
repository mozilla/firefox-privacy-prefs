# Firefox Privacy Prefs

Extracts resolved privacy preference values from running Firefox instances
(Desktop and Android). The resulting JSON files are consumed by the Firefox
source docs Sphinx extension (`docs/_addons/etp_matrix.py`) to generate the
[Privacy Capabilities Matrix](https://firefox-source-docs.mozilla.org/toolkit/components/antitracking/anti-tracking/etp-matrix/).

## How it works

A daily GitHub Actions workflow:

1. **Desktop**: Downloads the latest Firefox Nightly, launches it headlessly
   via Marionette, switches ETP modes, and reads back resolved pref values.
2. **Android**: Downloads GeckoView test APKs from Mozilla CI, boots an
   Android emulator, runs the extraction test, and parses pref values from
   the test output.
3. Commits updated `desktop.json` and `android.json` to this repo.

The Firefox doc build fetches `android.json` from this repo's raw URL at
build time. Desktop prefs are parsed from source files directly by the
Sphinx extension.

## Files

| File | Purpose |
|------|---------|
| `desktop.json` | Resolved Desktop ETP prefs per mode |
| `android.json` | Resolved Android ETP prefs per mode |
| `scripts/extract_desktop.py` | Marionette extraction script |
| `EtpPrefExtractionTest.kt` | GeckoView JUnit test (reference) |
| `.github/workflows/extract.yml` | Daily extraction workflow |

## Manual run

To trigger the extraction manually, go to Actions > Extract ETP Prefs > Run workflow.

## JSON schema

```json
{
  "platform": "android",
  "modes": {
    "standard": { "pref.name": value, ... },
    "strict":   { "pref.name": value, ... }
  }
}
```
