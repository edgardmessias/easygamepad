// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.dev.edgard.easygamepad.BuildConfig
import br.dev.edgard.easygamepad.HubViewModel
import br.dev.edgard.easygamepad.demo.DemoController
import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.data.DiagnosticsSanitizer
import br.dev.edgard.easygamepad.protocol.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable fun ToolsScreen(model: HubViewModel) {
    val selectedDevice by model.selectedDevice.collectAsStateWithLifecycle()
    val state by model.hardware.collectAsStateWithLifecycle()
    val link by model.link.collectAsStateWithLifecycle()
    val logs by model.ble.logs.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(CalibrationKind.STICKS) }
    var confirm by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    var showLogs by rememberSaveable { mutableStateOf(false) }
    var versionDetails by rememberSaveable { mutableStateOf(false) }
    var includeRawPayloads by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val cal = state.calibration
    val ready = link.phase == LinkPhase.READY
    ScreenColumn {
        ScreenHeading(tr("Controller care", "Cuidados com o controle"), tr("Fine-tune your game.", "Tudo no ponto."), tr("Guided calibration and tools for your X10.", "Calibração guiada e ferramentas para seu X10."))
        if (DemoController.isDevice(selectedDevice)) SimulatedControllerBanner()
        ScreenLinkStatus(ready, tr("App connected", "App conectado"), tr("Connect to calibrate", "Conecte para calibrar"))
        if (!ready) OutlinedButton(onClick = model::connectSelected, modifier = Modifier.fillMaxWidth()) { Text(tr("Connect to app", "Conectar ao app")) }
        HubCard(Modifier.fillMaxWidth()) {
            ScreenCardHeader(Glyph.TOOLS, tr("Calibration studio", "Central de calibração"), tint = MaterialTheme.colorScheme.tertiary)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalibrationKind.entries.forEach { kind ->
                    FilterChip(selected == kind, onClick = { selected = kind }, enabled = !cal.stopRequired,
                        label = { Text(calibrationName(kind)) })
                }
            }
            val activeKind = if (cal.stopRequired) cal.kind else selected
            val instructions = when (activeKind) {
                CalibrationKind.STICKS -> listOf(
                    tr("Release the sticks and place the controller on a stable surface.", "Solte os analógicos e coloque o controle em uma superfície estável."),
                    tr("Start calibration. Slowly rotate both sticks through their full range several times.", "Inicie a calibração. Gire os dois analógicos lentamente por todo o curso algumas vezes."),
                    tr("Release both sticks and wait for the controller's result, then finish.", "Solte os dois analógicos e aguarde o resultado do controle; depois finalize."))
                CalibrationKind.TRIGGERS -> listOf(
                    tr("Release both triggers completely.", "Solte completamente os dois gatilhos."),
                    tr("Start calibration, then slowly press and release LT and RT fully several times.", "Inicie a calibração e pressione e solte LT e RT por todo o curso algumas vezes."),
                    tr("Wait for the controller's result, then finish.", "Aguarde o resultado do controle e finalize."))
                CalibrationKind.SELF -> listOf(
                    tr("Place the controller flat on a stable surface.", "Coloque o controle apoiado em uma superfície plana e estável."),
                    tr("Start self-calibration. Leave all buttons and sticks untouched.", "Inicie a autocalibração. Não toque nos botões nem nos analógicos."),
                    tr("Wait for the controller to confirm, then finish.", "Aguarde a confirmação do controle e finalize."))
            }
            instructions.forEachIndexed { index, text ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) { Text("${index + 1}", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.primary) }
                    Description(text, Modifier.weight(1f))
                }
            }
            if (cal.phase != CalibrationPhase.IDLE) {
                InfoBanner(when (cal.phase) {
                    CalibrationPhase.STARTING -> tr("Starting calibration…", "Iniciando calibração…")
                    CalibrationPhase.ACTIVE -> tr("Calibration in progress. Follow the steps above. Waiting for the controller's result…", "Calibração em andamento. Siga as etapas acima. Aguardando o resultado do controle…")
                    CalibrationPhase.NEEDS_RETRY -> tr("The controller requested a retry. Stop this session and start again.", "O controle solicitou uma nova tentativa. Encerre esta sessão e inicie novamente.")
                    CalibrationPhase.SUCCEEDED -> tr("Calibration confirmed by the controller.", "Calibração confirmada pelo controle.")
                    CalibrationPhase.FAILED -> tr("Calibration was not completed. Reconnect if needed and try again.", "A calibração não foi concluída. Reconecte se necessário e tente novamente.")
                    CalibrationPhase.CANCELLED -> tr("Calibration stopped. No successful result was received.", "Calibração encerrada. Nenhum resultado de sucesso foi recebido.")
                    else -> ""
                }, cal.phase in listOf(CalibrationPhase.FAILED, CalibrationPhase.NEEDS_RETRY))
            }
            if (cal.stopRequired) {
                if (cal.phase in listOf(CalibrationPhase.ACTIVE, CalibrationPhase.STARTING)) LinearProgressIndicator(Modifier.fillMaxWidth())
                Button(onClick = model::stopCalibration, enabled = ready && !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (cal.phase == CalibrationPhase.SUCCEEDED) tr("Finish calibration", "Finalizar calibração") else tr("Stop calibration", "Encerrar calibração"))
                }
            } else Button(onClick = { confirm = true }, enabled = ready && !state.busy, modifier = Modifier.fillMaxWidth()) { Text(tr("Start calibration", "Iniciar calibração")) }
            Description(tr("This session ends after 90 seconds. Keep the app open. Leaving the app stops the session and disconnects Bluetooth.", "A sessão encerra após 90 segundos. Mantenha o app aberto. Ao sair do app, a sessão é encerrada e o Bluetooth é desconectado."))
        }
        HubCard(Modifier.fillMaxWidth()) {
            Text(tr("Controller details", "Detalhes do controle"), style = MaterialTheme.typography.titleLarge)
            DetailRow(tr("Model", "Modelo"), "EasySMX X10")
            DetailRow(tr("Connection", "Conexão"), if (ready) "Bluetooth LE" else tr("Disconnected", "Desconectado"))
            state.firmware?.let { firmware ->
                DetailRow(tr("Controller software", "Software do controle"), firmware.firmware)
                TextButton(onClick = { versionDetails = !versionDetails }) { Text(if (versionDetails) tr("Hide technical details", "Ocultar detalhes técnicos") else tr("Technical details", "Detalhes técnicos")) }
                if (versionDetails) {
                    DetailRow(tr("Hardware", "Hardware"), firmware.hardware)
                    DetailRow(tr("Protocol", "Protocolo"), firmware.protocol)
                    DetailRow(tr("Project", "Projeto"), firmware.project)
                }
            } ?: Description(tr("Read version information from your controller.", "Leia as informações de versão do controle."))
            OutlinedButton(onClick = model::readVersions, enabled = ready && !state.busy && !cal.stopRequired) { Text(tr("Check version", "Consultar versão")) }
            Description(tr("Controller software updates and lighting adjustments are not available in this version.", "Atualizações do software do controle e ajustes de iluminação não estão disponíveis nesta versão."))
        }
        HubCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Connection diagnostics", "Diagnóstico da conexão"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showLogs = !showLogs }) { Text(if (showLogs) tr("Hide", "Ocultar") else tr("View", "Ver")) }
            }
            Description(tr(
                "Connection history is sanitized by default. Enable raw payloads only when a maintainer needs packet detail.",
                "O histórico é sanitizado por padrão. Ative cargas brutas só quando um mantenedor precisar de detalhe de pacotes.",
            ))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeRawPayloads, onCheckedChange = { includeRawPayloads = it })
                Text(tr("Include raw payloads when sharing", "Incluir cargas brutas ao compartilhar"))
            }
            if (showLogs) {
                if (logs.isEmpty()) Description(tr("No messages yet.", "Nenhuma mensagem ainda."))
                logs.takeLast(35).forEach { log ->
                    val body = DiagnosticsSanitizer.sanitizeLine(log.message, includeRawPayloads)
                    Mono("${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(log.time))} ${log.direction} $body")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    val text = DiagnosticsSanitizer.buildExport(
                        BuildConfig.VERSION_NAME,
                        android.os.Build.VERSION.RELEASE,
                        logs,
                        includeRawPayloads,
                    )
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                }, enabled = logs.isNotEmpty()) { Text(tr("Share history", "Compartilhar histórico")) }
                TextButton(onClick = model.ble::clearLogs) { Text(tr("Clear", "Limpar")) }
            }
        }
        HubCard(Modifier.fillMaxWidth()) {
            Text(tr("Factory settings", "Configurações de fábrica"), style = MaterialTheme.typography.titleMedium)
            Description(tr("Restore the controller's factory configuration. Your saved phone profiles are kept.", "Restaure a configuração de fábrica do controle. Os perfis salvos no celular são mantidos."))
            OutlinedButton(onClick = { reset = true }, enabled = ready && !state.busy && !cal.stopRequired, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(tr("Reset controller", "Restaurar controle")) }
        }
    }
    if (confirm) ConfirmDialog(calibrationName(selected), tr("Make sure the controller is charged, keep it nearby and follow the guided steps. The controller decides whether calibration succeeds.", "Confira a carga do controle, mantenha-o próximo e siga as etapas guiadas. O controle informa se a calibração foi bem-sucedida."), tr("Start", "Iniciar"), { confirm = false }) { confirm = false; model.startCalibration(selected) }
    if (reset) ConfirmDialog(tr("Restore factory settings?", "Restaurar configurações de fábrica?"), tr("This resets settings stored on the X10, including mappings and macros. This action cannot be undone without reapplying a saved profile.", "Isso restaura as configurações do X10, incluindo mapeamentos e macros. A ação só pode ser revertida reaplicando um perfil salvo."), tr("Reset X10", "Restaurar X10"), { reset = false }) { reset = false; model.reset() }
}

@Composable private fun calibrationName(kind: CalibrationKind) = when (kind) {
    CalibrationKind.STICKS -> tr("Analog sticks", "Analógicos")
    CalibrationKind.TRIGGERS -> tr("Triggers", "Gatilhos")
    CalibrationKind.SELF -> tr("Self-calibration", "Autocalibração")
}
@Composable fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { Description(label, Modifier.weight(1f)); Text(value, style = MaterialTheme.typography.bodyMedium) }
}
