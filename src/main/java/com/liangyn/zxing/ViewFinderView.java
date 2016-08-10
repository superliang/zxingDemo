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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.google.zxing.ResultPoint;
import com.liangyn.zxing.utils.DensityUtil;

import java.util.Collection;
import java.util.HashSet;


public final class ViewFinderView extends View {

    private static final int[] SCANNER_ALPHA = {0, 64, 128, 192, 255, 192, 128, 64};
    private static final long ANIMATION_DELAY = 100L;
    private static final int OPAQUE = 0xFF;
    /**
     * 四个绿色边角对应的宽度
     */
    private static final int CORNER_WIDTH = 5;
    private final Paint paint;
    private final int maskColor;
    private final int resultColor;
    private final int frameColor;
    private final int laserColor;
    private final int resultPointColor;
    /**
     * 提示文字大小
     */
    private final int scanTextSize;
    private Bitmap resultBitmap;
    private int scannerAlpha;
    private Collection<ResultPoint> possibleResultPoints;
    private Collection<ResultPoint> lastPossibleResultPoints;
    /**
     * 四个绿色边角对应的长度
     */
    private int cornerLen;

    /**
     * 初始化onDraw中需要的Paint画笔等参数
     * 说明：因每次调用View都会调用onDraw()方法，从性能方面考虑将Paint画笔等参数一次性初始化
     */
    public ViewFinderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        Resources resources = getResources();
        maskColor = resources.getColor(R.color.vf_mask);
        resultColor = resources.getColor(R.color.vf_result);
        frameColor = resources.getColor(R.color.vf_frame);
        laserColor = resources.getColor(R.color.vf_laser);
        resultPointColor = resources.getColor(R.color.vf_result_points);
        scannerAlpha = 0;
        possibleResultPoints = new HashSet<ResultPoint>(8);

        scanTextSize = DensityUtil.dip2px(context, 16.0F);
        cornerLen = DensityUtil.dip2px(context, 20.0F);
    }

    @Override
    public void onDraw(Canvas canvas) {
        Rect frame = CameraManager.get().getFrameRect();
        if (frame == null)
            return;

        int width = canvas.getWidth();
        int height = canvas.getHeight();

        paint.setColor(resultBitmap != null ? resultColor : maskColor);
        //条码识别区域，画出扫描框外面的阴影部分，共四个部分：
        //扫描框的上面到屏幕上面，扫描框的下面到屏幕下面，扫描框的左边面到屏幕左边，扫描框的右边到屏幕右边
        canvas.drawRect(0, 0, width, frame.top, paint);
        canvas.drawRect(0, frame.top, frame.left, frame.bottom, paint);
        canvas.drawRect(frame.right, frame.top, width, frame.bottom, paint);
        canvas.drawRect(0, frame.bottom, width, height, paint);

        if (resultBitmap != null) {
            paint.setAlpha(OPAQUE);
            canvas.drawBitmap(resultBitmap, frame.left, frame.top, paint);
        } else {
            //画扫描框边上的角，总共8个部分
            paint.setColor(Color.GRAY);
            canvas.drawRect(frame.left, frame.top, frame.left + cornerLen, frame.top + CORNER_WIDTH, paint);
            canvas.drawRect(frame.left, frame.top, frame.left + CORNER_WIDTH, frame.top + cornerLen, paint);
            canvas.drawRect(frame.right - cornerLen, frame.top, frame.right, frame.top + CORNER_WIDTH, paint);
            canvas.drawRect(frame.right - CORNER_WIDTH, frame.top, frame.right, frame.top + cornerLen, paint);
            canvas.drawRect(frame.left, frame.bottom - CORNER_WIDTH, frame.left + cornerLen, frame.bottom, paint);
            canvas.drawRect(frame.left, frame.bottom - cornerLen, frame.left + CORNER_WIDTH, frame.bottom, paint);
            canvas.drawRect(frame.right - cornerLen, frame.bottom - CORNER_WIDTH, frame.right, frame.bottom, paint);
            canvas.drawRect(frame.right - CORNER_WIDTH, frame.bottom - cornerLen, frame.right, frame.bottom, paint); 
            /*
			//画扫描框上面的字
			paint.setColor(Color.WHITE);
			paint.setTextSize(scanTextSize);
			paint.setAlpha(0x40);
			paint.setTypeface(Typeface.create("System", Typeface.BOLD));
			int xPos = (canvas.getWidth() / 2);
			int yPos = (int) ((canvas.getHeight() / 2) - ((paint.descent() + paint.ascent()) / 2)); //the distance from the baseline to the center
			canvas.drawText(getResources().getString(R.string.scan_text), xPos, yPos - 200.0F, paint);
			*/

            paint.setColor(frameColor);

            Collection<ResultPoint> currentPossible = possibleResultPoints;
            Collection<ResultPoint> currentLast = lastPossibleResultPoints;
            if (currentPossible.isEmpty()) {
                lastPossibleResultPoints = null;
            } else {
                possibleResultPoints = new HashSet<ResultPoint>(8);
                lastPossibleResultPoints = currentPossible;
                paint.setAlpha(OPAQUE);
                paint.setColor(resultPointColor);
                for (ResultPoint point : currentPossible) {
                    canvas.drawCircle(frame.left + point.getX(), frame.top + point.getY(), 6.0f, paint);
                }
            }
            if (currentLast != null) {
                paint.setAlpha(OPAQUE / 2);
                paint.setColor(resultPointColor);
                for (ResultPoint point : currentLast) {
                    canvas.drawCircle(frame.left + point.getX(), frame.top + point.getY(), 3.0f, paint);
                }
            }

            // 只刷新扫描框的内容，其他地方不刷新
            postInvalidateDelayed(ANIMATION_DELAY, frame.left, frame.top,
                    frame.right, frame.bottom);
        }
    }

    public void drawViewfinder() {
        resultBitmap = null;
        invalidate();
    }

    /**
     * Draw a bitmap with the result points highlighted instead of the live
     * scanning display.
     *
     * @param barcode An image of the decoded barcode.
     */
    public void drawResultBitmap(Bitmap barcode) {
        resultBitmap = barcode;
        invalidate();
    }

    public void addPossibleResultPoint(ResultPoint point) {
        possibleResultPoints.add(point);
    }

}
