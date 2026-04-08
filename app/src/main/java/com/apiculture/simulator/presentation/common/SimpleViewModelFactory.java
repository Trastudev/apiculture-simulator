package com.apiculture.simulator.presentation.common;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.function.Supplier;

public class SimpleViewModelFactory<T extends ViewModel> implements ViewModelProvider.Factory {

    private final Supplier<T> supplier;

    public SimpleViewModelFactory(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <V extends ViewModel> V create(@NonNull Class<V> modelClass) {
        return (V) supplier.get();
    }
}
