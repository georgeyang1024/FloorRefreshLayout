/*
 * Copyright (C) 2013 The Android Open Source Project
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

package cn.georgeyang.floorrefreshlayout;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Transformation;
import android.widget.AbsListView;
import android.widget.TextView;


/**
 * 改版的SwipeRefreshLayout，下拉刷新，继续下拉进入二楼
 * 参考：http://blog.csdn.net/gesanri/article/details/50149059
 */
public class FloorRefreshLayout extends ViewGroup {
	private static final String LOG_TAG = FloorRefreshLayout.class
			.getSimpleName();

	private static final long RETURN_TO_ORIGINAL_POSITION_TIMEOUT = 300;
	private static final float DECELERATE_INTERPOLATION_FACTOR = 2f;
	private static final float MAX_SWIPE_DISTANCE_FACTOR = .6f;
	private static final int REFRESH_TRIGGER_DISTANCE = 120;
	private static final int INVALID_POINTER = -1;

	private View mTarget; // the content that gets pulled down
	private int mOriginalOffsetTop;
	private OnRefreshListener mListener;
	private int mFrom;
	private boolean mRefreshing = false;
	private int mTouchSlop;
	private float mDistanceToTriggerSync = -1;
	private int mMediumAnimationDuration;
	private int mCurrentTargetOffsetTop;

	private float mInitialMotionY;
	private float mLastMotionY;
	private boolean mIsBeingDragged;
	private int mActivePointerId = INVALID_POINTER;

	// Target is returning to its start offset because it was cancelled or a
	// refresh was triggered.
	private boolean mReturningToStart;
	private final DecelerateInterpolator mDecelerateInterpolator;
	private static final int[] LAYOUT_ATTRS = new int[] { android.R.attr.enabled };

	private View mHeaderView;
	private int mHeaderHeight;
	private STATUS mStatus = STATUS.NORMAL;
	private boolean mDisable; // 用来控制控件是否允许滚动


    //Home SwipeRefreshLayout
    private View animNextPageView;
    private TextView statusTextView;
    private int nextPageScrollDist;
    private int pullToRefreshDist;
    private boolean isSlowAnim;
    private DisplayMetrics metrics;
    private int nextPageScrollOffset;//下拉到二楼时的距离，隐藏掉文字那块

    private static final String tip0 = "下拉开始刷新";
    private static final String tip1 = "松开刷新界面,继续下拉去签到抽奖";
    private static final String tip2 = "松开去签到抽奖";
    private static final String tip_loading = "刷新中";

    private enum STATUS {
		NORMAL, LOOSEN,DOUBLE_LOOSEN, REFRESHING
	}

	private boolean doubleLoosenEnable;

    public void setDoubleLoosenEnable(boolean doubleLoosenEnable) {
        this.doubleLoosenEnable = doubleLoosenEnable;
    }

    /**
     * 回到顶部
     */
	private final Animation mAnimateToStartPosition = new Animation() {
		@Override
		public void applyTransformation(float interpolatedTime, Transformation t) {
			int targetTop = 0;
			if (mFrom != mOriginalOffsetTop) {
				targetTop = (mFrom + (int) ((mOriginalOffsetTop - mFrom) * interpolatedTime));
			}

			int offset = targetTop - mTarget.getTop();
			final int currentTop = mTarget.getTop();

			if (offset + currentTop < 0) {
				offset = 0 - currentTop;
			}
			setTargetOffsetTopAndBottom(offset);
		}
	};

    /**
     * 下拉到第二页
     */
	private final Animation mAnimateToNextPage = new Animation() {
		@Override
		public void applyTransformation(float interpolatedTime, Transformation t) {
			int targetTop = 0;
            if (nextPageScrollOffset<=0) {
                nextPageScrollOffset = mTarget.getMeasuredHeight() + (mHeaderHeight - statusTextView.getTop());
            }
            int mTo = nextPageScrollOffset;
			if (mFrom != mTo) {
				targetTop = (mFrom + (int) ((mTo - mFrom) * interpolatedTime));
			}

			int offset = targetTop - mTarget.getTop();
			final int currentTop = mTarget.getTop();

			if (offset + currentTop < 0) {
				offset = 0 - currentTop;
			}
			setTargetOffsetTopAndBottom(offset);
		}
	};

