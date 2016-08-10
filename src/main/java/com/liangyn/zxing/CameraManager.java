/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.liangyn.zxing;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.os.Handler;
import android.view.SurfaceHolder;


import com.liangyn.zxing.utils.DensityUtil;

import java.io.IOException;

/**
 * 功能：拍照界面预览和图片解码服务类
 * 说明：该类封装了摄像头服务，简化图片预览步骤，用作条码预览及解码。
 */
public final class CameraManager {

//	private static final String TAG = CameraManager.class.getName();

	private static final int FRAME_WIDTH_DIP = 280;
	private static final int FRAME_HEIGHT_DIP= 120;

	private static CameraManager cameraMgr;

	/**
	 * 摄像头硬件参数
	 */
//	private Parameters cameraParams;

	/**
	 * 可用的版本：Build.VERSION.SDK_INT
	 * 说明：见 CameraConfigurationManager.setFlash() 方法
	 */
	static final int SDK_INT;
	static {
		int sdkInt = 10000;
		try {
			sdkInt = Build.VERSION.SDK_INT;
		} catch (NumberFormatException nfe) {
		}
		SDK_INT = sdkInt;
	}

	private final Context context;
	private Camera camera;
	private final CameraConfigManager configMgr;
	private Rect frameRect;
	private Rect frameRectOfPreview;
	private boolean cameraInited;
	private boolean cameraPreviewing;
	private final boolean useOneShotPreviewCallback;
	
	/**
	 * 通过调用camera.setPreviewDisplay(surfaceHolder)方法注册，预览界面会回掉该Callback。
	 * 注：PreviewCallback会注册一个Handler，并确保Handler只接受单一的消息
	 */
	private final CameraPreviewCallback previewCallback;
	
	/**
	 * 自动对焦回调
	 */
	private final CameraAutoFocusCallback autoFocusCallback;

	/**
	 * 在调用的Activity中调用该方法初始化本类实例
	 * @param context
	 */
	public static void init(Context context) {
		if (cameraMgr == null) {
			cameraMgr = new CameraManager(context);
		}
	}

	/**
	 * 获取单例的 CameraManager
	 */
	public static CameraManager get() {
		return cameraMgr;
	}

	private CameraManager(Context context) {
		this.context = context;
		this.configMgr = new CameraConfigManager(context);
		useOneShotPreviewCallback = Build.VERSION.SDK_INT > 3; // 3
		previewCallback = new CameraPreviewCallback(configMgr, useOneShotPreviewCallback);
		autoFocusCallback = new CameraAutoFocusCallback();
	}

	/**
	 * 打开系统摄像驱动并初始化硬件参数
	 * @param holder Camera所在的SurfaceView会向该holder中渲染拍照预览界面
	 * @throws IOException 摄像头驱动异常，设备打开失败
	 */
	public void openDriver(SurfaceHolder holder) throws IOException {
		if (camera == null) {
			camera = Camera.open();
			if (camera == null) {
				throw new IOException();
			}
			camera.setPreviewDisplay(holder);

			if (!cameraInited) {
				cameraInited = true;
				configMgr.initFromCameraParameters(camera);
			}
			configMgr.setDesiredCameraParameters(camera);

			/*mParameters = camera.getParameters();
			String flashMode = Camera.Parameters.FLASH_MODE_TORCH;
			if (flashMode != null) {
				mParameters.setFlashMode(flashMode);
				camera.setParameters(mParameters);
			}

			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
			if (prefs.getBoolean(PreferencesActivity.KEY_FRONT_LIGHT, false)) {
				FlashlightManager.enableFlashlight();
			}
			 */
			
			FlashlightManager.enableFlashlight();
		}
	}

	/**
	 * 关闭摄像头驱动
	 */
	public void closeDriver() {
		if (camera != null) {
			/*
			mParameters.setFlashMode(Parameters.FLASH_MODE_OFF);
			camera.setParameters(mParameters);
			*/
			FlashlightManager.disableFlashlight();
			camera.release();
			camera = null;
		}
	}

	/**
	 * 启动拍照预览界面
	 */
	public void startPreview() {
		if (camera != null && !cameraPreviewing) {
			camera.startPreview();
			cameraPreviewing = true;
		}
	}

