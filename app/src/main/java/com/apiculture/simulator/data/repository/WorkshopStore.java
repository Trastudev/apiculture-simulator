package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.AppDatabase;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.domain.workshop.JarMix;
import com.apiculture.simulator.domain.workshop.WorkshopRules;
import com.apiculture.simulator.domain.workshop.WorkshopRules.Format;
import com.apiculture.simulator.domain.workshop.WorkshopRules.Machine;
import com.apiculture.simulator.domain.workshop.WorkshopScheduler;
import com.apiculture.simulator.domain.workshop.WorkshopState;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Obradores del jugador. Cada almacén es un obrador con sus máquinas, sus tandas y sus tarros.
 * Se guardan todos juntos en el almacén "workshop" del servidor y en local, como
 * {@code {"v":2,"obradores":{hexId:{...}}}}; el formato antiguo (un solo obrador en la raíz) se
 * convierte al leerlo. Las tandas se recalculan desde sus horas al leer, sin reloj en marcha.
 * Las operaciones que escriben van fuera del hilo principal.
 */
public final class WorkshopStore {

    private static final String PREFS = "workshop_store";
    private static final String KIND = "workshop";
    private static final Object LOCK = new Object();
    private static final Format[] JAR_FORMATS = { Format.JAR_1000, Format.JAR_500, Format.JAR_250 };

    private WorkshopStore() {
    }

    // ---------------------------------------------------------------- lectura

    /** Obrador de un almacén al momento actual; si aún no tiene nada, solo con la recepción. */
    @NonNull
    public static WorkshopState get(@NonNull Context context, @Nullable String ownerId, @Nullable String hexId) {
        synchronized (LOCK) {
            WorkshopState s = obrador(read(context, ownerId), hexId);
            WorkshopScheduler.advance(s, System.currentTimeMillis());
            return s;
        }
    }

    /** Todos los obradores (uno por almacén del jugador), en el orden de los almacenes. */
    @NonNull
    public static List<WorkshopState> all(@NonNull Context context, @Nullable String ownerId) {
        synchronized (LOCK) {
            Map<String, WorkshopState> doc = read(context, ownerId);
            List<WorkshopState> out = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (String hex : warehouseHexes(context, ownerId)) {
                WorkshopState s = obrador(doc, hex);
                WorkshopScheduler.advance(s, now);
                out.add(s);
            }
            // Obradores de almacenes que ya no están en la lista local (aún sin sincronizar).
            for (Map.Entry<String, WorkshopState> e : doc.entrySet()) {
                boolean listed = false;
                for (WorkshopState s : out) {
                    if (e.getKey().equals(s.hexId)) {
                        listed = true;
                        break;
                    }
                }
                if (!listed && (!e.getValue().batches.isEmpty() || !e.getValue().packed.isEmpty())) {
                    WorkshopScheduler.advance(e.getValue(), now);
                    out.add(e.getValue());
                }
            }
            return out;
        }
    }

    /**
     * Fuera del hilo principal: trae la copia del servidor (las recogidas añaden tandas allí)
     * y pasa el bidón al almacén de cada obrador.
     */
    @NonNull
    public static List<WorkshopState> refresh(@NonNull Context context, @Nullable String ownerId,
            @NonNull EconomyRepository economy) {
        if (ownerId == null || ownerId.isEmpty()) {
            return new ArrayList<>();
        }
        synchronized (LOCK) {
            fresh(context, ownerId);
        }
        flushBulk(context, ownerId, economy);
        return all(context, ownerId);
    }

    /** El jugador tiene al menos un obrador (es decir, un almacén). */
    public static boolean built(@NonNull Context context, @Nullable String ownerId) {
        return !warehouseHexes(context, ownerId).isEmpty();
    }

    /** Primer obrador del jugador, para las pantallas que abren uno por defecto. */
    @Nullable
    public static String firstHex(@NonNull Context context, @Nullable String ownerId) {
        List<String> hexes = warehouseHexes(context, ownerId);
        return hexes.isEmpty() ? null : hexes.get(0);
    }

