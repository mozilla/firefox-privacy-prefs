#!/usr/bin/env python
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.

"""
Extract resolved ETP preference values from a running Desktop Firefox instance.

Uses Marionette to launch headless Firefox in chrome context, switch ETP modes
via ContentBlockingPrefs, and read back effective pref values.

Output: JSON with { "platform": "desktop", "modes": { "standard": {...}, "strict": {...} } }
"""

import argparse
import json
import sys

from marionette_driver.marionette import Marionette


EXTRACT_SCRIPT = """
const PREFS = [
  "privacy.trackingprotection.enabled",
  "privacy.trackingprotection.pbmode.enabled",
  "privacy.trackingprotection.fingerprinting.enabled",
  "privacy.trackingprotection.cryptomining.enabled",
  "privacy.trackingprotection.socialtracking.enabled",
  "privacy.trackingprotection.emailtracking.enabled",
  "privacy.trackingprotection.emailtracking.pbmode.enabled",
  "privacy.trackingprotection.consentmanager.skip.enabled",
  "privacy.trackingprotection.consentmanager.skip.pbmode.enabled",
  "privacy.annotate_channels.strict_list.enabled",
  "network.cookie.cookieBehavior",
  "network.cookie.cookieBehavior.pbmode",
  "network.cookie.cookieBehavior.optInPartitioning",
  "network.http.referer.disallowCrossSiteRelaxingDefault",
  "network.http.referer.disallowCrossSiteRelaxingDefault.top_navigation",
  "privacy.partition.network_state.ocsp_cache",
  "privacy.query_stripping.enabled",
  "privacy.query_stripping.enabled.pbmode",
  "privacy.fingerprintingProtection",
  "privacy.fingerprintingProtection.pbmode",
  "privacy.bounceTrackingProtection.mode",
  "network.lna.blocking",
  "privacy.trackingprotection.allow_list.baseline.enabled",
  "privacy.trackingprotection.allow_list.convenience.enabled",
];

function readPrefs() {
  const out = {};
  for (const pref of PREFS) {
    const type = Services.prefs.getPrefType(pref);
    if (type === Services.prefs.PREF_BOOL)
      out[pref] = Services.prefs.getBoolPref(pref);
    else if (type === Services.prefs.PREF_INT)
      out[pref] = Services.prefs.getIntPref(pref);
    else if (type === Services.prefs.PREF_STRING)
      out[pref] = Services.prefs.getStringPref(pref);
  }
  return out;
}

const { ContentBlockingPrefs } = ChromeUtils.importESModule(
  "resource:///modules/ContentBlockingPrefs.sys.mjs"
);
ContentBlockingPrefs.setPrefExpectations();

const result = {};
for (const mode of ["standard", "strict"]) {
  ContentBlockingPrefs.setPrefsToCategory(mode);
  result[mode] = readPrefs();
}
return result;
"""


def extract_etp_prefs(binary, output):
    client = Marionette(bin=binary, headless=True)
    try:
        client.start_session()
        client.set_context(client.CONTEXT_CHROME)
        modes = client.execute_script(EXTRACT_SCRIPT, sandbox="system")

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
