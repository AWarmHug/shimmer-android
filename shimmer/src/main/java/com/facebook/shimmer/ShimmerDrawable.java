/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.shimmer;

import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.FloatEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ShimmerDrawable extends Drawable {
  private final ValueAnimator.AnimatorUpdateListener mUpdateListener =
      new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
          invalidateSelf();
        }
      };

  private final Paint mShimmerPaint = new Paint();
  private final Rect mDrawRect = new Rect();
  private final Matrix mShaderMatrix = new Matrix();

  private @Nullable
  ValueAnimator mValueAnimator;
  private float mStaticAnimationProgress = -1f;

  private @Nullable
  Shimmer mShimmer;

  public ShimmerDrawable() {
  }

  public void setShimmer(@Nullable Shimmer shimmer) {
    mShimmer = shimmer;
    mShimmerPaint.reset();
    mShimmerPaint.setAntiAlias(true);
    if (mShimmer != null) {
      mShimmerPaint.setXfermode(
          new PorterDuffXfermode(
              mShimmer.alphaShimmer ? PorterDuff.Mode.DST_IN : PorterDuff.Mode.SRC_IN));
    }
    updateShader();
    updateValueAnimator();
    invalidateSelf();
  }

  public @Nullable
  Shimmer getShimmer() {
    return mShimmer;
  }

  /**
   * Starts the shimmer animation.
   */
  public void startShimmer() {
    if (mValueAnimator != null && !isShimmerStarted() && getCallback() != null) {
      mValueAnimator.start();
    }
  }

  /**
   * Stops the shimmer animation.
   */
  public void stopShimmer() {
    if (mValueAnimator != null && isShimmerStarted()) {
      mValueAnimator.cancel();
    }
  }

  /**
   * Return whether the shimmer animation has been started.
   */
  public boolean isShimmerStarted() {
    return mValueAnimator != null && mValueAnimator.isStarted();
  }

  /**
   * Return whether the shimmer animation is running.
   */
  public boolean isShimmerRunning() {
    return mValueAnimator != null && mValueAnimator.isRunning();
  }

  @Override
  public void onBoundsChange(Rect bounds) {
    super.onBoundsChange(bounds);
    mDrawRect.set(bounds);
    updateShader();
    maybeStartShimmer();
  }

  public void setStaticAnimationProgress(float value) {
    if (Float.compare(value, mStaticAnimationProgress) == 0
        || (value < 0f && mStaticAnimationProgress < 0f)) {
      return;
    }
    mStaticAnimationProgress = Math.min(value, 1f);
    invalidateSelf();
  }

  public void clearStaticAnimationProgress() {
    setStaticAnimationProgress(-1f);
  }

  @Override
  public void draw(@NonNull Canvas canvas) {
    if (mShimmer == null ) {
      return;
    }
    if (mShimmer.shape != Shimmer.Shape.BREATHE && mShimmerPaint.getShader() == null){
      return;
    }
    final float animatedValue;

    if (mStaticAnimationProgress < 0f) {
      animatedValue = mValueAnimator != null ? (float) mValueAnimator.getAnimatedValue() : 0f;
    } else {
      animatedValue = mStaticAnimationProgress;
    }

    if (mShimmer.shape == Shimmer.Shape.BREATHE) {
      mShimmerPaint.setColor(evaluate(animatedValue, mShimmer.baseColor, mShimmer.highlightColor));
    } else {
      final float tiltTan = (float) Math.tan(Math.toRadians(mShimmer.tilt));
      final float translateHeight = mDrawRect.height() + tiltTan * mDrawRect.width();
      final float translateWidth = mDrawRect.width() + tiltTan * mDrawRect.height();
      final float dx;
      final float dy;

      switch (mShimmer.direction) {
        default:
        case Shimmer.Direction.LEFT_TO_RIGHT:
          dx = offset(-translateWidth, translateWidth, animatedValue);
          dy = 0;
          break;
        case Shimmer.Direction.RIGHT_TO_LEFT:
          dx = offset(translateWidth, -translateWidth, animatedValue);
          dy = 0f;
          break;
        case Shimmer.Direction.TOP_TO_BOTTOM:
          dx = 0f;
          dy = offset(-translateHeight, translateHeight, animatedValue);
          break;
        case Shimmer.Direction.BOTTOM_TO_TOP:
          dx = 0f;
          dy = offset(translateHeight, -translateHeight, animatedValue);
          break;
      }
      mShaderMatrix.reset();
      mShaderMatrix.setRotate(mShimmer.tilt, mDrawRect.width() / 2f, mDrawRect.height() / 2f);
      mShaderMatrix.preTranslate(dx, dy);
      mShimmerPaint.getShader().setLocalMatrix(mShaderMatrix);
    }
    canvas.drawRect(mDrawRect, mShimmerPaint);
  }

  /** copy from {@link ArgbEvaluator#evaluate(float, Object, Object)} */
  public int evaluate(float fraction, Object startValue, Object endValue) {
    int startInt = (Integer) startValue;
    float startA = ((startInt >> 24) & 0xff) / 255.0f;
    float startR = ((startInt >> 16) & 0xff) / 255.0f;
    float startG = ((startInt >> 8) & 0xff) / 255.0f;
    float startB = (startInt & 0xff) / 255.0f;

    int endInt = (Integer) endValue;
    float endA = ((endInt >> 24) & 0xff) / 255.0f;
    float endR = ((endInt >> 16) & 0xff) / 255.0f;
    float endG = ((endInt >> 8) & 0xff) / 255.0f;
    float endB = (endInt & 0xff) / 255.0f;

    // convert from sRGB to linear
    startR = (float) Math.pow(startR, 2.2);
    startG = (float) Math.pow(startG, 2.2);
    startB = (float) Math.pow(startB, 2.2);

    endR = (float) Math.pow(endR, 2.2);
    endG = (float) Math.pow(endG, 2.2);
    endB = (float) Math.pow(endB, 2.2);

    // compute the interpolated color in linear space
    float a = startA + fraction * (endA - startA);
    float r = startR + fraction * (endR - startR);
    float g = startG + fraction * (endG - startG);
    float b = startB + fraction * (endB - startB);

    // convert back to sRGB in the [0..255] range
    a = a * 255.0f;
    r = (float) Math.pow(r, 1.0 / 2.2) * 255.0f;
    g = (float) Math.pow(g, 1.0 / 2.2) * 255.0f;
    b = (float) Math.pow(b, 1.0 / 2.2) * 255.0f;

    return Math.round(a) << 24 | Math.round(r) << 16 | Math.round(g) << 8 | Math.round(b);
  }

  @Override
  public void setAlpha(int alpha) {
    // No-op, modify the Shimmer object you pass in instead
  }

  @Override
  public void setColorFilter(@Nullable ColorFilter colorFilter) {
    // No-op, modify the Shimmer object you pass in instead
  }

  @Override
  public int getOpacity() {
    return mShimmer != null && (mShimmer.clipToChildren || mShimmer.alphaShimmer)
        ? PixelFormat.TRANSLUCENT
        : PixelFormat.OPAQUE;
  }

  private float offset(float start, float end, float percent) {
    return start + (end - start) * percent;
  }

  private void updateValueAnimator() {
    if (mShimmer == null) {
      return;
    }

    final boolean started;
    if (mValueAnimator != null) {
      started = mValueAnimator.isStarted();
      mValueAnimator.cancel();
      mValueAnimator.removeAllUpdateListeners();
    } else {
      started = false;
    }

    mValueAnimator =
        ValueAnimator.ofFloat(0f, 1f + (float) (mShimmer.repeatDelay / mShimmer.animationDuration));
    mValueAnimator.setInterpolator(new LinearInterpolator());
    mValueAnimator.setRepeatMode(mShimmer.repeatMode);
    mValueAnimator.setStartDelay(mShimmer.startDelay);
    mValueAnimator.setRepeatCount(mShimmer.repeatCount);
    mValueAnimator.setDuration(mShimmer.animationDuration + mShimmer.repeatDelay);
    mValueAnimator.addUpdateListener(mUpdateListener);
    if (started) {
      mValueAnimator.start();
    }
  }

  void maybeStartShimmer() {
    if (mValueAnimator != null
        && !mValueAnimator.isStarted()
        && mShimmer != null
        && mShimmer.autoStart
        && getCallback() != null) {
      mValueAnimator.start();
    }
  }

  private void updateShader() {
    if (mShimmer == null) {
      return;
    }
    if (mShimmer.shape != Shimmer.Shape.BREATHE) {
      final Rect bounds = getBounds();
      final int boundsWidth = bounds.width();
      final int boundsHeight = bounds.height();
      if (boundsWidth == 0 || boundsHeight == 0 || mShimmer == null) {
        return;
      }
      final int width = mShimmer.width(boundsWidth);
      final int height = mShimmer.height(boundsHeight);

      final Shader shader;
      switch (mShimmer.shape) {
        default:
        case Shimmer.Shape.LINEAR:
          boolean vertical =
              mShimmer.direction == Shimmer.Direction.TOP_TO_BOTTOM
                  || mShimmer.direction == Shimmer.Direction.BOTTOM_TO_TOP;
          int endX = vertical ? 0 : width;
          int endY = vertical ? height : 0;
          shader =
              new LinearGradient(
                  0, 0, endX, endY, mShimmer.colors, mShimmer.positions, Shader.TileMode.CLAMP);
          break;
        case Shimmer.Shape.RADIAL:
          shader =
              new RadialGradient(
                  width / 2f,
                  height / 2f,
                  (float) (Math.max(width, height) / Math.sqrt(2)),
                  mShimmer.colors,
                  mShimmer.positions,
                  Shader.TileMode.CLAMP);
          break;
      }
      mShimmerPaint.setShader(shader);
    } else {
      mShimmerPaint.setColor(mShimmer.baseColor);
    }
  }
}
