// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.protocol.ControllerConfig
import br.dev.edgard.easygamepad.protocol.DeviceProfile
import br.dev.edgard.easygamepad.protocol.GattProfile
import br.dev.edgard.easygamepad.protocol.ProfileExportCodec
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HubStorageCodecTest {
    private val validProfile = SavedProfile("id-1", ControllerConfig(name = "OK"))

    @Test fun profilesKeepValidRowsWhenOneEntryIsDamaged() {
        val array = JSONArray()
            .put(JSONObject().put("id", "good").put("config", ProfileExportCodec.encodeConfig(validProfile.config)))
            .put(JSONObject().put("id", "bad").put("config", JSONObject().put("name", "")))
        val decoded = decodeProfilesLenient(array.toString())
        assertEquals(1, decoded.items.size)
        assertEquals(1, decoded.warnings.single().skippedEntries)
        assertFalse(decoded.warnings.single().rootUnreadable)
    }

    @Test fun malformedProfilesRootSurfacesUnreadableWarning() {
        val decoded = decodeProfilesLenient("not-json")
        assertTrue(decoded.items.isEmpty())
        assertTrue(decoded.warnings.single().rootUnreadable)
    }

    @Test fun legacyControllerWithoutModelDefaultsToX10() {
        val row = JSONObject()
            .put("address", "AA:BB:CC:DD:EE:01")
            .put("name", "Sala")
            .put("input", JSONObject.NULL)
            .put("service", GattProfile().service)
            .put("write", GattProfile().write)
            .put("notify", GattProfile().notify)
        val decoded = decodeControllersLenient(JSONArray().put(row).toString())
        assertEquals(DeviceProfile.X10.id, decoded.items.single().modelId)
    }

    @Test fun loadPreferencesMigratesSingleLegacyAddress() {
        val snapshot = loadHubPreferences(
            language = "en", theme = "dark", address = "AA:BB:CC:DD:EE:01",
            service = GattProfile().service, write = GattProfile().write, notify = GattProfile().notify,
            profilesJson = "[]", controllersJson = null, inputNamesJson = "{}",
        )
        assertEquals("AA:BB:CC:DD:EE:01", snapshot.preferences.controllers.single().address)
        assertTrue(snapshot.warnings.isEmpty())
    }

    @Test fun emptyProfilesArrayIsNotTreatedAsCorruption() {
        val decoded = decodeProfilesLenient("[]")
        assertTrue(decoded.items.isEmpty())
        assertTrue(decoded.warnings.isEmpty())
    }

    @Test fun rewriteProfilesKeepsPreservedDamagedEntries() {
        val damaged = JSONObject().put("id", "bad").put("config", JSONObject().put("name", ""))
        val array = JSONArray()
            .put(JSONObject().put("id", "good").put("config", ProfileExportCodec.encodeConfig(validProfile.config)))
            .put(damaged)
        val decoded = decodeProfilesLenient(array.toString())
        assertEquals(1, decoded.preservedSlots.size)
        val rewritten = rewriteProfilesJson(array.toString()) { decoded.items }
        val again = decodeProfilesLenient(rewritten)
        assertEquals(1, again.items.size)
        assertEquals(1, again.preservedSlots.size)
    }

    @Test fun rewriteProfilesPreservesNonObjectSkippedSlots() {
        val array = JSONArray()
            .put(JSONObject().put("id", "good").put("config", ProfileExportCodec.encodeConfig(validProfile.config)))
            .put(7)
            .put("orphan")
            .put(JSONArray().put(1))
        val decoded = decodeProfilesLenient(array.toString())
        assertEquals(1, decoded.items.size)
        assertEquals(3, decoded.preservedSlots.size)
        val another = validProfile.copy(id = "added")
        val rewritten = rewriteProfilesJson(array.toString()) { decoded.items + another }
        val roundTrip = decodeProfilesLenient(rewritten)
        assertEquals(2, roundTrip.items.size)
        assertEquals(3, roundTrip.preservedSlots.size)
    }

    @Test fun controllersAllowPreservedRowsBeyondStructuredCap() {
        val rows = (1..29).map { index ->
            SavedController("AA:BB:CC:DD:EE:%02X".format(index), gatt = GattProfile())
        }
        val damaged = JSONObject().put("not", "controller")
        val array = JSONArray()
        rows.forEach { row ->
            array.put(
                JSONObject()
                    .put("address", row.address)
                    .put("name", row.name)
                    .put("model", row.modelId)
                    .put("input", JSONObject.NULL)
                    .put("service", row.gatt.service)
                    .put("write", row.gatt.write)
                    .put("notify", row.gatt.notify),
            )
        }
        array.put(damaged)
        val decoded = decodeControllersLenient(array.toString())
        assertEquals(29, decoded.items.size)
        assertEquals(1, decoded.preservedSlots.size)
        val added = SavedController("AA:BB:CC:DD:EE:30", gatt = GattProfile())
        val rewritten = rewriteControllersJson(array.toString()) { decoded.items + added }
        val reload = decodeControllersLenient(rewritten)
        assertEquals(30, reload.items.size)
        assertEquals(1, reload.preservedSlots.size)
    }

    @Test fun unreadableControllerRootCanBeReplacedWithStructuredList() {
        val unreadable = "{not-valid-json"
        val decoded = decodeControllersLenient(unreadable)
        assertTrue(decoded.warnings.single().rootUnreadable)
        val row = SavedController("AA:BB:CC:DD:EE:01", gatt = GattProfile())
        val structured = encodeControllers(listOf(row))
        val reload = decodeControllersLenient(structured)
        assertEquals(1, reload.items.size)
    }
}
