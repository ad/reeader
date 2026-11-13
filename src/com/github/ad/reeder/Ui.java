package com.github.ad.reeder;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static int color(Context c, int res) {
        return c.getColor(res);
    }

    static TextView text(Context c, float sp, int colorRes, boolean bold) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color(c, colorRes));
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static GradientDrawable rounded(Context c, int colorRes, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color(c, colorRes));
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static ImageView icon(Context c, int res, View.OnClickListener click) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        v.setScaleType(ImageView.ScaleType.CENTER);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 48), dp(c, 48)));
        if (click != null) {
            v.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(color(c, R.color.highlight)), null,
                    new android.graphics.drawable.ShapeDrawable(new android.graphics.drawable.shapes.OvalShape())));
            v.setOnClickListener(click);
        }
        return v;
    }

    static View space(Context c, float weight) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, weight));
        return v;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout topBar(Context c, View left, View center, View right) {
        LinearLayout bar = row(c);
        bar.setPadding(dp(c, 4), 0, dp(c, 4), 0);
        bar.addView(left != null ? left : spacer(c, 48));
        bar.addView(space(c, 1));
        if (center != null) bar.addView(center);
        bar.addView(space(c, 1));
        bar.addView(right != null ? right : spacer(c, 48));
        return bar;
    }

    static View refreshButton(Context c, View.OnClickListener click) {
        ImageView v = icon(c, R.drawable.ic_refresh, click);
        FrameLayout f = new FrameLayout(c);
        View bg = new View(c);
        bg.setBackground(rounded(c, R.color.highlight, 14));
        int s = dp(c, 28);
        f.addView(bg, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        v.setScaleX(0.75f);
        v.setScaleY(0.75f);
        f.addView(v, new FrameLayout.LayoutParams(dp(c, 48), dp(c, 48)));
        return f;
    }

    static void spin(View refresh, boolean on) {
        View icon = ((FrameLayout) refresh).getChildAt(1);
        Object a = icon.getTag();
        if (on && a == null) {
            android.animation.ObjectAnimator r = android.animation.ObjectAnimator.ofFloat(icon, "rotation", 0, 360);
            r.setDuration(900);
            r.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            r.setInterpolator(new android.view.animation.LinearInterpolator());
            r.start();
            icon.setTag(r);
        } else if (!on && a != null) {
            ((android.animation.Animator) a).cancel();
            icon.setRotation(0);
            icon.setTag(null);
        }
    }

    static void pull(View refresh, float progress) {
        View icon = ((FrameLayout) refresh).getChildAt(1);
        if (icon.getTag() != null) return;
        icon.setRotation(Math.min(progress, 1f) * 360);
        refresh.setScaleX(progress >= 1 ? 1.15f : 1f);
        refresh.setScaleY(progress >= 1 ? 1.15f : 1f);
    }

    static View spacer(Context c, float dpW) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, dpW), 1));
        return v;
    }

    static void edgeToEdge(Activity a, View root) {
        a.setContentView(root);
        int pl = root.getPaddingLeft(), pt = root.getPaddingTop(), pr = root.getPaddingRight(), pb = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, in) -> {
            int l, t, r, b;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                l = i.left;
                t = i.top;
                r = i.right;
                b = i.bottom;
            } else {
                l = in.getSystemWindowInsetLeft();
                t = in.getSystemWindowInsetTop();
                r = in.getSystemWindowInsetRight();
                b = in.getSystemWindowInsetBottom();
            }
            v.setPadding(pl + l, pt + t, pr + r, pb + b);
            return in;
        });
    }
}
