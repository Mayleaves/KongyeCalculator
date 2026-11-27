package com.kongye.calculator;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/** Scheme B starts on the first native frame, independently of WebView startup. */
final class BrandSplashView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final boolean animationsEnabled = ValueAnimator.areAnimatorsEnabled();
    private final int background, ink, muted, line, border, tile, tileBorder, accent, onAccent;
    private final Runnable initializePage, onFinished;
    private long started;
    private boolean initialized, pageReady, skipped, finished;

    BrandSplashView(Context context, boolean dark, Runnable initializePage, Runnable onFinished) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        background = Color.parseColor(dark ? "#1C1F27" : "#FFFFFF");
        ink = Color.parseColor(dark ? "#ECEEF4" : "#22252A");
        muted = Color.parseColor(dark ? "#8E94A5" : "#767C8C");
        line = Color.parseColor(dark ? "#343949" : "#DCE0E8");
        border = Color.parseColor(dark ? "#343949" : "#CFD3DC");
        tile = Color.parseColor(dark ? "#262A36" : "#F1F3F7");
        tileBorder = Color.parseColor(dark ? "#383E50" : "#DCE0E8");
        accent = Color.parseColor(dark ? "#FFFFFF" : "#22252A");
        onAccent = Color.parseColor(dark ? "#15171E" : "#FFFFFF");
        this.initializePage = initializePage;
        this.onFinished = onFinished;
        setBackgroundColor(background);
        setContentDescription("空叶计算器启动动画，点按可跳过");
        setClickable(true);
        setOnClickListener(view -> { skipped = true; finishWhenReady(); });
    }

    void pageReady() { pageReady = true; finishWhenReady(); }

    private void finishWhenReady() {
        if (finished || !pageReady || (!skipped && animationsEnabled && SystemClock.uptimeMillis() - started < 1950)) return;
        finished = true;
        animate().alpha(0f).setDuration(animationsEnabled ? 160 : 0).withEndAction(onFinished).start();
    }

    private float progress(long elapsed, int delay, int duration) {
        float value = Math.max(0f, Math.min(1f, (elapsed - delay) / (float) duration));
        return 1f - (1f - value) * (1f - value);
    }

    private void style(int color, float alpha, float stroke) {
        paint.setColor(color);
        paint.setAlpha(Math.round(Math.max(0f, Math.min(1f, alpha)) * 255));
        paint.setStyle(stroke > 0 ? Paint.Style.STROKE : Paint.Style.FILL);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    private void text(Canvas canvas, String text, float x, float y, float size, int color, float alpha, boolean bold) {
        style(color, alpha, 0);
        paint.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        paint.setTextSize(size);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, x, y, paint);
    }

    private void tile(Canvas canvas, float x, float y, float alpha) {
        style(tile, alpha, 0);canvas.drawRoundRect(x,y,x+80,y+80,20,20,paint);
        style(tileBorder,alpha,2);canvas.drawRoundRect(x,y,x+80,y+80,20,20,paint);
    }

    private void segment(Canvas canvas, Path path, float progress, int color, float width) {
        Path visible = new Path();
        PathMeasure measure = new PathMeasure(path, false);
        measure.getSegment(0, measure.getLength() * progress, visible, true);
        style(color,1,width);canvas.drawPath(visible,paint);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (started == 0) started = SystemClock.uptimeMillis();
        long elapsed = animationsEnabled ? SystemClock.uptimeMillis() - started : 1950;
        canvas.save();canvas.scale(density,density);
        float width=getWidth()/density,height=getHeight()/density,cx=width/2,cy=height/2-20;
        float axis=progress(elapsed,0,360);
        style(line,1,1);
        canvas.drawLine(cx-96*axis,cy,cx+96*axis,cy,paint);
        canvas.drawLine(cx,cy-80*axis,cx,cy+80*axis,paint);

        canvas.save();canvas.translate(cx-54,cy-54);canvas.scale(108f/256,108f/256);
        float reveal=progress(elapsed,60,420),scale=.86f+.14f*reveal;
        canvas.translate(128,128);canvas.scale(scale,scale);canvas.translate(-128,-128);
        style(background,reveal,0);canvas.drawRoundRect(8,8,248,248,56,56,paint);
        style(border,reveal,3);canvas.drawRoundRect(8,8,248,248,56,56,paint);
        float keys=progress(elapsed,160,400);
        tile(canvas,42,42,keys);tile(canvas,134,42,keys);tile(canvas,42,134,keys);
        style(ink,keys,6);
        canvas.drawLine(67,80,93,80,paint);canvas.drawLine(80,67,80,93,paint);
        canvas.drawLine(162,80,188,80,paint);canvas.drawLine(67,174,93,174,paint);
        style(ink,keys,0);canvas.drawCircle(175,68,3.4f,paint);canvas.drawCircle(175,92,3.4f,paint);
        float stamp=progress(elapsed,440,300);
        style(accent,stamp,0);canvas.drawRoundRect(134,134,214,214,20,20,paint);
        style(onAccent,stamp,6.5f);canvas.drawLine(161,167,191,167,paint);canvas.drawLine(161,181,191,181,paint);
        Path leaf=new Path();leaf.moveTo(158,98);leaf.cubicTo(118,98,98,118,98,158);leaf.cubicTo(138,158,158,138,158,98);leaf.close();
        float stroke=progress(elapsed,320,560);
        style(background,stroke,0);canvas.drawPath(leaf,paint);segment(canvas,leaf,stroke,ink,5.5f);
        Path vein=new Path();vein.moveTo(107,149);vein.lineTo(147,109);
        segment(canvas,vein,progress(elapsed,550,360),accent,4.5f);
        canvas.restore();

        text(canvas,"空叶计算器",cx,cy+103,21,ink,progress(elapsed,360,360),true);
        text(canvas,"极简科学计算 · 留白成算",cx,cy+127,11.5f,muted,progress(elapsed,560,320),false);
        style(line,1,2);canvas.drawLine(cx-48,height-54,cx+48,height-54,paint);
        style(accent,1,2);canvas.drawLine(cx-48,height-54,cx-48+96*Math.min(1,elapsed/1950f),height-54,paint);
        text(canvas,"K O N G Y E  ·  1.0.4",cx,height-27,10,muted,1,false);
        style(line,1,1);canvas.drawRoundRect(width-77,28,width-24,55,13.5f,13.5f,paint);
        text(canvas,"跳过",width-50.5f,46,11,muted,1,false);
        canvas.restore();

        if (!initialized) { initialized=true; postDelayed(initializePage,32); }
        finishWhenReady();
        if (!finished) postInvalidateOnAnimation();
    }
}


