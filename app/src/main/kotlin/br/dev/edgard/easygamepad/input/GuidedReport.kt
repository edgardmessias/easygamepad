// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.input

import java.util.Locale

data class GuidedReportHeader(
    val appVersion: String,
    val appVersionCode: String,
    val androidRelease: String,
    val androidSdk: String,
)

object GuidedReport {
    private val mac = Regex("(?i)\\b(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}\\b")

    fun render(
        session: GuidedSession,
        header: GuidedReportHeader,
        portuguese: Boolean,
        apply: GuidedApplyNote? = null,
    ): String {
        fun t(en: String, pt: String) = if (portuguese) pt else en
        val binding = session.binding
        val text = buildString {
            appendLine("EasyGamepad ${header.appVersion} (${header.appVersionCode})")
            appendLine("Android ${header.androidRelease} (SDK ${header.androidSdk})")
            appendLine(t("Status: ", "Estado: ") + status(session, portuguese))
            if (binding?.synthetic == true) {
                appendLine(t(
                    "SYNTHETIC DEMO. Simulated input, not a physical controller.",
                    "DEMO SINTÉTICO. Entrada simulada, não é um controle físico.",
                ))
            }
            appendLine()
            appendLine(t("Model: ", "Modelo: ") + (binding?.modelLabel ?: t("none", "nenhum")))
            appendLine(t("Input: ", "Entrada: ") + (binding?.inputLabel ?: t("none", "nenhuma")))
            if (binding != null && session.plan.layout == GuidedLayout.GENERIC) {
                appendLine(t(
                    "Control list: optional for this model. Missing controls may be unavailable.",
                    "Lista de controles: opcional neste modelo. Controles ausentes podem não existir.",
                ))
            }
            appendLine()
            appendGroup(this, session, portuguese, GuidedGroup.BUTTON, t("Buttons", "Botões"))
            appendGroup(this, session, portuguese, GuidedGroup.SHOULDER, t("Shoulders", "Superiores"))
            appendGroup(this, session, portuguese, GuidedGroup.DPAD, t("D-pad", "Direcional"))
            appendLine(t("Triggers:", "Gatilhos:"))
            GuidedCatalog.triggers.forEach { id -> appendLine(triggerLine(id, session.triggers[id], portuguese)) }
            appendLine(t(
                "Stick directions follow Android axes; negative Y is up.",
                "As direções dos analógicos seguem os eixos do Android; Y negativo é para cima.",
            ))
            appendLine(stickLine(t("Left stick", "Analógico esquerdo"), session.sticks["LS"], portuguese))
            appendLine(stickLine(t("Right stick", "Analógico direito"), session.sticks["RS"], portuguese))
            appendLine(restLine(session.rest, portuguese))
            appendRear(this, session, portuguese)
            if (apply != null) {
                appendLine()
                appendLine(applyLine(apply, portuguese))
            }
            appendLine()
            appendLine(t(
                "This log is one guided session of Android input observed after Start. It does not grade the controller and it does not show that settings remain after the controller is turned off.",
                "Este registro é uma sessão guiada da entrada Android observada depois de Iniciar. Ele não classifica o controle e não mostra se os ajustes permanecem depois de desligar o controle.",
            ))
        }
        return mac.replace(text, "<omitted>")
    }

    fun itemLabel(id: String, portuguese: Boolean): String = rowLabel(id, portuguese)

    fun applySummary(note: GuidedApplyNote, portuguese: Boolean): String = applyLine(note, portuguese)

    private fun status(session: GuidedSession, portuguese: Boolean): String {
        fun t(en: String, pt: String) = if (portuguese) pt else en
        val progress = GuidedTest.progress(session)
        return when (session.phase) {
            GuidedPhase.DISABLED -> t("no input", "sem entrada")
            GuidedPhase.IDLE -> t("not started", "não iniciado")
            GuidedPhase.ACTIVE -> if (progress.totalRequired > 0) {
                t(
                    "in progress, ${progress.observedRequired}/${progress.totalRequired} required",
                    "em andamento, ${progress.observedRequired}/${progress.totalRequired} obrigatórias",
                )
            } else {
                t(
                    "in progress, ${progress.observedOptional} optional observations",
                    "em andamento, ${progress.observedOptional} observações opcionais",
                )
            }
            GuidedPhase.PAUSED -> t("paused", "pausado")
            GuidedPhase.COMPLETED -> if (session.stoppedEarly) t("partial", "parcial") else t("completed", "concluído")
        }
    }

