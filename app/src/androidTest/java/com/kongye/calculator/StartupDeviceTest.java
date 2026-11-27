package com.kongye.calculator;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.graphics.Bitmap;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Relaunch regression for the shipped WebView and native fallback. */
public final class StartupDeviceTest extends Instrumentation {
    private WebView web;
    private Activity activity;
    @Override public void onCreate(Bundle args) {super.onCreate(args);start();}
    private WebView findWeb(View view) {
        if (view instanceof WebView) return (WebView)view;
        if (view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){WebView found=findWeb(group.getChildAt(i));if(found!=null)return found;}
        }
        return null;
    }
    private Object js(String body) throws Exception {
        CountDownLatch ready=new CountDownLatch(1);
        String[] result=new String[1];
        runOnMainSync(()->web.evaluateJavascript("JSON.stringify((()=>{try{"+body+"}catch(e){return {jsError:e.stack}}})())",value->{result[0]=value;ready.countDown();}));
        if(!ready.await(10,TimeUnit.SECONDS))throw new AssertionError("WebView evaluation timed out");
        Object outer=new JSONTokener(result[0]).nextValue();
        if(!(outer instanceof String))throw new AssertionError("JavaScript failed: "+result[0]+" in "+body);
        return new JSONTokener((String)outer).nextValue();
    }
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private void screenshot(String name) throws Exception {
        Bitmap bitmap=getUiAutomation().takeScreenshot();
        check(bitmap!=null,"Screenshot unavailable");
        try(FileOutputStream output=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,output);}
        bitmap.recycle();
    }

    private void launch() throws Exception {
        web=null;
        activity=startActivitySync(new Intent().setClassName(getTargetContext(),"com.kongye.calculator.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        for(int i=0;i<100&&web==null;i++){runOnMainSync(()->web=findWeb(activity.getWindow().getDecorView()));SystemClock.sleep(60);}
        check(web!=null,"WebView unavailable");
        for(int i=0;i<100;i++) {if(Boolean.TRUE.equals(js("return document.documentElement.dataset.ready==='true'")))break;SystemClock.sleep(60);}
        js("document.getElementById('skip-brand-splash').click();return true;");
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            launch();
            for(String formula:new String[]{"k*x+b","x²/a²+y²/b²"}) {
                js("insertFormulaPreset("+JSONObject.quote(formula)+",true);retain();return true;");
                check(Boolean.TRUE.equals(js("return tokens.length>0")),"Formula was not inserted");
                String persistent=(String)js("return JSON.stringify({history,functions,variables,calculationOptions,keyLayout})");
                runOnMainSync(()->activity.finish());waitForIdleSync();launch();
                check(Boolean.TRUE.equals(js("return tokens.length===0&&cursor===0&&!confirmed&&undo.length===0&&redo.length===0&&document.querySelector('#answer').textContent.trim()===''")),"Expression survived relaunch");
                check(persistent.equals(js("return JSON.stringify({history,functions,variables,calculationOptions,keyLayout})")),"Persistent data changed");
            }
            screenshot("qa-38-empty-startup");
            runOnMainSync(()->activity.finish());waitForIdleSync();
            android.content.SharedPreferences prefs=getTargetContext().getSharedPreferences("calculator",0);
            String old=prefs.getString("expression",null);boolean confirmed=prefs.getBoolean("confirmed",false);int cursor=prefs.getInt("cursor",0);
            try {
                prefs.edit().putString("expression","7+8").putBoolean("confirmed",true).putInt("cursor",3).commit();
                activity=startActivitySync(new Intent().setClassName(getTargetContext(),"com.kongye.calculator.ClassicActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                java.lang.reflect.Field field=ClassicActivity.class.getDeclaredField("document");field.setAccessible(true);
                check(((MathDocument)field.get(activity)).value.isEmpty(),"Native fallback restored old draft");
                runOnMainSync(()->activity.finish());waitForIdleSync();
            } finally {
                android.content.SharedPreferences.Editor e=prefs.edit();
                if(old==null)e.remove("expression");else e.putString("expression",old);
                e.putBoolean("confirmed",confirmed).putInt("cursor",cursor).commit();
            }
            result.putString("stream","PASS: formula relaunch starts empty, persistent history/preferences preserved, native fallback ignores old draft\n");finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));result.putString("stream","FAIL: "+trace+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
}