    /**
     * 重置刷新高度
     */
    private final Animation mAnimateToHeaderPosition = new Animation() {
        @Override
        public void applyTransformation(float interpolatedTime, Transformation t) {
            int targetTop = 0;
            int mTo = pullToRefreshDist;
            if (mFrom != mTo) {
                targetTop = (mFrom + (int) ((mTo - mFrom) * interpolatedTime));
            }

            int offset = targetTop - mTarget.getTop();
            final int currentTop = mTarget.getTop();

            if (offset + currentTop < 0) {
                offset = 0 - currentTop;
            }
            setTargetOffsetTopAndBottom(offset);
        }
    };


	private final AnimationListener mReturnToStartPositionListener = new BaseAnimationListener() {
		@Override
		public void onAnimationEnd(Animation animation) {
			// Once the target content has returned to its start position, reset
			// the target offset to 0
			mCurrentTargetOffsetTop = 0;
			mStatus = STATUS.NORMAL;
			mDisable = false;
            if (statusTextView!=null) {
                statusTextView.setText(tip0);
            }
            isSlowAnim = false;
		}
	};
	
	private final AnimationListener mReturnToNextPageListener = new BaseAnimationListener() {
		@Override
		public void onAnimationEnd(Animation animation) {
            // Once the target content has returned to its start position, reset
            // the target offset to 0
            if (mStatus==STATUS.DOUBLE_LOOSEN && mListener != null) {
                mListener.onPullToNextPage(animNextPageView);
            }

            mStatus = STATUS.NORMAL;
            mDisable = false;
            if (statusTextView!=null) {
                statusTextView.setText(tip0);
            }

            postDelayed(new Runnable() {
                @Override
                public void run() {
                    resetToTop();
                }
            },500);
		}
	};

    private final AnimationListener mReturnToHeaderPositionListener = new BaseAnimationListener() {
        @Override
        public void onAnimationEnd(Animation animation) {
            // Once the target content has returned to its start position, reset
            // the target offset to 0
            mCurrentTargetOffsetTop = mHeaderHeight;
            mStatus = STATUS.REFRESHING;
        }
    };

	private final Runnable mReturnToStartPosition = new Runnable() {
		@Override
		public void run() {
			mReturningToStart = true;
			animateOffsetToStartPosition(mCurrentTargetOffsetTop
					+ getPaddingTop(), mReturnToStartPositionListener);
		}
	};

    /**
     * 开始刷新的高度
     */
	private final Runnable mReturnToHeaderPosition = new Runnable() {
		@Override
		public void run() {
			mReturningToStart = true;
			animateOffsetToHeaderPosition(mCurrentTargetOffsetTop
					+ getPaddingTop(), mReturnToHeaderPositionListener);
		}
	};

    /**
     * 下拉至下一页
     */
    private final Runnable mReturnToNextPagePosition = new Runnable() {
        @Override
        public void run() {
            mReturningToStart = true;
            animateOffsetToNextPagePosition(mCurrentTargetOffsetTop
                    + getPaddingTop(), mReturnToNextPageListener);
        }
    };

    /**
     * 取消刷新
     */
	// Cancel the refresh gesture and animate everything back to its original
	// state.
	private final Runnable mCancel = new Runnable() {
		@Override
		public void run() {
			mReturningToStart = true;
			animateOffsetToStartPosition(mCurrentTargetOffsetTop
					+ getPaddingTop(), mReturnToStartPositionListener);
		}
	};

	/**
	 * Simple constructor to use when creating a SwipeRefreshLayout from code.
	 * 
	 * @param context
	 */
	public FloorRefreshLayout(Context context) {
		this(context, null);
	}

