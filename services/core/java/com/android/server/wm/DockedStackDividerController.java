/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wm;

import android.content.Context;
import android.graphics.Rect;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.Log;
import android.util.Slog;
import android.view.DisplayInfo;
import android.view.IDockedStackListener;
import android.view.SurfaceControl;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;

import com.android.server.wm.DimLayer.DimLayerUser;

import java.util.ArrayList;

import static android.app.ActivityManager.StackId.DOCKED_STACK_ID;
import static android.app.ActivityManager.StackId.INVALID_STACK_ID;
import static android.view.WindowManager.DOCKED_BOTTOM;
import static android.view.WindowManager.DOCKED_LEFT;
import static android.view.WindowManager.DOCKED_RIGHT;
import static android.view.WindowManager.DOCKED_TOP;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WM;

/**
 * Keeps information about the docked stack divider.
 */
public class DockedStackDividerController implements DimLayerUser {

    private static final String TAG = TAG_WITH_CLASS_NAME ? "DockedStackDividerController" : TAG_WM;

    private static final long MINIMIZED_DOCK_ANIMATION_DURATION = 400;

    private final WindowManagerService mService;
    private final DisplayContent mDisplayContent;
    private final int mDividerWindowWidth;
    private final int mDividerInsets;
    private boolean mResizing;
    private WindowState mWindow;
    private final Rect mTmpRect = new Rect();
    private final Rect mTmpRect2 = new Rect();
    private final Rect mLastRect = new Rect();
    private boolean mLastVisibility = false;
    private final RemoteCallbackList<IDockedStackListener> mDockedStackListeners
            = new RemoteCallbackList<>();
    private final DimLayer mDimLayer;

    private boolean mMinimizedDock;
    private boolean mAnimating;
    private boolean mAnimationStarted;
    private long mAnimationStartTime;
    private float mAnimationStart;
    private float mAnimationTarget;
    private final Interpolator mMinimizedDockInterpolator;