    private fun appendGroup(out: StringBuilder, session: GuidedSession, portuguese: Boolean, group: GuidedGroup, title: String) {
        fun t(en: String, pt: String) = if (portuguese) pt else en
        val items = GuidedTest.rows(session).filter { it.group == group }
        if (items.isEmpty()) return
        val observed = items.filter { it.observed }.joinToString(", ") { rowLabel(it.id, portuguese) }
        val missing = items.filter { !it.observed }.joinToString(", ") { rowLabel(it.id, portuguese) }
        out.appendLine("$title " + t("observed: ", "observados: ") + observed.ifEmpty { t("none", "nenhum") })
        out.appendLine("$title " + t("not observed: ", "não observados: ") + missing.ifEmpty { t("none", "nenhum") })
    }

    private fun triggerLine(id: String, fact: TriggerObservation?, portuguese: Boolean): String {
        fun t(en: String, pt: String) = if (portuguese) pt else en
        if (fact == null) return "$id: " + t("not observed", "não observado")
        val peak = fact.peak?.let { number(it) }
        return when {
            fact.fullTravel && fact.digital -> "$id: " + t(
                "analog peak $peak, full travel observed, step change also seen",
                "pico analógico $peak, curso completo observado, também houve salto",
            )
            fact.fullTravel -> "$id: " + t(
                "analog peak $peak, full travel observed",
                "pico analógico $peak, curso completo observado",
            )
            fact.digital || (fact.peak ?: 0f) >= GuidedSignals.TRIGGER_FULL -> "$id: " + t(
                "press or endpoint observed; gradual analog travel not observed; axis peak ${peak ?: "—"}",
                "toque ou extremo observado; curso analógico gradual não observado; pico do eixo ${peak ?: "—"}",
            )
            peak != null -> "$id: " + t(
                "analog peak $peak, full travel not observed",
                "pico analógico $peak, curso completo não observado",
            )
            else -> "$id: " + t("not observed", "não observado")
        }
    }

    private fun stickLine(title: String, stick: StickObservation?, portuguese: Boolean): String {
        fun t(en: String, pt: String) = if (portuguese) pt else en
        if (stick == null || (stick.directions.isEmpty() && stick.maxRadius == 0f && stick.minX == null)) {
            return "$title: " + t("not observed", "não observado")
        }
        val names = stick.directions.sortedBy { it.name }.joinToString(", ") { directionLabel(it, portuguese) }
        val seen = names.ifEmpty { t("none", "nenhuma") }
        val span = "X ${span(stick.minX, stick.maxX)} Y ${span(stick.minY, stick.maxY)}"
        return "$title: " + t("directions ", "direções ") + seen +
            t("; max radius ", "; raio máximo ") + number(stick.maxRadius) + "; " + span
    }

    private fun restLine(rest: RestObservation?, portuguese: Boolean): String {
        fun t(en: String, pt: String) = if (portuguese) pt else en
        if (rest == null || !rest.anyFinite) {
            return t("Resting offsets: not captured.", "Desvios em repouso: não registrados.")
        }
        return t(
            "Resting offsets (user confirmed the current reading): " +
                "left ${pair(rest.leftX, rest.leftY)} right ${pair(rest.rightX, rest.rightY)} " +
                "LT ${channel(rest.leftTrigger)} RT ${channel(rest.rightTrigger)}",
            "Desvios em repouso (leitura atual confirmada por você): " +
                "esquerdo ${pair(rest.leftX, rest.leftY)} direito ${pair(rest.rightX, rest.rightY)} " +
                "LT ${channel(rest.leftTrigger)} RT ${channel(rest.rightTrigger)}",
        )
    }

    private fun appendRear(out: StringBuilder, session: GuidedSession, portuguese: Boolean) {
        fun t(en: String, pt: String) = if (portuguese) pt else en
        if (session.plan.rearIds.isEmpty()) {
            out.appendLine(t("Rear paddles: not in this checklist.", "Botões traseiros: fora desta lista."))
            return
        }
        out.appendLine(t(
            "Rear paddles are manual. An Android button, including a mapped face button, does not verify the physical paddle.",
            "Botões traseiros são manuais. Um botão Android, inclusive um botão da frente mapeado, não verifica o paddle físico.",
        ))
        session.plan.rearIds.forEach { id ->
            out.appendLine(if (id in session.rearConfirmed) {
                t(
                    "$id: user confirmed a physical press. Mapping and a distinct electrical input were not verified.",
                    "$id: você confirmou um pressionamento físico. O mapeamento e uma entrada elétrica distinta não foram verificados.",
                )
            } else {
                t("$id: not verified.", "$id: não verificado.")
            })
        }
    }

