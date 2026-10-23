package dev.helmvox

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import java.text.Normalizer

/** Un mensaje recibido (WhatsApp, SMS…) con lo necesario para responderlo desde su notificación. */
data class IncomingMessage(
    val id: Long,
    val app: String,
    val appLabel: String,
    val sender: String,
    val text: String,
    val replyIntent: PendingIntent?,
    val remoteInput: RemoteInput?,
) {
    val canReply get() = replyIntent != null && remoteInput != null
}

/** Mensajes recientes en memoria (se pierden al reiniciar el móvil; no se guardan en disco). */
object MessageStore {

    private const val TAG = "HelmVox"
    private const val MAX = 30
    private val messages = ArrayDeque<IncomingMessage>()
    private val unread = LinkedHashSet<Long>()
    private var nextId = 1L

    @Synchronized
    fun add(app: String, appLabel: String, sender: String, text: String, n: Notification): IncomingMessage? {
        // WhatsApp vuelve a publicar la conversación entera al llegar otro mensaje: no repetir.
        if (messages.any { it.app == app && it.sender == sender && it.text == text }) return null
        val action = n.actions?.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
        val msg = IncomingMessage(
            id = nextId++,
            app = app,
            appLabel = appLabel,
            sender = sender,
            text = text,
            replyIntent = action?.actionIntent,
            remoteInput = action?.remoteInputs?.firstOrNull { it.allowFreeFormInput },
        )
        messages.addLast(msg)
        unread += msg.id
        while (messages.size > MAX) unread -= messages.removeFirst().id
        return msg
    }

    @Synchronized
    fun get(id: Long) = messages.firstOrNull { it.id == id }

    @Synchronized
    fun markRead(id: Long) {
        unread -= id
    }

    /** Mensajes sin leer, del más antiguo al más nuevo. */
    @Synchronized
    fun unread(): List<IncomingMessage> = messages.filter { it.id in unread }

    /** La conversación más reciente con [name] que se pueda responder (para enviarle cosas por WhatsApp). */
    @Synchronized
    fun latestReplyableFrom(name: String): IncomingMessage? {
        val wanted = fold(name)
        if (wanted.isEmpty()) return null
        return messages.lastOrNull { it.canReply && fold(it.sender).let { s -> s == wanted || s.startsWith(wanted) } }
    }

    /** Responde con [text] usando la acción "Responder" de la notificación original. */
    fun reply(ctx: Context, msg: IncomingMessage, text: String): Boolean {
        val pi = msg.replyIntent ?: return false
        val input = msg.remoteInput ?: return false
        val fill = Intent()
        RemoteInput.addResultsToIntent(arrayOf(input), fill, Bundle().apply { putCharSequence(input.resultKey, text) })
        return try {
            pi.send(ctx, 0, fill)
            true
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "la notificación ya no admite respuesta", e)
            false
        }
    }

    private fun fold(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").lowercase().trim()
}
