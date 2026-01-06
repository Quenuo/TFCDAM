package com.example.sendme.service;


import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.Log;


import androidx.core.app.NotificationCompat;

import com.example.sendme.MainActivity;
import com.example.sendme.R;
import com.example.sendme.repository.FirebaseManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;


public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFCMService";
    private static final String CHANNEL_ID = "sendme_channel";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "Mensaje FCM recibido desde: " + remoteMessage.getFrom());

        // Prioridad 1: Mensajes con "data" (funcionan siempre, incluso con app cerrada)
        if (!remoteMessage.getData().isEmpty()) {
            Log.d(TAG, "Mensaje con data payload: " + remoteMessage.getData());

            String title = remoteMessage.getData().get("title");
            String body = remoteMessage.getData().get("body");
            String chatId = remoteMessage.getData().get("chatId");
            String isGroupStr = remoteMessage.getData().get("isGroup");
            boolean isGroup = "true".equals(isGroupStr);

            // Si no viene título personalizado, usamos uno genérico
            if (title == null || title.isEmpty()) {
                String sender = remoteMessage.getData().get("senderUsername");
                title = sender != null ? sender : "Nuevo mensaje";
            }

            if (body == null || body.isEmpty()) {
                body = "Tienes un nuevo mensaje en SendMe";
            }

            sendNotification(title, body, chatId, isGroup);
            return; // Ya manejado
        }

        // Prioridad 2: Mensajes con "notification" payload (solo si app en foreground)
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Mensaje con notification payload: " + remoteMessage.getNotification().getBody());

            String title = remoteMessage.getNotification().getTitle();
            String body = remoteMessage.getNotification().getBody();

            String chatId = remoteMessage.getData().get("chatId");
            String isGroupStr = remoteMessage.getData().get("isGroup");
            boolean isGroup = "true".equals(isGroupStr);

            sendNotification(title, body, chatId, isGroup);
        }
    }

    private void sendNotification(String title, String body, String chatId, boolean isGroup) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Pasar datos para abrir el chat correcto
        if (chatId != null) {
            intent.putExtra("openChatId", chatId);
            intent.putExtra("openIsGroup", isGroup);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification) // Asegúrate de tener este icono (blanco transparente)
                .setContentTitle(title != null ? title : "SendMe")
                .setContentText(body)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Crear canal para Android 8.0+
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Mensajes de SendMe",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Notificaciones de nuevos mensajes");
        notificationManager.createNotificationChannel(channel);

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
        Log.d(TAG, "Notificación mostrada: " + title + " - " + body);
    }

    @Override
    public void onNewToken(String token) {
        Log.d(TAG, "Token FCM renovado: " + token);

        String uid = FirebaseManager.getInstance().getAuth().getCurrentUser() != null
                ? FirebaseManager.getInstance().getAuth().getCurrentUser().getUid()
                : null;

        if (uid != null) {
            FirebaseManager.getInstance().getFirestore()
                    .collection("users")
                    .document(uid)
                    .update("fcmToken", token)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM Token guardado en Firestore"))
                    .addOnFailureListener(e -> Log.e(TAG, "Error guardando FCM Token", e));
        }
    }
}