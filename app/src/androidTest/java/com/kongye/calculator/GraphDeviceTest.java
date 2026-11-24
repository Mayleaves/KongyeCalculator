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

/** Offline, release-signed on-device gesture and rendering regression test. */
public final class GraphDeviceTest extends Instrumentation {
    private WebView web;
    private Activity activity;
    private boolean drawerOnly;
    @Override public void onCreate(Bundle args) { super.onCreate(args); drawerOnly="true".equals(args.getString("drawerOnly")); start(); }
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
        runOnMainSync(()->web.evaluateJavascript("JSON.stringify((()=>{"+body+"})())",value->{result[0]=value;ready.countDown();}));
        if(!ready.await(10,TimeUnit.SECONDS))throw new AssertionError("WebView evaluation timed out");
        Object outer=new JSONTokener(result[0]).nextValue();
        if(!(outer instanceof String))throw new AssertionError("JavaScript failed: "+result[0]);
        return new JSONTokener((String)outer).nextValue();
    }
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private void screenshot(String name) throws Exception {
        Bitmap bitmap=getUiAutomation().takeScreenshot();
        check(bitmap!=null,"Screenshot unavailable");
        try(FileOutputStream output=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,output);}
        bitmap.recycle();
    }
    private void event(long down,int action,float[] x,float[] y) {
        MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[x.length];
        MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[x.length];
        for(int i=0;i<x.length;i++){props[i]=new MotionEvent.PointerProperties();props[i].id=i;props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            coords[i]=new MotionEvent.PointerCoords();coords[i].x=x[i];coords[i].y=y[i];coords[i].pressure=1;coords[i].size=.1f;}
        MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x.length,props,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
        check(getUiAutomation().injectInputEvent(e,true),"Touch injection rejected");e.recycle();
    }
    @Override public void onStart() {
        Bundle report=new Bundle();
        try {
            activity=startActivitySync(new Intent().setClassName(getTargetContext(),"com.kongye.calculator.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            for(int i=0;i<100&&web==null;i++){runOnMainSync(()->web=findWeb(activity.getWindow().getDecorView()));SystemClock.sleep(100);}
            check(web!=null,"WebView unavailable");
            for(int i=0;i<100;i++){if(Boolean.TRUE.equals(js("return document.documentElement.dataset.ready==='true'")))break;SystemClock.sleep(100);}
            JSONObject result=new JSONObject();
            js("document.getElementById('skip-brand-splash').click();window.deviceTestOriginalTheme=theme;return true;");
            if(drawerOnly){
                for(String theme:new String[]{"light","dark"}){
                    js("home();setTheme('"+theme+"');return true;");SystemClock.sleep(280);
                    js("window.drawerTestInset=document.getElementById('open-drawer').getBoundingClientRect().left-document.getElementById('stage').getBoundingClientRect().left;toggleDrawer(true);return true;");
                    SystemClock.sleep(80);
                    check(Boolean.TRUE.equals(js("const b=document.getElementById('open-drawer').getBoundingClientRect(),h=document.querySelector('.home-card-shell').getBoundingClientRect();return Math.abs(b.left-h.left-window.drawerTestInset)<2;")),"Arrow animation moved separately");
                    SystemClock.sleep(220);
                    JSONObject geometry=(JSONObject)js("const b=document.getElementById('open-drawer').getBoundingClientRect(),h=document.querySelector('.home-card-shell').getBoundingClientRect();return {gap:b.left-h.left,homeX:h.left,hit:!!document.elementFromPoint(b.left+b.width/2,b.top+b.height/2)?.closest('#open-drawer')};");
                    check(Math.abs(geometry.getDouble("gap")-((Number)js("return window.drawerTestInset")).doubleValue())<1,"Arrow misaligned "+geometry);
                    check(geometry.getBoolean("hit"),"Arrow cannot be tapped");
                    screenshot("qa-34-drawer-"+theme);
                    js("openPage('计算设置');return true;");SystemClock.sleep(280);
                    check(Boolean.TRUE.equals(js("const b=document.getElementById('open-drawer').getBoundingClientRect(),s=document.getElementById('stage').getBoundingClientRect();return Math.abs(b.left-s.left-window.drawerTestInset)<1;")),"Tool page arrow shifted");
                    js("document.getElementById('open-drawer').click();return true;");SystemClock.sleep(280);
                    check(Boolean.TRUE.equals(js("const b=document.getElementById('open-drawer').getBoundingClientRect(),h=document.querySelector('.home-card-shell').getBoundingClientRect();return Math.abs(b.left-h.left-window.drawerTestInset)<1;")),"Arrow failed to return beside calculator");
                    js("document.getElementById('open-drawer').click();return true;");SystemClock.sleep(280);
                    result.put(theme,geometry);
                }
                js("setTheme(window.deviceTestOriginalTheme);return true;");
                report.putString("stream","PASS drawer arrow geometry, animation and tool-page return "+result+"\n");finish(Activity.RESULT_OK,report);return;
            }
            for(String theme:new String[]{"light","dark"}){
                js("setTheme('"+theme+"');return true;");SystemClock.sleep(200);
                PackageManager pm=getTargetContext().getPackageManager();
                ComponentName icon=new ComponentName(getTargetContext(),"com.kongye.calculator.Launcher"+(theme.equals("dark")?"Dark":"Light"));
                int state=pm.getComponentEnabledSetting(icon);
                check(state==PackageManager.COMPONENT_ENABLED_STATE_ENABLED || theme.equals("light")&&state==0,"Wrong launcher variant "+theme);
                result.put(theme+"Icon",true);
            }
            for(String source:new String[]{"y-x","x^2+y^2-4","group"}){
                js("mathToolState={source:"+JSONObject.quote(source.equals("group")?"y-x":source)+",record:{rawTokens:['y','=','x']},displayEquation:'y=x'};"+
                    (source.equals("group")?"mathToolState.curves=prepareGroupedGraph(['y','=','x',String.fromCharCode(10),'y','=','x','^','2',String.fromCharCode(10),'x','^','2','+','y','^','2','=','4']);":"")+
                    "openPage('函数图像');return true;");SystemClock.sleep(150);
                check(Boolean.TRUE.equals(js("const n=document.getElementById('open-drawer'),r=n.getBoundingClientRect();return !!document.elementFromPoint(r.x+r.width/2,r.y+r.height/2).closest('#open-drawer');")),"Navigation icon covered by tool page");
                if(source.equals("group"))check(((Number)js("return document.querySelectorAll('.curve').length")).intValue()==3,"Grouped curves missing");
                if(source.equals("group")){
                    check(((Number)js("return document.querySelectorAll('.graph-legend-row .graph-visible').length")).intValue()==3,"Missing per-function eyes");
                    check(Boolean.TRUE.equals(js("const before=[...document.querySelectorAll('.curve')].map(n=>n.getAttribute('d'));document.querySelectorAll('.graph-visible')[1].click();const after=[...document.querySelectorAll('.curve')].map(n=>n.getAttribute('d'));return before[0]===after[0]&&!after[1]&&before[2]===after[2];")),"Eye changed another curve");
                    screenshot("qa-33-function-eyes");
                    js("document.querySelectorAll('.graph-visible')[1].click();return true;");
                }
                Object timings=js("const b=document.querySelector('.graph-visible'),times=[];for(let i=0;i<10;i++){b.click();b.click();times.push(+document.querySelector('.function-graph').dataset.drawMs);}return times;");
                result.put(source,timings);
                JSONObject viewport=(JSONObject)js("const r=document.querySelector('.graph-viewport').getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height,dpr:devicePixelRatio};");
                int[] location=new int[2];runOnMainSync(()->web.getLocationOnScreen(location));
                float ratio=(float)viewport.getDouble("dpr");
                float cx=location[0]+(float)(viewport.getDouble("x")+viewport.getDouble("w")/2)*ratio;
                float cy=location[1]+(float)(viewport.getDouble("y")+viewport.getDouble("h")/2)*ratio;
                long down=SystemClock.uptimeMillis();event(down,MotionEvent.ACTION_DOWN,new float[]{cx},new float[]{cy});
                for(int i=1;i<=40;i++){event(down,MotionEvent.ACTION_MOVE,new float[]{cx+i*3},new float[]{cy+i});SystemClock.sleep(16);}
                event(down,MotionEvent.ACTION_UP,new float[]{cx+120},new float[]{cy+40});SystemClock.sleep(100);
                check(!"0,0".equals(js("return document.querySelector('.function-graph').dataset.center")),"Pan did not change center");
                down=SystemClock.uptimeMillis();event(down,MotionEvent.ACTION_DOWN,new float[]{cx-60},new float[]{cy});
                event(down,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[]{cx-60,cx+60},new float[]{cy,cy});
                for(int i=1;i<=30;i++){event(down,MotionEvent.ACTION_MOVE,new float[]{cx-60-i*2,cx+60+i*2},new float[]{cy,cy});SystemClock.sleep(16);}
                event(down,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[]{cx-120,cx+120},new float[]{cy,cy});
                event(down,MotionEvent.ACTION_UP,new float[]{cx-120},new float[]{cy});SystemClock.sleep(100);
                double zoom=((Number)js("return +document.querySelector('.function-graph').dataset.zoom")).doubleValue();
                check(zoom>1.8&&zoom<2.2,"Pinch scale incorrect "+zoom);
                result.put(source+"PanPinch",true);
                js("document.querySelector('.graph-reset').click();return true;");SystemClock.sleep(80);
                check("0,0".equals(js("return document.querySelector('.function-graph').dataset.center")),"Reset center failed");
                js("home();return true;");
            }
            js("mathToolState={source:'y-x^2-x',displayEquation:'y=x^2+x',record:{rawTokens:['y','=','x']}};openPage('求解方程');return true;");
            for(int i=0;i<100&&!Boolean.TRUE.equals(js("return document.querySelector('.solve-result').getAttribute('aria-busy')==='false'"));i++)SystemClock.sleep(100);
            JSONObject checkbox=(JSONObject)js("const start=performance.now();document.querySelector('.solve-substitution input').click();return {ms:performance.now()-start,visible:!document.querySelector('.solve-known').hidden};");
            check(checkbox.getBoolean("visible"),"Coordinate input did not update immediately");
            check(checkbox.getDouble("ms")<40,"Checkbox handler blocked UI "+checkbox);
            for(int i=0;i<6;i++){js("document.querySelector('.solve-substitution input').click();return true;");SystemClock.sleep(20);}
            for(int i=0;i<100&&!Boolean.TRUE.equals(js("return document.querySelector('.solve-result').getAttribute('aria-busy')==='false'"));i++)SystemClock.sleep(100);
            check(Boolean.TRUE.equals(js("return !document.querySelector('.solve-result').textContent.includes('无法')&&document.querySelectorAll('.solve-result .solve-math').length>0")),"Rapid solver switching failed");
            result.put("checkbox",checkbox);
            screenshot("qa-33-solver");
            js("home();return true;");
            check(Boolean.TRUE.equals(js("return document.getElementById('open-drawer').dataset.back==='false'")),"Navigation did not morph back");
            result.put("toolbarColumns",js("const bar=document.querySelector('.edit-tools'),previous=savedResults;const columns=[];for(const values of [[],['1'],['123456']]){savedResults=values;drawSavedResults();columns.push(getComputedStyle(bar).gridTemplateColumns.split(' ').length);}savedResults=previous;drawSavedResults();return columns;"));
            js("openPage('历史记录');return true;");SystemClock.sleep(200);
            check(Boolean.TRUE.equals(js("return [...document.querySelectorAll('.history-math .expression-group')].every(group=>{const b=group.querySelector('.group-brace').getBoundingClientRect(),r=group.querySelector('.group-rows').getBoundingClientRect();return b.right<=r.left+1&&Math.abs(b.top-r.top)<2&&Math.abs(b.bottom-r.bottom)<2;});")),"History brace overlap");
            result.put("historyGroups",js("return document.querySelectorAll('.history-math .expression-group').length;"));
            screenshot("qa-33-history");
            js("home();return true;");SystemClock.sleep(200);
            screenshot("qa-33-toolbar");
            js("setTheme(window.deviceTestOriginalTheme);return true;");
            result.put("toolbarHeight",js("return document.querySelector('.edit-tools').getBoundingClientRect().height"));
            report.putString("stream","PASS "+result.toString()+"\n");finish(Activity.RESULT_OK,report);
        } catch(Throwable error){report.putString("stream","FAIL "+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,report);}
    }
}
