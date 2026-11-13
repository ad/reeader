package com.github.ad.reeder;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class LoginActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        App.init(this);
        LinearLayout root = Ui.column(this);
        root.setPadding(Ui.dp(this, 24), Ui.dp(this, 48), Ui.dp(this, 24), 0);
        TextView title = Ui.text(this, 34, R.color.text, true);
        title.setText("FreshRSS");
        root.addView(title);
        TextView hint = Ui.text(this, 13, R.color.text2, false);
        hint.setText("Use the API password from FreshRSS → Settings → Profile.");
        hint.setPadding(0, 0, 0, Ui.dp(this, 24));
        root.addView(hint);
        EditText server = field("Server, e.g. https://rss.example.com", InputType.TYPE_TEXT_VARIATION_URI);
        EditText user = field("Username", InputType.TYPE_CLASS_TEXT);
        EditText pass = field("API password", InputType.TYPE_TEXT_VARIATION_PASSWORD);
        server.setText(App.prefs.getString("base", ""));
        user.setText(App.prefs.getString("user", ""));
        for (EditText e : new EditText[]{server, user, pass}) {
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
            String base = Logic.apiBase(server.getText().toString());
            String u = user.getText().toString().trim(), p = pass.getText().toString();
            login.setEnabled(false);
            error.setText("");
            new Thread(() -> {
                try {
                    String auth = Api.login(base, u, p);
                    runOnUiThread(() -> {
                        App.prefs.edit().putString("base", base).putString("user", u).putString("auth", auth).apply();
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        login.setEnabled(true);
                        error.setText("Login failed: " + e.getMessage());
                    });
                }
            }).start();
        });
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
