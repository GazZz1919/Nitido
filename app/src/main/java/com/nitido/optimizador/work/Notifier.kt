package com.nitido.optimizador.work

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nitido.optimizador.MainActivity
import com.nitido.optimizador.R
import com.nitido.optimizador.core.Perms

/** Notificaciones del mantenimiento automático y de las alertas. */
object Notifier {

    private const val CHANNEL_MAINTENANCE = "mantenimiento"
    private const val CHANNEL_ALERTS = "alertas"
    private const val ID_DONE = 1001
    private const val ID_LOW_STORAGE = 1002
    private const val ID_UNUSED = 1003

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MAINTENANCE, "Mantenimiento automático", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Resultado de las optimizaciones programadas" },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Alertas del dispositivo", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Avisos de poco espacio y de apps sin usar" },
        )
    }

    fun maintenanceDone(context: Context, text: String) =
        show(context, CHANNEL_MAINTENANCE, ID_DONE, "Mantenimiento completado", text)

    fun lowStorage(context: Context, text: String) =
        show(context, CHANNEL_ALERTS, ID_LOW_STORAGE, "Queda poco espacio", text)

    fun unusedApps(context: Context, text: String) =
        show(context, CHANNEL_ALERTS, ID_UNUSED, "Apps que no usas", text)

    @SuppressLint("MissingPermission") // Se comprueba con Perms.hasNotifications().
    private fun show(context: Context, channel: String, id: Int, title: String, text: String) {
        if (!Perms.hasNotifications(context)) return
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_nitido)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // El usuario retiró el permiso de notificaciones.
        }
    }
}
