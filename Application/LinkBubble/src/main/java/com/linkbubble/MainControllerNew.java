package com.linkbubble;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.widget.Toast;
import com.linkbubble.physics.Draggable;
import com.linkbubble.physics.DraggableHelper;
import com.linkbubble.ui.BubbleDraggable;
import com.linkbubble.ui.BubbleLegacyView;
import com.linkbubble.ui.BubblePagerDraggable;
import com.linkbubble.ui.ContentView;
import com.linkbubble.util.Util;


public class MainControllerNew extends MainController {

    public static void create(Context context, EventHandler eventHandler) {
        if (sInstance != null) {
            new RuntimeException("Only one instance of MainController allowed at any one time");
        }
        sInstance = new MainControllerNew(context, eventHandler);
    }

    private BubblePagerDraggable mBubblePagerDraggable;
    private BubbleDraggable mBubbleDraggable;

    protected MainControllerNew(Context context, EventHandler eventHandler) {
        super(context, eventHandler);

        LayoutInflater inflater = LayoutInflater.from(mContext);
        mBubblePagerDraggable = (BubblePagerDraggable) inflater.inflate(R.layout.view_bubble_pager, null);
        mBubblePagerDraggable.configure(0, 0, 0, 0, 0.f, null);
        mBubblePagerDraggable.setVisibility(View.GONE);

        mBubbleDraggable = (BubbleDraggable) inflater.inflate(R.layout.view_bubble_draggable, null);
        mBubbleDraggable.configure((int) (Config.mBubbleSnapLeftX - Config.mBubbleWidth), Config.BUBBLE_HOME_Y,
                Config.BUBBLE_HOME_X, Config.BUBBLE_HOME_Y, 0.4f, new BubbleDraggable.EventHandler() {
            @Override
            public void onMotionEvent_Touch(BubbleDraggable sender, DraggableHelper.TouchEvent event) {
                mCurrentState.onTouchActionDown(sender, event);
            }

            @Override
            public void onMotionEvent_Move(BubbleDraggable sender, DraggableHelper.MoveEvent event) {
                mCurrentState.onTouchActionMove(sender, event);
            }

            @Override
            public void onMotionEvent_Release(BubbleDraggable sender, DraggableHelper.ReleaseEvent event) {
                mCurrentState.onTouchActionRelease(sender, event);
            }
        });

        mBubbleDraggable.setOnUpdateListener(new BubbleDraggable.OnUpdateListener() {
            @Override
            public void onUpdate(Draggable draggable, float dt, boolean contentView) {
                mBubblePagerDraggable.syncWithBubble(draggable);
            }
        });
    }

    @Override
    public void updateIncognitoMode(boolean incognito) {
        CookieSyncManager.createInstance(mContext);
        CookieManager.getInstance().setAcceptCookie(!incognito);

        if (mBubblePagerDraggable != null) {
            mBubblePagerDraggable.updateIncognitoMode(incognito);
        }
    }

    @Override
    public int getBubbleCount() {
        return mBubblePagerDraggable != null ? mBubblePagerDraggable.getBubbleCount() : 0;
    }

    @Override
    protected void openUrlInBubble(String url, long startTime) {
        if (mDraggables.contains(mBubbleDraggable) == false) {
            mDraggables.add(mBubbleDraggable);
        }
        if (mFrontDraggable == null) {
            int x, targetX, y, targetY;
            float time;

            int bubbleIndex = mDraggables.size();

            if (mCurrentState == STATE_ContentView) {
                x = (int) Config.getContentViewX(bubbleIndex, getBubbleCount()+1);
                y = (int) -Config.mBubbleHeight;
                targetX = x;
                targetY = Config.mContentViewBubbleY;
                time = 0.4f;
            } else {
                if (bubbleIndex == 0) {
                    x = (int) (Config.mBubbleSnapLeftX - Config.mBubbleWidth);
                    y = Config.BUBBLE_HOME_Y;
                    targetX = Config.BUBBLE_HOME_X;
                    targetY = y;
                    time = 0.4f;
                } else {
                    x = Config.BUBBLE_HOME_X;
                    y = Config.BUBBLE_HOME_Y;
                    targetX = x;
                    targetY = y;
                    time = 0.0f;
                }
            }

            setActiveDraggable(mBubbleDraggable);

            mBubbleDraggable.setExactPos(x, y);
            mBubbleDraggable.setTargetPos(targetX, targetY, time, true);
        }

        mBubblePagerDraggable.openUrlInBubble(url, startTime);
        ++mBubblesLoaded;
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        mUpdateScheduled = false;

        float dt = 1.0f / 60.0f;

        int draggableCount = mDraggables.size();
        for (int i=0 ; i < draggableCount ; ++i) {
            Draggable draggable = mDraggables.get(i);
            draggable.update(dt, mCurrentState == STATE_ContentView);
        }

        Draggable frontDraggable = null;
        if (getBubbleCount() > 0) {
            frontDraggable = getActiveDraggable();
        }
        mCanvasView.update(dt, frontDraggable);

        if (mCurrentState.onUpdate(dt)) {
            scheduleUpdate();
        }

        //mTextView.setText("S=" + mCurrentState.getName() + " F=" + mFrameNumber++);

        if (mCurrentState == STATE_BubbleView && mDraggables.size() == 0 &&
                mBubblesLoaded > 0 && !mUpdateScheduled) {
            mEventHandler.onDestroy();
        }
    }

    @Override
    public ContentView getActiveContentView() {
        return mBubblePagerDraggable.getContentView();
    }

    @Override
    public boolean destroyDraggable(Draggable draggable, Config.BubbleAction action) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        boolean debug = prefs.getBoolean("debug_flick", true);

        if (debug) {
            Toast.makeText(mContext, "HIT TARGET!", 400).show();
        } else {
            String currentBubbleUrl = mBubblePagerDraggable.getContentView().getCurrentUrl();
            mBubblePagerDraggable.destroyCurrentBubble();
            if (mBubblePagerDraggable.getBubbleCount() == 0) {
                removeBubbleDraggable();

                Config.BUBBLE_HOME_X = Config.mBubbleSnapLeftX;
                Config.BUBBLE_HOME_Y = (int) (Config.mScreenHeight * 0.4f);
            }

            mCurrentState.onDestroyDraggable(null);

            doTargetAction(action, currentBubbleUrl);
        }

        return getBubbleCount() > 0;
    }

    @Override
    public void destroyAllBubbles() {
        mBubblePagerDraggable.destroyAllBubbles();
        removeBubbleDraggable();
    }

    private void removeBubbleDraggable() {
        mBubbleDraggable.destroy();
        mDraggables.remove(mBubbleDraggable);
        if (mFrontDraggable == mBubbleDraggable) {
            mFrontDraggable = null;
        }
    }

    @Override
    public void showBubblePager(boolean show) {
        if (show) {
            mBubblePagerDraggable.show();
        } else {
            mBubblePagerDraggable.hide();
        }
        mBubbleDraggable.setVisibility(show ? View.GONE : View.VISIBLE);
    }
}