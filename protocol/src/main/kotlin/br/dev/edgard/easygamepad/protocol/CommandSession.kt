// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

interface GamepadTransport {
    val notifications: Flow<ByteArray>
    suspend fun write(frame: ByteArray)
    fun abort(cause: Throwable)
}

class CommandSession(private val transport: GamepadTransport, scope: CoroutineScope) {
    private val encoder = FrameEncoder()
    private val decoder = FrameDecoder()
    private val lock = Mutex()
    private val mutableEvents = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val events = mutableEvents.asSharedFlow()
    private data class Pending(val id: Int, val response: CompletableDeferred<ByteArray>, val accept: (ByteArray) -> Boolean)
    @Volatile private var pending: Pending? = null
    @Volatile private var closed = false
    val isClosed get() = closed
    private val collector = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        transport.notifications.collect { notification ->
            decoder.feed(notification).forEach { frame ->
                mutableEvents.emit(frame)
                val waiting = pending
                if (waiting != null && frame[1].u8() == waiting.id) {
                    try {
                        if (waiting.accept(frame)) waiting.response.complete(frame)
                    } catch (error: RuntimeException) {
                        waiting.response.completeExceptionally(error)
                    }
                }
            }
        }
    }

    suspend fun request(id: Int, payload: ByteArray = byteArrayOf(), accept: (ByteArray) -> Boolean = { true }): ByteArray = lock.withLock {
        check(!closed) { "Connection closed" }
        val response = CompletableDeferred<ByteArray>()
        pending = Pending(id, response, accept)
        try {
            withTimeout(5000) {
                try {
                    transport.write(encoder.encode(id, payload))
                } catch (writeError: Exception) {
                    close(writeError); transport.abort(writeError); throw writeError
                }
                response.await()
            }
        } catch (error: TimeoutCancellationException) {
            // Responses are matched by ID in the official protocol, not by SN.
            // Invalidate the connection so a late ACK cannot confirm the next write.
            close(error); transport.abort(error); throw error
        } catch (error: CancellationException) {
            close(error); transport.abort(error); throw error
        } catch (error: Exception) {
            // Validation failures from accept() keep the session open for the next command.
            throw error
        } finally { pending = null }
    }

    suspend fun ack(id: Int, payload: ByteArray = byteArrayOf()) {
        val raw = request(id, payload)
        require(raw.size >= 3) { "Incomplete acknowledgment" }
        check(raw[2].u8() == 0) { "Controller rejected command $id (code ${raw[2].u8()})" }
    }

    /** Commands whose completion is reported exclusively through async events. */
    suspend fun send(id: Int, payload: ByteArray = byteArrayOf()) = lock.withLock {
        check(!closed) { "Connection closed" }
        transport.write(encoder.encode(id, payload))
    }

    fun close(cause: Throwable = IllegalStateException("Disconnected")) {
        closed = true
        pending?.response?.completeExceptionally(cause)
        collector.cancel()
        decoder.clear()
    }
}
