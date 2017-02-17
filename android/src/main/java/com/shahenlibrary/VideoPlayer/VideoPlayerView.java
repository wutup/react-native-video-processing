/*
 * MIT License
 *
 * Copyright (c) 2017 Shahen Hovhannisyan.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.shahenlibrary.VideoPlayer;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.Base64;
import android.util.Log;
import android.widget.MediaController;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.shahenlibrary.interfaces.OnTrimVideoListener;
import com.shahenlibrary.utils.VideoEdit;
import com.yqritc.scalablevideoview.ScalableVideoView;
import com.yqritc.scalablevideoview.ScaleManager;
import com.yqritc.scalablevideoview.Size;
import com.shahenlibrary.Events.Events;
import com.shahenlibrary.Events.EventsEnum;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

public class VideoPlayerView extends ScalableVideoView implements
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnBufferingUpdateListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnInfoListener, LifecycleEventListener, MediaController.MediaPlayerControl {

    private ThemedReactContext themedReactContext;
    private RCTEventEmitter eventEmitter;
    private String mediaSource;
    private boolean playerPlaying = true;
    private String LOG_TAG = "RNVideoProcessing";
    private Runnable progressRunnable = null;
    private Handler progressUpdateHandler = new Handler();
    private MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
    private int progressUpdateHandlerDelay = 1000;
    private int videoStartAt = 0;
    private int videoEndAt = -1;


    public VideoPlayerView(ThemedReactContext ctx) {
        super(ctx);
        themedReactContext = ctx;
        eventEmitter = themedReactContext.getJSModule(RCTEventEmitter.class);
        themedReactContext.addLifecycleEventListener(this);
        setSurfaceTextureListener(this);
        initPlayerIfNeeded();
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                    Log.d(LOG_TAG, "run: Send event");
                    WritableMap event = Arguments.createMap();
                    event.putDouble(Events.CURRENT_TIME, mMediaPlayer.getCurrentPosition() / 1000.0);
                    if (mMediaPlayer.getCurrentPosition() >= videoEndAt && videoEndAt != -1) {
                        Log.d(LOG_TAG, "run: End time reached");
                        mMediaPlayer.seekTo(videoStartAt);
                        if (!mMediaPlayer.isLooping()) {
                            Log.d(LOG_TAG, "run: Set loop");
                            pause();
                        }
                    }
                    eventEmitter.receiveEvent(getId(), EventsEnum.EVENT_PROGRESS.toString(), event);
                }

                progressUpdateHandler.postDelayed(progressRunnable, progressUpdateHandlerDelay);
            }
        };

        progressUpdateHandler.post(progressRunnable);
    }

    private void initPlayerIfNeeded() {
        if (mMediaPlayer != null) {
            return;
        }
        Log.d(LOG_TAG, "initPlayerIfNeeded");
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setScreenOnWhilePlaying(true);
        mMediaPlayer.setOnVideoSizeChangedListener(this);
        mMediaPlayer.setOnErrorListener(this);
        mMediaPlayer.setOnPreparedListener(this);
        mMediaPlayer.setOnBufferingUpdateListener(this);
        mMediaPlayer.setOnCompletionListener(this);
        mMediaPlayer.setOnInfoListener(this);
    }

    public void setSource(final String uriString) {
        if (mediaSource != null && mediaSource.equals(uriString)) {
            return;
        }
        if (mMediaPlayer == null) {
            Log.d(LOG_TAG, "setSource: Media player is null");
            return;
        }
        reset();

        mediaSource = uriString;
        Log.d(LOG_TAG, "set source: " + mediaSource);

        initPlayerIfNeeded();

        try {
            if (uriString.startsWith("content://")) {
                Uri parsedUri = Uri.parse(mediaSource);
                setDataSource(themedReactContext, parsedUri);
                metadataRetriever.setDataSource(themedReactContext, parsedUri);
            } else {
                setDataSource(mediaSource);
                metadataRetriever.setDataSource(mediaSource);
            }
            prepare(this);

            if (playerPlaying && !mMediaPlayer.isPlaying()) {
                Log.d(LOG_TAG, "setSource: Start video at once");
                start();
            }

        } catch (IOException e) {
            e.printStackTrace();
            Log.d(LOG_TAG, "setSrc: ERROR");
        }
    }

    public void setPlay(final boolean shouldPlay) {
        Log.d(LOG_TAG, "setPlay: " + shouldPlay);
        playerPlaying = shouldPlay;

        if (mMediaPlayer == null) {
            Log.d(LOG_TAG, "setPlay: Player reference is null");
            return;
        }
        if (shouldPlay && !mMediaPlayer.isPlaying()) {
            start();
            Log.d(LOG_TAG, "setPlay: START");
        }
        if (!shouldPlay && mMediaPlayer.isPlaying()) {
            pause();
            Log.d(LOG_TAG, "setPlay: PAUSE");
        }
    }

    public void setMediaVolume(float volume) {
        if (volume < 0) {
            return;
        }
        if (mMediaPlayer == null) {
            return;
        }
        setVolume(volume, volume);
    }

    public void setCurrentTime(float currentTime) {
        float seekTime = currentTime * 1000;
        if (mMediaPlayer == null) {
            Log.d(LOG_TAG, "MEDIA PLAYER IS NULL");
            return;
        }
        int duration = getDuration();
        if (currentTime > duration || currentTime < 0) {
            seekTime = 0;
        }
        Log.d(LOG_TAG, "set seek to " + String.valueOf((int) seekTime));
        seekTo((int) seekTime);
    }

    public void setVideoEndAt(int endAt) {
        videoEndAt = endAt;
        if (mMediaPlayer == null) {
            return;
        }
        if (endAt > mMediaPlayer.getDuration() * 1000) {
            videoEndAt = mMediaPlayer.getDuration() * 1000;
        }
        if (mMediaPlayer.getCurrentPosition() * 1000 > videoEndAt) {
            mMediaPlayer.seekTo(videoStartAt);
        }
    }

    public void cleanup() {
        if (mMediaPlayer == null) {
            return;
        }
        mMediaPlayer.stop();
        mMediaPlayer.release();
        mMediaPlayer = null;
    }

    public void setVideoStartAt(int startAt) {
        videoStartAt = startAt;
        if (mMediaPlayer == null) {
            return;
        }
        if (mMediaPlayer.getDuration() * 1000 < videoStartAt) {
            mMediaPlayer.seekTo(videoStartAt);
        }
    }

    public void setProgressUpdateHandlerDelay(int delay) {
        this.progressUpdateHandlerDelay = delay;
    }

    public void sendMediaInfo() {
        if (mMediaPlayer == null) {
            Log.d(LOG_TAG, "sendMediaInfo: media Player is null");
            return;
        }
        int videoWidth = getVideoWidth();
        int videoHeight = getVideoHeight();

        WritableMap event = Arguments.createMap();

        event.putInt(Events.DURATION, mMediaPlayer.getDuration() / 1000);
        event.putInt(Events.WIDTH, videoWidth);
        event.putInt(Events.HEIGHT, videoHeight);

        eventEmitter.receiveEvent(getId(), EventsEnum.EVENT_GET_INFO.toString(), event);
    }

    public void getFrame(float sec) {
        Bitmap bmp = metadataRetriever.getFrameAtTime((long) (sec * 1000000));
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream .toByteArray();
        String encoded = Base64.encodeToString(byteArray, Base64.DEFAULT);

        WritableMap event = Arguments.createMap();
        event.putString("image", encoded);

        eventEmitter.receiveEvent(getId(), EventsEnum.EVENT_GET_PREVIEW_IMAGE.toString(), event);
    }

    public void trimMedia(@Nullable int startMs, int endMs) {
        OnTrimVideoListener trimVideoListener = new OnTrimVideoListener() {
            @Override
            public void onError(String message) {
                Log.d(LOG_TAG, "Trimmed onError: " + message);
                WritableMap event = Arguments.createMap();
                event.putString(Events.ERROR_TRIM, message);

                eventEmitter.receiveEvent(getId(), EventsEnum.EVENT_GET_TRIMMED_SOURCE.toString(), event);
            }

            @Override
            public void onTrimStarted() {
                Log.d(LOG_TAG, "Trimmed onTrimStarted");
            }

            @Override
            public void getResult(Uri uri) {
                Log.d(LOG_TAG, "getResult: " + uri.toString());
                WritableMap event = Arguments.createMap();
                event.putString("source", uri.toString());
                eventEmitter.receiveEvent(getId(), EventsEnum.EVENT_GET_TRIMMED_SOURCE.toString(), event);
            }

            @Override
            public void cancelAction() {
                Log.d(LOG_TAG, "Trimmed cancelAction");
            }
        };

        Log.d(LOG_TAG, "trimMedia at : startAt -> " + startMs + " : endAt -> " + endMs);
        File mediaFile = new File(mediaSource);
        long startTrimFromPos = startMs * 1000;
        long endTrimFromPos = endMs * 1000;
        String[] dPath = mediaSource.split("/");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < dPath.length; ++i) {
            if (i == dPath.length - 1) {
                continue;
            }
            builder.append(dPath[i]);
            builder.append(File.separator);
        }
        String path = builder.toString();
        try {
            VideoEdit.startTrim(mediaFile, path, startTrimFromPos, endTrimFromPos, trimVideoListener);
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(LOG_TAG, "trimMedia: error -> " + e.toString());
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        return false;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        int videoWidth = getVideoWidth();
        int videoHeight = getVideoHeight();

        if (videoWidth == 0 || videoHeight == 0) {
            return;
        }
        Size viewSize = new Size(getWidth(), getHeight());
        Size videoSize = new Size(videoWidth, videoHeight);
        ScaleManager scaleManager = new ScaleManager(viewSize, videoSize);

        Matrix matrix = scaleManager.getScaleMatrix(mScalableType);
        if (matrix != null) {
            Log.d(LOG_TAG, "set transform");
            setTransform(matrix);
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        videoEndAt = mp.getDuration() * 1000;
        Log.d(LOG_TAG, "onPrepared: " + videoEndAt);
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {

    }

    @Override
    public void onCompletion(MediaPlayer mp) {

    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        return false;
    }

    @Override
    public void onHostResume() {

    }

    @Override
    public void onHostPause() {

    }

    @Override
    public void onHostDestroy() {

    }

    @Override
    public int getBufferPercentage() {
        return 0;
    }

    @Override
    public boolean canPause() {
        return false;
    }

    @Override
    public boolean canSeekBackward() {
        return false;
    }

    @Override
    public boolean canSeekForward() {
        return false;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }
}