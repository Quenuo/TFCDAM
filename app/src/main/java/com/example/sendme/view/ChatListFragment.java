package com.example.sendme.view;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import com.example.sendme.R;
import com.example.sendme.data.model.Chat;
import com.example.sendme.data.model.User;
import com.example.sendme.databinding.FragmentChatListBinding;
import com.example.sendme.repository.FirebaseManager;
import com.example.sendme.ui.ChatAdapter;
import com.example.sendme.ui.HomeViewModel;

import java.util.List;
import java.util.stream.Collectors;


public class ChatListFragment extends Fragment {
    private FragmentChatListBinding binding;
    private HomeViewModel viewModel;
    private ChatAdapter adapter;
    private NavController navController;




    public ChatListFragment() {
        // Required empty public constructor
    }



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentChatListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }


    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Configurar Navigation Component
        navController = NavHostFragment.findNavController(this);

        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        adapter = new ChatAdapter();

        binding.chatRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.chatRecyclerView.setAdapter(adapter);

        // Observar chats y usuarios
        viewModel.getChatList().observe(getViewLifecycleOwner(), chats -> {
            List<User> users = viewModel.getUserList().getValue();
            if (users != null) {
                updateAdapter(chats, users);
            }
        });

        viewModel.getUserList().observe(getViewLifecycleOwner(), users -> {
            List<Chat> chats = viewModel.getChatList().getValue();
            if (chats != null) {
                updateAdapter(chats, users);
            }
        });

        // Menú
        binding.menuButton.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(requireContext(), binding.menuButton);
            popup.getMenu().add(0, 1, 0, "Nuevo Grupo");
            popup.getMenu().add(0, 2, 0, "Ajustes");
            popup.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1:
                        // TODO: Implementar navegación a Fragment de nuevo grupo
                        return true;
                    case 2:
                        navController.navigate(R.id.action_chatListFragment_to_settingsFragment);
                        return true;
                    default:
                        return false;
                }
            });
            popup.show();
        });

        // Botón flotante
        binding.fabNewChat.setOnClickListener(v -> {
            navController.navigate(R.id.action_chatListFragment_to_newChatFragment);
        });

        // Búsqueda
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.setSearchQuery(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        viewModel.getSearchQuery().observe(getViewLifecycleOwner(), query -> {
            List<Chat> chats = viewModel.getChatList().getValue();
            List<User> users = viewModel.getUserList().getValue();
            if (chats != null && users != null) {
                updateAdapter(chats, users);
            }
        });
    }

    private void updateAdapter(List<Chat> chats, List<User> users) {
        String query = viewModel.getSearchQuery().getValue();
        List<Chat> filteredChats = chats;
        if (query != null && !query.isEmpty()) {
            String lowerQuery = query.toLowerCase();
            filteredChats = chats.stream()
                    .filter(chat -> {
                        String otherParticipant = chat.getParticipants().stream()
                                .filter(p -> !p.equals(FirebaseManager.getInstance().getAuth().getCurrentUser().getPhoneNumber()))
                                .findFirst()
                                .orElse("");
                        User user = users.stream()
                                .filter(u -> u.getPhone().equals(otherParticipant))
                                .findFirst()
                                .orElse(null);
                        return (user != null && user.getUsername().toLowerCase().contains(lowerQuery))
                                || (chat.getLastMessage() != null && chat.getLastMessage().toLowerCase().contains(lowerQuery));
                    })
                    .collect(Collectors.toList());
        }
        adapter.setChats(filteredChats, users);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}