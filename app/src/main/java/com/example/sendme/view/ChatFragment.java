package com.example.sendme.view;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.sendme.R;
import com.example.sendme.data.model.Message;
import com.example.sendme.data.model.User;
import com.example.sendme.databinding.FragmentChatBinding;
import com.example.sendme.repository.FirebaseManager;
import com.example.sendme.repository.ImgurApiClient;
import com.example.sendme.ui.MessageAdapter;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fragmento principal para la interfaz de chat.
 * Gestiona el envío/recepción de mensajes, la visualización y la interacción del usuario.
 */
public class ChatFragment extends Fragment {

    // View Binding para acceder a los elementos de la interfaz de forma segura.
    private FragmentChatBinding binding;
    // Adaptador para el RecyclerView que muestra los mensajes.
    private MessageAdapter adapter;
    // ID único del chat actual.
    private String chatId;
    // Objeto User que representa al otro participante en el chat.
    private User otherUser;
    // Número de teléfono del usuario actual autenticado.
    private String currentUserPhone;
    // Referencia a la ubicación de los mensajes de este chat en la base de datos de Firebase.
    private DatabaseReference messagesRef;
    // Listener para eventos de adición, cambio o eliminación de mensajes en Firebase.
    private ChildEventListener messageListener;
    // Etiqueta para mensajes de log, útil para depuración.
    private static final String TAG = "ChatFragment";
    // Lista para mantener un registro de los IDs de mensajes ya mostrados para evitar duplicados.
    private List<String> displayedMessageIds = new ArrayList<>();
    // Launcher para solicitar permisos de Android.
    private ActivityResultLauncher<String> requestPermissionLauncher;
    // Launcher para seleccionar imágenes de la galería.
    private ActivityResultLauncher<String> pickImageLauncher;
    // Controlador de navegación para la gestión de fragmentos.
    private NavController navController;

    /**
     * Constructor público vacío requerido por Android.
     */
    public ChatFragment() {
        // Constructor vacío.
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Inicializa el NavController.
        navController = NavHostFragment.findNavController(this);

        // Recupera argumentos pasados al fragmento (ID del chat y datos del otro usuario).
        if (getArguments() != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                otherUser = getArguments().getParcelable("user", User.class);
            } else {
                otherUser = getArguments().getParcelable("user"); // Deprecado, pero necesario para versiones antiguas.
            }
            chatId = getArguments().getString("chatId");
        }

        // Valida que los argumentos esenciales no sean nulos. Si lo son, se registra un error y se navega hacia atrás.
        if (otherUser == null || chatId == null) {
            Log.e(TAG, "Argumentos 'user' o 'chatId' nulos pasados a ChatFragment. Volviendo atrás.");
            // Asegura que el fragmento está añadido y tiene un destino válido antes de intentar navegar.
            if (isAdded() && NavHostFragment.findNavController(this).getCurrentDestination() != null) {
                NavHostFragment.findNavController(this).popBackStack();
            }
            return;
        }

        // Obtiene el número de teléfono del usuario actual. Si no está autenticado, se registra un error.
        currentUserPhone = FirebaseManager.getInstance().getAuth().getCurrentUser() != null
                ? FirebaseManager.getInstance().getAuth().getCurrentUser().getPhoneNumber()
                : null;

        // Si el teléfono del usuario actual es nulo, indica un problema de autenticación y se navega hacia atrás.
        if (currentUserPhone == null) {
            Log.e(TAG, "El teléfono del usuario actual es nulo. ¿Usuario no autenticado?");
            if (isAdded() && NavHostFragment.findNavController(this).getCurrentDestination() != null) {
                NavHostFragment.findNavController(this).popBackStack();
            }
            return;
        }

        // Inicializa la referencia a la base de datos para los mensajes del chat.
        messagesRef = FirebaseManager.getInstance().getDatabase().getReference("chats").child(chatId).child("messages");
        Log.d(TAG, "ChatFragment onCreate: messagesRef inicializado con chatId: " + chatId);

