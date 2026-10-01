package com.apiculture.simulator.presentation.market;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.repository.HeadquartersStore;
import com.apiculture.simulator.domain.game.ClimateUnlock;
import com.apiculture.simulator.domain.game.TranshumanceRules;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.map.ProvincialMarket;
import com.apiculture.simulator.domain.map.ProvincialMarketCatalog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.apiculture.simulator.data.session.PlayerAuth;
import com.apiculture.simulator.data.session.SignedInUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Selector de mercados para la acción rápida de venta del menú Inicio. */
public final class MarketPickerDialogs {

    private static final int PAGE_SIZE = 8;

    private MarketPickerDialogs() {
    }

    public static void show(@NonNull Fragment fragment) {
        if (!fragment.isAdded() || fragment.getContext() == null) {
            return;
        }
        String uid = currentUid();
        if (uid.isEmpty()) {
            return;
        }
        Context context = fragment.requireContext();
        ApicultureApp app = (ApicultureApp) fragment.requireActivity().getApplication();
        int playerLevel = app.getPlayerProgressRepository().getLevel(uid);
        boolean southAfricaUnlocked = ClimateUnlock.canAccessSouthAfrica(playerLevel);

        // La referencia solicitada es siempre la sede de Iberia, no el almacén
        // ni el apiario más cercano. Si aún no se ha colocado, se usa el centro
        // regional por defecto para mantener visibles y ordenadas las distancias.
        HeadquartersStore.Hq headquarters = HeadquartersStore.get(
                context, uid, PlayableMapRegion.IBERIA);
        double originLat = headquarters != null
                ? headquarters.lat : PlayableMapRegion.IBERIA.defaultLookLat();
        double originLng = headquarters != null
                ? headquarters.lng : PlayableMapRegion.IBERIA.defaultLookLon();

        present(fragment, context, originLat, originLng, southAfricaUnlocked);
    }

