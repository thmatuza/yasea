package net.ossrs.yasea;

import android.app.Service;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Binder;
import android.graphics.PixelFormat;
import android.support.v4.app.NotificationCompat;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.LayoutInflater;
import android.util.Log;
import android.content.Context;
import android.widget.Button;
import android.widget.Toast;
import android.view.View;

import net.ossrs.yasea.rtmp.RtmpPublisher;

/**
 * Created by tomohiro on 9/14/16.
 */
public class CastService extends Service {
    private static final String TAG = "CastService";

    private SrsPublisher mPublisher = new SrsPublisher();
    private ScreenCaptureRequestParams mParams;
    private Integer mStartId;
    private Handler mHandler;
    private CastService mSelf = this;
    private MediaProjectionManager mProjectionManager;
    private final IBinder mBinder = new CastBinder();
    private WindowManager mWindowManager;
    private View mOverlapView;
    private WindowManager.LayoutParams mOverlapViewParams;
    private Button btnStop = null;

    public class CastBinder extends Binder {
        CastService getService() {
            // Return this instance of LocalService so clients can call public methods
            return CastService.this;
        }
    }

    public class DisplayToast implements Runnable {
        private final Context mContext;
        String mText;

        public DisplayToast(Context mContext, String text){
            this.mContext = mContext;
            mText = text;
        }

        public void run(){
            Toast.makeText(mContext, mText, Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    public void onCreate() {
        Log.i(TAG, "Service onCreate");

        mHandler = new Handler();
        mWindowManager = (WindowManager)getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service onStartCommand");

        if (intent != null) {
            String action = intent.getAction();
            if (action == null) {
                stopSelf(startId);
            } else if (action.equals("net.ossrs.yasea.castservice.action.PLAY")) {
                ScreenCaptureRequestParams params = (ScreenCaptureRequestParams) intent.getParcelableExtra("requestParams");
                if (params != null) {
                    this.mParams = params;
                }
                if (this.mProjectionManager == null) {
                    this.mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                }

                setHandlers();
                startForeground();
                startView();
                startPublish();
                this.mStartId = Integer.valueOf(startId);
            } else if (action.equals("net.ossrs.yasea.castservice.action.STOP")) {
                stopPublish(startId);
                stopView();
                stopForeground();
            }
        }
        return Service.START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        Log.i(TAG, "Service onBind");
        return mBinder;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Service onDestroy");
    }

    private void setHandlers() {
        mPublisher.setPublishEventHandler(new RtmpPublisher.EventHandler() {
            @Override
            public void onRtmpConnecting(final String msg) {
                mHandler.post(new DisplayToast(mSelf, msg));
            }

            @Override
            public void onRtmpConnected(final String msg) {
                mHandler.post(new DisplayToast(mSelf, msg));
            }

            @Override
            public void onRtmpVideoStreaming(final String msg) {
            }

            @Override
            public void onRtmpAudioStreaming(final String msg) {
            }

            @Override
            public void onRtmpStopped(final String msg) {
                mHandler.post(new DisplayToast(mSelf, msg));
            }

            @Override
            public void onRtmpDisconnected(final String msg) {
                mHandler.post(new DisplayToast(mSelf, msg));
            }

            @Override
            public void onRtmpOutputFps(final double fps) {
                Log.i(TAG, String.format("Output Fps: %f", fps));
            }
        });

        mPublisher.setRecordEventHandler(new SrsMp4Muxer.EventHandler() {
            @Override
            public void onRecordPause(final String msg) {
                mHandler.post(new DisplayToast(mSelf, msg));
            }

            @Override
            public void onRecordResume(final String msg) {
                mHandler.post(new DisplayToast(mSelf, msg));
            }

            @Override
            public void onRecordStarted(final String msg) {
                mHandler.post(new DisplayToast(mSelf, msg));
            }

            @Override
            public void onRecordFinished(final String msg) {
                mHandler.post(new DisplayToast(mSelf, msg));
            }
        });

        mPublisher.setNetworkEventHandler(new SrsEncoder.EventHandler() {
            @Override
            public void onNetworkResume(final String msg) {
                mHandler.post(new DisplayToast(mSelf, msg));
            }

            @Override
            public void onNetworkWeak(final String msg) {
                mHandler.post(new DisplayToast(mSelf, msg));
            }
        });

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                final String msg = ex.getMessage();
                mHandler.post(new DisplayToast(mSelf, msg));
                mPublisher.stopPublish();
                mPublisher.stopRecord();
            }
        });
    }

    private void startPublish() {


        mPublisher.switchToScreenShare();

        mPublisher.initScreenCapture(mProjectionManager, mParams);
        mPublisher.setOutputResolution(mParams.mWidth, mParams.mHeight);
        mPublisher.setVideoSmoothMode();
        mPublisher.startPublish(mParams.mRtmpUrl);
    }

    private void stopPublish(int startId) {
        mPublisher.stopPublish();
        mPublisher.stopRecord();

        stopSelf(startId);

        if (mStartId != null) {
            stopSelf(mStartId.intValue());
            mStartId = null;
        }
    }

    private void startView() {
        LayoutInflater layoutInflater = LayoutInflater.from(this);

        mOverlapViewParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);

        int  dpScale = (int)getResources().getDisplayMetrics().density;
        mOverlapViewParams.gravity=  Gravity.TOP | Gravity.RIGHT;
        mOverlapViewParams.x = 20 * dpScale; // 20dp
        mOverlapViewParams.y = 80 * dpScale; // 80dp

        mOverlapView = layoutInflater.inflate(R.layout.service_layer, null);

        btnStop = (Button) mOverlapView.findViewById(R.id.stop);

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intentService = new Intent(getApplicationContext(), CastService.class);
                intentService.setAction("net.ossrs.yasea.castservice.action.STOP");
                startService(intentService);
            }
        });
        mWindowManager.addView(mOverlapView, mOverlapViewParams);
    }

    private void stopView() {
        mWindowManager.removeView(mOverlapView);
    }

    private void startForeground() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker("Publishing")
                .setWhen(System.currentTimeMillis())
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Screen Publishing!!")
                .setContentIntent(contentIntent)
                .build();

        startForeground(1337, notification);
    }

    private void stopForeground() {
        stopForeground(true);
    }
}
