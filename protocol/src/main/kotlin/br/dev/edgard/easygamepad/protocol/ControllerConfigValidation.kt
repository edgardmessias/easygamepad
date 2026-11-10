// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

enum class ProfileField { NAME, LEFT_STICK, RIGHT_STICK, LEFT_TRIGGER, RIGHT_TRIGGER, VIBRATION, MACROS, TURBOS, MAPPINGS }

enum class ProfileIssueKind {
    BLANK_NAME,
    NAME_TOO_LONG,
    STICK_RANGE,
    TRIGGER_RANGE,
    VIBRATION_RANGE,
    DUPLICATE_MACRO_TRIGGER,
    MACRO_NO_STEPS,
    MACRO_TOO_MANY_STEPS,
    MACRO_STEP,
    DUPLICATE_TURBO_KEY,
    TURBO_RATE,
    INVALID_MAPPING,
}

data class ProfileValidationIssue(val field: ProfileField, val kind: ProfileIssueKind, val index: Int? = null)

fun ControllerConfig.profileValidationIssues(): List<ProfileValidationIssue> = buildList {
    if (name.isBlank()) add(ProfileValidationIssue(ProfileField.NAME, ProfileIssueKind.BLANK_NAME))
    if (name.length > 40) add(ProfileValidationIssue(ProfileField.NAME, ProfileIssueKind.NAME_TOO_LONG))
  runCatching { leftStick.validate() }.onFailure { add(ProfileValidationIssue(ProfileField.LEFT_STICK, ProfileIssueKind.STICK_RANGE)) }
    runCatching { rightStick.validate() }.onFailure { add(ProfileValidationIssue(ProfileField.RIGHT_STICK, ProfileIssueKind.STICK_RANGE)) }
    runCatching { leftTrigger.validate() }.onFailure { add(ProfileValidationIssue(ProfileField.LEFT_TRIGGER, ProfileIssueKind.TRIGGER_RANGE)) }
    runCatching { rightTrigger.validate() }.onFailure { add(ProfileValidationIssue(ProfileField.RIGHT_TRIGGER, ProfileIssueKind.TRIGGER_RANGE)) }
    if (leftVibration !in 0..100 || rightVibration !in 0..100) add(ProfileValidationIssue(ProfileField.VIBRATION, ProfileIssueKind.VIBRATION_RANGE))
    if (mappings.values.any { it !in PadKey.outputs }) add(ProfileValidationIssue(ProfileField.MAPPINGS, ProfileIssueKind.INVALID_MAPPING))
    if (macros.map { it.trigger }.distinct().size != macros.size) add(ProfileValidationIssue(ProfileField.MACROS, ProfileIssueKind.DUPLICATE_MACRO_TRIGGER))
    macros.forEachIndexed { index, macro ->
        when {
            macro.steps.isEmpty() -> add(ProfileValidationIssue(ProfileField.MACROS, ProfileIssueKind.MACRO_NO_STEPS, index))
            macro.steps.size > 32 -> add(ProfileValidationIssue(ProfileField.MACROS, ProfileIssueKind.MACRO_TOO_MANY_STEPS, index))
            macro.trigger.code !in 1..4 -> add(ProfileValidationIssue(ProfileField.MACROS, ProfileIssueKind.MACRO_STEP, index))
            else -> macro.steps.forEachIndexed { stepIndex, _ ->
                runCatching { macro.steps[stepIndex].validate() }.onFailure {
                    add(ProfileValidationIssue(ProfileField.MACROS, ProfileIssueKind.MACRO_STEP, index * 100 + stepIndex))
                }
            }
        }
    }
    if (turbos.map { it.key }.distinct().size != turbos.size) add(ProfileValidationIssue(ProfileField.TURBOS, ProfileIssueKind.DUPLICATE_TURBO_KEY))
    turbos.forEachIndexed { index, turbo ->
        runCatching { turbo.validate() }.onFailure { add(ProfileValidationIssue(ProfileField.TURBOS, ProfileIssueKind.TURBO_RATE, index)) }
    }
}

fun ControllerConfig.profileIsValid(): Boolean = profileValidationIssues().isEmpty()
