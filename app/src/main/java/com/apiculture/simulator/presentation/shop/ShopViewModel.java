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

    public void buyTreatment(@Nullable String uid, Consumer<String> onMain) {
        buy(uid, HiveCareRules.TREAT_EUR, "Compra de tratamiento",
                () -> EventInventoryStore.addTreatments(getApplication(), 1), onMain);
    }

    public void buyFeed(@Nullable String uid, Consumer<String> onMain) {
        buy(uid, HiveCareRules.FEED_7_DAYS_EUR, "Compra de apialimento",
                () -> EventInventoryStore.addFeed(getApplication(), 1), onMain);
    }

    public void buyQueen(@Nullable String uid, Consumer<String> onMain) {
        buy(uid, HiveCareRules.QUEEN_EUR, "Compra de reina", () -> {
            int q = HiveCareRules.randomCommercialQueenQuality();
            if (!EventInventoryStore.addQueen(getApplication(), q)) {
                return false;
            }
            lastQueenQuality = q;
            return true;
        }, msg -> {
            if (msg == null) {
                onMain.accept("QUEEN:" + lastQueenQuality);
            } else {
                onMain.accept(msg);
            }
        });
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