	/**
	 * Constructor that is called when inflating SwipeRefreshLayout from XML.
	 * 
	 * @param context
	 * @param attrs
	 */
	public FloorRefreshLayout(Context context, AttributeSet attrs) {
		super(context, attrs);

		mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

		mMediumAnimationDuration = getResources().getInteger(
				android.R.integer.config_mediumAnimTime);

		mDecelerateInterpolator = new DecelerateInterpolator(
				DECELERATE_INTERPOLATION_FACTOR);

		final TypedArray a = context
				.obtainStyledAttributes(attrs, LAYOUT_ATTRS);
		setEnabled(a.getBoolean(0, true));
		a.recycle();

        metrics = getResources().getDisplayMetrics();
	}

	@Override
	public void onAttachedToWindow() {
		super.onAttachedToWindow();
		removeCallbacks(mCancel);
		removeCallbacks(mReturnToStartPosition);
		removeCallbacks(mReturnToHeaderPosition);
		removeCallbacks(mReturnToNextPagePosition);
	}

	@Override
	public void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		removeCallbacks(mReturnToStartPosition);
		removeCallbacks(mCancel);
		removeCallbacks(mReturnToHeaderPosition);
        removeCallbacks(mReturnToNextPagePosition);
	}

	private void animateOffsetToStartPosition(int from,
			AnimationListener listener) {
		mFrom = from;
		mAnimateToStartPosition.reset();
		mAnimateToStartPosition.setDuration(mMediumAnimationDuration);
        mAnimateToStartPosition.setAnimationListener(listener);
        mAnimateToStartPosition.setInterpolator(mDecelerateInterpolator);
        mAnimateToStartPosition.setStartTime(isSlowAnim?System.currentTimeMillis()+500:System.currentTimeMillis());
        mTarget.startAnimation(mAnimateToStartPosition);
	}
	
	private void animateOffsetToHeaderPosition(int from,
			AnimationListener listener) {
		mFrom = from;
		mAnimateToHeaderPosition.reset();
		mAnimateToHeaderPosition.setDuration(mMediumAnimationDuration);
		mAnimateToHeaderPosition.setAnimationListener(listener);
		mAnimateToHeaderPosition.setInterpolator(mDecelerateInterpolator);
		mTarget.startAnimation(mAnimateToHeaderPosition);
	}

    private void animateOffsetToNextPagePosition(int from,
                                               AnimationListener listener) {
        mFrom = from;
        mAnimateToNextPage.reset();
        mAnimateToNextPage.setDuration(mMediumAnimationDuration);
        mAnimateToNextPage.setAnimationListener(listener);
        mAnimateToNextPage.setInterpolator(mDecelerateInterpolator);
        mTarget.startAnimation(mAnimateToNextPage);
    }

	/**
	 * Set the listener to be notified when a refresh is triggered via the swipe
	 * gesture.
	 */
	public void setOnRefreshListener(OnRefreshListener listener) {
		mListener = listener;
	}

	/**
	 * Notify the widget that refresh state has changed. Do not call this when
	 * refresh is triggered by a swipe gesture.
	 * 
	 * @param refreshing
	 *            Whether or not the view_share should show refresh progress.
	 */
	public void setRefreshing(boolean refreshing) {
		if (mRefreshing != refreshing) {
			ensureTarget();
			mRefreshing = refreshing;
            if (!isRefreshing()) {
                stopRefresh();
            }
		}
	}

	/**
	 * @return Whether the SwipeRefreshWidget is actively showing refresh
	 *         progress.
	 */
	public boolean isRefreshing() {
		return mRefreshing;
	}

	private void ensureTarget() {
		// Don't bother getting the parent height if the parent hasn't been laid
		// out yet.
		if (mTarget == null) {
			if (getChildCount() > 2 && !isInEditMode()) {
				throw new IllegalStateException(
						"SwipeRefreshLayout can only host two children");
			}
			mTarget = getChildAt(1);
			
			// 控制是否允许滚动
			mTarget.setOnTouchListener(new OnTouchListener() {
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					return mDisable;
				}
			});
			
			mOriginalOffsetTop = mTarget.getTop() + getPaddingTop();
		}
		if (mDistanceToTriggerSync == -1) {
			if (getParent() != null && ((View) getParent()).getHeight() > 0) {
				mDistanceToTriggerSync = (int) Math.min(
						((View) getParent()).getHeight()
								* MAX_SWIPE_DISTANCE_FACTOR,
						REFRESH_TRIGGER_DISTANCE * metrics.density);
			}

		}
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		final int width = getMeasuredWidth();
		final int height = getMeasuredHeight();
		if (getChildCount() == 0 || getChildCount() == 1) {
			return;
		}
        if (mTarget == null) {
            ensureTarget();
        }
		final View child = getChildAt(1);
		final int childLeft = getPaddingLeft();
		final int childTop = mCurrentTargetOffsetTop + getPaddingTop();
		final int childWidth = width - getPaddingLeft() - getPaddingRight();
		final int childHeight = height - getPaddingTop() - getPaddingBottom();
		child.layout(childLeft, childTop, childLeft + childWidth, childTop
				+ childHeight);

		mHeaderView.layout(childLeft, childTop - mHeaderHeight, childLeft
				+ childWidth, childTop);
	}

	@Override
	public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		if (getChildCount() <= 1) {
			throw new IllegalStateException(
					"SwipeRefreshLayout must have the headerview and contentview");
		}

		if (getChildCount() > 2 && !isInEditMode()) {
			throw new IllegalStateException(
					"SwipeRefreshLayout can only host two children");
		}

		if (mHeaderView == null) {
			mHeaderView = getChildAt(0);
			measureChild(mHeaderView, widthMeasureSpec, heightMeasureSpec);
			mHeaderHeight = mHeaderView.getMeasuredHeight();

			mDistanceToTriggerSync = mHeaderHeight;
		}

		getChildAt(1).measure(
				MeasureSpec.makeMeasureSpec(getMeasuredWidth()
						- getPaddingLeft() - getPaddingRight(),
						MeasureSpec.EXACTLY),
				MeasureSpec.makeMeasureSpec(getMeasuredHeight()
						- getPaddingTop() - getPaddingBottom(),
						MeasureSpec.EXACTLY));

        if (statusTextView==null) {
            statusTextView = (TextView) findViewById(R.id.hint);
            statusTextView.setText(tip0);
        }
        if (animNextPageView==null) {
            animNextPageView = ((ViewGroup)mHeaderView).getChildAt(0);
        }

