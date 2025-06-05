package com.example.sendme.view;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.sendme.data.model.User;
import com.example.sendme.databinding.FragmentNewChatBinding;
import com.example.sendme.ui.ContactAdapter;
import com.example.sendme.ui.NewChatViewModel;

import java.util.ArrayList;

/**
 * Fragmento para iniciar un nuevo chat.
 * Muestra una lista de contactos (usuarios) con los que el usuario puede iniciar una conversación.
 */
public class NewChatFragment extends Fragment {
    // View Binding para acceder a los elementos del layout de forma segura.
    private FragmentNewChatBinding binding;
    // ViewModel para gestionar la lógica de negocio y los datos de los contactos.
    private NewChatViewModel viewModel;
    // Adaptador para el RecyclerView que muestra la lista de contactos.
    private ContactAdapter adapter;
    // Controlador de navegación para transiciones entre fragmentos.
    private NavController navController;
    // Etiqueta para mensajes de log, útil para depuración.
    private static final String TAG = "NewChatFragment";

    /**
     * Constructor público vacío requerido por Android.
     */
    public NewChatFragment() {
        // Constructor vacío.
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Infla el layout del fragmento usando View Binding.
        binding = FragmentNewChatBinding.inflate(inflater, container, false);
        return binding.getRoot(); // Devuelve la vista raíz del layout inflado.
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated called, Lifecycle state: " + getViewLifecycleOwner().getLifecycle().getCurrentState());

        // Inicializa el NavController y el ViewModel.
        navController = NavHostFragment.findNavController(this);
        viewModel = new ViewModelProvider(this).get(NewChatViewModel.class);

        // Inicializa el ContactAdapter.
        adapter = new ContactAdapter(navController);

        // Configura el RecyclerView para mostrar los contactos en una lista vertical.
        binding.contactsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.contactsRecyclerView.setAdapter(adapter);
        binding.contactsRecyclerView.setHasFixedSize(true); // Optimización para rendimiento si el tamaño de los elementos no cambia.

        // Configura el OnClickListener para la flecha de retorno.
        binding.backButton.setOnClickListener(v -> {
            Log.d(TAG, "Volviendo a la lista de chats.");
            if (navController != null) {
                navController.navigateUp(); // Simula el comportamiento del botón "atrás".
            } else {
                Log.e(TAG, "Nav controller es nulo.");
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart called, Lifecycle state: " + getViewLifecycleOwner().getLifecycle().getCurrentState());

        // Observa los cambios en la lista de usuarios (contactos) desde el ViewModel.
        viewModel.getUsers().observe(getViewLifecycleOwner(), users -> {
            Log.d(TAG, "Users received: " + (users != null ? users.size() : 0));
            if (users != null) {
                // Si la lista de usuarios no es nula, se la pasa al adaptador.
                for (User user : users) {
                    Log.d(TAG, "User: " + user.getUsername() + ", Phone: " + user.getPhone() + ", ImageUrl: " + user.getImageUrl());
                }
                adapter.setUsers(users);
            } else {
                // Si la lista es nula, se pasa una lista vacía para evitar NPE.
                Log.w(TAG, "Users list is null");
                adapter.setUsers(new ArrayList<>());
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called, Lifecycle state: " + getViewLifecycleOwner().getLifecycle().getCurrentState());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView called");
        // Libera la referencia al binding para evitar memory leaks.
        binding = null;
    }
}