        // Inicializa el launcher para solicitar permisos de lectura de imágenes.
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                Log.d(TAG, "Permiso concedido, abriendo galería");
                openGallery();
            } else {
                // Si el permiso es denegado permanentemente, se dirige al usuario a la configuración de la app.
                if (!ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.READ_MEDIA_IMAGES)) {
                    Log.w(TAG, "Permiso de almacenamiento denegado permanentemente");
                    Toast.makeText(requireContext(), getString(R.string.toast_permission_denied_permanently), Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", requireActivity().getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                } else {
                    Log.w(TAG, "Permiso de almacenamiento denegado");
                    Toast.makeText(requireContext(), getString(R.string.toast_permission_denied), Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Inicializa el launcher para seleccionar imágenes de la galería.
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                Log.d(TAG, "Imagen seleccionada con URI: " + uri);
                uploadImageToImgur(uri);
            } else {
                Log.w(TAG, "No se seleccionó ninguna imagen");
                Toast.makeText(requireContext(), getString(R.string.toast_no_image_selected), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Infla el layout del fragmento usando View Binding.
        binding = FragmentChatBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated");

        // Realiza una verificación final de la integridad de los datos esenciales.
        if (binding == null || otherUser == null || chatId == null || currentUserPhone == null) {
            Log.e(TAG, "ChatFragment onViewCreated: Faltan datos esenciales (binding, otherUser, chatId, currentUserPhone).");
            if (isAdded() && NavHostFragment.findNavController(this).getCurrentDestination() != null) {
                NavHostFragment.findNavController(this).popBackStack();
            }
            return;
        }

        // Configura el OnClickListener para el botón de retroceso en la barra superior.
        binding.backButton.setOnClickListener(v -> {
            Log.d(TAG, "Volviendo a la lista de chats");
            if (navController != null) {
                navController.navigateUp();
            }
        });

        // Establece el nombre del contacto y configura el OnClickListener para ver el perfil del otro usuario.
        binding.contactName.setText(otherUser.getUsername());
        binding.contactName.setOnClickListener(v -> {
            UserDetailFragment userDetailFragment = UserDetailFragment.newInstance(otherUser);
            if (isAdded() && NavHostFragment.findNavController(this).getCurrentDestination() != null) {
                NavHostFragment.findNavController(this).navigate(R.id.action_chatFragment_to_userDetailFragment, userDetailFragment.getArguments());
            }
        });

        // Carga la imagen de perfil del otro usuario usando Glide, con una imagen por defecto en caso de error o ausencia.
        String imageUrl = otherUser.getImageUrl();
        Glide.with(this)
                .load(imageUrl != null && !imageUrl.isEmpty() ? Uri.parse(imageUrl) : R.drawable.default_profile)
                .error(R.drawable.default_profile)
                .circleCrop()
                .into(binding.profileImage);

        // Configura el RecyclerView con un LinearLayoutManager que apila elementos desde el final.
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        binding.messagesRecyclerView.setLayoutManager(layoutManager);
        // Inicializa el MessageAdapter con el teléfono del usuario actual y el NavController.
        adapter = new MessageAdapter(currentUserPhone, NavHostFragment.findNavController(this));
        binding.messagesRecyclerView.setAdapter(adapter);

        // Reinicia el contador de mensajes no leídos al entrar al chat.
        resetUnreadCount();
        // Carga los mensajes existentes y comienza a escuchar nuevos mensajes.
        loadAllMessages();
        listenForMessages();

        // Configura el OnClickListener para el botón de enviar mensaje de texto.
        binding.sendIcon.setOnClickListener(v -> {
            String messageContent = binding.messageInput.getText().toString().trim();
            if (!messageContent.isEmpty()) {
                sendMessage(messageContent, null); // Envía el mensaje de texto.
                binding.messageInput.setText(""); // Limpia el campo de entrada.
            }
        });

        // Configura el OnClickListener para el botón de adjuntar (galería).
        binding.attachIcon.setOnClickListener(v -> {
            Log.d(TAG, "Icono de adjuntar clickeado, abriendo galería");
            openGallery(); // Abre la galería para seleccionar una imagen.
        });
    }

    /**
     * Gestiona la lógica para abrir la galería, incluyendo la solicitud de permisos de almacenamiento.
     * Soporta diferentes permisos para Android 13+ y versiones anteriores.
     */
    private void openGallery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Solicitando permiso READ_MEDIA_IMAGES");
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
            } else {
                Log.d(TAG, "Permiso READ_MEDIA_IMAGES ya concedido, lanzando selector de imagen");
                pickImageLauncher.launch("image/*");
            }
        } else { // Android 12 y anteriores
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Solicitando permiso READ_EXTERNAL_STORAGE");
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            } else {
                Log.d(TAG, "Permiso READ_EXTERNAL_STORAGE ya concedido, lanzando selector de imagen");
                pickImageLauncher.launch("image/*");
            }
        }
    }

    /**
     * Reinicia el contador de mensajes no leídos para el usuario actual en el chat.
     */
    private void resetUnreadCount() {
        if (currentUserPhone != null && chatId != null) {
            DatabaseReference chatRef = FirebaseManager.getInstance().getDatabase().getReference("chats").child(chatId);
            chatRef.child("unreadCount").child(currentUserPhone).setValue(0)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Contador de mensajes no leídos reseteado para " + currentUserPhone + " en el chat " + chatId))
                    .addOnFailureListener(e -> Log.e(TAG, "Error al resetear el contador de mensajes no leídos: " + e.getMessage()));
        } else {
            Log.w(TAG, "No se puede resetear el contador: currentUserPhone o chatId son nulos.");
        }
    }

    /**
     * Sube una imagen a Imgur y, si tiene éxito, envía un mensaje con la URL de la imagen.
     *
     * @param imageUri URI de la imagen a subir.
     */
    private void uploadImageToImgur(Uri imageUri) {
        Log.d(TAG, "Intentando subir imagen con URI: " + imageUri);
        ImgurApiClient.getInstance().uploadImage(imageUri, requireContext().getContentResolver(), new ImgurApiClient.UploadCallback() {
            @Override
            public void onSuccess(String imageUrl) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Log.d(TAG, "Imagen subida con éxito, URL: " + imageUrl);
                    Toast.makeText(requireContext(), getString(R.string.toast_image_uploaded_success), Toast.LENGTH_SHORT).show();
                    sendMessage(null, imageUrl); // Envía un mensaje con la URL de la imagen.
                });
            }

            @Override
            public void onFailure(String error) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Log.e(TAG, "Fallo al subir la imagen: " + error);
                    Toast.makeText(requireContext(), getString(R.string.toast_image_load_error) + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Envía un mensaje (texto o imagen) al chat y lo actualiza en la UI y en Firebase.
     *
     * @param content Contenido del mensaje de texto (puede ser nulo si es una imagen).
     * @param imageUrl URL de la imagen (puede ser nulo si es un mensaje de texto).
     */
    private void sendMessage(String content, String imageUrl) {
        if (messagesRef == null) {
            Log.e(TAG, "sendMessage: messagesRef es nulo. No se puede enviar el mensaje.");
            Toast.makeText(requireContext(), getString(R.string.error_chat_not_initialized), Toast.LENGTH_SHORT).show();
            return;
        }

        Message message = new Message(currentUserPhone, content, imageUrl, System.currentTimeMillis());
        String messageId = messagesRef.push().getKey(); // Genera un ID único para el mensaje.
        message.setId(messageId);

        if (messageId != null) {
            // Añade el mensaje localmente al adaptador del RecyclerView para una actualización instantánea de la UI.
            if (binding != null && isAdded()) {
                adapter.addMessage(message);
                displayedMessageIds.add(message.getId());
                binding.messagesRecyclerView.scrollToPosition(adapter.getItemCount() - 1);
                Log.d(TAG, "Mensaje añadido a la UI localmente: " + (content != null ? content : "Mensaje de imagen, URL: " + imageUrl));
            } else {
                Log.w(TAG, "Binding o fragmento desasociado, no se puede actualizar la UI localmente.");
            }

            // Guarda el mensaje en Firebase.
            messagesRef.child(messageId).setValue(message)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Mensaje enviado con éxito: " + messageId);
                        // Actualiza los metadatos del chat (último mensaje, timestamp, contador de no leídos).
                        if (otherUser != null) {
                            updateChatMetadata(content != null ? content : "Imagen");
                        } else {
                            Log.w(TAG, "otherUser es nulo, no se pudo actualizar el metadata del chat.");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Fallo al enviar el mensaje: " + e.getMessage());
                        Toast.makeText(requireContext(), "Error al enviar el mensaje: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        } else {
            Log.e(TAG, "Fallo al generar messageId");
        }
    }

    /**
     * Actualiza el último mensaje, su timestamp y el contador de mensajes no leídos en los metadatos del chat en Firebase.
     *
     * @param lastMessage El contenido del último mensaje enviado.
     */
    private void updateChatMetadata(String lastMessage) {
        if (chatId == null || otherUser == null) {
            Log.e(TAG, "updateChatMetadata: chatId o otherUser son nulos. No se puede actualizar el metadata.");
            return;
        }
        DatabaseReference chatRef = FirebaseManager.getInstance().getDatabase().getReference("chats").child(chatId);
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastMessage", lastMessage);
        updates.put("lastMessageTimestamp", System.currentTimeMillis());

        // Obtiene el contador actual de mensajes no leídos del otro usuario para incrementarlo.
        chatRef.child("unreadCount").child(otherUser.getPhone()).get()
                .addOnSuccessListener(snapshot -> {
                    if (binding == null || !isAdded()) {
                        Log.w(TAG, "updateChatMetadata: Fragmento desasociado o binding nulo en success. No se puede actualizar el metadata.");
                        return;
                    }
                    Integer currentCount = snapshot.getValue(Integer.class);
                    if (currentCount == null) currentCount = 0;
                    updates.put("unreadCount/" + otherUser.getPhone(), currentCount + 1); // Incrementa para el otro usuario.
                    updates.put("unreadCount/" + currentUserPhone, 0); // Reinicia para el usuario actual.

                    // Realiza la actualización de los metadatos.
                    chatRef.updateChildren(updates)
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "Metadata del chat actualizado"))
                            .addOnFailureListener(e -> Log.e(TAG, "Fallo al actualizar el metadata del chat: " + e.getMessage()));
                })
                .addOnFailureListener(e -> Log.e(TAG, "Fallo al obtener el contador de no leídos: " + e.getMessage()));
    }

    /**
     * Carga todos los mensajes existentes del chat desde Firebase.
     */
    private void loadAllMessages() {
        if (messagesRef == null) {
            Log.e(TAG, "loadAllMessages: messagesRef es nulo. No se pueden cargar los mensajes.");
            return;
        }

        messagesRef.orderByChild("timestamp").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (binding == null || !isAdded()) {
                    Log.w(TAG, "loadAllMessages onDataChange: Fragmento desasociado o binding nulo. No se pueden actualizar los mensajes.");
                    return;
                }

                List<Message> allMessages = new ArrayList<>();
                // Itera sobre los mensajes en la instantánea de datos y los añade a la lista.
                for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                    Message message = messageSnapshot.getValue(Message.class);
                    if (message != null && message.getId() != null) {
                        allMessages.add(message);
                        displayedMessageIds.add(message.getId()); // Registra los IDs de los mensajes cargados.
                    }
                }
                adapter.setMessages(allMessages); // Actualiza el adaptador con los mensajes cargados.
                binding.messagesRecyclerView.scrollToPosition(adapter.getItemCount() - 1); // Desplaza hasta el final de la conversación.
                Log.d(TAG, "Cargados " + allMessages.size() + " mensajes existentes");
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Error cargando mensajes existentes: " + error.getMessage());
            }
        });
    }

    /**
     * Configura un ChildEventListener para escuchar nuevos mensajes en tiempo real.
     */
    private void listenForMessages() {
        if (messagesRef == null) {
            Log.e(TAG, "listenForMessages: messagesRef es nulo. No se puede escuchar nuevos mensajes.");
            return;
        }

        messageListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                if (binding == null || !isAdded()) {
                    Log.w(TAG, "onChildAdded: Fragmento desasociado o binding nulo. No se pueden añadir nuevos mensajes.");
                    return;
                }

                Message message = snapshot.getValue(Message.class);
                if (message != null && message.getId() != null) {
                    // Si el mensaje es de otro usuario y no ha sido mostrado, se añade a la UI y se reinicia el contador de no leídos.
                    if (!message.getSender().equals(currentUserPhone) && !displayedMessageIds.contains(message.getId())) {
                        adapter.addMessage(message);
                        displayedMessageIds.add(message.getId());
                        binding.messagesRecyclerView.scrollToPosition(adapter.getItemCount() - 1);
                        Log.d(TAG, "Nuevo mensaje recibido de otro usuario: " + (message.getContent() != null ? message.getContent() : "Mensaje de imagen, URL: " + message.getImageUrl()));
                        resetUnreadCount();
                    }
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Error escuchando mensajes: " + error.getMessage());
            }
        };
        // Adjunta el listener a la referencia de mensajes, ordenando por timestamp.
        messagesRef.orderByChild("timestamp").addChildEventListener(messageListener);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView: Desasociando listener y limpiando binding.");
        // Remueve el listener de Firebase para evitar fugas de memoria.
        if (messagesRef != null && messageListener != null) {
            messagesRef.removeEventListener(messageListener);
        }
        binding = null; // Libera la referencia al binding.
        displayedMessageIds.clear(); // Limpia la lista de IDs de mensajes.
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        // Aquí se pueden liberar otros recursos si es necesario.
    }
}