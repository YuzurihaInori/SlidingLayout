package lib.homhomlib.design;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.AbsListView;
import android.widget.FrameLayout;

import com.nineoldandroids.view.ViewHelper;

/**
 * Created by Linhh on 16/4/12.
 */
public class SlidingLayout extends FrameLayout{

    private int mTouchSlop;//系统允许最小的滑动判断值
    private int mBackgroundViewLayoutId = 0;

    private View mBackgroundView;//背景View
    private View mTargetView;//正面View

    private boolean mIsBeingDragged;
    private float mInitialDownY;
    private float mInitialMotionY;
    private float mLastMotionY;
    private int mActivePointerId = INVALID_POINTER;

    private float mSlidingOffset = 2.0F;//滑动系数

    private static final int RESET_DURATION = 200;
    private static final int SMOOTH_DURATION = 1000;

    public static final int SLIDING_MODE_BOTH = 0;
    public static final int SLIDING_MODE_TOP = 1;
    public static final int SLIDING_MODE_BOTTOM = 2;

    public static final int SLIDING_POINTER_MODE_ONE = 0;
    public static final int SLIDING_POINTER_MODE_MORE = 1;

    private int mSlidingMode = SLIDING_MODE_BOTH;

    private int mSlidingPointerMode = SLIDING_POINTER_MODE_MORE;

    private static final int INVALID_POINTER = -1;

    private SlidingListener mSlidingListener;

    public static final int STATE_SLIDING = 2;
    public static final int STATE_IDLE = 1;

    public interface SlidingListener{
        //不能操作繁重的任务在这里
        public void onSlidingOffset(View view, float delta);
        public void onSlidingStateChange(View view, int state);
        public void onSlidingChangePointer(View view, int pointerId);
    }

    public SlidingLayout(Context context) {
        this(context, null);
    }

