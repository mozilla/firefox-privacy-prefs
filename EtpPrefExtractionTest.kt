/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * Any copyright is dedicated to the Public Domain.
   http://creativecommons.org/publicdomain/zero/1.0/ */

package org.mozilla.geckoview.test

import android.util.Log
import androidx.annotation.OptIn
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.ExperimentalGeckoViewApi
import org.mozilla.geckoview.GeckoPreferenceController
import org.mozilla.geckoview.GeckoPreferenceController.PREF_TYPE_BOOL
import org.mozilla.geckoview.GeckoPreferenceController.PREF_TYPE_INT
import org.mozilla.geckoview.GeckoPreferenceController.PREF_TYPE_STRING

/**
 * Extracts resolved Gecko pref values for each ETP mode (standard, strict).
 * Output is logged as JSON with tag ETP_PREF_DUMP for CI post-processing.
 *
 * Applies GeckoView ContentBlocking.Settings directly to simulate what the
 * Android Components glue layer does for each mode (see
 * TrackingProtectionPolicy.toContentBlockingSetting()).
 *
 * Pref list sourced from ContentBlocking.java Pref<> field definitions.
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
@OptIn(ExperimentalGeckoViewApi::class)
class EtpPrefExtractionTest : BaseSessionTest() {

    companion object {
        const val TAG = "ETP_PREF_DUMP"

        // Prefs managed by ContentBlocking.Settings, sourced from
        // ContentBlocking.java Pref<> field definitions.
        val ETP_PREFS = listOf(
            "urlclassifier.trackingTable",
            "privacy.trackingprotection.cryptomining.enabled",
            "urlclassifier.features.cryptomining.blacklistTables",
            "privacy.trackingprotection.fingerprinting.enabled",
            "urlclassifier.features.fingerprinting.blacklistTables",
            "privacy.socialtracking.block_cookies.enabled",
            "privacy.trackingprotection.socialtracking.enabled",
            "urlclassifier.features.socialtracking.annotate.blacklistTables",
            "browser.safebrowsing.malware.enabled",
            "browser.safebrowsing.phishing.enabled",
            "privacy.trackingprotection.harmfuladdon.enabled",
            "network.cookie.cookieBehavior",
            "network.cookie.cookieBehavior.pbmode",
            "privacy.purge_trackers.enabled",
            "privacy.trackingprotection.annotate_channels",
            "privacy.annotate_channels.strict_list.enabled",
            "browser.contentblocking.category",
            "privacy.trackingprotection.allow_list.baseline.enabled",
            "privacy.trackingprotection.allow_list.convenience.enabled",
            "privacy.trackingprotection.emailtracking.enabled",
            "privacy.trackingprotection.emailtracking.pbmode.enabled",
            "urlclassifier.features.emailtracking.blocklistTables",
            "privacy.query_stripping.enabled",
            "privacy.query_stripping.enabled.pbmode",
            "privacy.bounceTrackingProtection.mode",
        )

        // GeckoView AntiTracking bitmask composites.
        // EngineSession RECOMMENDED = AD|ANALYTICS|SOCIAL|TEST|MOZILLA_SOCIAL|CRYPTOMINING|FINGERPRINTING
        // After SCRIPTS_AND_SUB_RESOURCES subtraction in glue layer, this maps to:
        val AT_RECOMMENDED = ContentBlocking.AntiTracking.DEFAULT or
            ContentBlocking.AntiTracking.STP or
            ContentBlocking.AntiTracking.CRYPTOMINING or
            ContentBlocking.AntiTracking.FINGERPRINTING

        // EngineSession STRICT = RECOMMENDED + SCRIPTS_AND_SUB_RESOURCES + EMAIL
        // After SCRIPTS_AND_SUB_RESOURCES subtraction in glue layer: RECOMMENDED + EMAIL
        // CONTENT blocking is enabled separately via EtpLevel.STRICT, not this bitmask.
        val AT_STRICT = AT_RECOMMENDED or
            ContentBlocking.AntiTracking.EMAIL
    }

    private fun readPrefs(): JSONObject {
        val prefs = sessionRule.waitForResult(
            GeckoPreferenceController.getGeckoPrefs(ETP_PREFS),
        )
        val obj = JSONObject()
        for (pref in prefs) {
            val value: Any? = when (pref.type) {
                PREF_TYPE_BOOL -> pref.value as? Boolean
                PREF_TYPE_INT -> pref.value as? Int
                PREF_TYPE_STRING -> pref.value as? String
                else -> null
            }
            if (value != null) {
                obj.put(pref.pref, value)
            }
        }
        return obj
    }

    // Apply settings that mirror TrackingProtectionPolicy.recommended()
    // via toContentBlockingSetting() in the glue layer.
    private fun applyStandardMode(settings: ContentBlocking.Settings) {
        settings.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.DEFAULT)
        settings.setEnhancedTrackingProtectionCategory(ContentBlocking.EtpCategory.STANDARD)
        settings.setAntiTracking(AT_RECOMMENDED)
        settings.setCookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
        settings.setCookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
        settings.setCookiePurging(true)
        settings.setStrictSocialTrackingProtection(false)
        settings.setBounceTrackingProtectionMode(
            ContentBlocking.BounceTrackingProtectionMode.BOUNCE_TRACKING_PROTECTION_MODE_ENABLED_STANDBY,
        )
        settings.setAllowListBaselineTrackingProtection(true)
        settings.setAllowListConvenienceTrackingProtection(true)
    }

    // Apply settings that mirror TrackingProtectionPolicy.strict()
    // via toContentBlockingSetting() in the glue layer.
    private fun applyStrictMode(settings: ContentBlocking.Settings) {
        settings.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
        settings.setEnhancedTrackingProtectionCategory(ContentBlocking.EtpCategory.STRICT)
        settings.setAntiTracking(AT_STRICT)
        settings.setCookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
        settings.setCookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
        settings.setCookiePurging(true)
        settings.setStrictSocialTrackingProtection(true)
        settings.setBounceTrackingProtectionMode(
            ContentBlocking.BounceTrackingProtectionMode.BOUNCE_TRACKING_PROTECTION_MODE_ENABLED,
        )
        settings.setAllowListBaselineTrackingProtection(true)
        settings.setAllowListConvenienceTrackingProtection(false)
    }

    @Test
    fun extractEtpPrefs() {
        val output = JSONObject()
        output.put("platform", "android")

        val modes = JSONObject()
        val settings = sessionRule.runtime.settings.contentBlocking

        applyStandardMode(settings)
        modes.put("standard", readPrefs())

        applyStrictMode(settings)
        modes.put("strict", readPrefs())

        output.put("modes", modes)

        Log.i(TAG, output.toString())
    }
}
