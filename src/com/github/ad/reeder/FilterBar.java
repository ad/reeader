package com.github.ad.reeder;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class FilterBar extends LinearLayout {
    interface Listener {
        void onFilter(int filter);
    }

    private static final int[] ICONS = {R.drawable.ic_star, R.drawable.ic_dot, R.drawable.ic_lines};
    private static final String[] LABELS = {"STARRED", "UNREAD", "ALL"};
    private final LinearLayout[] tabs = new LinearLayout[3];
    private final boolean compact;

    FilterBar(Context c, View left, View right, Listener l) {
        super(c);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4));
        addView(left != null ? left : Ui.spacer(c, 48));
        addView(Ui.space(c, 1));
        for (int i = 0; i < 3; i++) {
            int f = i;
            LinearLayout tab = Ui.row(c);
            tab.setGravity(Gravity.CENTER);
            tab.setMinimumWidth(Ui.dp(c, 56));
            tab.setMinimumHeight(Ui.dp(c, 32));
            ImageView icon = new ImageView(c);
            icon.setImageResource(ICONS[i]);
            tab.addView(icon, new LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18)));
            TextView label = Ui.text(c, 11, R.color.icon, true);
            label.setText(LABELS[i]);
            label.setLetterSpacing(0.05f);
            label.setPadding(Ui.dp(c, 6), 0, 0, 0);
            tab.addView(label);
            tab.setOnClickListener(v -> {
                select(f);
                App.setFilter(f);
                l.onFilter(f);
            });
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.setMargins(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
            addView(tab, lp);
            tabs[i] = tab;
        }
        addView(Ui.space(c, 1));
        addView(right != null ? right : Ui.spacer(c, 48));
        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED); // all labels visible: the widest the bar gets
        compact = getMeasuredWidth() > c.getResources().getDisplayMetrics().widthPixels;
        select(App.filter());
    }

    void select(int f) {
        for (int i = 0; i < 3; i++) {
            boolean on = i == f;
            tabs[i].setBackground(on ? Ui.rounded(getContext(), R.color.highlight, 16) : null);
            tabs[i].setPadding(Ui.dp(getContext(), on ? 14 : 8), 0, Ui.dp(getContext(), on ? 14 : 8), 0);
            tabs[i].getChildAt(1).setVisibility(on && !compact ? VISIBLE : GONE);
        }
    }
}
