package com.example.sendme.view;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.sendme.R;
import com.example.sendme.data.model.User;
import com.example.sendme.databinding.FragmentNewChatBinding;
import com.example.sendme.ui.ContactAdapter;
import com.example.sendme.ui.NewChatViewModel;


public class NewChatFragment extends Fragment {
    private FragmentNewChatBinding binding;
    private NewChatViewModel viewModel;
    private ContactAdapter adapter;
    private NavController navController;

    public NewChatFragment() {
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
        binding = FragmentNewChatBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        navController = NavHostFragment.findNavController(this);
        viewModel = new ViewModelProvider(this).get(NewChatViewModel.class);
        adapter = new ContactAdapter(navController);
        binding.contactsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.contactsRecyclerView.setAdapter(adapter);
        binding.contactsRecyclerView.setHasFixedSize(true);
        Log.d("Ciclo de vida", "Lifecycle state: " + getViewLifecycleOwner().getLifecycle().getCurrentState());
        viewModel.getUsers().observe(getViewLifecycleOwner(), users -> {
            Log.d("New chat fragment", "Users received: " + users.size());

            adapter.setUsers(users);
        });


    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}