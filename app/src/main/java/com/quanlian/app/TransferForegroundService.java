package com.quanlian.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class TransferForegroundService extends Service {
    private static final String CHANNEL_ID = "quanlian_transfer";
    private static final int NOTIFICATION_ID = 2101;
    private static final String ACTION_START = "com.quanlian.app.action.TRANSFER_START";
    private static final String ACTION_UPDATE = "com.quanlian.app.action.TRANSFER_UPDATE";
    private static final String ACTION_STOP = "com.quanlian.app.action.TRANSFER_STOP";
    private static final String EXTRA_TEXT = "notification_text";
    private PowerManager.WakeLock wakeLock;
    private String currentText = "正在进行文件传输…";

    public static void start(Context context, String text) {
        Intent intent = new Intent(context, TransferForegroundService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_TEXT, text);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }

    public static void update(Context context, String text) {
        Intent intent = new Intent(context, TransferForegroundService.class);
        intent.setAction(ACTION_UPDATE);
        intent.putExtra(EXTRA_TEXT, text);
        context.startService(intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, TransferForegroundService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "QuanLian:FileTransfer");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(6 * 60 * 60 * 1000L);
            }
        } catch (Exception ignored) { }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (intent != null && intent.hasExtra(EXTRA_TEXT)) currentText = intent.getStringExtra(EXTRA_TEXT);
        if (ACTION_STOP.equals(action)) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        Notification notification = buildNotification(currentText);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        if (ACTION_UPDATE.equals(action)) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(NOTIFICATION_ID, notification);
        }
        return START_NOT_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "文件传输", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("显示小李全联正在进行的文件传输状态");
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("小李全联 · 文件传输")
                .setContentText(text == null ? "正在进行文件传输…" : text)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    @Override public void onDestroy() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
