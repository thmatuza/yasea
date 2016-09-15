package net.ossrs.yasea;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Build.VERSION;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import net.ossrs.yasea.rtmp.RtmpPublisher;

import java.util.Random;

public class MainActivity extends Activity {
    private static final String TAG = "Yasea";
    private static final int REQUEST_CODE = 1000;

    CastService mService;
    boolean mBound = false;

    Button btnPublish = null;
    Button btnSwitchCamera = null;
    Button btnRecord = null;
    Button btnSwitchEncoder = null;
    Button btnScreenCapture = null;

    private SharedPreferences sp;
    private String rtmpUrl = "rtmp://ossrs.net/" + getRandomAlphaString(3) + '/' + getRandomAlphaDigitString(5);
    private String recPath = Environment.getExternalStorageDirectory().getPath() + "/test.mp4";

    private SrsPublisher mPublisher = new SrsPublisher();

    private ScreenCaptureRequestParams requestParams;
    private MediaProjectionManager mProjectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (VERSION.SDK_INT >= 21) {
            bindService(new Intent(this, CastService.class), mConnection, BIND_AUTO_CREATE);
            mProjectionManager = (MediaProjectionManager) getSystemService
                    (Context.MEDIA_PROJECTION_SERVICE);
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        // response screen rotation event
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);

        // restore data.
        sp = getSharedPreferences("Yasea", MODE_PRIVATE);
        rtmpUrl = sp.getString("rtmpUrl", rtmpUrl);

        // initialize url.
        final EditText efu = (EditText) findViewById(R.id.url);
        efu.setText(rtmpUrl);

        btnPublish = (Button) findViewById(R.id.publish);
        btnSwitchCamera = (Button) findViewById(R.id.swCam);
        btnRecord = (Button) findViewById(R.id.record);
        btnSwitchEncoder = (Button) findViewById(R.id.swEnc);
        btnScreenCapture = (Button) findViewById(R.id.capture);

        mPublisher.setSurfaceView((SurfaceView) findViewById(R.id.preview));