	/**
	 * 结束拍照预览界面
	 */
	public void stopPreview() {
		if (camera != null && cameraPreviewing) {
			if (!useOneShotPreviewCallback) {
				camera.setPreviewCallback(null);
			}
			camera.stopPreview();
			previewCallback.setHandler(null, 0);
			autoFocusCallback.setHandler(null, 0);
			cameraPreviewing = false;
		}
	}

	public void requestPreviewFrame(Handler handler, int message) {
		if (camera != null && cameraPreviewing) {
			previewCallback.setHandler(handler, message);
			if (useOneShotPreviewCallback) {
				camera.setOneShotPreviewCallback(previewCallback);
			} else {
				camera.setPreviewCallback(previewCallback);
			}
		}
	}

	/**
	 * 发送自动对焦请求
	 * @param handler	自动对焦完成后发送Handler
	 * @param message
	 */
	public void requestAutoFocus(Handler handler, int message) {
		if (camera != null && cameraPreviewing) {
			autoFocusCallback.setHandler(handler, message);
			camera.autoFocus(autoFocusCallback);
		}
	}

	/**
	 * Calculates the framing rect which the UI should draw to show the user
	 * where to place the barcode. This target helps with alignment as well as
	 * forces the user to hold the device far enough away to ensure the image
	 * will be in focus.
	 * 
	 * @return The rectangle to draw on screen in window coordinates.
	 */
	public Rect getFrameRect() {
		Point screenResolution = configMgr.getScreenResolution();
		if (frameRect == null) {
			if (camera == null) {
				return null;
			}
			int width = DensityUtil.dip2px(context, FRAME_WIDTH_DIP);
			int height = DensityUtil.dip2px(context, FRAME_HEIGHT_DIP);
			int leftOffset = (screenResolution.x - width) / 2;
			int topOffset = (screenResolution.y - height) / 2;
			frameRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
		}
		return frameRect;
	}

	/**
	 * Like {@link #} but coordinates are in terms of the preview
	 * frame, not UI / screen.
	 */
	public Rect getFrameRectInPreview() {
		if (frameRectOfPreview == null) {
			Rect rect = new Rect(getFrameRect());
			Point cameraResolution = configMgr.getCameraResolution();
			Point screenResolution = configMgr.getScreenResolution();
			rect.left = rect.left * cameraResolution.y / screenResolution.x;
			rect.right = rect.right * cameraResolution.y / screenResolution.x;
			rect.top = rect.top * cameraResolution.x / screenResolution.y;
			rect.bottom = rect.bottom * cameraResolution.x / screenResolution.y;
			frameRectOfPreview = rect;
		}
		return frameRectOfPreview;
	}

	/**
	 * A factory method to build the appropriate LuminanceSource object based on
	 * the format of the preview buffers, as described by Camera.Parameters.
	 * 
	 * @param data
	 *            A preview frame.
	 * @param width
	 *            The width of the image.
	 * @param height
	 *            The height of the image.
	 * @return A PlanarYUVLuminanceSource instance.
	 */
	public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data,
			int width, int height) {
		Rect rect = getFrameRectInPreview();
		int previewFormat = configMgr.getPreviewFormat();
		String previewFormatString = configMgr.getPreviewFormatString();
		switch (previewFormat) {
		// This is the standard Android format which all devices are REQUIRED to
		// support.
		// In theory, it's the only one we should ever care about.
		case ImageFormat.NV21:
			// This format has never been seen in the wild, but is compatible as
			// we only care
			// about the Y channel, so allow it.
		case ImageFormat.NV16:
			return new PlanarYUVLuminanceSource(data, width, height, rect.left,
					rect.top, rect.width(), rect.height());
		default:
			// The Samsung Moment incorrectly uses this variant instead of the
			// 'sp' version.
			// Fortunately, it too has all the Y data up front, so we can read
			// it.
			if ("yuv420p".equals(previewFormatString)) {
				return new PlanarYUVLuminanceSource(data, width, height,
						rect.left, rect.top, rect.width(), rect.height());
			}
		}
		throw new IllegalArgumentException("Unsupported picture format: "
				+ previewFormat + '/' + previewFormatString);
	}

	public Context getContext() {
		return context;
	}
	
	public Camera getCamera(){
		if(this.camera!=null){
			return this.camera;
		}
		return null;
	}

}
