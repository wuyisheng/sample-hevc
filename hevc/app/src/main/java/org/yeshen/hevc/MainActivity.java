package org.yeshen.hevc;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;


/*********************************************************************
 * This file is part of hevc project
 * Created by hello@yeshen.org on 2019/07/11.
 * Copyright (c) 2019 Yeshen.org, Inc. - All Rights Reserved
 *********************************************************************/

public class MainActivity extends AppCompatActivity {

    private Camera camera;

    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;
    public static final int FRAME_RATE = 30;
    public static final int TIMEOUT_US = 12000;

    private HevcEncodeThread avcEncode;
    private HevcDecodeThread avcDecode;
    private SurfaceView mPreview;
    private SurfaceView mEchoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mPreview = findViewById(R.id.preview);
        mEchoView = findViewById(R.id.echo_view);

        handleCameraPreview();
        handleEchoView();
    }

    private void handleCameraPreview() {
        mPreview.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                try {
                    camera = Camera.open(0);
                    camera.setPreviewCallback(new PreviewCallback() {
                        @Override
                        public void onPreviewFrame(byte[] data, Camera camera) {
                            if (avcEncode != null) avcEncode.onFrame(data);
                        }
                    });
                    camera.setDisplayOrientation(90);
                    Parameters parameters = camera.getParameters();
                    parameters.setPreviewFormat(ImageFormat.NV21);
                    parameters.setPreviewSize(WIDTH, HEIGHT);
                    camera.setParameters(parameters);
                    camera.setPreviewDisplay(mPreview.getHolder());
                    camera.startPreview();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                avcEncode = new HevcEncodeThread();
                avcEncode.start();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                if (null != camera) {
                    camera.setPreviewCallback(null);
                    camera.stopPreview();
                    camera.release();
                    camera = null;
                    avcEncode.stopThread();
                }
            }
        });
    }

    private void handleEchoView() {
        mEchoView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                if (avcDecode == null) {
                    avcDecode = new HevcDecodeThread(holder.getSurface());
                    avcDecode.start();
                    mEchoView.bringToFront();
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                if (avcDecode != null) {
                    avcDecode.interrupt();
                }
            }
        });
    }
}
