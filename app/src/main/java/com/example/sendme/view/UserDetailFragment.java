package com.example.sendme.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.example.sendme.R;
import com.example.sendme.data.model.User;
import com.example.sendme.databinding.FragmentUserDetailBinding;

/**
 * Fragmento que muestra los detalles de un usuario.
 * Recibe un objeto 'User' como argumento y muestra su nombre, teléfono, estado e imagen de perfil.
 */
public class UserDetailFragment extends Fragment {
    // View Binding para acceder a los elementos del layout de forma segura.
    private FragmentUserDetailBinding binding;
    // Objeto User cuyos detalles se van a mostrar.
    private User user;

    /**
     * Constructor vacío requerido para la instanciación de fragmentos por el sistema Android.
     */
    public UserDetailFragment() {
        // Constructor público vacío.
    }

    /**
     * Crea una nueva instancia de UserDetailFragment.
     *
     * @param user El objeto User con los detalles a mostrar.
     * @return Una nueva instancia de UserDetailFragment.
     */
    public static UserDetailFragment newInstance(User user) {
        UserDetailFragment fragment = new UserDetailFragment();
        Bundle args = new Bundle();
        args.putParcelable("user", user); // Pasa el objeto User como argumento.
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Infla el layout del fragmento utilizando View Binding.
        binding = FragmentUserDetailBinding.inflate(inflater, container, false);
        View view = binding.getRoot();

        // Obtener el objeto 'User' de los argumentos.
        if (getArguments() != null) {
            user = getArguments().getParcelable("user");
        }

        // Configura el OnClickListener para el botón de retroceso.
        ImageButton backButton = binding.backButton;
        backButton.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                NavHostFragment.findNavController(UserDetailFragment.this).popBackStack();
            }
        });

        // Si el objeto 'user' no es nulo, se cargan sus datos en las vistas correspondientes.
        if (user != null) {
            TextView userName = binding.userName;
            TextView userPhone = binding.userPhone;
            TextView userStatus = binding.userStatus;
            ImageView userImage = binding.userImage;

            // Establece el nombre de usuario.
            userName.setText(user.getUsername());

            // Establece el número de teléfono, con un mensaje por defecto si no está disponible.
            String phoneNumber = user.getPhone() != null ? user.getPhone() : "Número no disponible";
            userPhone.setText(phoneNumber);

            // Establece el estado del usuario, con un mensaje por defecto si está vacío.
            String status = user.getStatus() != null && !user.getStatus().isEmpty() ? user.getStatus() : "Este usuario no tiene estado";
            userStatus.setText(status);

            // Carga la imagen de perfil usando Glide, con una imagen por defecto si no hay URL.
            String imageUrl = user.getImageUrl() != null && !user.getImageUrl().isEmpty() ? user.getImageUrl() : null;
            Glide.with(this)
                    .load(imageUrl != null ? imageUrl : R.drawable.default_profile)
                    .error(R.drawable.default_profile) // Imagen de fallback en caso de error de carga.
                    .circleCrop() // Recorta la imagen en forma circular.
                    .into(userImage); // Destino de la imagen cargada.
        }

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Libera la referencia al binding para evitar memory leaks.
        binding = null;
    }
}