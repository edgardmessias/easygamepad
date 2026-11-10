// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

const val MAX_SAVED_PROFILES = 30

/** Replace an existing row or append; new copies are blocked at [MAX_SAVED_PROFILES]. */
fun mergeSavedProfile(existing: List<SavedProfile>, profile: SavedProfile): List<SavedProfile> {
    val without = existing.filterNot { it.id == profile.id }
    if (existing.none { it.id == profile.id }) require(without.size < MAX_SAVED_PROFILES) { "profile limit" }
    return without + profile
}

fun canAddSavedProfile(existing: List<SavedProfile>) = existing.size < MAX_SAVED_PROFILES
