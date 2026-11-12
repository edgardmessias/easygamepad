// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.demo

/**
 * Routes debug ADB lifecycle commands through [br.dev.edgard.easygamepad.HubViewModel]
 * so selection, manual disconnect and real BLE sessions stay coherent.
 */
interface DemoAdbCoordinator {
    fun adbConnectDemo()
    fun adbDisconnectDemo()
    fun adbFactoryReset()
}
