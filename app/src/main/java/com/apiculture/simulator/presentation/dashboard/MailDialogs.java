package com.apiculture.simulator.presentation.dashboard;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.apiculture.simulator.R;
import com.apiculture.simulator.data.repository.Mailbox;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Buzón, mensajes y amistad. El destinatario de un mensaje solo puede ser un amigo. */
public final class MailDialogs {

    private static final String[] EMOJIS = {"😀", "🐝", "🍯", "🌸", "👍", "❤️", "🌻", "👑"};

    private MailDialogs() {
    }

    public static void show(@NonNull Fragment fragment) {
        if (!fragment.isAdded()) {
            return;
        }
        LinearLayout root = column(fragment);
        root.setPadding(dp(fragment, 16), dp(fragment, 16), dp(fragment, 16), dp(fragment, 12));
        TextView title = title(fragment, fragment.getString(R.string.mail_title));
        root.addView(title);
        LinearLayout actions = row(fragment);
        MaterialButton received = button(fragment, fragment.getString(R.string.mail_inbox));
        MaterialButton sent = button(fragment, fragment.getString(R.string.mail_sent));
        MaterialButton compose = button(fragment, fragment.getString(R.string.mail_compose));
        MaterialButton add = button(fragment, fragment.getString(R.string.mail_add_friendship));
        actions.addView(received);
        actions.addView(sent);
        root.addView(actions);
        LinearLayout actions2 = row(fragment);
        actions2.addView(compose);
        actions2.addView(add);
        root.addView(actions2);
        ScrollView scroll = new ScrollView(fragment.requireContext());
        LinearLayout list = column(fragment);
        scroll.addView(list);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(fragment, 280));
        scrollLp.topMargin = dp(fragment, 8);
        root.addView(scroll, scrollLp);
        Dialog dialog = creamDialog(fragment, root);
        received.setOnClickListener(v -> loadList(fragment, list, false));
        sent.setOnClickListener(v -> loadList(fragment, list, true));
        compose.setOnClickListener(v -> showCompose(fragment));
        add.setOnClickListener(v -> showAddFriend(fragment, null));
        dialog.show();
        loadList(fragment, list, false);
    }

    public static void showHiveFriend(@NonNull Fragment fragment, @NonNull String playerId) {
        if (!fragment.isAdded() || playerId.isEmpty()) {
            return;
        }
        Mailbox.get("/friends/status?playerId=" + playerId, raw -> {
            if (!fragment.isAdded()) {
                return;
            }
            if (raw == null) {
                GameNotice.show(fragment.requireContext(), R.string.mail_friendship_fail);
                return;
            }
            try {
                JSONObject json = new JSONObject(raw);
                if (!json.optBoolean("found")) {
                    GameNotice.show(fragment.requireContext(), R.string.mail_player_missing);
                    return;
                }
                showAddFriend(fragment, json);
            } catch (Exception e) {
                GameNotice.show(fragment.requireContext(), R.string.mail_friendship_fail);
            }
        });
    }

    private static void loadList(Fragment fragment, LinearLayout list, boolean sent) {
        list.removeAllViews();
        TextView wait = body(fragment, fragment.getString(R.string.mail_loading));
        list.addView(wait);
        Mailbox.get(sent ? "/mail/sent" : "/mail/inbox", raw -> {
            if (!fragment.isAdded()) {
                return;
            }
            list.removeAllViews();
            if (raw == null) {
                list.addView(body(fragment, fragment.getString(R.string.mail_offline)));
                return;
            }
            try {
                JSONArray messages = new JSONObject(raw).optJSONArray("messages");
                if (messages == null || messages.length() == 0) {
                    list.addView(body(fragment, fragment.getString(sent
                            ? R.string.mail_sent_empty : R.string.mail_inbox_empty)));
                    return;
                }
                for (int i = 0; i < messages.length(); i++) {
                    JSONObject msg = messages.getJSONObject(i);
                    list.addView(messageRow(fragment, msg, !sent));
                }
            } catch (Exception e) {
                list.addView(body(fragment, fragment.getString(R.string.mail_read_fail)));
            }
        });
    }

    private static View messageRow(Fragment fragment, JSONObject msg, boolean incoming) {
        LinearLayout box = column(fragment);
        box.setPadding(0, dp(fragment, 8), 0, dp(fragment, 8));
        String who = incoming ? msg.optString("from_name", "") : msg.optString("to_id", "");
        String head = (msg.optBoolean("unread") && incoming ? "● " : "")
                + msg.optString("subject", "") + (who.isEmpty() ? "" : " · " + who);
        LinearLayout headRow = row(fragment);
        TextView title = title(fragment, head);
        title.setTextSize(14);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(titleLp);
        ImageButton trash = new ImageButton(fragment.requireContext());
        trash.setImageResource(R.drawable.ic_delete);
        trash.setBackgroundColor(Color.TRANSPARENT);
        trash.setContentDescription(fragment.getString(R.string.mail_delete));
        int trashPad = dp(fragment, 4);
        trash.setPadding(trashPad, trashPad, trashPad, trashPad);
        LinearLayout.LayoutParams trashLp = new LinearLayout.LayoutParams(dp(fragment, 36), dp(fragment, 36));
        trash.setLayoutParams(trashLp);
        trash.setOnClickListener(v -> deleteMessage(fragment, msg.optString("id"), box));
        headRow.addView(title);
        headRow.addView(trash);
        box.addView(headRow);
        box.addView(body(fragment, msg.optString("body", "")));
        if (incoming && "friend_request".equals(msg.optString("kind"))) {
            LinearLayout actions = row(fragment);
            String reply = msg.optString("reply", "");
            if ("accepted".equals(reply) || "rejected".equals(reply)) {
                actions.addView(answeredButton(fragment, reply));
            } else {
                MaterialButton accept = button(fragment, fragment.getString(R.string.mail_accept));
                MaterialButton reject = button(fragment, fragment.getString(R.string.mail_reject));
                String id = msg.optString("id");
                accept.setOnClickListener(v -> respond(fragment, id, true, actions, accept, reject));
                reject.setOnClickListener(v -> respond(fragment, id, false, actions, accept, reject));
                actions.addView(accept);
                actions.addView(reject);
            }
            box.addView(actions);
        } else if (incoming && msg.optBoolean("unread")) {
            Mailbox.post("/mail/read", json("id", msg.optString("id")), ignored -> refreshBadge(fragment));
        }
        return box;
    }

    private static MaterialButton answeredButton(Fragment fragment, String reply) {
        MaterialButton button = button(fragment, fragment.getString(
                "accepted".equals(reply) ? R.string.mail_accepted : R.string.mail_rejected));
        button.setEnabled(false);
        return button;
    }

    private static void respond(Fragment fragment, String id, boolean accept, LinearLayout actions,
            MaterialButton acceptButton, MaterialButton rejectButton) {
        JSONObject body = new JSONObject();
        try {
            body.put("messageId", id);
            body.put("accept", accept);
        } catch (Exception ignored) {
            return;
        }
        acceptButton.setEnabled(false);
        rejectButton.setEnabled(false);
        Mailbox.post("/friends/respond", body, raw -> {
            if (!fragment.isAdded()) {
                return;
            }
            if (raw == null) {
                acceptButton.setEnabled(true);
                rejectButton.setEnabled(true);
                GameNotice.show(fragment.requireContext(), R.string.mail_reply_fail);
                return;
            }
            String reply = accept ? "accepted" : "rejected";
            try {
                reply = new JSONObject(raw).optString("reply", reply);
            } catch (Exception ignored) {
                // se queda la respuesta pulsada
            }
            actions.removeAllViews();
            actions.addView(answeredButton(fragment, reply));
            refreshBadge(fragment);
        });
    }

    private static void deleteMessage(Fragment fragment, String id, View row) {
        Mailbox.post("/mail/delete", json("id", id), raw -> {
            if (!fragment.isAdded()) {
                return;
            }
            if (raw == null) {
                GameNotice.show(fragment.requireContext(), R.string.mail_delete_fail);
                return;
            }
            ViewGroup parent = row.getParent() instanceof ViewGroup ? (ViewGroup) row.getParent() : null;
            if (parent != null) {
                parent.removeView(row);
            }
            refreshBadge(fragment);
        });
    }

    private static void refreshBadge(Fragment fragment) {
        if (fragment instanceof DashboardFragment) {
            ((DashboardFragment) fragment).refreshMailBadge();
        }
    }

    private static void showCompose(Fragment fragment) {
        Mailbox.get("/friends", raw -> {
            if (!fragment.isAdded()) {
                return;
            }
            List<String> ids = new ArrayList<>();
            List<String> names = new ArrayList<>();
            try {
                JSONArray friends = raw == null ? null : new JSONObject(raw).optJSONArray("friends");
                if (friends != null) {
                    for (int i = 0; i < friends.length(); i++) {
                        JSONObject f = friends.getJSONObject(i);
                        ids.add(f.optString("id"));
                        names.add(f.optString("player_name"));
                    }
                }
            } catch (Exception ignored) {
                ids.clear();
            }
            if (ids.isEmpty()) {
                GameNotice.show(fragment.requireContext(), R.string.mail_friends_only);
                return;
            }
            LinearLayout root = column(fragment);
            root.setPadding(dp(fragment, 16), dp(fragment, 16), dp(fragment, 16), dp(fragment, 12));
            root.addView(title(fragment, fragment.getString(R.string.mail_compose)));
            Spinner spinner = new Spinner(fragment.requireContext());
            spinner.setAdapter(new ArrayAdapter<>(fragment.requireContext(),
                    android.R.layout.simple_spinner_dropdown_item, names));
            root.addView(spinner);
            EditText subject = field(fragment, fragment.getString(R.string.mail_subject));
            EditText text = field(fragment, fragment.getString(R.string.mail_body));
            text.setMinLines(4);
            text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            root.addView(subject);
            root.addView(text);
            LinearLayout emojis = row(fragment);
            emojis.setVisibility(View.GONE);
            for (String emoji : EMOJIS) {
                MaterialButton chip = button(fragment, emoji);
                chip.setOnClickListener(v -> text.append(emoji));
                emojis.addView(chip);
            }
            MaterialButton emojiBtn = button(fragment, fragment.getString(R.string.mail_emoji));
            emojiBtn.setOnClickListener(v ->
                    emojis.setVisibility(emojis.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
            MaterialButton send = button(fragment, fragment.getString(R.string.mail_send));
            root.addView(emojiBtn);
            root.addView(emojis);
            root.addView(send);
            Dialog dialog = creamDialog(fragment, root);
            send.setOnClickListener(v -> {
                JSONObject body = new JSONObject();
                try {
                    body.put("toId", ids.get(spinner.getSelectedItemPosition()));
                    body.put("subject", subject.getText().toString().trim());
                    body.put("body", text.getText().toString().trim());
                } catch (Exception ignored) {
                    return;
                }
                Mailbox.post("/mail/send", body, result -> {
                    if (!fragment.isAdded()) {
                        return;
                    }
                    if (result == null) {
                        GameNotice.show(fragment.requireContext(), R.string.mail_send_fail);
                        return;
                    }
                    dialog.dismiss();
                    GameNotice.show(fragment.requireContext(), R.string.mail_sent_ok);
                });
            });
            dialog.show();
        });
    }

    private static void showAddFriend(Fragment fragment, JSONObject known) {
        LinearLayout root = column(fragment);
        root.setPadding(dp(fragment, 16), dp(fragment, 16), dp(fragment, 16), dp(fragment, 12));
        root.addView(title(fragment, fragment.getString(R.string.mail_friendship)));
        TextView result = body(fragment, "");
        MaterialButton action = button(fragment, fragment.getString(R.string.mail_add_friend));
        action.setVisibility(View.GONE);
        final String[] playerId = {known == null ? "" : known.optString("playerId")};
        final String[] link = {known == null ? "none" : known.optString("link")};
        if (known != null) {
            paintFriend(fragment, result, action, known.optString("playerName"), link[0]);
        }
        EditText name = field(fragment, fragment.getString(R.string.mail_exact_name));
        if (known != null) {
            name.setVisibility(View.GONE);
        }
        MaterialButton search = button(fragment, fragment.getString(R.string.mail_search));
        if (known != null) {
            search.setVisibility(View.GONE);
        }
        search.setOnClickListener(v -> Mailbox.get(
                "/friends/lookup?name=" + android.net.Uri.encode(name.getText().toString().trim()),
                raw -> {
                    if (!fragment.isAdded() || raw == null) {
                        result.setText(R.string.mail_nobody);
                        action.setVisibility(View.GONE);
                        return;
                    }
                    try {
                        JSONObject json = new JSONObject(raw);
                        if (!json.optBoolean("found")) {
                            result.setText(R.string.mail_nobody);
                            action.setVisibility(View.GONE);
                            return;
                        }
                        playerId[0] = json.optString("playerId");
                        link[0] = json.optString("link");
                        paintFriend(fragment, result, action, json.optString("playerName"), link[0]);
                    } catch (Exception e) {
                        result.setText(R.string.mail_search_fail);
                    }
                }));
        action.setOnClickListener(v -> {
            boolean remove = "friend".equals(link[0]);
            JSONObject body = new JSONObject();
            try {
                body.put("playerId", playerId[0]);
            } catch (Exception ignored) {
                return;
            }
            Mailbox.post(remove ? "/friends/remove" : "/friends/request", body, raw -> {
                if (!fragment.isAdded()) {
                    return;
                }
                link[0] = remove ? "none" : "pending";
                paintFriend(fragment, result, action, result.getText().toString().split(" · ")[0], link[0]);
                GameNotice.show(fragment.requireContext(),
                        remove ? R.string.mail_removed : R.string.mail_request_sent);
            });
        });
        root.addView(name);
        root.addView(search);
        root.addView(result);
        root.addView(action);
        creamDialog(fragment, root).show();
    }

    private static void paintFriend(Fragment fragment, TextView result, MaterialButton action,
            String name, String link) {
        if ("friend".equals(link)) {
            result.setText(fragment.getString(R.string.mail_already_friends, name));
            action.setText(R.string.mail_remove_friend);
            action.setVisibility(View.VISIBLE);
        } else if ("pending".equals(link)) {
            result.setText(fragment.getString(R.string.mail_pending, name));
            action.setVisibility(View.GONE);
        } else {
            result.setText(name);
            action.setText(R.string.mail_add_friend);
            action.setVisibility(View.VISIBLE);
        }
    }

    private static Dialog creamDialog(Fragment fragment, View content) {
        Dialog dialog = new Dialog(fragment.requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(fragment.requireContext());
        scroll.setBackgroundColor(fragment.requireContext().getColor(R.color.event_cream));
        scroll.addView(content);
        dialog.setContentView(scroll);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return dialog;
    }

    private static LinearLayout column(Fragment fragment) {
        LinearLayout layout = new LinearLayout(fragment.requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private static LinearLayout row(Fragment fragment) {
        LinearLayout layout = new LinearLayout(fragment.requireContext());
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private static TextView title(Fragment fragment, String text) {
        TextView view = new TextView(fragment.requireContext());
        view.setText(text);
        view.setTextColor(fragment.requireContext().getColor(R.color.event_ink));
        view.setTextSize(18);
        view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private static TextView body(Fragment fragment, String text) {
        TextView view = new TextView(fragment.requireContext());
        view.setText(text);
        view.setTextColor(fragment.requireContext().getColor(R.color.event_ink));
        view.setTextSize(14);
        view.setPadding(0, dp(fragment, 4), 0, 0);
        return view;
    }

    private static EditText field(Fragment fragment, String hint) {
        EditText view = new EditText(fragment.requireContext());
        view.setHint(hint);
        view.setTextColor(fragment.requireContext().getColor(R.color.event_ink));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(fragment, 8);
        view.setLayoutParams(lp);
        return view;
    }

    private static MaterialButton button(Fragment fragment, String text) {
        MaterialButton button = new MaterialButton(fragment.requireContext());
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(fragment.requireContext().getColor(R.color.event_ink));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(fragment, 6));
        lp.topMargin = dp(fragment, 6);
        button.setLayoutParams(lp);
        return button;
    }

    private static JSONObject json(String key, String value) {
        JSONObject object = new JSONObject();
        try {
            object.put(key, value);
        } catch (Exception ignored) {
            return object;
        }
        return object;
    }

    private static int dp(Fragment fragment, int value) {
        return Math.round(value * fragment.getResources().getDisplayMetrics().density);
    }
}