        btnPublish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnPublish.getText().toString().contentEquals("publish")) {
                    rtmpUrl = efu.getText().toString();
                    Log.i(TAG, String.format("RTMP URL changed to %s", rtmpUrl));
                    SharedPreferences.Editor editor = sp.edit();
                    editor.putString("rtmpUrl", rtmpUrl);
                    editor.apply();

                    mPublisher.setPreviewResolution(1280, 720);
                    mPublisher.setOutputResolution(384, 640);
                    mPublisher.setVideoSmoothMode();
                    mPublisher.startPublish(rtmpUrl);

                    if (btnSwitchEncoder.getText().toString().contentEquals("soft enc")) {
                        Toast.makeText(getApplicationContext(), "Use hard encoder", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getApplicationContext(), "Use soft encoder", Toast.LENGTH_SHORT).show();
                    }
                    btnPublish.setText("stop");
                    btnSwitchEncoder.setEnabled(false);
                } else if (btnPublish.getText().toString().contentEquals("stop")) {
                    mPublisher.stopPublish();
                    mPublisher.stopRecord();

                    btnPublish.setText("publish");
                    btnRecord.setText("record");
                    btnSwitchEncoder.setEnabled(true);
                }
            }
        });

        btnSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPublisher.getNumberOfCameras() > 0) {
                    mPublisher.switchCameraFace((mPublisher.getCamraId() + 1) % mPublisher.getNumberOfCameras());
                }
            }
        });

        btnRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnRecord.getText().toString().contentEquals("record")) {
                    mPublisher.startRecord(recPath);

                    btnRecord.setText("pause");
                } else if (btnRecord.getText().toString().contentEquals("pause")) {
                    mPublisher.pauseRecord();
                    btnRecord.setText("resume");
                } else if (btnRecord.getText().toString().contentEquals("resume")) {
                    mPublisher.resumeRecord();
                    btnRecord.setText("pause");
                }
            }
        });

        btnSwitchEncoder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnSwitchEncoder.getText().toString().contentEquals("soft enc")) {
                    mPublisher.swithToSoftEncoder();
                    btnSwitchEncoder.setText("hard enc");
                } else if (btnSwitchEncoder.getText().toString().contentEquals("hard enc")) {
                    mPublisher.swithToHardEncoder();
                    btnSwitchEncoder.setText("soft enc");
                }
            }
        });

        btnScreenCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (VERSION.SDK_INT < 21) {
                    Toast.makeText(getApplicationContext(), "not supported", Toast.LENGTH_SHORT).show();
                } else {
                    rtmpUrl = efu.getText().toString();
                    Log.i(TAG, String.format("RTMP URL changed to %s", rtmpUrl));
                    SharedPreferences.Editor editor = sp.edit();
                    editor.putString("rtmpUrl", rtmpUrl);
                    editor.apply();

                    shareScreen();
                }
            }
        });

        mPublisher.setPublishEventHandler(new RtmpPublisher.EventHandler() {
            @Override
            public void onRtmpConnecting(final String msg) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onRtmpConnected(final String msg) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onRtmpVideoStreaming(final String msg) {
            }

            @Override
            public void onRtmpAudioStreaming(final String msg) {
            }

            @Override
            public void onRtmpStopped(final String msg) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onRtmpDisconnected(final String msg) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onRtmpOutputFps(final double fps) {
                Log.i(TAG, String.format("Output Fps: %f", fps));
            }
        });

        mPublisher.setRecordEventHandler(new SrsMp4Muxer.EventHandler() {
            @Override
            public void onRecordPause(final String msg) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onRecordResume(final String msg) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onRecordStarted(final String msg) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Recording file: " + msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onRecordFinished(final String msg) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "MP4 file saved: " + msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        mPublisher.setNetworkEventHandler(new SrsEncoder.EventHandler() {
            @Override
            public void onNetworkResume(final String msg) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onNetworkWeak(final String msg) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                final String msg = ex.getMessage();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                        mPublisher.stopPublish();
                        mPublisher.stopRecord();
                        btnPublish.setText("publish");
                        btnRecord.setText("record");
                        btnSwitchEncoder.setEnabled(true);
                    }
                });
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        final Button btn = (Button) findViewById(R.id.publish);
        btn.setEnabled(true);
        mPublisher.resumeRecord();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPublisher.pauseRecord();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mPublisher.stopPublish();
        mPublisher.stopRecord();

        if (VERSION.SDK_INT >= 21 && mService != null) {
            unbindService(mConnection);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            mPublisher.setPreviewRotation(90);
        } else if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mPublisher.setPreviewRotation(0);
        }
        mPublisher.stopEncode();
        mPublisher.stopRecord();
        btnRecord.setText("record");
        mPublisher.setScreenOrientation(newConfig.orientation);
        if (btnPublish.getText().toString().contentEquals("stop")) {
            mPublisher.startEncode();
        }
    }

    private static String getRandomAlphaString(int length) {
        String base = "abcdefghijklmnopqrstuvwxyz";
        Random random = new Random();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < length; i++) {
            int number = random.nextInt(base.length());
            sb.append(base.charAt(number));
        }
        return sb.toString();
    }

    private static String getRandomAlphaDigitString(int length) {
        String base = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < length; i++) {
            int number = random.nextInt(base.length());
            sb.append(base.charAt(number));
        }
        return sb.toString();
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            CastService.CastBinder binder = (CastService.CastBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mService = null;
            mBound = false;
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE) {
            Log.e(TAG, "Unknown request code: " + requestCode);
            return;
        }
        if (resultCode != RESULT_OK) {
            Toast.makeText(getApplicationContext(),
                    "Screen Cast Permission Denied", Toast.LENGTH_SHORT).show();
            return;
        }
        this.requestParams = new ScreenCaptureRequestParams(resultCode, data, 384, 640, 400, rtmpUrl);
        Intent it = new Intent(getApplicationContext(), CastService.class);
        it.putExtra("requestParams", this.requestParams);
        it.setAction("net.ossrs.yasea.castservice.action.PLAY");
        startService(it);
        Intent home = new Intent();
        home.setAction("android.intent.action.MAIN");
        home.addCategory("android.intent.category.HOME");
        startActivity(home);
    }

    @TargetApi(21)
    private void shareScreen() {
        startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
    }
}
