package com.example.sendme.view;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.sendme.AuthActivity;
import com.example.sendme.R;
import com.example.sendme.data.model.Chat;
import com.example.sendme.data.model.User;
import com.example.sendme.databinding.FragmentChatListBinding;
import com.example.sendme.repository.FirebaseManager;
import com.example.sendme.ui.ChatAdapter;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragmento que muestra la lista de chats activos del usuario.
 * Incluye funcionalidad para iniciar nuevos chats, gestionar el perfil del usuario a través de un drawer
 * y cerrar sesión.
 */
public class ChatListFragment extends Fragment {
    // Referencia al binding del layout para acceder a las vistas.
    private FragmentChatListBinding binding;
    // Adaptador para el RecyclerView que muestra la lista de chats.
    private ChatAdapter adapter;
    // Layout que permite un menú deslizable (drawer).
    private DrawerLayout drawerLayout;
    // Controlador de navegación para transiciones entre destinos.
    private NavController navController;
    // Referencia al documento del usuario actual en Firestore.
    private DocumentReference userRef;
    // Listener de Firestore para observar cambios en el documento del usuario.
    private ListenerRegistration userListener;
    // Etiqueta para logs de depuración.
    private static final String TAG = "ChatListFragment";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Infla el layout del fragmento.
        binding = FragmentChatListBinding.inflate(inflater, container, false);
        Log.d(TAG, "onCreateView: binding inflado");
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated");

        // Inicializa el NavController, DrawerLayout y el ChatAdapter.
        navController = NavHostFragment.findNavController(this);
        drawerLayout = binding.drawerLayout;
        adapter = new ChatAdapter(navController);

        // Configura el RecyclerView con un LinearLayoutManager y asigna el adaptador.
        binding.chatRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.chatRecyclerView.setAdapter(adapter);

        // Configura el botón de acción flotante (FAB) para navegar a NewChatFragment.
        binding.fabNewChat.setOnClickListener(v -> {
            Log.d(TAG, "FAB clickeado, navegando a NewChatFragment");
            // Se verifica que el fragmento esté adjunto antes de navegar.
            if (isAdded() && navController != null) {
                navController.navigate(R.id.action_chatListFragment_to_newChatFragment);
            }
        });

        // Obtiene referencias a las vistas dentro del menú deslizable.
        ImageView profileImageDrawer = binding.profileImageDrawer;
        TextView logoutButton = binding.logoutButton;

        // Establece un listener en Firestore para obtener y actualizar la información del usuario en el drawer.
        String currentPhone = FirebaseManager.getInstance().getAuth().getCurrentUser() != null
                ? FirebaseManager.getInstance().getAuth().getCurrentUser().getPhoneNumber() : null;
        if (currentPhone != null) {
            userRef = FirebaseManager.getInstance().getFirestore().collection("users").document(currentPhone);
            userListener = userRef.addSnapshotListener((snapshot, error) -> {
                if (error != null) {
                    Log.e(TAG, "Error escuchando datos de usuario: " + error.getMessage());
                    return;
                }

                // Valida que el fragmento esté adjunto y el binding no sea nulo antes de actualizar la UI.
                if (binding == null || !isAdded()) {
                    Log.w(TAG, "El fragmento está desasociado o el binding es nulo, no se puede actualizar el perfil de usuario en el drawer.");
                    return;
                }

                // Si el snapshot existe, se mapea a un objeto User y se actualiza la UI del drawer.
                if (snapshot != null && snapshot.exists()) {
                    User user = snapshot.toObject(User.class);
                    if (user != null) {
                        String imageUrl = user.getImageUrl() != null && !user.getImageUrl().isEmpty() ? user.getImageUrl() : null;
                        Glide.with(requireContext())
                                .load(imageUrl != null ? imageUrl : R.drawable.default_profile)
                                .error(R.drawable.default_profile)
                                .circleCrop()
                                .into(profileImageDrawer);
                        if (user.getUsername() != null) {
                            binding.usernameDrawer.setText(user.getUsername());
                        }
                    }
                }
            });
        }

