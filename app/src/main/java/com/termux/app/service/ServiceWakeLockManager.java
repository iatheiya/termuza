package com.termux.app.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;

import com.termux.app.TermuxService;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

public class ServiceWakeLockManager {

    private static final String LOG_TAG = "ServiceWakeLockManager";
    private final TermuxService mService;
    private PowerManager.WakeLock mWakeLock;
    private WifiManager.WifiLock mWifiLock;

    public ServiceWakeLockManager(TermuxService service) {
        this.mService = service;
    }

    @SuppressLint({"WakelockTimeout", "BatteryLife"})
    public void acquireWakeLock() {
        if (mWakeLock != null) {
            Logger.logDebug(LOG_TAG, "Ignoring acquiring WakeLocks since they are already held");
            return;
        }
        Logger.logDebug(LOG_TAG, "Acquiring WakeLocks");
        PowerManager pm = (PowerManager) mService.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TermuxConstants.TERMUX_APP_NAME.toLowerCase() + ":service-wakelock");
        mWakeLock.acquire();
        WifiManager wm = (WifiManager) mService.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, TermuxConstants.TERMUX_APP_NAME.toLowerCase());
        mWifiLock.acquire();
        if (!PermissionUtils.checkIfBatteryOptimizationsDisabled(mService)) {
            PermissionUtils.requestDisableBatteryOptimizations(mService);
        }
        mService.updateNotification();
        Logger.logDebug(LOG_TAG, "WakeLocks acquired successfully");
    }

    public void releaseWakeLock(boolean updateNotification) {
        if (mWakeLock == null && mWifiLock == null) {
            Logger.logDebug(LOG_TAG, "Ignoring releasing WakeLocks since none are already held");
            return;
        }
        Logger.logDebug(LOG_TAG, "Releasing WakeLocks");
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
        if (mWifiLock != null) {
            mWifiLock.release();
            mWifiLock = null;
        }
        if (updateNotification) mService.updateNotification();
        Logger.logDebug(LOG_TAG, "WakeLocks released successfully");
    }

    public boolean isWakeLockHeld() {
        return mWakeLock != null;
    }
}
