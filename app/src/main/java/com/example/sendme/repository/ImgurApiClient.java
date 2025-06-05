package com.example.sendme.repository;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

// Cliente para subir imágenes a Imgur usando su API pública
public class ImgurApiClient {
    private static final String TAG = "ImgurApiClient";

    // Este es el Client-ID que se usa para autenticar con la API de Imgur (registro necesario en Imgur)
    private static final String IMGUR_CLIENT_ID = "b95c88d94e4276c";
    private static final String IMGUR_UPLOAD_URL = "https://api.imgur.com/3/image";

    // Instancia singleton para evitar múltiples clientes en memoria
    private static ImgurApiClient instance;

    private final OkHttpClient okHttpClient;
    private final Gson gson;

    // Constructor privado: se usa solo dentro del singleton
    private ImgurApiClient() {
        okHttpClient = new OkHttpClient();
        gson = new Gson();
    }

    // Patrón singleton para obtener la única instancia de esta clase
    public static synchronized ImgurApiClient getInstance() {
        if (instance == null) {
            instance = new ImgurApiClient();
        }
        return instance;
    }

    // Interfaz que define los callbacks para notificar si la subida fue exitosa o falló
    public interface UploadCallback {
        void onSuccess(String imageUrl);
        void onFailure(String error);
    }

    // Método principal para subir una imagen a Imgur
    public void uploadImage(Uri imageUri, ContentResolver contentResolver, UploadCallback callback) {
        byte[] imageBytes;

        // Leer la imagen desde el URI usando un InputStream
        try {
            InputStream inputStream = contentResolver.openInputStream(imageUri);
            if (inputStream == null) {
                Log.e(TAG, "Failed to open InputStream for URI: " + imageUri);
                callback.onFailure("Failed to read image data");
                return;
            }

            // Convertimos la imagen a un array de bytes
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }

            inputStream.close();
            imageBytes = byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            Log.e(TAG, "Failed to read image bytes: " + e.getMessage(), e);
            callback.onFailure("Failed to read image: " + e.getMessage());
            return;
        }

        // Construimos la petición POST con el archivo como parte del formulario
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                        "image",
                        imageUri.getLastPathSegment(),
                        RequestBody.create(MediaType.parse("image/*"), imageBytes)
                )
                .build();

        // Armamos la petición con cabecera de autenticación (Client-ID)
        Request request = new Request.Builder()
                .url(IMGUR_UPLOAD_URL)
                .post(requestBody)
                .addHeader("Authorization", "Client-ID " + IMGUR_CLIENT_ID)
                .build();

        // Enviamos la petición de forma asíncrona
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to upload image to Imgur: " + e.getMessage(), e);
                callback.onFailure("Failed to upload image: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                // Si la respuesta no fue exitosa, logueamos y notificamos el fallo
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No error body";
                    Log.e(TAG, "Imgur API error: " + response.code() + " - " + response.message() + " - " + errorBody);
                    callback.onFailure("Imgur API error: " + response.code() + " - " + response.message());
                    return;
                }

                // Leemos el cuerpo de la respuesta (el JSON con los datos)
                String responseBody = response.body().string();
                Log.d(TAG, "Imgur response: " + responseBody);

                // Extraemos la URL de la imagen desde la respuesta
                String imageUrl = parseImgurResponse(responseBody);
                if (imageUrl != null) {
                    callback.onSuccess(imageUrl);  // Éxito: devolvemos la URL al callback
                } else {
                    Log.e(TAG, "Failed to parse Imgur response: " + responseBody);
                    callback.onFailure("Failed to parse Imgur response");
                }
            }
        });
    }

    // Método auxiliar para interpretar el JSON devuelto por Imgur
    private String parseImgurResponse(String responseBody) {
        try {
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

            // Verificamos si la subida fue exitosa
            if (!jsonObject.get("success").getAsBoolean()) {
                Log.e(TAG, "Imgur upload failed: " + jsonObject.get("status").getAsString());
                return null;
            }

            // Extraemos el campo "link" donde está la URL de la imagen subida
            JsonObject data = jsonObject.getAsJsonObject("data");
            return data.get("link").getAsString();
        } catch (Exception e) {
            Log.e(TAG, "Error parsing Imgur response: " + e.getMessage(), e);
            return null;
        }
    }
}
