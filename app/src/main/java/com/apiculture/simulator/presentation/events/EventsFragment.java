package com.apiculture.simulator.presentation.events;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.databinding.FragmentEventsBinding;
import com.apiculture.simulator.presentation.common.SimpleViewModelFactory;

public class EventsFragment extends Fragment {
    private FragmentEventsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentEventsBinding.inflate(inflater, container, false);
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        EventsViewModel viewModel = new ViewModelProvider(this,
                new SimpleViewModelFactory<>(() -> new EventsViewModel(app.getHiveRepository())))
                .get(EventsViewModel.class);
        viewModel.eventsText().observe(getViewLifecycleOwner(), text -> binding.tvEventsInfo.setText(text));
        return binding.getRoot();
    }
}
