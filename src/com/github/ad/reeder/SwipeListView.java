package com.github.ad.reeder;

import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;

final class SwipeListView extends ListView {
    interface Callback {
        void onRefresh();

        void onPull(float progress);

        void onSwipe(int position, boolean right);
    }

    static final class Row extends FrameLayout {
        final View front;
        private final View back;
        private final ImageView icon;
        private int leftIcon, rightIcon;

        Row(Context c, View front) {
            super(c);
            back = new View(c);
            back.setBackground(Ui.rounded(c, R.color.highlight, 10));
            addView(back, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            icon = new ImageView(c);
            addView(icon, new LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER_VERTICAL));
            this.front = front;
            addView(front, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            setOffset(0);
        }

        void setIcons(int left, int right) {
            leftIcon = left;
            rightIcon = right;
        }

        void setOffset(float dx) {
            front.setTranslationX(dx);
            boolean show = Math.abs(dx) >= 1;
            back.setVisibility(show ? VISIBLE : INVISIBLE);
            icon.setVisibility(show ? VISIBLE : INVISIBLE);
            if (!show) return;
            int w = getWidth(), a = (int) Math.abs(dx);
            icon.setImageResource(dx > 0 ? leftIcon : rightIcon);
            back.setClipBounds(dx > 0 ? new Rect(0, 0, a, getHeight()) : new Rect(w - a, 0, w, getHeight()));
            float center = dx > 0 ? a / 2f : w - a / 2f;
            icon.setTranslationX(center - icon.getWidth() / 2f);
        }
    }

    private final int slop;
    private final float density;
    private Callback callback;
    private float downX, downY;
    private int mode;
    private Row swipeRow;
    private int swipePos;
    private boolean armed;

    SwipeListView(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        density = c.getResources().getDisplayMetrics().density;
        setDivider(null);
        setSelector(android.R.color.transparent);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    void setCallback(Callback cb) {
        callback = cb;
    }

    private boolean atTop() {
        return getFirstVisiblePosition() == 0 && (getChildCount() == 0 || getChildAt(0).getTop() >= getPaddingTop());
    }

    private void cancelSuper(MotionEvent e) {
        MotionEvent c = MotionEvent.obtain(e);
        c.setAction(MotionEvent.ACTION_CANCEL);
        super.onTouchEvent(c);
        c.recycle();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY = e.getRawY();
                mode = 0;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mode == 0) {
                    if (Math.abs(dx) > slop && Math.abs(dx) > 2 * Math.abs(dy)) {
                        int pos = pointToPosition((int) e.getX(), (int) e.getY());
                        View v = pos == INVALID_POSITION ? null : getChildAt(pos - getFirstVisiblePosition());
                        if (v instanceof Row) {
                            swipeRow = (Row) v;
                            swipePos = pos;
                            mode = 1;
                            armed = false;
                            cancelSuper(e);
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    } else if (dy > slop && dy > Math.abs(dx) && atTop() && callback != null) {
                        mode = 2;
                        armed = false;
                        cancelSuper(e);
                    }
                }
                if (mode == 1) {
                    swipeRow.setOffset(dx - Math.signum(dx) * slop);
                    boolean over = Math.abs(dx) > getWidth() * 0.25f;
                    if (over && !armed) tick();
                    armed = over;
                    return true;
                }
                if (mode == 2) {
                    setTranslationY(Math.max(0, dy) * 0.45f);
                    callback.onPull(getTranslationY() / (64 * density));
                    boolean over = getTranslationY() > 64 * density;
                    if (over && !armed) tick();
                    armed = over;
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mode == 1) {
                    Row row = swipeRow;
                    boolean fire = e.getActionMasked() == MotionEvent.ACTION_UP && armed;
                    android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(row.front.getTranslationX(), 0);
                    a.setDuration(200);
                    a.addUpdateListener(an -> row.setOffset((float) an.getAnimatedValue()));
                    a.start();
                    if (fire) callback.onSwipe(swipePos, dx > 0);
                    mode = 0;
                    return true;
                }
                if (mode == 2) {
                    callback.onPull(0);
                    if (e.getActionMasked() == MotionEvent.ACTION_UP && getTranslationY() > 64 * density) callback.onRefresh();
                    animate().translationY(0).setDuration(200).start();
                    mode = 0;
                    return true;
                }
                break;
        }
        return super.onTouchEvent(e);
    }

    private void tick() {
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    @Override
    public boolean performHapticFeedback(int feedbackConstant) {
        // AbsListView fires the heavy LONG_PRESS itself after onItemLongClick
        return super.performHapticFeedback(feedbackConstant == HapticFeedbackConstants.LONG_PRESS
                ? HapticFeedbackConstants.KEYBOARD_TAP : feedbackConstant);
    }
}
