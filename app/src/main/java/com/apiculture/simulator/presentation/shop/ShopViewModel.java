package com.apiculture.simulator.presentation.shop;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.data.repository.EconomyRepository;
import com.apiculture.simulator.data.repository.EventInventoryStore;
import com.apiculture.simulator.domain.game.HiveCareRules;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.function.Consumer;

public class ShopViewModel extends AndroidViewModel {

    private final EconomyRepository economy;
    private final MutableLiveData<ShopStock> stock = new MutableLiveData<>();

    public ShopViewModel(@NonNull Application application) {
        super(application);
        economy = ((ApicultureApp) application).getEconomyRepository();
        refresh();
    }

    public LiveData<ShopStock> stock() {
        return stock;
    }

    public void refresh() {
        Application ap = getApplication();
        stock.setValue(new ShopStock(
                economy.getBalance(),
                EventInventoryStore.treatments(ap),
                EventInventoryStore.feed(ap),
                EventInventoryStore.queens(ap)));
    }

    public void buyTreatment(@Nullable String uid, int count, Consumer<String> onMain) {
        int n = Math.max(0, count);
        if (n < 1) {
            refuse(HiveCareRules.TREAT_EUR, onMain);
            return;
        }
        buy(uid, HiveCareRules.TREAT_EUR * n, "Compra de tratamiento",
                () -> EventInventoryStore.addTreatments(getApplication(), n), onMain);
    }

    public void buyFeed(@Nullable String uid, int count, Consumer<String> onMain) {
        int n = Math.max(0, count);
        if (n < 1) {
            refuse(HiveCareRules.FEED_7_DAYS_EUR, onMain);
            return;
        }
        buy(uid, HiveCareRules.FEED_7_DAYS_EUR * n, "Compra de apialimento",
                () -> EventInventoryStore.addFeed(getApplication(), n), onMain);
    }

    public void buyQueen(@Nullable String uid, int count, Consumer<String> onMain) {
        int n = Math.max(0, count);
        if (n < 1) {
            refuse(HiveCareRules.QUEEN_EUR, onMain);
            return;
        }
        buy(uid, HiveCareRules.QUEEN_EUR * n, "Compra de reina", () -> {
            int added = 0;
            for (int i = 0; i < n; i++) {
                int q = HiveCareRules.randomCommercialQueenQuality();
                if (!EventInventoryStore.addQueen(getApplication(), q)) {
                    break;
                }
                lastQueenQuality = q;
                added++;
            }
            if (added > 0 && added < n) {
                economy.addToBalance(HiveCareRules.QUEEN_EUR * (n - added),
                        "Devolución de Compra de reina");
            }
            return added > 0;
        }, msg -> {
            if (msg == null) {
                onMain.accept(n == 1 ? "QUEEN:" + lastQueenQuality : "QUEENS:" + n);
            } else {
                onMain.accept(msg);
            }
        });
    }

    private void refuse(double price, @Nullable Consumer<String> onMain) {
        if (onMain == null) {
            return;
        }
        String reason = economy.blockedReason(price);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> onMain.accept(reason));
    }

    private int lastQueenQuality = 0;

    private void buy(@Nullable String uid, double price, String concept,
            java.util.function.BooleanSupplier grant, Consumer<String> onMain) {
        new Thread(() -> {
            if (!economy.trySpend(price, concept)) {
                if (onMain != null) {
                    String reason = economy.blockedReason(price);
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .post(() -> onMain.accept(reason));
                }
                return;
            }
            android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
            if (!grant.getAsBoolean()) {
                economy.addToBalance(price, "Devolución de " + concept);
                if (onMain != null) {
                    main.post(() -> onMain.accept(EconomyRepository.OFFLINE_ACTION));
                }
                return;
            }
            main.post(() -> {
                refresh();
                if (onMain != null) {
                    onMain.accept(null);
                }
            });
        }, "shop-buy").start();
    }

    public static final class ShopStock {
        public final double balance;
        public final int treatments;
        public final int feed;
        public final List<Integer> queens;

        ShopStock(double balance, int treatments, int feed, List<Integer> queens) {
            this.balance = balance;
            this.treatments = treatments;
            this.feed = feed;
            this.queens = queens;
        }
    }
}