    DockedStackDividerController(WindowManagerService service, DisplayContent displayContent) {
        mService = service;
        mDisplayContent = displayContent;
        final Context context = service.mContext;
        mDividerWindowWidth = context.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.docked_stack_divider_thickness);
        mDividerInsets = context.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.docked_stack_divider_insets);
        mDimLayer = new DimLayer(displayContent.mService, this, displayContent.getDisplayId());
        mMinimizedDockInterpolator = AnimationUtils.loadInterpolator(
                context, android.R.interpolator.fast_out_slow_in);
    }

    boolean isResizing() {
        return mResizing;
    }

    int getContentWidth() {
        return mDividerWindowWidth - 2 * mDividerInsets;
    }

    int getContentInsets() {
        return mDividerInsets;
    }

    void setResizing(boolean resizing) {
        if (mResizing != resizing) {
            mResizing = resizing;
            resetDragResizingChangeReported();
        }
    }

    private void resetDragResizingChangeReported() {
        final WindowList windowList = mDisplayContent.getWindowList();
        for (int i = windowList.size() - 1; i >= 0; i--) {
            windowList.get(i).resetDragResizingChangeReported();
        }
    }

    void setWindow(WindowState window) {
        mWindow = window;
        reevaluateVisibility(false);
    }

    void reevaluateVisibility(boolean force) {
        if (mWindow == null) {
            return;
        }
        TaskStack stack = mDisplayContent.mService.mStackIdToStack.get(DOCKED_STACK_ID);

        // If the stack is invisible, we policy force hide it in WindowAnimator.shouldForceHide
        final boolean visible = stack != null;
        if (mLastVisibility == visible && !force) {
            return;
        }
        mLastVisibility = visible;
        notifyDockedDividerVisibilityChanged(visible);
        if (!visible) {
            setResizeDimLayer(false, INVALID_STACK_ID, 0f);
        }
    }

    boolean wasVisible() {
        return mLastVisibility;
    }

    void positionDockedStackedDivider(Rect frame) {
        TaskStack stack = mDisplayContent.getDockedStackLocked();
        if (stack == null) {
            // Unfortunately we might end up with still having a divider, even though the underlying
            // stack was already removed. This is because we are on AM thread and the removal of the
            // divider was deferred to WM thread and hasn't happened yet. In that case let's just
            // keep putting it in the same place it was before the stack was removed to have
            // continuity and prevent it from jumping to the center. It will get hidden soon.
            frame.set(mLastRect);
            return;
        } else {
            stack.getDimBounds(mTmpRect);
        }
        int side = stack.getDockSide();
        switch (side) {
            case DOCKED_LEFT:
                frame.set(mTmpRect.right - mDividerInsets, frame.top,
                        mTmpRect.right + frame.width() - mDividerInsets, frame.bottom);
                break;
            case DOCKED_TOP:
                frame.set(frame.left, mTmpRect.bottom - mDividerInsets,
                        mTmpRect.right, mTmpRect.bottom + frame.height() - mDividerInsets);
                break;
            case DOCKED_RIGHT:
                frame.set(mTmpRect.left - frame.width() + mDividerInsets, frame.top,
                        mTmpRect.left + mDividerInsets, frame.bottom);
                break;
            case DOCKED_BOTTOM:
                frame.set(frame.left, mTmpRect.top - frame.height() + mDividerInsets,
                        frame.right, mTmpRect.top + mDividerInsets);
                break;
        }
        mLastRect.set(frame);
    }

    void notifyDockedDividerVisibilityChanged(boolean visible) {
        final int size = mDockedStackListeners.beginBroadcast();
        for (int i = 0; i < size; ++i) {
            final IDockedStackListener listener = mDockedStackListeners.getBroadcastItem(i);
            try {
                listener.onDividerVisibilityChanged(visible);
            } catch (RemoteException e) {
                Slog.e(TAG_WM, "Error delivering divider visibility changed event.", e);
            }
        }
        mDockedStackListeners.finishBroadcast();
    }

    void notifyDockedStackExistsChanged(boolean exists) {
        final int size = mDockedStackListeners.beginBroadcast();
        for (int i = 0; i < size; ++i) {
            final IDockedStackListener listener = mDockedStackListeners.getBroadcastItem(i);
            try {
                listener.onDockedStackExistsChanged(exists);
            } catch (RemoteException e) {
                Slog.e(TAG_WM, "Error delivering docked stack exists changed event.", e);
            }
        }
        mDockedStackListeners.finishBroadcast();
    }

    void notifyDockedStackMinimizedChanged(boolean minimizedDock, long animDuration) {
        final int size = mDockedStackListeners.beginBroadcast();
        for (int i = 0; i < size; ++i) {
            final IDockedStackListener listener = mDockedStackListeners.getBroadcastItem(i);
            try {
                listener.onDockedStackMinimizedChanged(minimizedDock, animDuration);
            } catch (RemoteException e) {
                Slog.e(TAG_WM, "Error delivering minimized dock changed event.", e);
            }
        }
        mDockedStackListeners.finishBroadcast();
    }

    void registerDockedStackListener(IDockedStackListener listener) {
        mDockedStackListeners.register(listener);
        notifyDockedDividerVisibilityChanged(wasVisible());
        notifyDockedStackExistsChanged(
                mDisplayContent.mService.mStackIdToStack.get(DOCKED_STACK_ID) != null);
    }

    void setResizeDimLayer(boolean visible, int targetStackId, float alpha) {
        SurfaceControl.openTransaction();
        TaskStack stack = mDisplayContent.mService.mStackIdToStack.get(targetStackId);
        boolean visibleAndValid = visible && stack != null;
        if (visibleAndValid) {
            stack.getDimBounds(mTmpRect);
            if (mTmpRect.height() > 0 && mTmpRect.width() > 0) {
                mDimLayer.setBounds(mTmpRect);
                mDimLayer.show(mDisplayContent.mService.mLayersController.getResizeDimLayer(),
                        alpha, 0 /* duration */);
            } else {
                visibleAndValid = false;
            }
        }
        if (!visibleAndValid) {
            mDimLayer.hide();
        }
        SurfaceControl.closeTransaction();
    }

    /**
     * Notifies the docked stack divider controller of a visibility change that happens without
     * an animation.
     */
    void notifyAppVisibilityChanged(AppWindowToken wtoken, boolean visible) {
        final Task task = wtoken.mTask;
        if (!task.isHomeTask() || !task.isVisibleForUser()) {
            return;
        }

        // If the stack is completely offscreen, this might just be an intermediate state when
        // docking a task/launching recents at the same time, but home doesn't actually get
        // visible after the state settles in.
        if (isWithinDisplay(task)
                && mDisplayContent.getDockedStackVisibleForUserLocked() != null) {
            setMinimizedDockedStack(visible, false /* animate */);
        }
    }

    void notifyAppTransitionStarting(ArraySet<AppWindowToken> openingApps,
            ArraySet<AppWindowToken> closingApps) {
        if (containsHomeTaskWithinDisplay(openingApps)) {
            setMinimizedDockedStack(true /* minimized */, true /* animate */);
        } else if (containsHomeTaskWithinDisplay(closingApps)) {
            setMinimizedDockedStack(false /* minimized */, true /* animate */);
        }
    }

    private boolean containsHomeTaskWithinDisplay(ArraySet<AppWindowToken> apps) {
        for (int i = apps.size() - 1; i >= 0; i--) {
            final Task task = apps.valueAt(i).mTask;
            if (task != null && task.isHomeTask()) {
                return isWithinDisplay(task);
            }
        }
        return false;
    }

    private boolean isWithinDisplay(Task task) {
        task.mStack.getBounds(mTmpRect);
        mDisplayContent.getLogicalDisplayRect(mTmpRect2);
        return mTmpRect.intersect(mTmpRect2);
    }

    /**
     * Sets whether the docked stack is currently in a minimized state, i.e. all the tasks in the
     * docked stack are heavily clipped so you can only see a minimal peek state.
     *
     * @param minimizedDock Whether the docked stack is currently minimized.
     * @param animate Whether to animate the change.
     */
    private void setMinimizedDockedStack(boolean minimizedDock, boolean animate) {
        if (minimizedDock == mMinimizedDock
                || mDisplayContent.getDockedStackVisibleForUserLocked() == null) {
            return;
        }

        mMinimizedDock = minimizedDock;
        if (minimizedDock) {
            if (animate) {
                startAdjustAnimation(0f, 1f);
            } else {
                setMinimizedDockedStack(true);
            }
        } else {
            if (animate) {
                startAdjustAnimation(1f, 0f);
            } else {
                setMinimizedDockedStack(false);
            }
        }
    }

    private void startAdjustAnimation(float from, float to) {
        mAnimating = true;
        mAnimationStarted = false;
        mAnimationStart = from;
        mAnimationTarget = to;
    }

    private void setMinimizedDockedStack(boolean minimized) {
        final TaskStack stack = mDisplayContent.getDockedStackVisibleForUserLocked();
        if (stack == null) {
            return;
        }
        if (stack.setAdjustedForMinimizedDock(minimized ? 1f : 0f)) {
            mService.mWindowPlacerLocked.performSurfacePlacement();
        }
        notifyDockedStackMinimizedChanged(minimized, 0);
    }

    public boolean animate(long now) {
        if (!mAnimating) {
            return false;
        }

        if (!mAnimationStarted) {
            mAnimationStarted = true;
            mAnimationStartTime = now;
            notifyDockedStackMinimizedChanged(mMinimizedDock,
                    MINIMIZED_DOCK_ANIMATION_DURATION);
        }
        float t = Math.min(1f, (float) (now - mAnimationStartTime)
                / MINIMIZED_DOCK_ANIMATION_DURATION);
        t = mMinimizedDockInterpolator.getInterpolation(t);
        final TaskStack stack = mDisplayContent.getDockedStackVisibleForUserLocked();
        if (stack != null) {
            final float amount = t * mAnimationTarget + (1 - t) * mAnimationStart;
            if (stack.setAdjustedForMinimizedDock(amount)) {
                mService.mWindowPlacerLocked.performSurfacePlacement();
            }
        }
        if (t >= 1.0f) {
            mAnimating = false;
            return false;
        } else {
            return true;
        }
    }

    @Override
    public boolean isFullscreen() {
        return false;
    }

    @Override
    public DisplayInfo getDisplayInfo() {
        return mDisplayContent.getDisplayInfo();
    }

    @Override
    public void getDimBounds(Rect outBounds) {
        // This dim layer user doesn't need this.
    }

    @Override
    public String toShortString() {
        return TAG;
    }
}
