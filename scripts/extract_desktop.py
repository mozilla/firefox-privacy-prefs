#!/usr/bin/env python
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.

"""
Extract resolved ETP (Enhanced Tracking Protection) preference values from a
running Desktop Firefox instance for each ETP mode (standard, strict).

Uses Marionette to launch headless Firefox, switch ETP modes via
ContentBlockingPrefs, and read back the effective pref values. The pref list
is derived at runtime from ContentBlockingPrefs.CATEGORY_PREFS -- no
hardcoded list is maintained here.

Output: a JSON file with the schema:
{
  "platform": "desktop",
  "modes": {
    "standard": { "pref.name": value, ... },
    "strict":   { "pref.name": value, ... }
  }
}
"""

import argparse
import json
import sys

from marionette_driver.marionette import Marionette


EXTRACT_SCRIPT = """
const { ContentBlockingPrefs } = ChromeUtils.importESModule(
  "resource:///modules/ContentBlockingPrefs.sys.mjs"
);

ContentBlockingPrefs.setPrefExpectations();

const result = {};
for (const mode of ["standard", "strict"]) {
  ContentBlockingPrefs.setPrefsToCategory(mode);
  result[mode] = {};
  for (const pref of Object.keys(ContentBlockingPrefs.CATEGORY_PREFS[mode])) {
    const type = Services.prefs.getPrefType(pref);
    if (type === Services.prefs.PREF_BOOL) {
      result[mode][pref] = Services.prefs.getBoolPref(pref);
    } else if (type === Services.prefs.PREF_INT) {
      result[mode][pref] = Services.prefs.getIntPref(pref);
    } else if (type === Services.prefs.PREF_STRING) {
      result[mode][pref] = Services.prefs.getStringPref(pref);
    }
  }
}
return result;
"""


def extract_etp_prefs(binary, output):
    client = Marionette(bin=binary, headless=True)
    try:
        client.start_session()
        with client.using_context(client.CONTEXT_CHROME):
            modes = client.execute_script(EXTRACT_SCRIPT)

        result = {
            "platform": "desktop",
            "modes": modes,
        }

        with open(output, "w") as f:
            json.dump(result, f, indent=2, sort_keys=True)

        print(f"Wrote {len(modes.get('standard', {}))} prefs "
              f"x {len(modes)} modes to {output}")
    finally:
        try:
            client.delete_session()
            client.cleanup()
        except Exception:
            pass


def main():
    parser = argparse.ArgumentParser(
        description="Extract ETP pref values from a Desktop Firefox build."
    )
    parser.add_argument(
        "--binary", required=True, help="Path to the Firefox binary."
    )
    parser.add_argument(
        "--output",
        default="desktop_etp_prefs.json",
        help="Output JSON file path.",
    )
    args = parser.parse_args()
    extract_etp_prefs(args.binary, args.output)


if __name__ == "__main__":
    sys.exit(main())
