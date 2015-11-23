package com.mingchaogui.image.crop.component;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.mingchaogui.image.crop.R;

/**
 * 底图缩放，浮层不变
 * @author mingchaogui
 *
 */
public class BaseImageView extends View {

    //单点触摸的时候
    private float mOldX = 0;
    private float mOldY = 0;

    //多点触摸的时候
    private float mOldX_0 = 0;
    private float mOldY_0 = 0;

    private float mOldX_1 = 0;
    private float mOldY_1 = 0;

    // 状态
    private final int STATUS_TOUCH_IDLE = 0;
    private final int STATUS_TOUCH_SINGLE = 1;// 单点拖拽中
    private final int STATUS_TOUCH_MULTI = 2;// 多点拖拽中
    // 当前操作状态
    private int mStatus = STATUS_TOUCH_IDLE;
    // 是否旋转中
    private boolean mRotating = false;

    // 默认的输出分辨率
    private final int DEFAULT_CROP_WIDTH = 512;
    private final int DEFAULT_CROP_HEIGHT = 512;
    // 输出分辨率
    private int mOutWidth = DEFAULT_CROP_WIDTH;
    private int mOutHeight = DEFAULT_CROP_HEIGHT;

    protected float mOriRationWH = 1.0f;// 原始宽高比

    protected BitmapDrawable mDrawable;// 源图
    protected FloatDrawable mFloatDrawable;// 浮层（选框）
    protected int mHighLightBorderColor;// 浮层的颜色

    protected Rect mTmpRect = new Rect();
    protected Rect mAdjustRect = new Rect();
    protected Rect mFloatRect = new Rect();// 浮层
    protected boolean mShouldInitBounds = true;// 是否需要初始化Bounds

    public final int DOUBLE_CLICK_TIME = 200;
    protected long mLastTouchUpTime = 0;

    public BaseImageView(Context context) {
        super(context);
        init();
    }

    public BaseImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BaseImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        mFloatDrawable = new FloatDrawable();
        mHighLightBorderColor = getContext().getResources().getColor(R.color.highlight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mDrawable == null) {
            return;
        }

        if (mDrawable.getIntrinsicWidth() == 0 || mDrawable.getIntrinsicHeight() == 0) {
            return;     // nothing to draw (empty bounds)
        }

        configureBounds();

