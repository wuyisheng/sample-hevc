package org.yeshen.hevc;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/*********************************************************************
 * This file is part of hevc project
 * Created by hello@yeshen.org on 2019/07/11.
 * Copyright (c) 2019 Yeshen.org, Inc. - All Rights Reserved
 *********************************************************************/

public class AvcDecodeThread extends Thread {

    // refer to https://github.com/cedricfung/MediaCodecDemo
    // how to get h265
    // ffmpeg -i some-video.mp4 -c:a copy -c:v libx265 h265.mp4
    // adb push h265.mp4 /sdcard/DCIM/Camera

    private static final String SAMPLE = Environment.getExternalStorageDirectory() + "/DCIM/Camera/h265.mp4";
    private static final String TAG = AvcDecodeThread.class.getSimpleName();
    private MediaCodec decoder;
    private Surface surface;

    AvcDecodeThread(Surface surface) {
        this.surface = surface;
    }

    @Override
    public void run() {
        try {
            inner();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void inner() throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(SAMPLE);

        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            Log.d(TAG, mime);
            if (mime.startsWith("video/")) {
                extractor.selectTrack(i);
                decoder = MediaCodec.createDecoderByType(mime);
                decoder.configure(format, surface, null, 0);
                break;
            }
        }

        if (decoder == null) {
            Log.e(TAG, "Can't find video info!");
            return;
        }

        decoder.start();

        ByteBuffer[] inputBuffers = decoder.getInputBuffers();
        ByteBuffer[] outputBuffers = decoder.getOutputBuffers();
        BufferInfo info = new BufferInfo();
        boolean isEOS = false;
        long startMs = System.currentTimeMillis();

        while (!Thread.interrupted()) {
            if (!isEOS) {
                int inIndex = decoder.dequeueInputBuffer(10000);
                if (inIndex >= 0) {
                    ByteBuffer buffer = inputBuffers[inIndex];
                    int sampleSize = extractor.readSampleData(buffer, 0);
                    if (sampleSize < 0) {
                        // We shouldn't stop the playback at this point, just pass the EOS
                        // flag to decoder, we will get it again from the
                        // dequeueOutputBuffer
                        Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isEOS = true;
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                }
            }

            int outIndex = decoder.dequeueOutputBuffer(info, 10000);
            switch (outIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                    outputBuffers = decoder.getOutputBuffers();
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.d(TAG, "New format " + decoder.getOutputFormat());
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    Log.d(TAG, "dequeueOutputBuffer timed out!");
                    break;
                default:
                    ByteBuffer buffer = outputBuffers[outIndex];
                    Log.v(TAG, "We can't use this buffer but render it due to the API limit, " + buffer);

                    // We use a very simple clock to keep the video FPS, or the video
                    // playback will be too fast
                    while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                        try {
                            sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            break;
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, true);
                    break;
            }

            // All decoded frames have been rendered, we can stop playing now
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                break;
            }
        }

        decoder.stop();
        decoder.release();
        extractor.release();
    }

}
