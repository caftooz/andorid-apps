package ru.diary.training.rest

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.diary.training.MainActivity
import ru.diary.training.R
import ru.diary.training.ui.RestController

/**
 * Таймер отдыха между подходами. Работает как служба переднего плана с частичной блокировкой сна,
 * поэтому срабатывает при выключенном экране; запасной будильник `AlarmManager` дублирует окончание,
 * если систему принудительно выгрузила службу. По окончании: уведомление со звуком и вибрация.
 */
class RestTimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                RestNotifier.cancelAll(this)
                shutDown()
            }
            else -> {
                val endAt = intent?.getLongExtra(EXTRA_END, 0L) ?: 0L
                val label = intent?.getStringExtra(EXTRA_LABEL).orEmpty()
                if (endAt <= 0L) { shutDown(); return START_NOT_STICKY }
                RestNotifier.cancelDone(this)
                startForegroundCompat(RestNotifier.runningNotification(this, endAt, label))
                holdWakeLock(endAt)
                RestNotifier.scheduleBackupAlarm(this, endAt, label)
                job?.cancel()
                job = scope.launch {
                    delay(maxOf(0L, endAt - System.currentTimeMillis()))
                    RestNotifier.notifyDone(this@RestTimerService, label)
                    RestNotifier.cancelBackupAlarm(this@RestTimerService)
                    shutDown()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(RestNotifier.ID_RUNNING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(RestNotifier.ID_RUNNING, n)
    }

    private fun holdWakeLock(endAt: Long) {
        wakeLock?.let { if (it.isHeld) it.release() }
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "training:rest").apply {
            setReferenceCounted(false)
            acquire(maxOf(1_000L, endAt - System.currentTimeMillis()) + 15_000L)
        }
    }

    private fun shutDown() {
        job?.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "ru.diary.training.rest.START"
        const val ACTION_STOP = "ru.diary.training.rest.STOP"
        const val EXTRA_END = "end"
        const val EXTRA_LABEL = "label"
    }
}

/** Принимает запасной будильник и сообщает об окончании отдыха, если служба не успела. */
class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RestNotifier.notifyDone(context, intent.getStringExtra(RestTimerService.EXTRA_LABEL).orEmpty())
        context.stopService(Intent(context, RestTimerService::class.java))
    }
}

internal object RestNotifier {
    const val ID_RUNNING = 4101
    const val ID_DONE = 4102
    private const val CH_RUNNING = "rest_running"
    private const val CH_DONE = "rest_done"
    private val PATTERN = longArrayOf(0, 300, 150, 300, 150, 500)

    private fun ensureChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH_RUNNING) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_RUNNING, "Отдых между подходами", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Идёт отдых между подходами"
                    setSound(null, null); enableVibration(false)
                },
            )
        }
        if (nm.getNotificationChannel(CH_DONE) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_DONE, "Отдых закончен", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Сигнал об окончании отдыха"
                    enableVibration(true); vibrationPattern = PATTERN
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                    )
                },
            )
        }
    }

    private fun openApp(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun runningNotification(c: Context, endAt: Long, label: String): Notification {
        ensureChannels(c)
        val stop = PendingIntent.getService(
            c, 1, Intent(c, RestTimerService::class.java).setAction(RestTimerService.ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(c, CH_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_rest)
            .setContentTitle("Отдых")
            .setContentText(label.ifEmpty { "Следующий подход, когда будете готовы" })
            .setWhen(endAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openApp(c))
            .addAction(0, "Пропустить", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Уведомление «Отдых закончен» плюс вибрация и звук напрямую: так сигнал есть, даже если уведомления запрещены. */
    fun notifyDone(c: Context, label: String) {
        ensureChannels(c)
        val nm = c.getSystemService(NotificationManager::class.java)
        val n = NotificationCompat.Builder(c, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_rest)
            .setContentTitle("Отдых закончен")
            .setContentText(label.ifEmpty { "Пора на следующий подход" })
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true).setTimeoutAfter(90_000L).setContentIntent(openApp(c))
            .setVibrate(PATTERN).setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .build()
        val notificationsOn = nm.areNotificationsEnabled() && nm.getNotificationChannel(CH_DONE)?.importance != NotificationManager.IMPORTANCE_NONE
        runCatching { nm.notify(ID_DONE, n) }
        if (!notificationsOn) {
            vibrate(c)
            runCatching { RingtoneManager.getRingtone(c, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play() }
        }
    }

    private fun vibrate(c: Context) {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) c.getSystemService(VibratorManager::class.java)?.defaultVibrator else @Suppress("DEPRECATION") c.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        v?.vibrate(VibrationEffect.createWaveform(PATTERN, -1))
    }

    @android.annotation.SuppressLint("MissingPermission") // точный будильник только после проверки canScheduleExactAlarms
    fun scheduleBackupAlarm(c: Context, endAt: Long, label: String) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(c, label)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pi)
    }

    fun cancelBackupAlarm(c: Context) {
        c.getSystemService(AlarmManager::class.java).cancel(alarmIntent(c, ""))
    }

    private fun alarmIntent(c: Context, label: String): PendingIntent = PendingIntent.getBroadcast(
        c, 2, Intent(c, RestAlarmReceiver::class.java).putExtra(RestTimerService.EXTRA_LABEL, label),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun cancelDone(c: Context) { c.getSystemService(NotificationManager::class.java).cancel(ID_DONE) }

    fun cancelAll(c: Context) {
        cancelBackupAlarm(c)
        cancelDone(c)
    }
}

/** Реальный управляющий таймером: запускает службу, а при отмене гасит уведомления и будильник. */
class AndroidRestController(private val context: Context) : RestController {
    override fun start(endAt: Long, label: String) {
        val i = Intent(context, RestTimerService::class.java).setAction(RestTimerService.ACTION_START)
            .putExtra(RestTimerService.EXTRA_END, endAt).putExtra(RestTimerService.EXTRA_LABEL, label)
        runCatching { ContextCompat.startForegroundService(context, i) }
    }

    override fun cancel() {
        RestNotifier.cancelAll(context)
        context.stopService(Intent(context, RestTimerService::class.java))
    }
}
