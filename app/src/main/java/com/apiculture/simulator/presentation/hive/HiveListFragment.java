package com.apiculture.simulator.presentation.hive;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.repository.HiveRepository;
import com.apiculture.simulator.databinding.DialogBuyHiveBinding;
import com.apiculture.simulator.databinding.DialogSuperPurchaseBinding;
import com.apiculture.simulator.databinding.DialogTitleBuyHiveBinding;
import com.apiculture.simulator.databinding.FragmentHivesBinding;
import com.apiculture.simulator.domain.game.ColonyGameRules;
import com.apiculture.simulator.domain.game.HiveHoneyRules;
import com.apiculture.simulator.domain.population.HivePopulationState;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.presentation.common.SimpleViewModelFactory;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;

import java.text.NumberFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class HiveListFragment extends Fragment {

    private FragmentHivesBinding binding;
    private HiveViewModel viewModel;
    private String sessionOwnerId = "";
    private List<HiveEntity> cachedHives = Collections.emptyList();
    private List<HexParcelOwnershipEntity> cachedOwnerships = Collections.emptyList();
    @Nullable
    private String selectedTerrainHexId = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHivesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        viewModel = new ViewModelProvider(this,
                new SimpleViewModelFactory<>(() -> new HiveViewModel(app.getHiveRepository(),
                        app.getHexParcelRepository())))
                .get(HiveViewModel.class);
        String ownerId = FirebaseAuth.getInstance().getUid();
        if (ownerId == null) ownerId = "guest";
        sessionOwnerId = ownerId;

        if (!"guest".equals(ownerId)) {
            viewModel.startHexParcelCloudSync();
            viewModel.startRealtimeCloudSync(ownerId);
        }

        binding.btnNewHive.setOnClickListener(v -> showBuyHiveDialog());

        androidx.recyclerview.widget.LinearLayoutManager layoutManager =
                new androidx.recyclerview.widget.LinearLayoutManager(requireContext(),
                        androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false);
        binding.rvHives.setLayoutManager(layoutManager);
        HiveListFragment.HiveAdapter adapter = new HiveAdapter(hive -> {
            if (hive == null || getParentFragmentManager() == null) return;
            Bundle args = new Bundle();
            args.putString("hiveId", hive.id);
            NavHostFragment.findNavController(this).navigate(R.id.action_hives_to_hive_detail, args);
        }, this::showRenameHiveDialog, this::showBuySuperDialog);
        binding.rvHives.setAdapter(adapter);

        viewModel.hexOwnerships().observe(getViewLifecycleOwner(), rows -> {
            List<HexParcelOwnershipEntity> mine = new ArrayList<>();
            if (rows != null) {
                for (HexParcelOwnershipEntity r : rows) {
                    if (r != null && sessionOwnerId.equals(r.ownerId)) {
                        mine.add(r);
                    }
                }
            }
            cachedOwnerships = mine;
            validateTerrainSelection();
            rebuildTerrainPicker();
            applyHiveFilter(adapter);
        });

        viewModel.hives(ownerId).observe(getViewLifecycleOwner(), hives -> {
            cachedHives = hives != null ? hives : Collections.emptyList();
            rebuildTerrainPicker();
            applyHiveFilter(adapter);
        });
    }

    private void validateTerrainSelection() {
        if (selectedTerrainHexId == null) {
            return;
        }
        boolean ok = false;
        for (HexParcelOwnershipEntity o : cachedOwnerships) {
            if (o != null && selectedTerrainHexId.equals(o.hexId)) {
                ok = true;
                break;
            }
        }
        if (!ok) {
            selectedTerrainHexId = null;
        }
    }

    private List<TerrainChipModel> buildTerrainModels() {
        int total = cachedHives.size();
        List<TerrainChipModel> out = new ArrayList<>();
        out.add(new TerrainChipModel(null, getString(R.string.hives_terrain_all), total));
        List<HexParcelOwnershipEntity> sorted = new ArrayList<>();
        for (HexParcelOwnershipEntity o : cachedOwnerships) {
            if (o != null) {
                sorted.add(o);
            }
        }
        sorted.sort(Comparator.comparing(o -> terrainDisplayTitle(o).toLowerCase(Locale.ROOT)));
        Map<String, Integer> counts = new HashMap<>();
        for (HiveEntity h : cachedHives) {
            if (h == null || h.hexId == null || h.hexId.isEmpty()) {
                continue;
            }
            counts.merge(h.hexId, 1, Integer::sum);
        }
        for (HexParcelOwnershipEntity o : sorted) {
            if (o == null) {
                continue;
            }
            String title = terrainDisplayTitle(o);
            int c = counts.getOrDefault(o.hexId, 0);
            out.add(new TerrainChipModel(o.hexId, title, c));
        }
        return out;
    }

    /**
     * Etiqueta de terreno sin tocar Room (los observers de LiveData ya van en el hilo principal).
     * Misma lógica que {@link com.apiculture.simulator.data.repository.HexParcelRepository#getParcelDisplayNameSync}
     * pero usando la fila en memoria.
     */
    private static String terrainDisplayTitle(@NonNull HexParcelOwnershipEntity o) {
        if (o.parcelName != null && !o.parcelName.trim().isEmpty()) {
            return o.parcelName.trim();
        }
        String hexId = o.hexId != null ? o.hexId : "";
        if (hexId.isEmpty()) {
            return "Terreno";
        }
        int u = hexId.lastIndexOf('_');
        String tail = u > 0 ? hexId.substring(u + 1) : hexId;
        return "Terreno · " + tail;
    }

    private void rebuildTerrainPicker() {
        if (!isAdded() || binding == null) {
            return;
        }
        rebuildTerrainChips();
    }

    private void rebuildTerrainChips() {
        android.widget.LinearLayout row = binding.llTerrainChips;
        row.removeAllViews();
        LayoutInflater inf = getLayoutInflater();
        int stroke = Math.round(2f * getResources().getDisplayMetrics().density);
        for (TerrainChipModel m : buildTerrainModels()) {
            View chip = inf.inflate(R.layout.item_terrain_chip, row, false);
            android.widget.TextView tvTitle = chip.findViewById(R.id.tv_terrain_name);
            android.widget.TextView tvSub = chip.findViewById(R.id.tv_terrain_hive_count);
            tvTitle.setText(m.title);
            tvSub.setText(getString(R.string.terrain_chip_hives, m.hiveCount));
            MaterialCardView card = (MaterialCardView) chip;
            boolean sel = Objects.equals(selectedTerrainHexId, m.hexId);
            if (sel) {
                card.setCardBackgroundColor(requireContext().getColor(R.color.dash_soft_orange));
                card.setStrokeWidth(stroke);
                card.setStrokeColor(ColorStateList.valueOf(
                        requireContext().getColor(R.color.login_primary_orange)));
            } else {
                card.setCardBackgroundColor(requireContext().getColor(R.color.dash_soft_green));
                card.setStrokeWidth(0);
            }
            chip.setOnClickListener(v -> {
                if (Objects.equals(selectedTerrainHexId, m.hexId)) {
                    return;
                }
                selectedTerrainHexId = m.hexId;
                rebuildTerrainChips();
                if (binding != null && binding.rvHives.getAdapter() instanceof HiveAdapter) {
                    applyHiveFilter((HiveAdapter) binding.rvHives.getAdapter());
                }
            });
            row.addView(chip);
        }
    }

    private static List<HiveEntity> filterHivesByTerrain(@Nullable List<HiveEntity> all,
                                                         @Nullable String hexId) {
        if (all == null || all.isEmpty()) {
            return Collections.emptyList();
        }
        if (hexId == null) {
            return new ArrayList<>(all);
        }
        List<HiveEntity> out = new ArrayList<>();
        for (HiveEntity h : all) {
            if (h != null && hexId.equals(h.hexId)) {
                out.add(h);
            }
        }
        return out;
    }

    private void applyHiveFilter(HiveAdapter adapter) {
        List<HiveEntity> filtered = filterHivesByTerrain(cachedHives, selectedTerrainHexId);
        adapter.setItems(filtered);
        int total = cachedHives.size();
        if (selectedTerrainHexId == null) {
            binding.tvHivesCount.setText(getString(R.string.hives_count_total, total));
        } else {
            binding.tvHivesCount.setText(getString(R.string.hives_count_filtered, filtered.size(), total));
        }
    }

    private void showBuyHiveDialog() {
        String ownerId = FirebaseAuth.getInstance().getUid();
        if (ownerId == null || ownerId.isEmpty()) {
            Toast.makeText(requireContext(), R.string.hive_buy_session_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        DialogBuyHiveBinding d = DialogBuyHiveBinding.inflate(getLayoutInflater());
        DialogTitleBuyHiveBinding titleBinding = DialogTitleBuyHiveBinding.inflate(getLayoutInflater());
        titleBinding.tvTitleHiveBuy.setText(getString(R.string.hive_buy_title));
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setCustomTitle(titleBinding.getRoot())
                .setView(d.getRoot())
                .setPositiveButton(R.string.hive_buy_confirm, null)
                .setNegativeButton(android.R.string.cancel, null);
        AlertDialog dialog = builder.create();

        int spinRow = R.layout.item_spinner_dialog_light;
        ArrayAdapter<String> floraAdapter = new ArrayAdapter<>(requireContext(), spinRow, HexFlora.FLORA_TYPES);
        floraAdapter.setDropDownViewResource(spinRow);
        d.spinnerFlora.setAdapter(floraAdapter);

        final List<HiveRepository.OwnedHexOption>[] hexBuffer = new List[]{Collections.emptyList()};

        Runnable refreshHexOptions = () -> {
            if (!isAdded()) {
                return;
            }
            Object sel = d.spinnerFlora.getSelectedItem();
            String flora = sel instanceof String ? (String) sel : HexFlora.FLORA_TYPES[0];
            viewModel.listOwnedHexOptionsForFlora(ownerId, flora, options -> {
                if (!isAdded()) {
                    return;
                }
                hexBuffer[0] = options != null ? options : Collections.emptyList();
                List<String> labels = new ArrayList<>();
                for (HiveRepository.OwnedHexOption o : hexBuffer[0]) {
                    labels.add(o.label);
                }
                ArrayAdapter<String> hexAd = new ArrayAdapter<>(requireContext(), spinRow, labels);
                hexAd.setDropDownViewResource(spinRow);
                d.spinnerHex.setAdapter(hexAd);
            });
        };

        d.spinnerFlora.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshHexOptions.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        Runnable updatePrice = () -> {
            int checked = d.rgSupers.getCheckedRadioButtonId();
            int superCount = checked == d.rbSuper2.getId() ? 2
                    : (checked == d.rbSuper1.getId() ? 1 : 0);
            d.tvHiveBuyPrice.setText(getString(R.string.hive_buy_price_template,
                    HiveViewModel.hivePurchasePriceEuros(superCount)));
        };
        d.rgSupers.setOnCheckedChangeListener((g, checkedId) -> updatePrice.run());
        updatePrice.run();

        dialog.setOnShowListener(di -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            d.editHiveName.post(() -> {
                d.editHiveName.requestFocus();
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) requireContext()
                                .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(d.editHiveName, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int checked = d.rgSupers.getCheckedRadioButtonId();
                int superCount = checked == d.rbSuper2.getId() ? 2
                        : (checked == d.rbSuper1.getId() ? 1 : 0);
                String name = d.editHiveName.getText() != null ? d.editHiveName.getText().toString().trim() : "";
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.hive_buy_name_required, Toast.LENGTH_SHORT).show();
                    return;
                }
                int hexIdx = d.spinnerHex.getSelectedItemPosition();
                List<HiveRepository.OwnedHexOption> opts = hexBuffer[0];
                if (opts == null || opts.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.hive_buy_no_hexes, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (hexIdx < 0 || hexIdx >= opts.size()) {
                    Toast.makeText(requireContext(), R.string.hive_buy_need_hex, Toast.LENGTH_SHORT).show();
                    return;
                }
                String hexId = opts.get(hexIdx).hexId;
                String flora = (String) d.spinnerFlora.getSelectedItem();
                viewModel.purchaseHive(ownerId, hexId, flora, name, superCount, msg -> {
                    if (!isAdded()) {
                        return;
                    }
                    if (msg == null) {
                        dialog.dismiss();
                        Toast.makeText(requireContext(), R.string.hive_created_ok, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                    }
                });
            });
        });

        dialog.show();
        refreshHexOptions.run();
    }

    private void showBuySuperDialog(HiveEntity hive) {
        if (hive == null || hive.id == null) {
            return;
        }
        int current = Math.max(0, Math.min(2, hive.superCount));
        if (current >= 2) {
            Toast.makeText(requireContext(), R.string.hive_super_already_max, Toast.LENGTH_SHORT).show();
            return;
        }
        int room = 2 - current;
        int price = HiveViewModel.singleSuperPurchasePriceEuros();
        DialogSuperPurchaseBinding f = DialogSuperPurchaseBinding.inflate(getLayoutInflater());
        double capKg = HiveHoneyRules.maxHoneyKgForSuperCount(current);
        f.tvSuperDialogSummary.setText(getString(R.string.hive_super_purchase_summary,
                hive.name != null ? hive.name : "",
                current,
                capKg));
        f.rbSuperBuyOne.setText(getString(R.string.hive_super_purchase_one, price));
        f.rbSuperBuyTwo.setText(getString(R.string.hive_super_purchase_two, price * 2));
        if (room < 2) {
            f.rbSuperBuyTwo.setVisibility(View.GONE);
            f.rbSuperBuyOne.setChecked(true);
        } else {
            f.rbSuperBuyTwo.setVisibility(View.VISIBLE);
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.hive_super_purchase_title)
                .setView(f.getRoot())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.hex_purchase_confirm_buy, null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(di -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int want = f.rbSuperBuyTwo.getVisibility() == View.VISIBLE && f.rbSuperBuyTwo.isChecked() ? 2 : 1;
            if (want > room) {
                want = room;
            }
            viewModel.purchaseSupers(hive, want, msg -> {
                if (!isAdded()) {
                    return;
                }
                if (msg == null) {
                    dialog.dismiss();
                    Toast.makeText(requireContext(), R.string.hive_super_purchase_ok, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                }
            });
        }));
        dialog.show();
    }

    private void showRenameHiveDialog(HiveEntity hive) {
        if (hive == null || hive.id == null) {
            return;
        }
        EditText input = new EditText(requireContext());
        input.setText(hive.name != null ? hive.name : "");
        input.setHint(R.string.hive_rename_hint);
        input.setSingleLine(true);
        input.post(() -> input.selectAll());
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout wrap = new FrameLayout(requireContext());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = pad;
        lp.rightMargin = pad;
        input.setLayoutParams(lp);
        wrap.addView(input);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.hive_rename_title)
                .setView(wrap)
                .setPositiveButton(R.string.hive_rename_save, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.hive_rename_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    viewModel.updateHiveName(hive.id, name);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static class HiveAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<HiveViewHolder> {

        interface OnHiveClickListener {
            void onHiveClick(HiveEntity hive);
        }

        private final OnHiveClickListener listener;
        private final Consumer<HiveEntity> renameListener;
        private final Consumer<HiveEntity> buySuperListener;
        private java.util.List<HiveEntity> items = java.util.Collections.emptyList();

        HiveAdapter(OnHiveClickListener listener, Consumer<HiveEntity> renameListener,
                    Consumer<HiveEntity> buySuperListener) {
            this.listener = listener;
            this.renameListener = renameListener;
            this.buySuperListener = buySuperListener;
        }

        void setItems(java.util.List<HiveEntity> newItems) {
            this.items = newItems != null ? newItems : java.util.Collections.emptyList();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public HiveViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            android.view.LayoutInflater inflater = android.view.LayoutInflater.from(parent.getContext());
            android.view.View view = inflater.inflate(R.layout.item_hive_card, parent, false);
            return new HiveViewHolder(view, listener, renameListener, buySuperListener);
        }

        @Override
        public void onBindViewHolder(@NonNull HiveViewHolder holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static class HiveViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {

        private final android.widget.TextView tvName;
        private final android.widget.TextView tvHealth;
        private final android.widget.TextView tvBees;
        private final android.widget.TextView tvHoney;
        private final android.widget.TextView tvFlora;
        private final android.widget.TextView tvSupers;
        private final android.widget.ProgressBar pbHealth;
        private final ImageView ivHoneyWarn;
        private final ImageView ivSwarmWarn;
        private final android.widget.Button btnDetail;
        private final com.google.android.material.button.MaterialButton btnBuySuper;
        private final HiveAdapter.OnHiveClickListener listener;
        private final Consumer<HiveEntity> renameListener;
        private final Consumer<HiveEntity> buySuperListener;

        HiveViewHolder(@NonNull View itemView, HiveAdapter.OnHiveClickListener listener,
                       Consumer<HiveEntity> renameListener, Consumer<HiveEntity> buySuperListener) {
            super(itemView);
            this.listener = listener;
            this.renameListener = renameListener;
            this.buySuperListener = buySuperListener;
            tvName = itemView.findViewById(R.id.tv_hive_name);
            tvHealth = itemView.findViewById(R.id.tv_hive_health);
            tvBees = itemView.findViewById(R.id.tv_hive_bees);
            tvHoney = itemView.findViewById(R.id.tv_hive_honey);
            tvFlora = itemView.findViewById(R.id.tv_hive_flora);
            tvSupers = itemView.findViewById(R.id.tv_hive_supers);
            pbHealth = itemView.findViewById(R.id.pb_hive_health);
            ivHoneyWarn = itemView.findViewById(R.id.iv_hive_card_honey_warn);
            ivSwarmWarn = itemView.findViewById(R.id.iv_hive_card_swarm_warn);
            btnDetail = itemView.findViewById(R.id.btn_hive_detail);
            btnBuySuper = itemView.findViewById(R.id.btn_hive_buy_super);
        }

        void bind(HiveEntity hive) {
            tvName.setText(hive.name);
            boolean honeyAtCap = HiveHoneyRules.isHoneyAtCapacity(hive);
            HivePopulationState pop = HivePopulationState.fromHiveEntityOrDefault(hive,
                    HiveRepository.DEFAULT_BEE_COUNT_PER_HIVE);
            boolean swarmRisk = ColonyGameRules.swarmRiskForAdultWorkers(pop.workersAdult) > 0.0
                    || pop.workersAdult >= ColonyGameRules.SPLIT_RECOMMEND_BEES;
            ivHoneyWarn.setVisibility(honeyAtCap ? View.VISIBLE : View.GONE);
            ivSwarmWarn.setVisibility(swarmRisk ? View.VISIBLE : View.GONE);
            int health = hive.health;
            tvHealth.setText(health + "%");
            pbHealth.setMax(100);
            pbHealth.setProgress(Math.max(0, Math.min(100, health)));

            android.content.res.ColorStateList tint;
            if (health >= 65) {
                tint = android.content.res.ColorStateList.valueOf(
                        itemView.getResources().getColor(R.color.dash_good));
            } else if (health >= 40) {
                tint = android.content.res.ColorStateList.valueOf(
                        itemView.getResources().getColor(R.color.dash_warning));
            } else {
                tint = android.content.res.ColorStateList.valueOf(
                        itemView.getResources().getColor(R.color.dash_bad));
            }
            pbHealth.setProgressTintList(tint);
            pbHealth.setProgressBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            itemView.getResources().getColor(R.color.dash_soft_yellow)));
            NumberFormat beeNf = NumberFormat.getNumberInstance(new Locale("es", "ES"));
            tvBees.setText(itemView.getContext().getString(R.string.hive_card_obreras_line,
                    beeNf.format(pop.workersAdult)));
            int sc = Math.max(0, Math.min(2, hive.superCount));
            double cap = HiveHoneyRules.maxHoneyKgForSuperCount(sc);
            tvHoney.setText(String.format(java.util.Locale.getDefault(),
                    "Miel %.1f / %.0f kg", hive.honeyProduction, cap));
            tvFlora.setText(hive.floraType != null ? hive.floraType : "");
            tvSupers.setText(itemView.getContext().getString(R.string.hive_card_supers, sc));

            boolean canBuySuper = sc < 2;
            btnBuySuper.setEnabled(canBuySuper);
            btnBuySuper.setAlpha(canBuySuper ? 1f : 0.45f);
            btnBuySuper.setOnClickListener(v -> {
                if (buySuperListener != null && canBuySuper) {
                    buySuperListener.accept(hive);
                }
            });

            android.view.View.OnClickListener detailListener = v -> {
                if (listener != null) listener.onHiveClick(hive);
            };
            btnDetail.setOnClickListener(detailListener);
            tvName.setOnClickListener(v -> {
                if (renameListener != null) {
                    renameListener.accept(hive);
                }
            });
            tvName.setContentDescription(
                    itemView.getContext().getString(R.string.hive_rename_title));
        }
    }

    private static final class TerrainChipModel {
        @Nullable final String hexId;
        @NonNull final String title;
        final int hiveCount;

        TerrainChipModel(@Nullable String hexId, @NonNull String title, int hiveCount) {
            this.hexId = hexId;
            this.title = title;
            this.hiveCount = hiveCount;
        }
    }
}