    /** Algún obrador tiene todas las máquinas. */
    public static boolean anyComplete(@NonNull Context context, @Nullable String ownerId) {
        for (WorkshopState s : all(context, ownerId)) {
            if (s.complete()) {
                return true;
            }
        }
        return false;
    }

    public static int jars(@NonNull Context context, @Nullable String ownerId, @Nullable String hexId,
            @Nullable String flora, @NonNull Format format) {
        WorkshopState.Packed p = get(context, ownerId, hexId).packed(flora, format);
        return p == null ? 0 : p.jars;
    }

    /** Hay tarros de esa flora para toda la mezcla en ese obrador. */
    public static boolean hasMix(@NonNull Context context, @Nullable String ownerId, @Nullable String hexId,
            @Nullable String flora, @Nullable JarMix mix) {
        if (mix == null) {
            return false;
        }
        WorkshopState s = get(context, ownerId, hexId);
        for (Format f : JAR_FORMATS) {
            WorkshopState.Packed p = s.packed(flora, f);
            if ((p == null ? 0 : p.jars) < mix.count(f)) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- escritura

    /** Compra la máquina si no está o la sube un nivel. */
    @Nullable
    public static String buyOrUpgrade(@NonNull Context context, @NonNull EconomyRepository economy,
            @Nullable String ownerId, @Nullable String hexId, @NonNull Machine machine) {
        if (ownerId == null || ownerId.isEmpty()) {
            return context.getString(R.string.workshop_err_login);
        }
        if (hexId == null || hexId.isEmpty()) {
            return context.getString(R.string.workshop_err_build_first);
        }
        synchronized (LOCK) {
            Map<String, WorkshopState> doc = fresh(context, ownerId);
            WorkshopState s = obrador(doc, hexId);
            int level = s.level(machine);
            if (level > 0 && !WorkshopRules.upgradable(machine)) {
                return context.getString(R.string.workshop_err_not_upgradable);
            }
            if (level >= WorkshopRules.MAX_LEVEL) {
                return context.getString(R.string.workshop_err_max);
            }
            int cost = level == 0 ? WorkshopRules.buyCostB(machine)
                    : WorkshopRules.upgradeCostB(machine, level);
            String what = level == 0 ? "Compra de máquina del obrador" : "Mejora de máquina del obrador";
            if (!economy.trySpend(cost, what)) {
                return economy.blockedReason(cost);
            }
            WorkshopScheduler.setLevel(s, machine, level + 1, System.currentTimeMillis());
            doc.put(hexId, s);
            if (!write(context, ownerId, doc)) {
                economy.addToBalance(cost, "Devolución de máquina del obrador");
                return EconomyRepository.OFFLINE_ACTION;
            }
            return null;
        }
    }

    /** Alzas descargadas en un obrador: una tanda por flora. */
    @Nullable
    public static String receiveLines(@NonNull Context context, @Nullable String ownerId,
            @Nullable String hexId, @NonNull Map<String, Double> kgByFlora, @Nullable String source) {
        if (ownerId == null || ownerId.isEmpty() || hexId == null || hexId.isEmpty()) {
            return context.getString(R.string.workshop_err_none);
        }
        synchronized (LOCK) {
            Map<String, WorkshopState> doc = fresh(context, ownerId);
            WorkshopState s = obrador(doc, hexId);
            long now = System.currentTimeMillis();
            WorkshopScheduler.advance(s, now);
            boolean any = false;
            for (Map.Entry<String, Double> e : kgByFlora.entrySet()) {
                if (e.getKey() == null || e.getKey().startsWith("_")
                        || e.getValue() == null || e.getValue() <= 1e-6) {
                    continue;
                }
                WorkshopScheduler.receive(s, e.getKey(), e.getValue(), source, now);
                any = true;
            }
            if (!any) {
                return null;
            }
            WorkshopScheduler.advance(s, now);
            doc.put(hexId, s);
            return write(context, ownerId, doc) ? null : EconomyRepository.OFFLINE_ACTION;
        }
    }

    /** Envase de una tanda; se busca en todos los obradores (los ids no se repiten). */
    @Nullable
    public static String chooseFormat(@NonNull Context context, @Nullable String ownerId,
            @NonNull String batchId, @NonNull Format format) {
        if (ownerId == null || ownerId.isEmpty()) {
            return context.getString(R.string.workshop_err_none);
        }
        synchronized (LOCK) {
            Map<String, WorkshopState> doc = fresh(context, ownerId);
            for (WorkshopState s : doc.values()) {
                if (s.batch(batchId) == null) {
                    continue;
                }
                if (!WorkshopScheduler.chooseFormat(s, batchId, format, System.currentTimeMillis())) {
                    return context.getString(R.string.workshop_err_packing);
                }
                return write(context, ownerId, doc) ? null : EconomyRepository.OFFLINE_ACTION;
            }
            return context.getString(R.string.workshop_err_packing);
        }
    }

    /**
     * Saca miel envasada de un obrador. En tarros cuenta {@code amount} tarros; en bidón, kilos.
     * @return kilos retirados, o 0 si no hay bastante.
     */
    public static double takePacked(@NonNull Context context, @Nullable String ownerId, @Nullable String hexId,
            @NonNull String flora, @NonNull Format format, double amount) {
        if (amount <= 1e-9 || ownerId == null || ownerId.isEmpty() || hexId == null) {
            return 0;
        }
        synchronized (LOCK) {
            Map<String, WorkshopState> doc = fresh(context, ownerId);
            WorkshopState s = obrador(doc, hexId);
            WorkshopScheduler.advance(s, System.currentTimeMillis());
            WorkshopState.Packed p = s.packed(flora, format);
            if (p == null) {
                return 0;
            }
            double kg;
            if (format == Format.BULK) {
                if (p.kg + 1e-9 < amount) {
                    return 0;
                }
                kg = amount;
                p.kg = Math.max(0, p.kg - kg);
            } else {
                int jars = (int) Math.round(amount);
                if (jars <= 0 || p.jars < jars) {
                    return 0;
                }
                kg = jars * format.jarKg;
                p.jars -= jars;
                p.kg = Math.max(0, p.kg - kg);
            }
            if (p.kg <= 1e-9 && p.jars <= 0) {
                s.packed.remove(p);
            }
            doc.put(hexId, s);
            return write(context, ownerId, doc) ? kg : 0;
        }
    }

    /** Devuelve tarros que no llegaron a salir (viaje cancelado o fallido). */
    public static boolean returnJars(@NonNull Context context, @Nullable String ownerId, @Nullable String hexId,
            @NonNull String flora, @NonNull Format format, int jars) {
        if (ownerId == null || ownerId.isEmpty() || hexId == null || jars <= 0 || format == Format.BULK) {
            return false;
        }
        synchronized (LOCK) {
            Map<String, WorkshopState> doc = fresh(context, ownerId);
            WorkshopState s = obrador(doc, hexId);
            WorkshopScheduler.advance(s, System.currentTimeMillis());
            WorkshopScheduler.addPacked(s, flora, format, jars * format.jarKg, jars);
            doc.put(hexId, s);
            return write(context, ownerId, doc);
        }
    }

    /** Saca de una vez todos los tarros de la mezcla. @return kilos retirados, o 0 si falta alguno. */
    public static double takeMix(@NonNull Context context, @Nullable String ownerId, @Nullable String hexId,
            @NonNull String flora, @Nullable JarMix mix) {
        if (ownerId == null || ownerId.isEmpty() || hexId == null || mix == null || mix.total() <= 0) {
            return 0;
        }
        synchronized (LOCK) {
            Map<String, WorkshopState> doc = fresh(context, ownerId);
            WorkshopState s = obrador(doc, hexId);
            WorkshopScheduler.advance(s, System.currentTimeMillis());
            for (Format f : JAR_FORMATS) {
                WorkshopState.Packed p = s.packed(flora, f);
                if ((p == null ? 0 : p.jars) < mix.count(f)) {
                    return 0;
                }
            }
            double kg = 0;
            for (Format f : JAR_FORMATS) {
                int n = mix.count(f);
                if (n <= 0) {
                    continue;
                }
                WorkshopState.Packed p = s.packed(flora, f);
                p.jars -= n;
                p.kg = Math.max(0, p.kg - n * f.jarKg);
                kg += n * f.jarKg;
                if (p.kg <= 1e-9 && p.jars <= 0) {
                    s.packed.remove(p);
                }
            }
            doc.put(hexId, s);
            return write(context, ownerId, doc) ? kg : 0;
        }
    }

    /** Devuelve la mezcla entera (viaje cancelado o fallido). */
    public static boolean returnMix(@NonNull Context context, @Nullable String ownerId, @Nullable String hexId,
            @NonNull String flora, @Nullable JarMix mix) {
        if (ownerId == null || ownerId.isEmpty() || hexId == null || mix == null || mix.total() <= 0) {
            return false;
        }
        synchronized (LOCK) {
            Map<String, WorkshopState> doc = fresh(context, ownerId);
            WorkshopState s = obrador(doc, hexId);
            WorkshopScheduler.advance(s, System.currentTimeMillis());
            for (Format f : JAR_FORMATS) {
                int n = mix.count(f);
                if (n > 0) {
                    WorkshopScheduler.addPacked(s, flora, f, n * f.jarKg, n);
                }
            }
            doc.put(hexId, s);
            return write(context, ownerId, doc);
        }
    }

    /**
     * La miel envasada en bidón pasa al almacén de su obrador (es el mismo edificio) y se vende como
     * la de siempre. Los tarros se quedan en las estanterías hasta que salen en un camión.
     */
    public static void flushBulk(@NonNull Context context, @Nullable String ownerId,
            @NonNull EconomyRepository economy) {
        if (ownerId == null || ownerId.isEmpty()
                || android.os.Looper.getMainLooper().isCurrentThread()) {
            return;
        }
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            boolean local = false;
            for (WorkshopState s : read(context, ownerId).values()) {
                WorkshopScheduler.advance(s, now);
                local |= hasBulk(s);
            }
            if (!local) {
                return;
            }
            Map<String, WorkshopState> doc = fresh(context, ownerId);
            Map<String, Map<String, Double>> moved = new LinkedHashMap<>();
            for (Map.Entry<String, WorkshopState> e : doc.entrySet()) {
                WorkshopState s = e.getValue();
                WorkshopScheduler.advance(s, now);
                for (Iterator<WorkshopState.Packed> it = s.packed.iterator(); it.hasNext(); ) {
                    WorkshopState.Packed p = it.next();
                    if (p.format == Format.BULK && p.kg > 1e-6) {
                        Map<String, Double> byFlora = moved.computeIfAbsent(e.getKey(), k -> new LinkedHashMap<>());
                        Double prev = byFlora.get(p.flora);
                        byFlora.put(p.flora, (prev == null ? 0.0 : prev) + p.kg);
                        it.remove();
                    }
                }
            }
            if (moved.isEmpty() || !write(context, ownerId, doc)) {
                return;
            }
            boolean leftover = false;
            for (Map.Entry<String, Map<String, Double>> e : moved.entrySet()) {
                for (Map.Entry<String, Double> f : e.getValue().entrySet()) {
                    double added = economy.addHoneyCapped(f.getKey(), f.getValue(), Double.MAX_VALUE);
                    if (added > 1e-9) {
                        WarehouseHoneyStore.add(context, ownerId, e.getKey(), f.getKey(), added);
                    }
                    if (f.getValue() - added > 1e-6) {
                        WorkshopScheduler.addPacked(obrador(doc, e.getKey()), f.getKey(), Format.BULK,
                                f.getValue() - added, 0);
                        leftover = true;
                    }
                }
            }
            if (leftover) {
                write(context, ownerId, doc);
            }
        }
    }

    private static boolean hasBulk(@NonNull WorkshopState s) {
        for (WorkshopState.Packed p : s.packed) {
            if (p.format == Format.BULK && p.kg > 1e-6) {
                return true;
            }
        }
        return false;
    }

    /** Vende toda la cera de un obrador. @return error o null. */
    @Nullable
    public static String sellWax(@NonNull Context context, @NonNull EconomyRepository economy,
            @Nullable String ownerId, @Nullable String hexId) {
        double kg = get(context, ownerId, hexId).waxKg;
        if (kg < 0.01) {
            return context.getString(R.string.workshop_err_no_wax);
        }
        double sold = takeWax(context, ownerId, hexId, kg);
        if (sold <= 1e-9) {
            return EconomyRepository.OFFLINE_ACTION;
        }
        double pay = Math.round(sold * WorkshopRules.WAX_PRICE_B_PER_KG * 100.0) / 100.0;
        economy.addToBalance(pay, "Venta de " + EconomyRepository.formatKg(sold) + " kg de cera");
        return null;
    }

    /** @return kilos de cera retirados, o 0 si no hay bastante. */
    public static double takeWax(@NonNull Context context, @Nullable String ownerId, @Nullable String hexId,
            double kg) {
        if (ownerId == null || ownerId.isEmpty() || hexId == null) {
            return 0;
        }
        synchronized (LOCK) {
            Map<String, WorkshopState> doc = fresh(context, ownerId);
            WorkshopState s = obrador(doc, hexId);
            WorkshopScheduler.advance(s, System.currentTimeMillis());
            if (kg <= 1e-9 || s.waxKg + 1e-9 < kg) {
                return 0;
            }
            s.waxKg = Math.max(0, s.waxKg - kg);
            doc.put(hexId, s);
            return write(context, ownerId, doc) ? kg : 0;
        }
    }

    // ---------------------------------------------------------------- servidor

    public static void applyServer(@NonNull Context context, @NonNull String ownerId, @NonNull JSONObject body) {
        synchronized (LOCK) {
            prefs(context).edit().putString(ownerId, body.toString()).commit();
        }
    }

    public static void pushServer(@NonNull Context context, @NonNull String ownerId) {
        if (!GameServer.enabled() || ownerId.isEmpty()) {
            return;
        }
        if (android.os.Looper.getMainLooper().isCurrentThread()) {
            new Thread(() -> pushServer(context, ownerId), "workshop-sync").start();
            return;
        }
        try {
            GameServer.saveStore(ownerId, KIND, toJson(read(context, ownerId)));
        } catch (Exception ignored) {
        }
    }

    public static void clear(@NonNull Context context, @Nullable String ownerId) {
        synchronized (LOCK) {
            prefs(context).edit().remove(ownerKey(ownerId)).commit();
        }
    }

    // ---------------------------------------------------------------- interno

    /** Obrador de un almacén dentro del documento; lo crea con la recepción si no estaba. */
    @NonNull
    private static WorkshopState obrador(@NonNull Map<String, WorkshopState> doc, @Nullable String hexId) {
        String key = hexId == null ? "" : hexId;
        WorkshopState s = doc.get(key);
        if (s == null) {
            s = new WorkshopState();
            s.hexId = hexId;
            s.levels.put(Machine.RECEPTION, 1);
            doc.put(key, s);
        }
        return s;
    }

    /** Almacenes de cada jugador, leídos de Room fuera del hilo principal. */
    private static final Map<String, List<String>> HEXES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ExecutorService LOADER =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    /**
     * Terrenos con almacén (cada uno es un obrador). Room no se puede leer en el hilo principal: allí
     * se usa la última lista leída o, la primera vez, los obradores que ya hay en el documento, y se
     * pide la lista buena en segundo plano.
     */
    @NonNull
    private static List<String> warehouseHexes(@NonNull Context context, @Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return new ArrayList<>();
        }
        if (android.os.Looper.getMainLooper().isCurrentThread()) {
            List<String> cached = HEXES.get(ownerId);
            if (cached != null) {
                return new ArrayList<>(cached);
            }
            Context app = context.getApplicationContext();
            LOADER.execute(() -> loadWarehouseHexes(app, ownerId));
            List<String> known = new ArrayList<>();
            for (String hex : read(context, ownerId).keySet()) {
                if (!hex.isEmpty()) {
                    known.add(hex);
                }
            }
            return known;
        }
        return loadWarehouseHexes(context, ownerId);
    }

