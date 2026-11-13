package com.github.ad.reeder;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.TextView;

final class PullFrame extends FrameLayout {
    interface Callback {
        String neighbor(int dir);

        void go(int dir);
    }

    private final int slop;
    private final float threshold;
    private final TextView topLabel, bottomLabel;
    private View target;
    private Callback callback;
    private float y0;
    private int dir;

    PullFrame(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        threshold = Ui.dp(c, 90);
        topLabel = label(c, Gravity.TOP);
        bottomLabel = label(c, Gravity.BOTTOM);
    }

    private TextView label(Context c, int gravity) {
        TextView t = Ui.text(c, 14, R.color.text2, true);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        t.setPadding(Ui.dp(c, 32), Ui.dp(c, 20), Ui.dp(c, 32), Ui.dp(c, 20));
        t.setAlpha(0);
        addView(t, new LayoutParams(-1, -2, gravity));
        return t;
    }

    void setTarget(View v, Callback cb) {
        target = v;
        callback = cb;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                y0 = e.getY();
                dir = 0;
                break;
            case MotionEvent.ACTION_MOVE:
                float dy = e.getY() - y0;
                if (dy < -slop && !target.canScrollVertically(1)) dir = 1;
                else if (dy > slop && !target.canScrollVertically(-1)) dir = -1;
                if (dir != 0) {
                    String title = callback.neighbor(dir);
                    TextView l = dir > 0 ? bottomLabel : topLabel;
                    l.setText(title == null ? (dir > 0 ? "No more articles" : "") : (dir > 0 ? "Next\n" : "Previous\n") + title);
                    y0 = e.getY();
                    return true;
                }
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (dir == 0) return false;
        float dy = e.getY() - y0;
        float t = dir > 0 ? Math.min(0, dy) * 0.45f : Math.max(0, dy) * 0.45f;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                target.setTranslationY(t);
                (dir > 0 ? bottomLabel : topLabel).setAlpha(Math.min(1, Math.abs(t) / threshold));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean fire = e.getActionMasked() == MotionEvent.ACTION_UP && Math.abs(target.getTranslationY()) >= threshold
                        && callback.neighbor(dir) != null;
                int d = dir;
                dir = 0;
                target.animate().translationY(0).setDuration(200).start();
                topLabel.animate().alpha(0).setDuration(200).start();
                bottomLabel.animate().alpha(0).setDuration(200).start();
                if (fire) callback.go(d);
                return true;
        }
        return true;
    }
}