    private fun applyLine(note: GuidedApplyNote, portuguese: Boolean): String {
        fun t(en: String, pt: String) = if (portuguese) pt else en
        val tail = t(
            "This is separate from the physical checklist. Whether settings remain after the controller is turned off was not checked.",
            "Isto é separado da lista física. Não foi verificado se os ajustes permanecem depois de desligar o controle.",
        )
        return when (note.status) {
            GuidedApplyStatus.MATCHED -> t(
                "X10 read-back: Matched. Readable settings matched the profile sent on this configuration link. $tail",
                "Releitura do X10: Confere. Os ajustes legíveis conferem com o perfil enviado neste vínculo de configuração. $tail",
            )
            GuidedApplyStatus.MISMATCH -> {
                val fields = note.fields.joinToString(", ") { fieldLabel(it, portuguese) }
                t(
                    "X10 read-back: Mismatch ($fields). Readable settings differ from the profile sent on this configuration link. $tail",
                    "Releitura do X10: Difere ($fields). Os ajustes legíveis diferem do perfil enviado neste vínculo de configuração. $tail",
                )
            }
            GuidedApplyStatus.UNCONFIRMED -> t(
                "X10 read-back: Unconfirmed. The configuration link did not confirm a read-back. $tail",
                "Releitura do X10: Não confirmada. O vínculo de configuração não confirmou a releitura. $tail",
            )
        }
    }

    private fun rowLabel(id: String, portuguese: Boolean): String = when (id) {
        "View" -> if (portuguese) "View" else "View"
        "Menu" -> if (portuguese) "Menu" else "Menu"
        "Up" -> if (portuguese) "cima" else "up"
        "Down" -> if (portuguese) "baixo" else "down"
        "Left" -> if (portuguese) "esquerda" else "left"
        "Right" -> if (portuguese) "direita" else "right"
        "rest" -> if (portuguese) "repouso" else "rest"
        else -> when {
            id.endsWith("-up") -> if (portuguese) "${stickTitle(id)} cima" else "${stickTitle(id)} up"
            id.endsWith("-down") -> if (portuguese) "${stickTitle(id)} baixo" else "${stickTitle(id)} down"
            id.endsWith("-left") -> if (portuguese) "${stickTitle(id)} esquerda" else "${stickTitle(id)} left"
            id.endsWith("-right") -> if (portuguese) "${stickTitle(id)} direita" else "${stickTitle(id)} right"
            id.endsWith("-range") -> if (portuguese) "${stickTitle(id)} curso" else "${stickTitle(id)} range"
            id.startsWith("rear:") -> id.removePrefix("rear:")
            else -> id
        }
    }

    private fun stickTitle(id: String): String = if (id.startsWith("LS")) "L" else "R"

    private fun directionLabel(direction: GuidedDirection, portuguese: Boolean): String = when (direction) {
        GuidedDirection.UP -> if (portuguese) "cima" else "up"
        GuidedDirection.DOWN -> if (portuguese) "baixo" else "down"
        GuidedDirection.LEFT -> if (portuguese) "esquerda" else "left"
        GuidedDirection.RIGHT -> if (portuguese) "direita" else "right"
    }

    private fun fieldLabel(token: String, portuguese: Boolean): String = when (token) {
        "buttons" -> if (portuguese) "botões" else "buttons"
        "sticks" -> if (portuguese) "analógicos" else "sticks"
        "triggers" -> if (portuguese) "gatilhos" else "triggers"
        "vibration" -> if (portuguese) "vibração" else "vibration"
        "turbo" -> "turbo"
        else -> token
    }

    private fun span(min: Float?, max: Float?): String = when {
        min == null && max == null -> "—"
        else -> "${signed(min)}..${signed(max)}"
    }

    private fun pair(x: Float?, y: Float?): String = "X ${channel(x)} Y ${channel(y)}"

    private fun channel(value: Float?): String = value?.let(::signed) ?: "—"

    private fun signed(value: Float?): String = value?.let { String.format(Locale.US, "%+.3f", it) } ?: "—"

    private fun number(value: Float): String = String.format(Locale.US, "%.3f", value)
}
