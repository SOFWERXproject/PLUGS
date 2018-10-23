package com.aftac.plugs;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;

import com.aftac.plugs.Queue.Queue;
import com.aftac.plugs.Queue.QueueCommand;

import org.json.JSONArray;

public class PlugsNotification {
    private static final String CHANNEL_ID   = "plugs_running";
    private static final String CHANNEL_NAME = "Running";

    private static NotificationManager notifier;
    private static NotificationChannel notificationChannel;
    private static NotificationCompat.Builder notificationBuilder;
    private static PendingIntent openAppIntent;
    private static PendingIntent stopIntent;
    private static int notificationId = 0;

    private int myId;
    private Notification notification;

    public PlugsNotification(Context context) {
        // Get the notification service
        if (notifier == null)
            notifier = (NotificationManager) context.getSystemService(Service.NOTIFICATION_SERVICE);

        // Create a notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationChannel == null) {
            notificationChannel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH);
            notificationChannel.setSound(null, null);
            notifier.createNotificationChannel(notificationChannel);
        }

        // Create intent for opening the app
        if (openAppIntent == null) {
            Intent intent = new Intent(context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            openAppIntent = PendingIntent.getActivity(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
        }

        // Create intent to stop the app
        if (stopIntent == null) {
            Intent intent = new Intent(context, Queue.class);
            QueueCommand command = new QueueCommand(Queue.COMMAND_TARGET_SELF,
                    Queue.COMMAND_CLASS_MISC, Queue.COMMAND_MISC_STOP,
                    new JSONArray());
            Bundle bundle = new Bundle();
            bundle.putByteArray("content", command.toBytes());
            intent.putExtras(bundle);
            stopIntent = PendingIntent.getService(context, 0, intent,
                                                  PendingIntent.FLAG_UPDATE_CURRENT);
        }

        // Configure the notification
        notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setAutoCancel(false)
                .setSmallIcon(R.mipmap.plugs_icon)
                .setContentTitle(context.getString(R.string.app_name))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setContentText(context.getText(R.string.notification_text))
                .setContentIntent(openAppIntent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)
            notificationBuilder.addAction(R.drawable.ic_stop, "STOP", stopIntent);

        // Build the notification
        notification = notificationBuilder.build();

        // Set the notifcation id
        myId = ++notificationId;
    }

    public Notification get() {
        return notification;
    }

    public int getId() {
        return myId;
    }

}
