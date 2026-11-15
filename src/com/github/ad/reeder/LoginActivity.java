package com.github.ad.reeder;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class LoginActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        App.init(this);
        String type = getIntent().getStringExtra("type");
        if (type == null) {
            pickService();
            return;
        }
        Service s = Service.of(type);
        LinearLayout outer = Ui.column(this);
        outer.addView(Ui.topBar(this, Ui.icon(this, R.drawable.ic_back, v -> finish()), null, null));
        LinearLayout root = Ui.column(this);
        root.setPadding(Ui.dp(this, 24), Ui.dp(this, 8), Ui.dp(this, 24), 0);
        outer.addView(root);
        TextView title = Ui.text(this, 34, R.color.text, true);
        title.setText(s.title);
        root.addView(title);
        TextView hint = Ui.text(this, 13, R.color.text2, false);
        hint.setText(s.hint);
        hint.setPadding(0, 0, 0, Ui.dp(this, 24));
        root.addView(hint);
        EditText server = field(s.serverHint(), InputType.TYPE_TEXT_VARIATION_URI);
        EditText user = field("Username", InputType.TYPE_CLASS_TEXT);
        EditText pass = field(s.passwordHint(), InputType.TYPE_TEXT_VARIATION_PASSWORD);
        server.setText(App.prefs.getString("base", ""));
        user.setText(App.prefs.getString("user", ""));
        for (EditText e : s.host == null ? new EditText[]{server, user, pass} : new EditText[]{user, pass}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = Ui.dp(this, 12);
            root.addView(e, lp);
        }
        TextView error = Ui.text(this, 13, R.color.accent, false);
        root.addView(error);
        Button login = new Button(this);
        login.setText("Log In");
        login.setTextColor(0xFFFFFFFF);
        login.setBackground(Ui.rounded(this, R.color.accent, 10));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 48));
        blp.topMargin = Ui.dp(this, 12);
        root.addView(login, blp);
        login.setOnClickListener(v -> {
            String base = s.apiBase(server.getText().toString());
            String u = user.getText().toString().trim(), p = pass.getText().toString();
            login.setEnabled(false);
            error.setText("");
            new Thread(() -> {
                try {
                    String auth = s.login(base, u, p);
                    runOnUiThread(() -> {
                        App.prefs.edit().putString("type", s.id).putString("base", base).putString("user", u).putString("auth", auth).apply();
                        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        login.setEnabled(true);
                        error.setText("Login failed: " + e.getMessage());
                    });
                }
            }).start();
        });
        Ui.edgeToEdge(this, outer);
    }

    private void pickService() {
        LinearLayout root = Ui.column(this);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 48), Ui.dp(this, 16), 0);
        SettingsActivity.title(this, root, "Add Account", null);
        for (boolean hosted : new boolean[]{true, false}) {
            SettingsActivity.section(this, root, hosted ? "SERVICES" : "SELF-HOSTED");
            LinearLayout card = SettingsActivity.card(this, root);
            for (Service s : Service.ALL) {
                if ((s.host != null) != hosted) continue;
                LinearLayout row = SettingsActivity.rowIn(this, card, s.title);
                TextView label = (TextView) row.getChildAt(0);
                row.removeView(label);
                LinearLayout text = Ui.column(this);
                text.addView(label);
                TextView sub = Ui.text(this, 13, R.color.text2, false);
                sub.setText(s.subtitle);
                text.addView(sub);
                row.addView(text, 0, new LinearLayout.LayoutParams(0, -2, 1));
                ImageView chevron = new ImageView(this);
                chevron.setImageResource(R.drawable.ic_chevron_right);
                chevron.setAlpha(0.6f);
                row.addView(chevron, new LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18)));
                row.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class).putExtra("type", s.id)));
            }
        }
        Ui.edgeToEdge(this, root);
    }

    private EditText field(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine();
        e.setInputType(InputType.TYPE_CLASS_TEXT | type);
        e.setTypeface(android.graphics.Typeface.DEFAULT);
        e.setTextColor(Ui.color(this, R.color.text));
        e.setHintTextColor(Ui.color(this, R.color.text2));
        e.setBackground(Ui.rounded(this, R.color.card, 10));
        e.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        return e;
    }
}
