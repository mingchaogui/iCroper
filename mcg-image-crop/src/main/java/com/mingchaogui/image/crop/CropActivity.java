package com.mingchaogui.image.crop;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import com.mingchaogui.image.crop.component.BaseImageView;
import com.mingchaogui.image.crop.component.CropToast;
import com.mingchaogui.image.crop.util.BitmapOperator;

import java.io.IOException;


public class CropActivity extends Activity {

    // 在小于2048 * 2048的图片上开启硬件加速
    public final int HARDWARE_ACCELERATED_MAX_SIZE = 2048;

    ViewGroup mAppBar;
    Button mBtnCancel;
    Button mBtnSave;
    ImageButton mBtnRotate;
    FrameLayout mBench;
    BaseImageView mImageView;

    Uri mOutUri;
    // 输出图片的宽度
    int mOutWidth;
    // 输出图片的高度
    int mOutHeight;
    // 正在保存
    boolean mIsSaving = false;

    Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        this.findViews();
        this.setListeners();
        this.init();
    }

    protected void findViews() {
        mAppBar = (ViewGroup)findViewById(R.id.appBar);
        mBtnCancel = (Button)findViewById(R.id.btnCancel);
        mBtnSave = (Button)findViewById(R.id.btnSave);
        mBtnRotate = (ImageButton)findViewById(R.id.btnRotate);
        mImageView = (BaseImageView)findViewById(R.id.imageView);
        mBench = (FrameLayout)findViewById(R.id.bench);
    }

    protected void setListeners() {
        mBtnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onCancelClick();
            }
        });
        mBtnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSaveClick();
            }
        });
        mBtnRotate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onRotateClick();
            }
        });
    }

    protected void init() {
        mHandler = new Handler();

        Intent intent = getIntent();
        Uri source = getIntent().getData();
        if (source == null) {
            CropToast.show(CropActivity.this, getString(R.string.load_image_failed), mAppBar.getHeight());
            return;
        }
        mOutUri = getIntent().getParcelableExtra(MediaStore.EXTRA_OUTPUT);
        if (mOutUri == null) {
            CropToast.show(this, getString(R.string.should_specify_out_uri), mAppBar.getHeight());
            return;
        }
        mOutWidth = intent.getIntExtra(Crop.Extra.OUT_WIDTH, Crop.Default.OUT_WIDTH);
        mOutHeight = intent.getIntExtra(Crop.Extra.OUT_HEIGHT, Crop.Default.OUT_HEIGHT);

        BitmapDrawable drawable;
        try {
            drawable = BitmapOperator.createBitmapDrawable(this, source);
        } catch (IOException e) {
            e.printStackTrace();
            CropToast.show(this, getString(R.string.load_image_failed), mAppBar.getHeight());
            finish();
            return;
        } catch (OutOfMemoryError error) {
            error.printStackTrace();
            CropToast.show(this, getString(R.string.out_of_memory));
            return;
        }

        if (drawable.getIntrinsicWidth() < HARDWARE_ACCELERATED_MAX_SIZE
                && drawable.getIntrinsicHeight() < HARDWARE_ACCELERATED_MAX_SIZE) {
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            );
        }
        mImageView.setDrawable(drawable, mOutWidth, mOutHeight);
    }

    protected void onCancelClick() {
        if (mIsSaving) {
            return;
        }

        this.finish();
    }

    protected void onSaveClick() {
        if (mIsSaving) {
            return;
        }

        mIsSaving = true;

        final Bitmap bitmap = mImageView.cropImage();

        if (bitmap == null) {
            mIsSaving = false;
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                final BitmapOperator.SaveResult saveResult = BitmapOperator.saveToDisk(CropActivity.this, mOutUri, bitmap);

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onSaveResult(saveResult);
                    }
                });
            }
        }).start();
    }

    protected void onRotateClick() {// 旋转90度
        mImageView.rotate(90.0f);
    }

    protected void onSaveResult(BitmapOperator.SaveResult result) {
        mIsSaving = false;

        if (!result.isOK) {
            CropToast.show(this, getString(R.string.save_image_failed), mAppBar.getHeight());
        } else {
            setResult(RESULT_OK, new Intent().setData(result.uri));
            finish();
        }
    }
}