//        nextPageScrollDist = getResources().getDimensionPixelSize(R.dimen.x400);
        nextPageScrollDist = metrics.heightPixels / 2;
        pullToRefreshDist = dip2px(getContext(),75);
	}


    public int dip2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }


    /**
	 * @return Whether it is possible for the child view_share of this layout to
	 *         scroll up. Override this if the child view_share is a custom view_share.
	 */
	public boolean canChildScrollUp() {
		if (android.os.Build.VERSION.SDK_INT < 14) {
			if (mTarget instanceof AbsListView) {
				final AbsListView absListView = (AbsListView) mTarget;
				return absListView.getChildCount() > 0
						&& (absListView.getFirstVisiblePosition() > 0 || absListView
								.getChildAt(0).getTop() < absListView
								.getPaddingTop());
			} else {
				return mTarget.getScrollY() > 0;
			}
		} else {
			return ViewCompat.canScrollVertically(mTarget, -1);
		}
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		ensureTarget();

		final int action = MotionEventCompat.getActionMasked(ev);

		if (mReturningToStart && action == MotionEvent.ACTION_DOWN) {
			mReturningToStart = false;
		}

		if (!isEnabled() || mReturningToStart || canChildScrollUp() || mStatus == STATUS.REFRESHING) {
			// Fail fast if we're not in a state where a swipe is possible
			return false;
		}

		switch (action) {
		case MotionEvent.ACTION_DOWN:
			mLastMotionY = mInitialMotionY = ev.getY();
			mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
			mIsBeingDragged = false;
			break;

		case MotionEvent.ACTION_MOVE:
			if (mActivePointerId == INVALID_POINTER) {
				Log.e(LOG_TAG,
						"Got ACTION_MOVE event but don't have an active pointer id.");
				return false;
			}

			final int pointerIndex = MotionEventCompat.findPointerIndex(ev,
					mActivePointerId);
			if (pointerIndex < 0) {
				Log.e(LOG_TAG,
						"Got ACTION_MOVE event but have an invalid active pointer id.");
				return false;
			}

			final float y = MotionEventCompat.getY(ev, pointerIndex);
			final float yDiff = y - mInitialMotionY;
			if (yDiff > mTouchSlop) {
				mLastMotionY = y;
				mIsBeingDragged = true;
			}
			break;

		case MotionEventCompat.ACTION_POINTER_UP:
			onSecondaryPointerUp(ev);
			break;

		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			mIsBeingDragged = false;
			mActivePointerId = INVALID_POINTER;
			break;
		}

		return mIsBeingDragged;
	}

	@Override
	public void requestDisallowInterceptTouchEvent(boolean b) {
		// Nope.
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		final int action = MotionEventCompat.getActionMasked(ev);

		if (mReturningToStart && action == MotionEvent.ACTION_DOWN) {
			mReturningToStart = false;
		}

		if (!isEnabled() || mReturningToStart || canChildScrollUp() || mStatus == STATUS.REFRESHING) {
			// Fail fast if we're not in a state where a swipe is possible
			return false;
		}

		switch (action) {
		case MotionEvent.ACTION_DOWN:
			mLastMotionY = mInitialMotionY = ev.getY();
			mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
			mIsBeingDragged = false;
			break;

		case MotionEvent.ACTION_MOVE:
			final int pointerIndex = MotionEventCompat.findPointerIndex(ev,
					mActivePointerId);

			if (pointerIndex < 0) {
				Log.e(LOG_TAG,
						"Got ACTION_MOVE event but have an invalid active pointer id.");
				return false;
			}

			final float y = MotionEventCompat.getY(ev, pointerIndex);

			final float yDiff = y - mInitialMotionY;

			if (!mIsBeingDragged && yDiff > mTouchSlop) {
				mIsBeingDragged = true;
			}

			if (mIsBeingDragged) {
                if (doubleLoosenEnable && yDiff>=nextPageScrollDist) {
                    if (mStatus!=STATUS.DOUBLE_LOOSEN) {
                        mStatus = STATUS.DOUBLE_LOOSEN;
                        statusTextView.setText(tip2);
                    }
                } else if (yDiff >= pullToRefreshDist ){
                    if (mStatus != STATUS.LOOSEN) {
                        mStatus = STATUS.LOOSEN;
                        statusTextView.setText(tip1);
                    }
                } else {
                    if (mStatus != STATUS.NORMAL) {
                        mStatus = STATUS.NORMAL;
                        statusTextView.setText(tip0);
                    }
                }



				// User velocity passed min velocity; trigger a refresh
				if (yDiff > mDistanceToTriggerSync) {

					updateContentOffsetTop((int) (yDiff));
				} else {

					updateContentOffsetTop((int) (yDiff));
					if (mLastMotionY > y && mTarget.getTop() == getPaddingTop()) {
						// If the user puts the view_share back at the top, we
						// don't need to. This shouldn't be considered
						// cancelling the gesture as the user can restart from
						// the top.
						removeCallbacks(mCancel);
					}
				}

				mLastMotionY = y;
			}
			break;

		case MotionEventCompat.ACTION_POINTER_DOWN: {
			final int index = MotionEventCompat.getActionIndex(ev);
			mLastMotionY = MotionEventCompat.getY(ev, index);
			mActivePointerId = MotionEventCompat.getPointerId(ev, index);
			break;
		}

		case MotionEventCompat.ACTION_POINTER_UP:
			onSecondaryPointerUp(ev);
			break;

		case MotionEvent.ACTION_UP:
			if (mStatus == STATUS.LOOSEN || mStatus==STATUS.DOUBLE_LOOSEN) {
				startRefresh();
			} else {
				updatePositionTimeout();
			}

			mIsBeingDragged = false;
			mActivePointerId = INVALID_POINTER;
			return false;
		case MotionEvent.ACTION_CANCEL:
			updatePositionTimeout();

			mIsBeingDragged = false;
			mActivePointerId = INVALID_POINTER;
			return false;
		}

		return true;
	}

	private void startRefresh() {
        mDisable = true;

        removeCallbacks(mCancel);
        if (mStatus == STATUS.DOUBLE_LOOSEN) {
            mReturnToNextPagePosition.run();
        } else {
            mReturnToHeaderPosition.run();
        }
        setRefreshing(true);

		if (mListener != null) {
            if (mStatus == STATUS.LOOSEN) {
                mListener.onRefresh();
                if (statusTextView!=null) {
                    statusTextView.setText(tip_loading);
                }
            }
            //STATUS.DOUBLE_LOOSEN when anim end call onPullToNextPage
		}
	}
	
	public void stopRefresh() {
		mReturnToStartPosition.run();
	}

    public void resetToTop () {
        setTargetOffsetTopAndBottom(- nextPageScrollOffset);
    }

	private void updateContentOffsetTop(int targetTop) {
		final int currentTop = mTarget.getTop();
		if (targetTop > mDistanceToTriggerSync) {
			targetTop = (int) mDistanceToTriggerSync + (int) (targetTop - mDistanceToTriggerSync) / 2; // 超过触发松手刷新的距离后，就只显示滑动一半的距离，避免随手势拉动到最底部，用户体验不好
		} else if (targetTop < 0) {
			targetTop = 0;
		}
		int dis = targetTop - currentTop;
		setTargetOffsetTopAndBottom(dis);
	}

	private void setTargetOffsetTopAndBottom(int offset) {
		mTarget.offsetTopAndBottom(offset);
		mHeaderView.offsetTopAndBottom(offset);
		mCurrentTargetOffsetTop = mTarget.getTop();
		invalidate();
	}

	private void updatePositionTimeout() {
		removeCallbacks(mCancel);
		postDelayed(mCancel, RETURN_TO_ORIGINAL_POSITION_TIMEOUT);
	}

	private void onSecondaryPointerUp(MotionEvent ev) {
		final int pointerIndex = MotionEventCompat.getActionIndex(ev);
		final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);

		if (pointerId == mActivePointerId) {
			// This was our active pointer going up. Choose a new
			// active pointer and adjust accordingly.
			final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
			mLastMotionY = MotionEventCompat.getY(ev, newPointerIndex);
			mActivePointerId = MotionEventCompat.getPointerId(ev,
					newPointerIndex);
		}
	}

	/**
	 * Classes that wish to be notified when the swipe gesture correctly
	 * triggers a normal/ready-refresh/refresh should implement this interface.
	 */
	public interface OnRefreshListener {
        void onNormal();
        void onRefresh();
        void onPullToNextPage(View view);
	}

	/**
	 * Simple AnimationListener to avoid having to implement unneeded methods in
	 * AnimationListeners.
	 */
	private class BaseAnimationListener implements AnimationListener {
		@Override
		public void onAnimationStart(Animation animation) {
		}

		@Override
		public void onAnimationEnd(Animation animation) {
		}

		@Override
		public void onAnimationRepeat(Animation animation) {
		}
	}
}
