package com.liangyn.zxing;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;

import java.util.Vector;


public final class CaptureActivityHandler extends Handler {

    private static final String TAG = CaptureActivityHandler.class.getName();

    private final CaptureActivity activity;
    private final DecodeThread decodeThread;
    private State state;

    public CaptureActivityHandler(CaptureActivity activity,
                                  Vector<BarcodeFormat> decodeFormats, String characterSet) {
        this.activity = activity;
        decodeThread = new DecodeThread(activity, decodeFormats, characterSet,
                new ViewFinderResultPointCallback(activity.getViewfinderView()));
        decodeThread.start();
        state = State.SUCCESS;
        // 预览、扫描解码
        CameraManager.get().startPreview();
        restartPreviewAndDecode();
    }

    @Override
    public void handleMessage(Message message) {
        if (message.what == R.id.scan_auto_focus) {// 当一次对焦结束后，指定间隔时间后继续下一次的对焦
            if (state == State.PREVIEW) {
                CameraManager.get().requestAutoFocus(this, R.id.scan_auto_focus);
            }

        } else if (message.what == R.id.scan_restart_preview) {
            restartPreviewAndDecode();

        } else if (message.what == R.id.scan_decode_succeed) {
            state = State.SUCCESS;
            Bundle bundle = message.getData();

            /***********************************************************************/
            Bitmap barcode = bundle == null ? null : (Bitmap) bundle
                    .getParcelable(DecodeThread.BARCODE_BITMAP);

            activity.handleDecode((Result) message.obj, barcode);

        } else if (message.what == R.id.scan_decode_failed) {// We're decoding as fast as possible, so when one decode fails,
            // start another.
            state = State.PREVIEW;
            CameraManager.get().requestPreviewFrame(decodeThread.getHandler(), R.id.scan_decode);

        } else if (message.what == R.id.scan_return_result) {
            Log.d(TAG, "Got return scan result message");
            activity.setResult(Activity.RESULT_OK, (Intent) message.obj);
            activity.finish();

        } else if (message.what == R.id.scan_query_launch) {
            String url = (String) message.obj;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
            activity.startActivity(intent);

        }
    }

    public void quitSynchronously() {
        state = State.DONE;
        CameraManager.get().stopPreview();
        Message quit = Message
                .obtain(decodeThread.getHandler(), R.id.scan_quit);
        quit.sendToTarget();
        try {
            decodeThread.join();
        } catch (InterruptedException e) {
        }

        // 退出后清空队列中的冗余消息
        removeMessages(R.id.scan_decode_succeed);
        removeMessages(R.id.scan_decode_failed);
    }

    private void restartPreviewAndDecode() {
        if (state == State.SUCCESS) {
            state = State.PREVIEW;
            CameraManager.get().requestPreviewFrame(decodeThread.getHandler(),
                    R.id.scan_decode);
            CameraManager.get().requestAutoFocus(this, R.id.scan_auto_focus);
            activity.drawViewfinder();
        }
    }

    private enum State {
        PREVIEW, SUCCESS, DONE
    }

}