        mDrawable.draw(canvas);
        canvas.save();
        canvas.clipRect(mFloatRect, Region.Op.DIFFERENCE);
        canvas.drawColor(mHighLightBorderColor);
        canvas.restore();
        mFloatDrawable.draw(canvas);
    }

    protected void configureBounds() {
        if(mShouldInitBounds) {
            // 获取视图尺寸
            float viewWidth = this.getWidth();
            float viewHeight = this.getHeight();
            // 获取图片尺寸
            float srcWidth = mDrawable.getIntrinsicWidth();
            float srcHeight = mDrawable.getIntrinsicHeight();

            // 浮层宽度
            float floatWidth = viewWidth;
            // 浮层宽度:输出宽度
            float scaleFloat = floatWidth / mOutWidth;
            // 浮层高度
            float floatHeight = mOutHeight * scaleFloat;
            // 浮层与视图的边距
            float floatTop = (viewHeight - floatHeight) / 2;
            float floatBottom = floatTop + floatHeight;
            float floatLeft = viewWidth - floatWidth;
            float floatRight = floatLeft + floatWidth;

            // 计算源图宽高比
            mOriRationWH = srcWidth / srcHeight;

            // 选择框宽度 : 源图宽度
            float scaleWidth = floatWidth / srcWidth;
            // 选择框高度 : 源图高度
            float scaleHeight = floatHeight / srcHeight;
            // 源图缩放比例
            float scale = scaleWidth > scaleHeight ? scaleWidth : scaleHeight;

            // 图片与选择框等宽，高度同比缩放
            float dWidth = srcWidth * scale;
            float dHeight = srcHeight * scale;
            // 图片与视图的边距
            float dLeft = (viewWidth - dWidth) / 2;
            float dRight = dLeft + dWidth;
            float dTop = (viewHeight - dHeight) / 2;
            float dBottom = dTop + dHeight;
            mTmpRect.set((int) dLeft, (int) dTop, (int) dRight, (int) dBottom);

            mAdjustRect.set(mTmpRect);
            mFloatRect.set((int) floatLeft, (int) floatTop, (int) floatRight, (int) floatBottom);

            mShouldInitBounds = false;
        }

        mDrawable.setBounds(mAdjustRect);
        mFloatDrawable.setBounds(mFloatRect);
    }

    /**
     * @param drawable 图片
     */
    public void setDrawable(BitmapDrawable drawable) {
        if (drawable == null) {
            return;
        }

        setDrawable(drawable, mOutWidth, mOutHeight);
    }

    /**
     * @param drawable 图片
     * @param outWidth 裁剪后输出的宽度
     * @param outHeight 裁剪后输出的高度
     */
    public void setDrawable(BitmapDrawable drawable,int outWidth,int outHeight) {
        this.mDrawable = drawable;
        this.mOutWidth =  outWidth;
        this.mOutHeight = outHeight;
        this.mShouldInitBounds = true;

        this.invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                mStatus = STATUS_TOUCH_SINGLE;
                saveFingersPosition(event);
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                mStatus = STATUS_TOUCH_MULTI;
                saveFingersPosition(event);
                break;

            case MotionEvent.ACTION_MOVE:
                if(mStatus == STATUS_TOUCH_MULTI) {
                    this.onZoom(event);
                } else if (mStatus == STATUS_TOUCH_SINGLE) {
                    this.onDrag(event);
                }
                break;

            case MotionEvent.ACTION_UP:
                if (System.currentTimeMillis() - mLastTouchUpTime < DOUBLE_CLICK_TIME) {
                    mLastTouchUpTime = 0;
                    scale(2.0f);
                } else {
                    mLastTouchUpTime = System.currentTimeMillis();
                }

            case MotionEvent.ACTION_POINTER_UP:
                mStatus = STATUS_TOUCH_IDLE;
                break;
        }

        return true;
    }

    protected void saveFingersPosition(MotionEvent event) {
        int pointerCount = event.getPointerCount();

        if (pointerCount > 1) {
            // 存储手指0的位置
            mOldX_0 = event.getX(0);
            mOldY_0 = event.getY(0);
            // 存储手指1的位置
            mOldX_1 = event.getX(1);
            mOldY_1 = event.getY(1);
        } else if (pointerCount == 1) {
            mOldX = event.getX();
            mOldY = event.getY();
        }
    }

    protected void onDrag(MotionEvent event) {
        // 计算移动的距离
        int dx = (int)(event.getX() - mOldX);
        int dy = (int)(event.getY() - mOldY);

        mAdjustRect.offset(dx, dy);
        this.adjustBoundOutSide();
        this.invalidate();

        // 存储手指位置
        saveFingersPosition(event);
    }

    protected void onZoom(MotionEvent event) {
        // 新的位置
        float newX_0 = event.getX(0);
        float newY_0 = event.getY(0);
        float newX_1 = event.getX(1);
        float newY_1 = event.getY(1);

        // 计算本次缩放的倍数，以较长边为基准
        float scale = distance(newX_0, newX_1, newY_0, newY_1) / distance(mOldX_0, mOldX_1, mOldY_0, mOldY_1);

        scale(scale);

        // 存储手指位置
        saveFingersPosition(event);
    }

    public float distance(float x0, float x1, float y0, float y1){
        float dx = Math.abs(x0 - x1);
        float dy = Math.abs(y0 - y1);
        return (float)Math.sqrt(dx * dx + dy * dy);
    }

    public void scale(float scale) {
        // 放大后图片的尺寸
        float dWidth = mAdjustRect.width() * scale;
        float dHeight = dWidth / mOriRationWH;

        // ImageView的中心点坐标
        float vCenterX = this.getWidth() / 2;
        float vCenterY = this.getHeight() / 2;

        // 图片中心点和ImageView中心点的距离
        float distanceX = mAdjustRect.centerX() - vCenterX;
        float distanceY = mAdjustRect.centerY() - vCenterY;

        // 相应的，对放大后图片的中心点进行偏离
        float centerX = (int)(vCenterX + distanceX * scale);
        float centerY = (int)(vCenterY + distanceY * scale);

        int left = (int)(centerX - dWidth / 2);
        int top = (int)(centerY - dHeight / 2);
        int right = (int)(centerX + dWidth / 2);
        int bottom = (int)(centerY + dHeight / 2);

        mAdjustRect.set(left, top, right, bottom);

        this.adjustBoundOverSize();
        this.adjustBoundOutSide();
        this.invalidate();
    }

    public void rotate(final float angle) {
        if (mRotating || mDrawable == null) {
            return;
        }

        final Bitmap oldBitmap = mDrawable.getBitmap();
        if (oldBitmap == null) {
            return;
        }

        mRotating = true;
        CropToast.show(getContext(), getResources().getString(R.string.rotating));

        final Handler handler = new Handler();
        final Runnable rotateRunnable = new Runnable() {
            @Override
            public void run() {
                Matrix matrix = new Matrix();
                matrix.postRotate(angle);

                Bitmap rotatedBitmap;
                try {
                    rotatedBitmap = Bitmap.createBitmap(oldBitmap, 0, 0, oldBitmap.getWidth(), oldBitmap.getHeight(), matrix, true);
                } catch (OutOfMemoryError error) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            CropToast.show(getContext(), getContext().getString(R.string.out_of_memory));
                        }
                    });

                    return;
                }

                final BitmapDrawable drawable = new BitmapDrawable(getResources(), rotatedBitmap);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        setDrawable(drawable);
                        oldBitmap.recycle();

                        mRotating = false;
                        CropToast.cancel();
                    }
                });
            }
        };
        new Thread(rotateRunnable).start();
    }

    /**
     * 限制图片越界（边界不能在浮层之内）
     * @! 应同时限制图片缩放，使图片最小宽度 = 浮层的宽度 && 最小高度 = 浮层的高度
     */
    protected void adjustBoundOutSide() {
        // 是否越界
        boolean isOut = false;
        // 调整后新的坐标
        int newLeft = mAdjustRect.left;
        int newTop = mAdjustRect.top;

        if (mAdjustRect.left > mFloatRect.left) {
            newLeft = mFloatRect.left;
            isOut = true;
        } else if (mAdjustRect.right < mFloatRect.right) {
            newLeft = mFloatRect.right - mAdjustRect.width();
            isOut = true;
        }
        if (mAdjustRect.top > mFloatRect.top) {
            newTop = mFloatRect.top;
            isOut = true;
        } else if (mAdjustRect.bottom < mFloatRect.bottom) {
            newTop = mFloatRect.bottom - mAdjustRect.height();
            isOut = true;
        }

        if (isOut) {
            mAdjustRect.offsetTo(newLeft, newTop);
        }
    }

    /**
     * 限制图片的四边不短于浮层的四边
     */
    protected void adjustBoundOverSize() {
        boolean isAdjust = false;
        int left = 0;
        int top = 0;
        int right = 0;
        int bottom = 0;

        // 判断图片四边是否短于浮层的四边
        if (mAdjustRect.width() < mFloatRect.width()) {// 宽度小于浮层
            // 图片的中心点
            int centerY = mAdjustRect.centerY();
            // 设置左右两边位置与浮层相等（即等宽）
            left = mFloatRect.left;
            right = mFloatRect.right;
            // 高度同比调整
            float height = mFloatRect.width() / mOriRationWH;
            // 上边位置 = 中心位置减去高度一半
            top = (int)(centerY - height / 2);
            // 同理
            bottom = (int)(centerY + height / 2);

            isAdjust = true;
        } else if (mAdjustRect.height() < mFloatRect.height()) {
            // 图片的中心点
            int centerX = mAdjustRect.centerX();
            // 设置上下两边位置与浮层相等（即等高）
            top = mFloatRect.top;
            bottom = mFloatRect.bottom;
            // 宽度同比调整
            float width = mFloatRect.height() * mOriRationWH;
            left = (int)(centerX - width / 2);
            right = (int)(centerX + width / 2);

            isAdjust = true;
        }

        if (isAdjust) {
            mAdjustRect.set(left, top, right, bottom);
        }
    }

    public Bitmap cropImage() {
        if (mDrawable == null) {
            return null;
        }

        // 原始Bitmap
        Bitmap src = mDrawable.getBitmap();

        // 缩放倍数
        float scaleX = (float)mAdjustRect.width() / (float)src.getWidth();
        float scaleY = (float)mAdjustRect.height() / (float)src.getHeight();
        // 截取浮层和底图的重叠区域，除以缩放倍数
        int x = (int)(Math.abs(mAdjustRect.left - mFloatRect.left) / scaleX);
        int y = (int)(Math.abs(mAdjustRect.top - mFloatRect.top) / scaleY);
        int width = (int)(mFloatRect.width() / scaleX);
        int height = (int)(mFloatRect.height() / scaleY);
        // 截取后放大图片至要的输出分辨率
        Matrix matrix = new Matrix();
        float outScaleX = (float)mOutWidth / (float)width;
        float outScaleY = (float)mOutHeight / (float)height;
        matrix.postScale(outScaleX, outScaleY);

        Bitmap bitmap = Bitmap.createBitmap(src, x, y, width, height, matrix, true);

        return bitmap;
    }
}
