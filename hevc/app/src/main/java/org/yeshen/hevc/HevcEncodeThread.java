package org.yeshen.hevc;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Environment;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

/*********************************************************************
 * This file is part of hevc project
 * Created by hello@yeshen.org on 2019/07/11.
 * Copyright (c) 2019 Yeshen.org, Inc. - All Rights Reserved
 *********************************************************************/

class HevcEncodeThread extends Thread {

    public interface OnCodeFrame {
        void onCodeFrame(byte[] data);
    }

    private static String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download/h265.mp4";

    private volatile boolean isRunning = false;
    private MediaCodec mediaCodec;
    private BufferedOutputStream outputStream;
    private byte[] configByte;
    private ArrayBlockingQueue<byte[]> YUVQueue = new ArrayBlockingQueue<>(10);
    private OnCodeFrame delegate = null;


    HevcEncodeThread() {
        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/hevc", MainActivity.WIDTH, MainActivity.HEIGHT);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, MainActivity.WIDTH * MainActivity.HEIGHT * 5);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, MainActivity.FRAME_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        try {
            mediaCodec = MediaCodec.createEncoderByType("video/hevc");
        } catch (IOException e) {
            e.printStackTrace();
        }
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();
        File file = new File(path);
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        try {
            outputStream = new BufferedOutputStream(new FileOutputStream(file));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void onFrame(byte[] data) {
        if (YUVQueue.size() >= 10) {
            YUVQueue.poll();
        }
        YUVQueue.add(data);
    }

    void setOnCode(OnCodeFrame callback) {
        delegate = callback;
    }

    void stopThread() {
        isRunning = false;
        try {
            mediaCodec.stop();
            mediaCodec.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.interrupt();
    }

    @Override
    public void run() {
        isRunning = true;

        byte[] input = null;
        long generateIndex = 0;

        while (isRunning) {
            if (YUVQueue.size() > 0) {
                input = YUVQueue.poll();
                byte[] yuv420sp = new byte[MainActivity.WIDTH * MainActivity.HEIGHT * 3 / 2];
                NV21ToNV12(input, yuv420sp);
                input = yuv420sp;
            }
            if (input == null || !isRunning) continue;
            try {
                ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
                ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
                int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
                if (inputBufferIndex >= 0) {
                    //computePresentationTime
                    long pts = 132 + generateIndex * 1000000 / MainActivity.FRAME_RATE;
                    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                    inputBuffer.clear();
                    inputBuffer.put(input);
                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, pts, 0);
                    generateIndex += 1;
                }

                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, MainActivity.TIMEOUT_US);
                while (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                    byte[] outData = new byte[bufferInfo.size];
                    outputBuffer.get(outData);
                    if (bufferInfo.flags == 2) {
                        configByte = new byte[bufferInfo.size];
                        configByte = outData;
                    } else if (bufferInfo.flags == 1) {
                        byte[] keyframe = new byte[bufferInfo.size + configByte.length];
                        System.arraycopy(configByte, 0, keyframe, 0, configByte.length);
                        System.arraycopy(outData, 0, keyframe, configByte.length, outData.length);
                        outputStream.write(keyframe, 0, keyframe.length);
                        if (delegate != null) delegate.onCodeFrame(keyframe);
                    } else {
                        outputStream.write(outData, 0, outData.length);
                        if (delegate != null) delegate.onCodeFrame(outData);
                    }
                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, MainActivity.TIMEOUT_US);
                }

            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private void NV21ToNV12(byte[] nv21, byte[] nv12) {
        if (nv21 == null || nv12 == null) return;
        int frameSize = MainActivity.WIDTH * MainActivity.HEIGHT;
        int i, j;
        System.arraycopy(nv21, 0, nv12, 0, frameSize);
        for (i = 0; i < frameSize; i++) {
            nv12[i] = nv21[i];
        }
        for (j = 0; j < frameSize / 2; j += 2) {
            nv12[frameSize + j - 1] = nv21[j + frameSize];
        }
        for (j = 0; j < frameSize / 2; j += 2) {
            nv12[frameSize + j] = nv21[j + frameSize - 1];
        }
    }
}