    /** Fuera del hilo principal, al cargar la partida: deja leída la lista de almacenes. */
    public static void preload(@NonNull Context context, @Nullable String ownerId) {
        if (ownerId != null && !ownerId.isEmpty() && !android.os.Looper.getMainLooper().isCurrentThread()) {
            loadWarehouseHexes(context, ownerId);
        }
    }

    @NonNull
    private static List<String> loadWarehouseHexes(@NonNull Context context, @NonNull String ownerId) {
        List<String> out = new ArrayList<>();
        List<HexParcelOwnershipEntity> rows;
        try {
            rows = AppDatabase.getInstance(context).hexParcelOwnershipDao().getWarehousesForOwnerSync(ownerId);
        } catch (RuntimeException e) {
            return out;
        }
        if (rows == null) {
            return out;
        }
        for (HexParcelOwnershipEntity row : rows) {
            if (row != null && row.hasWarehouse && row.hexId != null && !out.contains(row.hexId)) {
                out.add(row.hexId);
            }
        }
        HEXES.put(ownerId, new ArrayList<>(out));
        return out;
    }

    /**
     * Para escribir: el servidor también añade tandas al cerrar las recogidas, así que se parte
     * de su copia y no de la local.
     */
    @NonNull
    private static Map<String, WorkshopState> fresh(@NonNull Context context, @NonNull String ownerId) {
        if (GameServer.enabled() && !android.os.Looper.getMainLooper().isCurrentThread()) {
            JSONObject body = GameServer.loadStore(ownerId, KIND);
            if (body != null && (body.has("obradores") || body.has("levels"))) {
                prefs(context).edit().putString(ownerId, body.toString()).commit();
                return fromJson(body);
            }
        }
        return read(context, ownerId);
    }