        // Configura el botón de editar perfil en el drawer para navegar a EditProfileFragment.
        binding.editProfileButton.setOnClickListener(v -> {
            if (isAdded()) {
                drawerLayout.closeDrawer(GravityCompat.START); // Cierra el drawer antes de navegar.
                if (navController != null) {
                    navController.navigate(R.id.action_chatListFragment_to_editProfileFragment);
                }
            }
        });

        // Configura el botón de cerrar sesión para desautenticar al usuario y redirigirlo a AuthActivity.
        logoutButton.setOnClickListener(v -> {
            if (isAdded()) {
                // Elimina el listener de Firestore para evitar actualizaciones después de cerrar sesión.
                if (userListener != null) {
                    userListener.remove();
                    userListener = null;
                }
                FirebaseManager.getInstance().getAuth().signOut(); // Cierra la sesión de Firebase.
                // Inicia AuthActivity y limpia la pila de actividades para que el usuario no pueda volver atrás.
                Intent intent = new Intent(requireContext(), AuthActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                requireActivity().finish(); // Finaliza la actividad actual.
            }
        });

        // Inicia el proceso de carga de usuarios y luego de los chats del usuario.
        loadUsersAndThenChats();
    }

    /**
     * Carga todos los usuarios desde Firestore y luego procede a cargar los chats del usuario actual.
     */
    private void loadUsersAndThenChats() {
        FirebaseManager.getInstance().getFirestore()
                .collection("users")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    // Valida que el fragmento esté adjunto y el binding no sea nulo antes de procesar los datos.
                    if (binding == null || !isAdded()) {
                        Log.w(TAG, "El fragmento está desasociado o el binding es nulo, no se pueden cargar los usuarios.");
                        return;
                    }

                    List<User> users = new ArrayList<>();
                    for (var doc : queryDocumentSnapshots) {
                        users.add(doc.toObject(User.class));
                    }
                    Log.d(TAG, "Usuarios cargados: " + users.size());
                    loadUserChats(users); // Procede a cargar los chats del usuario con la lista de usuarios.
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error cargando usuarios: " + e.getMessage());
                });
    }

    /**
     * Carga los IDs de los chats asociados al usuario actual desde Realtime Database.
     *
     * @param users Lista de todos los usuarios registrados en el sistema.
     */
    private void loadUserChats(List<User> users) {
        String currentUserPhone = FirebaseManager.getInstance().getAuth().getCurrentUser() != null
                ? FirebaseManager.getInstance().getAuth().getCurrentUser().getPhoneNumber()
                : null;
        if (currentUserPhone == null) {
            Log.e(TAG, "El teléfono del usuario actual es nulo. ¿Usuario no autenticado?");
            // Muestra la vista de chat vacío si no hay usuario autenticado.
            if (binding != null && isAdded()) {
                binding.emptyChatView.setVisibility(View.VISIBLE);
                binding.chatRecyclerView.setVisibility(View.GONE);
            }
            return;
        }
        Log.d(TAG, "Cargando chats para el usuario: " + currentUserPhone);

        FirebaseManager.getInstance().getDatabase()
                .getReference("user-chats")
                .child(currentUserPhone)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        // Valida la integridad del fragmento antes de procesar los datos.
                        if (binding == null || !isAdded()) {
                            Log.w(TAG, "El fragmento está desasociado o el binding es nulo, no se pueden procesar los datos de chat del usuario.");
                            return;
                        }

                        List<String> chatIds = new ArrayList<>();
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            // Solo añade IDs de chat si el valor es true (indica participación activa).
                            if (snapshot.getValue(Boolean.class) != null && snapshot.getValue(Boolean.class)) {
                                chatIds.add(snapshot.getKey());
                            }
                        }
                        Log.d(TAG, "IDs de chat para el usuario cargados: " + chatIds.size());
                        loadChatDetails(chatIds, users); // Procede a cargar los detalles de cada chat.
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Error cargando los chats del usuario: " + databaseError.getMessage());
                        // Muestra la vista de chat vacío en caso de error.
                        if (binding != null && isAdded()) {
                            binding.emptyChatView.setVisibility(View.VISIBLE);
                            binding.chatRecyclerView.setVisibility(View.GONE);
                        }
                    }
                });
    }

    /**
     * Carga los detalles de cada chat individual usando sus IDs y actualiza el RecyclerView.
     * Gestiona la visibilidad de la vista de "chat vacío".
     *
     * @param chatIds Lista de IDs de los chats a cargar.
     * @param users   Lista de todos los usuarios para resolver información de contacto.
     */
    private void loadChatDetails(List<String> chatIds, List<User> users) {
        // Valida la integridad del fragmento antes de proceder.
        if (binding == null || !isAdded()) {
            Log.w(TAG, "El fragmento está desasociado o el binding es nulo, no se pueden cargar los detalles del chat o actualizar la UI.");
            return;
        }

        List<Chat> chats = new ArrayList<>();
        if (chatIds.isEmpty()) {
            adapter.setChats(new ArrayList<>(), users);
            // Muestra la vista de chat vacío si no hay chats.
            binding.emptyChatView.setVisibility(View.VISIBLE);
            binding.chatRecyclerView.setVisibility(View.GONE);
            return;
        }
        // Oculta la vista de chat vacío si hay chats.
        binding.emptyChatView.setVisibility(View.GONE);
        binding.chatRecyclerView.setVisibility(View.VISIBLE);

        // Contador para asegurar que todos los detalles de los chats se han procesado antes de actualizar el adaptador.
        final int[] loadedChatCount = {0};

        for (String chatId : chatIds) {
            FirebaseManager.getInstance().getDatabase()
                    .getReference("chats")
                    .child(chatId)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            // Valida la integridad del fragmento.
                            if (binding == null || !isAdded()) {
                                Log.w(TAG, "El fragmento está desasociado o el binding es nulo, no se pueden procesar los detalles del chat individual.");
                                return;
                            }

                            Chat chat = dataSnapshot.getValue(Chat.class);
                            if (chat != null) {
                                Log.d(TAG, "Detalles del chat cargados: " + chat.getId());
                                chats.add(chat);
                            }
                            loadedChatCount[0]++; // Incrementa el contador de chats procesados.

                            // Si todos los chats han sido procesados (cargados o con error), actualiza el adaptador.
                            if (loadedChatCount[0] == chatIds.size()) {
                                Log.d(TAG, "Todos (o intentados) los detalles del chat cargados: " + chats.size());
                                adapter.setChats(chats, users); // Actualiza el adaptador con los chats obtenidos.
                                // Revisa nuevamente la visibilidad de las vistas si la lista de chats resultó vacía.
                                if (chats.isEmpty()) {
                                    binding.emptyChatView.setVisibility(View.VISIBLE);
                                    binding.chatRecyclerView.setVisibility(View.GONE);
                                } else {
                                    binding.emptyChatView.setVisibility(View.GONE);
                                    binding.chatRecyclerView.setVisibility(View.VISIBLE);
                                }
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            Log.e(TAG, "Error cargando los detalles del chat para " + chatId + ": " + databaseError.getMessage());
                            loadedChatCount[0]++; // Incrementa el contador incluso en caso de error.
                            // Si todos los chats han sido procesados, actualiza el adaptador con los chats disponibles.
                            if (loadedChatCount[0] == chatIds.size()) {
                                Log.d(TAG, "Error durante la carga de detalles del chat, actualizando el adaptador con los chats actuales: " + chats.size());
                                adapter.setChats(chats, users); // Actualizar con los chats que se cargaron con éxito.
                                if (binding != null && isAdded()) {
                                    if (chats.isEmpty()) {
                                        binding.emptyChatView.setVisibility(View.VISIBLE);
                                        binding.chatRecyclerView.setVisibility(View.GONE);
                                    } else {
                                        binding.emptyChatView.setVisibility(View.GONE);
                                        binding.chatRecyclerView.setVisibility(View.VISIBLE);
                                    }
                                }
                            }
                        }
                    });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView: binding establecido a nulo");
        // Remueve el listener de Firestore para evitar fugas de memoria y posibles errores.
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
        binding = null; // Libera la referencia al binding para evitar memory leaks.
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}