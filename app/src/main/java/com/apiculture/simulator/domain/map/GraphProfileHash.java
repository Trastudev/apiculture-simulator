package com.apiculture.simulator.domain.map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.StringReader;

/** Lee el hash `car|…` que GraphHopper guardó al importar. */
public final class GraphProfileHash {

    private GraphProfileHash() {
    }

    @Nullable
    public static Integer readCarHash(@NonNull File graphDir) {
        Integer fromTxt = parseFile(new File(graphDir, "properties.txt"));
        if (fromTxt != null) {
            return fromTxt;
        }
        return parseFile(new File(graphDir, "properties"));
    }

    @Nullable
    static Integer parseFile(@NonNull File file) {
        if (!file.isFile() || file.length() == 0 || file.length() > 200_000) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Exception e) {
            return null;
        }
        return parseText(sb.toString());
    }

    @Nullable
    static Integer parseText(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try (BufferedReader br = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#") || line.startsWith("//") || !line.contains("=")) {
                    continue;
                }
                int eq = line.indexOf('=');
                String key = line.substring(0, eq).trim();
                if (!"profiles".equals(key)) {
                    continue;
                }
                return hashForCar(line.substring(eq + 1).trim());
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    @Nullable
    static Integer hashForCar(@NonNull String profilesValue) {
        String[] parts = profilesValue.split(",");
        for (String part : parts) {
            String[] kv = part.split("\\|", 2);
            if (kv.length == 2 && "car".equals(kv[0].trim())) {
                try {
                    return Integer.parseInt(kv[1].trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
