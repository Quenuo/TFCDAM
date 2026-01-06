package com.example.sendme.view;

import android.net.Uri;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.bumptech.glide.Glide;

import com.example.sendme.databinding.FragmentImageViewBinding;


/**
 * Fragmento para mostrar una imagen en pantalla completa.
 * Recibe la URL de la imagen como argumento.
 */
public class ImageViewFragment extends Fragment {

    // View Binding para acceder a los elementos del layout de forma segura.
    private FragmentImageViewBinding binding;
    // URL de la imagen a mostrar.
    private String imageUrl;

    /**
     * Constructor vacío requerido para la instanciación de fragmentos por el sistema Android.
     */
    public ImageViewFragment() {
        // Constructor público vacío.
    }

    /**
     * Crea una nueva instancia de ImageViewFragment con una URL de imagen específica.
     *
     * @param imageUrl La URL de la imagen a mostrar.
     * @return Una nueva instancia de ImageViewFragment.
     */
    public static ImageViewFragment newInstance(String imageUrl) {
        ImageViewFragment fragment = new ImageViewFragment();
        Bundle args = new Bundle();
        args.putString("imageUrl", imageUrl); // Pasa la URL como argumento.
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Recupera la URL de la imagen de los argumentos pasados al fragmento.
        if (getArguments() != null) {
            imageUrl = getArguments().getString("imageUrl");
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Infla el layout del fragmento utilizando View Binding.
        binding = FragmentImageViewBinding.inflate(inflater, container, false);
        return binding.getRoot(); // Devuelve la vista raíz del layout inflado.
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Carga la imagen en el ImageView si la URL no es nula ni vacía.
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(requireContext()) // Utiliza Glide para una carga eficiente de imágenes.
                    .load(Uri.parse(imageUrl)) // Convierte la URL String a Uri.
                    .into(binding.fullScreenImageView); // Carga la imagen en el ImageView.
        }

        // Configura el OnClickListener para el botón de cerrar, que navega hacia atrás.
        binding.closeButton.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                NavHostFragment.findNavController(ImageViewFragment.this).popBackStack();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Libera la referencia al binding para evitar memory leaks.
        binding = null;
    }
}