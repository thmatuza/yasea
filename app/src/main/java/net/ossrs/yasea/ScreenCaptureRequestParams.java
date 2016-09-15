package net.ossrs.yasea;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by tomohiro on 9/14/16.
 */
public class ScreenCaptureRequestParams implements Parcelable {
    public int mWidth;
    public int mHeight;
    public int mDpi;
    public String mRtmpUrl;
    private int mResultCode;
    private Intent mIntent;

    public static final ClassLoaderCreator<ScreenCaptureRequestParams> CREATOR
            = new ClassLoaderCreator<ScreenCaptureRequestParams>() {
        @Override
        public ScreenCaptureRequestParams createFromParcel(Parcel source)
        {
            return createFromParcel(source, null);
        }

        @Override
        public ScreenCaptureRequestParams createFromParcel(Parcel source, ClassLoader loader)
        {
            return new ScreenCaptureRequestParams(source);
        }

        @Override
        public ScreenCaptureRequestParams[] newArray(int size)
        {
            return new ScreenCaptureRequestParams[size];
        }
    };

    public ScreenCaptureRequestParams(int resultCode, Intent intent, int width, int height, int dpi, String rtmpUrl) {
        this.mResultCode = resultCode;
        this.mIntent = intent;
        this.mWidth = width;
        this.mHeight = height;
        this.mDpi = dpi;
        this.mRtmpUrl = rtmpUrl;
    }

    @SuppressLint({"NewApi"})
    public MediaProjection getMediaProjection(MediaProjectionManager manager) {
        return manager.getMediaProjection(this.mResultCode, this.mIntent);
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.mResultCode);
        dest.writeParcelable(this.mIntent, 0);
        dest.writeInt(this.mWidth);
        dest.writeInt(this.mHeight);
        dest.writeInt(this.mDpi);
        dest.writeString(this.mRtmpUrl);
    }

    protected ScreenCaptureRequestParams(Parcel in) {
        this.mResultCode = in.readInt();
        this.mIntent = (Intent) in.readParcelable(Intent.class.getClassLoader());
        this.mWidth = in.readInt();
        this.mHeight = in.readInt();
        this.mDpi = in.readInt();
        this.mRtmpUrl = in.readString();
    }
}
