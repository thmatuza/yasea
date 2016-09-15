package net.ossrs.yasea;

import java.util.concurrent.Semaphore;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.view.Surface;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.util.Log;

import net.ossrs.yasea.gl.TextureManager;
import net.ossrs.yasea.gl.SurfaceManager;

/**
 * Created by tomohiro on 9/12/16.
 */
@TargetApi(21)
public class SrsScreenCapture implements Runnable, OnFrameAvailableListener {
    private static final String TAG = "SrsScreenCapture";

    private final ScreenCaptureRequestParams params;
    private final MediaProjection.Callback mediaProjectionCallback;

    private static final int DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR;
    //private static final int DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    private static final int VIRTUAL_DISPLAY_DPI = 400;
    private MediaProjectionManager mProjectionManager;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;

    private Thread mThread = null;
    private boolean mFrameAvailable = false;
    private boolean mRunning = true;
    private int mWidth;
    private int mHeight;
    private EventHandler mHandler;

    // The input surface of the MediaCodec
    private SurfaceManager mCodecSurfaceManager = null;

    // Handles the rendering of the SurfaceTexture we got
    // from the camera, onto a Surface
    private TextureManager mTextureManager = null;

    private final Semaphore mLock = new Semaphore(0);
    private final Object mSyncObject = new Object();

    public interface EventHandler {

        long onNeedTimestamp();

        void onDrawSurface();
    }

    public SrsScreenCapture(
            MediaProjectionManager projectionManager,
            ScreenCaptureRequestParams params,
            MediaProjection.Callback mediaProjectionCallback) {
        this.mProjectionManager = projectionManager;
        this.params = params;
        this.mediaProjectionCallback = mediaProjectionCallback;
    }

    public void setEventHandler(EventHandler handler) {
        mHandler = handler;
    }

    public synchronized void startCapture(int width, int height, Surface surface) {
        mMediaProjection =  params.getMediaProjection(mProjectionManager);
        mMediaProjection.registerCallback(mediaProjectionCallback, null);

        mWidth = width;
        mHeight = height;
        addMediaCodecSurface(surface);
        startGLThread();
    }

    public synchronized void stopCapture() {
        if (mThread != null) {
            mThread.interrupt();
        }
        mRunning = false;

        removeMediaCodecSurface();

        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if (mMediaProjection != null) {
            mMediaProjection.unregisterCallback(mediaProjectionCallback);
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    private void addMediaCodecSurface(Surface surface) {
        synchronized (mSyncObject) {
            mCodecSurfaceManager = new SurfaceManager(surface);
        }
    }

    private void removeMediaCodecSurface() {
        synchronized (mSyncObject) {
            if (mCodecSurfaceManager != null) {
                mCodecSurfaceManager.release();
                mCodecSurfaceManager = null;
            }
        }
    }

    private void startGLThread() {
        Log.d(TAG,"Thread started.");
        if (mTextureManager == null) {
            mTextureManager = new TextureManager();
        }
        if (mTextureManager.getSurfaceTexture() == null) {
            mThread = new Thread(SrsScreenCapture.this);
            mRunning = true;
            mThread.start();
            mLock.acquireUninterruptibly();
        }
    }

    @Override
    public void run() {
        mCodecSurfaceManager.makeCurrent();
        mTextureManager.createTexture().setOnFrameAvailableListener(this);

        mTextureManager.getSurfaceTexture().setDefaultBufferSize(mWidth, mHeight);
        Surface surface = new Surface(mTextureManager.getSurfaceTexture());
        createVirtualDisplay(mWidth, mHeight, surface);

        mLock.release();

        try {
            long ts = 0, oldts = 0;
            while (mRunning) {
                synchronized (mSyncObject) {
                    mSyncObject.wait(2500);
                    if (mFrameAvailable) {
                        mFrameAvailable = false;

                        mTextureManager.updateFrame();
                        mTextureManager.drawFrame();
                        oldts = ts;
                        ts = mTextureManager.getSurfaceTexture().getTimestamp();
                        //Log.d(TAG,"FPS: "+(1000000000/(ts-oldts)));
                        if (mHandler != null) {
                            ts = mHandler.onNeedTimestamp();
                        }
                        mCodecSurfaceManager.setPresentationTime(ts);
                        mCodecSurfaceManager.swapBuffer();
                        if (mHandler != null) {
                            mHandler.onDrawSurface();
                        }

                    } else {
                        Log.e(TAG,"No frame received !");
                    }
                }
            }
        } catch (InterruptedException ignore) {
        } finally {
            mTextureManager.release();
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (mSyncObject) {
            mFrameAvailable = true;
            mSyncObject.notifyAll();
        }
    }

    private void createVirtualDisplay(int width, int height, Surface surface) {
        mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                "WebRTC_ScreenCapture", width, height, VIRTUAL_DISPLAY_DPI,
                DISPLAY_FLAGS, surface,
                null /* callback */, null /* callback handler */);
    }
}