    private static void present(@NonNull Fragment fragment, @NonNull Context context,
            double originLat, double originLng, boolean southAfricaUnlocked) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_market_picker);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView origin = dialog.findViewById(R.id.tv_market_picker_origin);
        TextView empty = dialog.findViewById(R.id.tv_market_picker_empty);
        RecyclerView recycler = dialog.findViewById(R.id.recycler_market_picker);
        View pager = dialog.findViewById(R.id.ll_market_picker_pager);
        TextView pageLabel = dialog.findViewById(R.id.tv_market_picker_page);
        ImageButton previous = dialog.findViewById(R.id.btn_market_picker_prev);
        ImageButton next = dialog.findViewById(R.id.btn_market_picker_next);
        MaterialButtonToggleGroup regionToggle = dialog.findViewById(R.id.market_picker_region_toggle);
        MaterialButton southAfricaButton = dialog.findViewById(R.id.btn_market_picker_south_africa);
        origin.setText(R.string.market_picker_origin_hq_iberia);

        if (southAfricaUnlocked) {
            southAfricaButton.setIcon(null);
        } else {
            southAfricaButton.setIconResource(R.drawable.ic_lock_padlock);
        }

        MarketAdapter adapter = new MarketAdapter(context, choice -> {
            dialog.dismiss();
            if (fragment.isAdded()) {
                LocalMarketDialogs.show(fragment, choice.market);
            }
        });
        recycler.setLayoutManager(new LinearLayoutManager(context));
        recycler.setAdapter(adapter);

        final PickerState state = new PickerState();
        final Runnable[] bindPage = new Runnable[1];
        bindPage[0] = new Runnable() {
            @Override
            public void run() {
                List<Choice> current = state.choices;
                int safePage = Math.max(0, Math.min(state.page, state.pageCount - 1));
                state.page = safePage;
                int from = safePage * PAGE_SIZE;
                int to = Math.min(current.size(), from + PAGE_SIZE);
                adapter.setChoices(from < to
                        ? new ArrayList<>(current.subList(from, to)) : new ArrayList<>());
                pageLabel.setText(fragment.getString(R.string.market_contracts_page,
                        safePage + 1, state.pageCount));
                previous.setEnabled(safePage > 0);
                next.setEnabled(safePage < state.pageCount - 1);
                previous.setAlpha(safePage > 0 ? 1f : 0.35f);
                next.setAlpha(safePage < state.pageCount - 1 ? 1f : 0.35f);
            }
        };
        Runnable bindRegion = new Runnable() {
            @Override
            public void run() {
                state.choices = buildChoices(context, state.region, originLat, originLng);
                state.pageCount = Math.max(1,
                        (state.choices.size() + PAGE_SIZE - 1) / PAGE_SIZE);
                state.page = 0;
                boolean hasChoices = !state.choices.isEmpty();
                empty.setVisibility(hasChoices ? View.GONE : View.VISIBLE);
                recycler.setVisibility(hasChoices ? View.VISIBLE : View.GONE);
                pager.setVisibility(state.choices.size() > PAGE_SIZE ? View.VISIBLE : View.GONE);
                bindPage[0].run();
            }
        };

        final boolean[] suppressToggle = {false};
        regionToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || suppressToggle[0]) {
                return;
            }
            PlayableMapRegion nextRegion = regionFromButton(checkedId);
            if (nextRegion == PlayableMapRegion.SOUTH_AFRICA && !southAfricaUnlocked) {
                suppressToggle[0] = true;
                group.check(buttonForRegion(state.region));
                suppressToggle[0] = false;
                showSouthAfricaLocked(fragment.requireContext());
                return;
            }
            state.region = nextRegion;
            bindRegion.run();
        });
        suppressToggle[0] = true;
        regionToggle.check(R.id.btn_market_picker_iberia);
        suppressToggle[0] = false;

        previous.setOnClickListener(v -> {
            state.page--;
            bindPage[0].run();
        });
        next.setOnClickListener(v -> {
            state.page++;
            bindPage[0].run();
        });
        dialog.findViewById(R.id.btn_market_picker_close).setOnClickListener(v -> dialog.dismiss());
        bindRegion.run();
        dialog.show();
    }

    public static void bindList(@NonNull Fragment fragment, @NonNull RecyclerView recycler,
            @NonNull TextView empty, @NonNull View pager, @NonNull TextView pageLabel,
            @NonNull ImageButton previous, @NonNull ImageButton next,
            @NonNull PlayableMapRegion region) {
        bindList(fragment, recycler, empty, pager, pageLabel, previous, next, region, Double.NaN, Double.NaN);
    }

    /** Listado de la pestaña Mercado: al pulsar un mercado se vende allí. */
    public static void bindList(@NonNull Fragment fragment, @NonNull RecyclerView recycler,
            @NonNull TextView empty, @NonNull View pager, @NonNull TextView pageLabel,
            @NonNull ImageButton previous, @NonNull ImageButton next,
            @NonNull PlayableMapRegion region, double originLat, double originLng) {
        if (!fragment.isAdded()) {
            return;
        }
        Context context = fragment.requireContext();
        String uid = currentUid();
        HeadquartersStore.Hq headquarters = HeadquartersStore.get(
                context, uid, PlayableMapRegion.IBERIA);
        double defLat = headquarters != null
                ? headquarters.lat : region.defaultLookLat();
        double defLng = headquarters != null
                ? headquarters.lng : region.defaultLookLon();
        double finalLat = Double.isNaN(originLat) ? defLat : originLat;
        double finalLng = Double.isNaN(originLng) ? defLng : originLng;
        ListBinding binding = recycler.getTag() instanceof ListBinding
                ? (ListBinding) recycler.getTag() : null;
        if (binding == null) {
            binding = new ListBinding();
            binding.adapter = new MarketAdapter(context, choice -> {
                if (fragment.isAdded()) {
                    LocalMarketDialogs.show(fragment, choice.market);
                }
            });
            recycler.setLayoutManager(new LinearLayoutManager(context));
            recycler.setAdapter(binding.adapter);
            recycler.setTag(binding);
            ListBinding bound = binding;
            previous.setOnClickListener(v -> {
                bound.page--;
                showPage(fragment, bound, pageLabel, previous, next);
            });
            next.setOnClickListener(v -> {
                bound.page++;
                showPage(fragment, bound, pageLabel, previous, next);
            });
        }
        binding.choices = buildChoices(context, region, finalLat, finalLng);
        binding.pageCount = Math.max(1, (binding.choices.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        binding.page = 0;
        boolean hasChoices = !binding.choices.isEmpty();
        empty.setVisibility(hasChoices ? View.GONE : View.VISIBLE);
        recycler.setVisibility(hasChoices ? View.VISIBLE : View.GONE);
        pager.setVisibility(binding.choices.size() > PAGE_SIZE ? View.VISIBLE : View.GONE);
        showPage(fragment, binding, pageLabel, previous, next);
    }

    private static void showPage(@NonNull Fragment fragment, @NonNull ListBinding binding,
            @NonNull TextView pageLabel, @NonNull ImageButton previous, @NonNull ImageButton next) {
        int safePage = Math.max(0, Math.min(binding.page, binding.pageCount - 1));
        binding.page = safePage;
        int from = safePage * PAGE_SIZE;
        int to = Math.min(binding.choices.size(), from + PAGE_SIZE);
        binding.adapter.setChoices(from < to
                ? new ArrayList<>(binding.choices.subList(from, to)) : new ArrayList<>());
        pageLabel.setText(fragment.getString(R.string.market_contracts_page,
                safePage + 1, binding.pageCount));
        previous.setEnabled(safePage > 0);
        next.setEnabled(safePage < binding.pageCount - 1);
        previous.setAlpha(safePage > 0 ? 1f : 0.35f);
        next.setAlpha(safePage < binding.pageCount - 1 ? 1f : 0.35f);
    }

    @NonNull
    private static List<Choice> buildChoices(@NonNull Context context,
            @NonNull PlayableMapRegion region, double originLat, double originLng) {
        List<Choice> choices = new ArrayList<>();
        List<ProvincialMarket> markets = ProvincialMarketCatalog.resolve(context, region);
        int order = 0;
        for (ProvincialMarket market : markets) {
            // La acción de Inicio ofrece provinciales y locales. Los internacionales
            // conservan su selección y su cuota desde el mapa/mercado principal.
            if (market == null || market.international) {
                continue;
            }
            double distance = TranshumanceRules.haversineKm(
                    originLat, originLng, market.lat, market.lng);
            choices.add(new Choice(market, distance, order++));
        }
        Collections.sort(choices, new Comparator<Choice>() {
            @Override
            public int compare(Choice left, Choice right) {
                int byDistance = Double.compare(left.distanceKm, right.distanceKm);
                if (byDistance != 0) {
                    return byDistance;
                }
                int byOrder = Integer.compare(left.order, right.order);
                if (byOrder != 0) {
                    return byOrder;
                }
                String leftName = left.market.name != null ? left.market.name : "";
                String rightName = right.market.name != null ? right.market.name : "";
                return leftName.compareToIgnoreCase(rightName);
            }
        });
        return choices;
    }

    /** Aviso crema al intentar abrir Sudáfrica antes del nivel 40. */
    public static void showSouthAfricaLocked(@NonNull Context context) {
        showLocked(context, R.string.market_picker_south_africa_locked_title,
                R.string.map_za_locked_message);
    }

    public static void showLocked(@NonNull Context context, int messageRes) {
        showLocked(context, R.string.market_picker_south_africa_locked_title, messageRes);
    }

    public static void showLocked(@NonNull Context context, int titleRes, int messageRes) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_market_region_locked);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        TextView title = dialog.findViewById(R.id.tv_market_region_locked_title);
        title.setText(titleRes);
        dialog.findViewById(R.id.iv_market_region_locked)
                .setContentDescription(context.getString(titleRes));
        TextView body = dialog.findViewById(R.id.tv_market_region_locked_body);
        body.setText(messageRes);
        dialog.findViewById(R.id.btn_market_region_locked_close)
                .setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    @NonNull
    private static PlayableMapRegion regionFromButton(int buttonId) {
        if (buttonId == R.id.btn_market_picker_madagascar) {
            return PlayableMapRegion.MADAGASCAR;
        }
        if (buttonId == R.id.btn_market_picker_south_africa) {
            return PlayableMapRegion.SOUTH_AFRICA;
        }
        return PlayableMapRegion.IBERIA;
    }

    private static int buttonForRegion(@NonNull PlayableMapRegion region) {
        if (region == PlayableMapRegion.MADAGASCAR) {
            return R.id.btn_market_picker_madagascar;
        }
        if (region == PlayableMapRegion.SOUTH_AFRICA) {
            return R.id.btn_market_picker_south_africa;
        }
        return R.id.btn_market_picker_iberia;
    }

    @NonNull
    private static String currentUid() {
        SignedInUser user = PlayerAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : "";
    }

    private static final class ListBinding {
        MarketAdapter adapter;
        List<Choice> choices = new ArrayList<>();
        int page;
        int pageCount = 1;
    }

    private static final class PickerState {
        PlayableMapRegion region = PlayableMapRegion.IBERIA;
        List<Choice> choices = new ArrayList<>();
        int page;
        int pageCount = 1;
    }

    private static final class Choice {
        final ProvincialMarket market;
        final double distanceKm;
        final int order;

        Choice(ProvincialMarket market, double distanceKm, int order) {
            this.market = market;
            this.distanceKm = distanceKm;
            this.order = order;
        }
    }

    private static final class MarketAdapter extends RecyclerView.Adapter<MarketAdapter.Holder> {
        private final LayoutInflater inflater;
        private final Listener listener;
        private List<Choice> choices = new ArrayList<>();

        MarketAdapter(@NonNull Context context, @NonNull Listener listener) {
            inflater = LayoutInflater.from(context);
            this.listener = listener;
        }

        void setChoices(List<Choice> choices) {
            this.choices = choices != null ? choices : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(inflater.inflate(R.layout.item_market_picker, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Choice choice = choices.get(position);
            Context context = holder.itemView.getContext();
            ProvincialMarket market = choice.market;
            holder.name.setText(market.name);
            holder.type.setText(market.local
                    ? R.string.market_picker_local : R.string.market_picker_provincial);
            holder.distance.setText(context.getString(R.string.market_picker_distance,
                    choice.distanceKm));
            holder.itemView.setOnClickListener(v -> listener.onChoice(choice));
        }

        @Override
        public int getItemCount() {
            return choices.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView type;
            final TextView distance;

            Holder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.tv_market_picker_name);
                type = itemView.findViewById(R.id.tv_market_picker_type);
                distance = itemView.findViewById(R.id.tv_market_picker_distance);
            }
        }

        interface Listener {
            void onChoice(Choice choice);
        }
    }
}