    @NonNull
    private static Map<String, WorkshopState> read(@NonNull Context context, @Nullable String ownerId) {
        String raw = prefs(context).getString(ownerKey(ownerId), null);
        if (raw == null || raw.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return fromJson(new JSONObject(raw));
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private static boolean write(@NonNull Context context, @NonNull String ownerId,
            @NonNull Map<String, WorkshopState> doc) {
        JSONObject body;
        try {
            body = toJson(doc);
        } catch (Exception e) {
            return false;
        }
        if (GameServer.enabled()) {
            if (android.os.Looper.getMainLooper().isCurrentThread()
                    || !GameServer.saveStore(ownerId, KIND, body)) {
                return false;
            }
        }
        prefs(context).edit().putString(ownerId, body.toString()).commit();
        return true;
    }

    @NonNull
    static JSONObject toJson(@NonNull Map<String, WorkshopState> doc) throws Exception {
        JSONObject o = new JSONObject();
        o.put("v", 2);
        JSONObject obradores = new JSONObject();
        for (Map.Entry<String, WorkshopState> e : doc.entrySet()) {
            if (e.getKey().isEmpty()) {
                continue;
            }
            obradores.put(e.getKey(), stateJson(e.getValue()));
        }
        o.put("obradores", obradores);
        return o;
    }

    @NonNull
    static JSONObject stateJson(@NonNull WorkshopState s) throws Exception {
        JSONObject o = new JSONObject();
        o.put("hexId", s.hexId == null ? "" : s.hexId);
        JSONObject levels = new JSONObject();
        for (Machine m : Machine.values()) {
            levels.put(m.name(), s.level(m));
        }
        o.put("levels", levels);
        o.put("waxKg", s.waxKg);
        JSONArray batches = new JSONArray();
        for (WorkshopState.Batch b : s.batches) {
            JSONObject j = new JSONObject();
            j.put("id", b.id);
            j.put("flora", b.flora);
            j.put("kg", b.kg);
            j.put("source", b.source == null ? "" : b.source);
            j.put("createdAt", b.createdAt);
            j.put("stage", b.stage.name());
            j.put("inMachine", b.inMachine);
            j.put("waitingSince", b.waitingSince);
            j.put("startAt", b.startAt);
            j.put("endAt", b.endAt);
            j.put("format", b.format == null ? "" : b.format.name());
            j.put("waxKg", b.waxKg);
            batches.put(j);
        }
        o.put("batches", batches);
        JSONArray packed = new JSONArray();
        for (WorkshopState.Packed p : s.packed) {
            JSONObject j = new JSONObject();
            j.put("flora", p.flora);
            j.put("format", p.format.name());
            j.put("kg", p.kg);
            j.put("jars", p.jars);
            packed.put(j);
        }
        o.put("packed", packed);
        return o;
    }

    @NonNull
    static Map<String, WorkshopState> fromJson(@NonNull JSONObject o) {
        Map<String, WorkshopState> doc = new LinkedHashMap<>();
        JSONObject obradores = o.optJSONObject("obradores");
        if (obradores != null) {
            Iterator<String> keys = obradores.keys();
            while (keys.hasNext()) {
                String hex = keys.next();
                JSONObject j = obradores.optJSONObject(hex);
                if (j != null && !hex.isEmpty()) {
                    WorkshopState s = stateFromJson(j);
                    s.hexId = hex;
                    doc.put(hex, s);
                }
            }
        } else if (o.has("levels")) {
            // Formato antiguo: un solo obrador en la raíz.
            WorkshopState s = stateFromJson(o);
            if (s.hexId != null && !s.hexId.isEmpty()) {
                doc.put(s.hexId, s);
            }
        }
        return doc;
    }

    @NonNull
    static WorkshopState stateFromJson(@NonNull JSONObject o) {
        WorkshopState s = new WorkshopState();
        String hex = o.optString("hexId", "");
        s.hexId = hex.isEmpty() ? null : hex;
        JSONObject levels = o.optJSONObject("levels");
        for (Machine m : Machine.values()) {
            s.levels.put(m, levels == null ? 0 : Math.max(0, levels.optInt(m.name(), 0)));
        }
        if (s.level(Machine.RECEPTION) <= 0) {
            s.levels.put(Machine.RECEPTION, 1);
        }
        s.waxKg = Math.max(0, o.optDouble("waxKg", 0));
        JSONArray batches = o.optJSONArray("batches");
        for (int i = 0; batches != null && i < batches.length(); i++) {
            JSONObject j = batches.optJSONObject(i);
            Machine stage = j == null ? null : machine(j.optString("stage", ""));
            if (stage == null) {
                continue;
            }
            WorkshopState.Batch b = new WorkshopState.Batch();
            b.id = j.optString("id", "");
            b.flora = j.optString("flora", "");
            b.kg = Math.max(0, j.optDouble("kg", 0));
            b.source = j.optString("source", "");
            b.createdAt = j.optLong("createdAt", 0);
            b.stage = stage;
            b.inMachine = j.optBoolean("inMachine", false);
            b.waitingSince = j.optLong("waitingSince", 0);
            b.startAt = j.optLong("startAt", 0);
            b.endAt = j.optLong("endAt", 0);
            b.format = Format.parse(j.optString("format", ""));
            b.waxKg = Math.max(0, j.optDouble("waxKg", 0));
            s.batches.add(b);
        }
        JSONArray packed = o.optJSONArray("packed");
        for (int i = 0; packed != null && i < packed.length(); i++) {
            JSONObject j = packed.optJSONObject(i);
            Format f = j == null ? null : Format.parse(j.optString("format", ""));
            if (f == null) {
                continue;
            }
            WorkshopState.Packed p = new WorkshopState.Packed();
            p.flora = j.optString("flora", "");
            p.format = f;
            p.kg = Math.max(0, j.optDouble("kg", 0));
            p.jars = Math.max(0, j.optInt("jars", 0));
            s.packed.add(p);
        }
        return s;
    }

    @Nullable
    private static Machine machine(@NonNull String name) {
        for (Machine m : Machine.values()) {
            if (m.name().equals(name)) {
                return m;
            }
        }
        return null;
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @NonNull
    private static String ownerKey(@Nullable String ownerId) {
        return ownerId == null || ownerId.isEmpty() ? "guest" : ownerId;
    }
}
