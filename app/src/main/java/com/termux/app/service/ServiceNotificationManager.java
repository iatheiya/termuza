package com.termux.app.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.shared.logger.Logger;
import com.termux.shared.notification.NotificationUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;

public class ServiceNotificationManager {

    private static final String LOG_TAG = "ServiceNotificationManager";
    private final TermuxService mService;
    private ServiceWakeLockManager mWakeLockManager;

    public ServiceNotificationManager(TermuxService service) {
        this.mService = service;
    }

    public void setWakeLockManager(ServiceWakeLockManager wakeLockManager) {
        this.mWakeLockManager = wakeLockManager;
    }

    public void setupNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationUtils.setupNotificationChannel(mService, TermuxConstants.TERMUX_APP_NOTIFICATION_CHANNEL_ID, TermuxConstants.TERMUX_APP_NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
    }

    public Notification buildNotification() {
        Resources res = mService.getResources();
        Intent notificationIntent = TermuxActivity.newInstance(mService);
        PendingIntent contentIntent = PendingIntent.getActivity(mService, 0, notificationIntent, 0);
        int sessionCount = mService.getTermuxSessionsSize();
        int taskCount = mService.getTermuxTasksSize();
        String notificationText = sessionCount + " session" + (sessionCount == 1 ? "" : "s");
        if (taskCount > 0) {
            notificationText += ", " + taskCount + " task" + (taskCount == 1 ? "" : "s");
        }
        final boolean wakeLockHeld = mWakeLockManager != null && mWakeLockManager.isWakeLockHeld();
        if (wakeLockHeld) notificationText += " (wake lock held)";
        int priority = (wakeLockHeld) ? Notification.PRIORITY_HIGH : Notification.PRIORITY_LOW;
        Notification.Builder builder = NotificationUtils.geNotificationBuilder(mService, TermuxConstants.TERMUX_APP_NOTIFICATION_CHANNEL_ID, priority, TermuxConstants.TERMUX_APP_NAME, notificationText, null, contentIntent, null, NotificationUtils.NOTIFICATION_MODE_SILENT);
        if (builder == null) return null;
        builder.setShowWhen(false);
        builder.setSmallIcon(R.drawable.ic_service_notification);
        builder.setColor(0xFF607D8B);
        builder.setOngoing(true);
        Intent exitIntent = new Intent(mService, TermuxService.class).setAction(TERMUX_SERVICE.ACTION_STOP_SERVICE);
        builder.addAction(android.R.drawable.ic_delete, res.getString(R.string.notification_action_exit), PendingIntent.getService(mService, 0, exitIntent, 0));
        String newWakeAction = wakeLockHeld ? TERMUX_SERVICE.ACTION_WAKE_UNLOCK : TERMUX_SERVICE.ACTION_WAKE_LOCK;
        Intent toggleWakeLockIntent = new Intent(mService, TermuxService.class).setAction(newWakeAction);
        String actionTitle = res.getString(wakeLockHeld ? R.string.notification_action_wake_unlock : R.string.notification_action_wake_lock);
        int actionIcon = wakeLockHeld ? android.R.drawable.ic_lock_idle_lock : android.R.drawable.ic_lock_lock;
        builder.addAction(actionIcon, actionTitle, PendingIntent.getService(mService, 0, toggleWakeLockIntent, 0));
        return builder.build();
    }

    public synchronized void updateNotification() {
        if (mWakeLockManager == null) return;
        if (!mWakeLockManager.isWakeLockHeld() && mService.isTermuxSessionsEmpty() && mService.isTermuxTasksEmpty()) {
            mService.requestStopService();
        } else {
            ((NotificationManager) mService.getSystemService(Context.NOTIFICATION_SERVICE)).notify(TermuxConstants.TERMUX_APP_NOTIFICATION_ID, buildNotification());
        }
    }
}
