package com.example.iec_be


import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

fun main() {
    embeddedServer(Netty, port = 8080) {
        install(WebSockets)
        routing {
            webSocket("/chat/{channel}") {
                val channel = call.parameters["channel"] ?: error("Channel not provided")
                val sessionId = UUID.randomUUID().toString()
                val session = Session(sessionId, this)

                try {
                    // Add the session to the channel
                    sessions.computeIfAbsent(channel) { Collections.newSetFromMap(ConcurrentHashMap()) }.add(session)

                    // Notify all users in the channel about the new user
                    broadcast("User $sessionId joined the channel $channel", channel, session)             // Listen for incoming messages
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val text = frame.readText()
                                broadcast("User $sessionId: $text", channel, session)
                            }
                            else -> {
                                // Ignore other frame types
                            }
                        }
                    }
                } catch (e: ClosedReceiveChannelException) {
                    // Handle client disconnect
                } finally {
                    // Remove the session from the channel
                    sessions[channel]?.remove(session)
                    broadcast("User $sessionId left the channel $channel", channel, session )
                }
            }
        }
    }.start(wait = true)
}

data class Session(val id: String, val socket: WebSocketSession)

val sessions = ConcurrentHashMap<String, MutableSet<Session>>()

suspend fun broadcast(message: String, channel: String, current: Session?) {
    sessions[channel]?.forEach { session ->
        if(session != current){
            session.socket.send(Frame.Text(message))
        }
    }
}