    public SlidingLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SlidingLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs){
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SlidingLayout);
        mBackgroundViewLayoutId = a.getResourceId(R.styleable.SlidingLayout_background_view, mBackgroundViewLayoutId);
        mSlidingMode = a.getInteger(R.styleable.SlidingLayout_sliding_mode,SLIDING_MODE_BOTH);
        mSlidingPointerMode = a.getInteger(R.styleable.SlidingLayout_sliding_pointer_mode,SLIDING_POINTER_MODE_MORE);
        a.recycle();
        if(mBackgroundViewLayoutId != 0){
            View view = View.inflate(getContext(), mBackgroundViewLayoutId, null);
            setBackgroundView(view);
        }

        //nexus 5 为24，应该是个很小的值
        mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        System.out.println("mTouchSlop  == "+mTouchSlop);
    }

    public void setBackgroundView(View view){
        if(mBackgroundView != null){
            this.removeView(mBackgroundView);
        }
        mBackgroundView = view;
        this.addView(view, 0);
    }

    public View getBackgroundView(){
        return this.mBackgroundView;
    }

    /**
     * 获得滑动幅度
     * @return
     */
    public float getSlidingOffset(){
        return this.mSlidingOffset;
    }

    /**
     * 设置滑动幅度
     * @param slidingOffset
     */
    public void setSlidingOffset(float slidingOffset){
        this.mSlidingOffset = slidingOffset;
    }

    public void setSlidingListener(SlidingListener slidingListener){
        this.mSlidingListener = slidingListener;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        //实际上整个layout只能存在一个背景和一个前景才有用途
//        if(getChildCount() > 2){
//
//        }
        if (getChildCount() == 0) {
            return;
        }
        if (mTargetView == null) {
            ensureTarget();
        }
        if (mTargetView == null) {
            return;
        }
    }

    private void ensureTarget() {
        if (mTargetView == null) {
            mTargetView = getChildAt(getChildCount() - 1);
        }
    }

    public void setTargetView(View view){
        if(mTargetView != null){
            this.removeView(mTargetView);
        }
        mTargetView = view;
        this.addView(view);
    }

    public View getTargetView(){
        return this.mTargetView;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {

        ensureTarget();

        final int action = MotionEventCompat.getActionMasked(ev);

        //判断拦截
        switch (action) {
            case MotionEvent.ACTION_DOWN:
//                Log.i("onInterceptTouchEvent", "down");
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
                mIsBeingDragged = false;

                //获取点击位置 y （屏幕坐标系）
                final float initialDownY = getMotionEventY(ev, mActivePointerId);
                if (initialDownY == -1) {
                    return false;
                }
                //记录初始按下Y值 （屏幕坐标系）
                mInitialDownY = initialDownY;
                break;

            case MotionEvent.ACTION_MOVE:
                if (mActivePointerId == INVALID_POINTER) {
//                    System.out.println(LOG_TAG, "Got ACTION_MOVE event but don't have an active pointer id.");
                    return false;
                }

                //获取手指移动后的Y值 （屏幕坐标系）
                final float y = getMotionEventY(ev, mActivePointerId);
                if (y == -1) {
                    return false;
                }

                //如果大于初始值Y
                if(y > mInitialDownY) {
                    //判断是否是上拉操作
                    //得到手指滑动Y轴的偏移量
                    final float yDiff = y - mInitialDownY;
                    //如果偏移量大于临界值  并且 mIsBeingDragged为false 并且 child <不可以>上拉
                    if (yDiff > mTouchSlop && !mIsBeingDragged && !canChildScrollUp()) {

                        //获取Motion Y值，为初始按下值+临界值 （不知道该怎么表达这个值的意思 - -）
                        mInitialMotionY = mInitialDownY + mTouchSlop;
                        //记录Motion Y值
                        mLastMotionY = mInitialMotionY;
                        //设置 下拉操作已 启动
                        mIsBeingDragged = true;
                    }
                }else if(y < mInitialDownY){
                    //判断是否是下拉操作
                    //得到手指滑动Y轴的偏移量
                    final float yDiff = mInitialDownY - y;
                    //如果偏移量大于临界值  并且 mIsBeingDragged为false 并且 child <不可以>下拉
                    if (yDiff > mTouchSlop && !mIsBeingDragged && !canChildScrollDown()) {
                        //获取Motion Y值，为初始按下值+临界值
                        mInitialMotionY = mInitialDownY + mTouchSlop;
                        //记录Motion Y值
                        mLastMotionY = mInitialMotionY;
                        //设置 上拉操作已 启动
                        mIsBeingDragged = true;
                    }
                }
                break;

            case MotionEventCompat.ACTION_POINTER_UP:
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
//                Log.i("onInterceptTouchEvent", "up");

                //设置 上下拉操作已 停止
                mIsBeingDragged = false;
                //设置event为无效
                mActivePointerId = INVALID_POINTER;
                break;
        }

        //根据 上下拉操作是否开始，拦截事件，只有true时拦截事件
        return mIsBeingDragged;
    }

    private float getMotionEventY(MotionEvent ev, int activePointerId) {
        final int index = MotionEventCompat.findPointerIndex(ev, activePointerId);
        if (index < 0) {
            return -1;
        }
        return MotionEventCompat.getY(ev, index);
    }

    /**
     * 判断View是否可以上拉
     * @return canChildScrollUp
     */
    public boolean canChildScrollUp() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            if (mTargetView instanceof AbsListView) {
                final AbsListView absListView = (AbsListView) mTargetView;
                return absListView.getChildCount() > 0
                        && (absListView.getFirstVisiblePosition() > 0 || absListView.getChildAt(0)
                        .getTop() < absListView.getPaddingTop());
            } else {
                return ViewCompat.canScrollVertically(mTargetView, -1) || mTargetView.getScrollY() > 0;
            }
        } else {
            return ViewCompat.canScrollVertically(mTargetView, -1);
        }
    }

    /**
     * 判断View是否可以下拉
     * @return canChildScrollDown
     */
    public boolean canChildScrollDown() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            if (mTargetView instanceof AbsListView) {
                final AbsListView absListView = (AbsListView) mTargetView;
                return absListView.getChildCount() > 0 && absListView.getAdapter() != null
                        && (absListView.getLastVisiblePosition() < absListView.getAdapter().getCount() - 1 || absListView.getChildAt(absListView.getChildCount() - 1)
                        .getBottom() < absListView.getPaddingBottom());
            } else {
                return ViewCompat.canScrollVertically(mTargetView, 1) || mTargetView.getScrollY() > 0;
            }
        } else {
            return ViewCompat.canScrollVertically(mTargetView, 1);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {

        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        switch (event.getAction()){
            case MotionEvent.ACTION_DOWN:
//                Log.i("onTouchEvent", "down");
                break;
            case MotionEvent.ACTION_MOVE:
                //拉动距离
                float delta = 0.0f;
                //判断拉动方向
                float movemment = 0.0f;

                //多点触摸？
                if(mSlidingPointerMode == SLIDING_POINTER_MODE_MORE) {
                    //homhom:it's different betweenn more than one pointer
                    int activePointerId = MotionEventCompat.getPointerId(event, event.getPointerCount() - 1);
                    if (mActivePointerId != activePointerId) {
                        //change pointer
//                    Log.i("onTouchEvent","change point");
                        mActivePointerId = activePointerId;
                        mInitialDownY = getMotionEventY(event, mActivePointerId);
                        mInitialMotionY = mInitialDownY + mTouchSlop;
                        mLastMotionY = mInitialMotionY;
                        if (mSlidingListener != null) {
                            mSlidingListener.onSlidingChangePointer(mTargetView, activePointerId);
                        }
                    }

                    //pointer delta
                    delta = Instrument.getInstance().getTranslationY(mTargetView)
                            + ((getMotionEventY(event, mActivePointerId) - mLastMotionY))
                            / mSlidingOffset;

                    mLastMotionY = getMotionEventY(event, mActivePointerId);

                    //used for judge which side move to
                    movemment = getMotionEventY(event, mActivePointerId) - mInitialMotionY;
                }else {
                    //拉动距离Y值 = （手指移动距离 - 手指初始距离 - 临界值）/阻力系数
                    delta = (event.getY() - mInitialMotionY) / mSlidingOffset;
                    //used for judge which side move to
                    //以下拉为例
                    //拉动方向 值 = 手指移动距离 - 手指初始距离 - 临界值
                    //这里并不用担心出现 临界值过大，导致 拉动方向值为负数，导致明明是下拉而出现下拉的情况，
                    // 在onInterceptTouchEvent中，只有（手指移动距离 - 手指初始距离）大于临界值时，事件才会传递到这里，所以这里这个值不会小于0
                    movemment = event.getY() - mInitialMotionY;
                }

                if(mSlidingListener != null){
                    mSlidingListener.onSlidingStateChange(this, STATE_SLIDING);
                    mSlidingListener.onSlidingOffset(this,delta);
                }

                //额。。这东西好像并没什么用，xml或java代码里设置both or top or bottom，然而代码其实是一样的，这个并不是上拉还是下拉的判断标志 movemment才是
                switch (mSlidingMode){
                    case SLIDING_MODE_BOTH:
                        Instrument.getInstance().slidingByDelta(mTargetView, delta);
                        break;
                    case SLIDING_MODE_TOP:
                        if(movemment > 0 ){
                            //向下滑动
                            Instrument.getInstance().slidingByDelta(mTargetView, delta);
                        }
                        break;
                    case SLIDING_MODE_BOTTOM:
                        if(movemment < 0 ){
                            //向下滑动
                            Instrument.getInstance().slidingByDelta(mTargetView, delta);
                        }
                        break;
                }

                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
//                Log.i("onTouchEvent", "up");
                if(mSlidingListener != null){
                    mSlidingListener.onSlidingStateChange(this, STATE_IDLE);
                }
                //松手复位
                Instrument.getInstance().reset(mTargetView);
                break;
        }
        //消费触摸
        return true;
    }

    public void setSlidingMode(int mode){
        mSlidingMode = mode;
    }

    public int getSlidingMode(){
        return mSlidingMode;
    }

    public void smoothScrollTo(float y){
        Instrument.getInstance().smoothTo(mTargetView, y);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }


    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if(mTargetView != null){
            mTargetView.clearAnimation();
        }
        mSlidingMode = 0;
        mTargetView = null;
        mBackgroundView = null;
        mSlidingListener = null;
    }

    static class Instrument {
        private static Instrument mInstrument;
        public static Instrument getInstance(){
            if(mInstrument == null){
                mInstrument = new Instrument();
            }
            return mInstrument;
        }

        public float getTranslationY(View view){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                return view.getTranslationY();
            }else{
                return ViewHelper.getTranslationY(view);
            }
        }

        public void slidingByDelta(final View view ,final float delta){
            if(view == null){
                return;
            }
            view.clearAnimation();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                view.setTranslationY(delta);
            }else{
                ViewHelper.setTranslationY(view, delta);
            }
        }

        public void slidingToY(final View view ,final float y){
            if(view == null){
                return;
            }
            view.clearAnimation();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                view.setY(y);
            }else{
                ViewHelper.setY(view, y);
            }
        }

        public void reset(final View view){
            if(view == null){
                return;
            }
            view.clearAnimation();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                android.animation.ObjectAnimator.ofFloat(view, "translationY", 0F).setDuration(RESET_DURATION).start();
            }else{
                com.nineoldandroids.animation.ObjectAnimator.ofFloat(view, "translationY", 0F).setDuration(RESET_DURATION).start();
            }
        }

        public void smoothTo(final View view ,final float y){
            if(view == null){
                return;
            }
            view.clearAnimation();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                android.animation.ObjectAnimator.ofFloat(view, "translationY", y).setDuration(SMOOTH_DURATION).start();
            }else{
                com.nineoldandroids.animation.ObjectAnimator.ofFloat(view, "translationY", y).setDuration(SMOOTH_DURATION).start();
            }
        }
    }
}
