package com.kongye.calculator;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Runs against the actual native update bridge, including public GitHub networking. */
public final class UpdateDeviceTest extends Instrumentation {
    private WebView web;
    private boolean download;
    @Override public void onCreate(Bundle args){super.onCreate(args);download="true".equals(args.getString("download"));start();}
    private WebView find(View v){if(v instanceof WebView)return(WebView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){WebView w=find(g.getChildAt(i));if(w!=null)return w;}}return null;}
    private Object js(String code)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);String[] value=new String[1];
        runOnMainSync(()->web.evaluateJavascript("JSON.stringify((()=>{"+code+"})())",r->{value[0]=r;latch.countDown();}));
        if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("JavaScript timeout");
        Object encoded=new JSONTokener(value[0]).nextValue();if(!(encoded instanceof String))throw new AssertionError(value[0]);return new JSONTokener((String)encoded).nextValue();
    }
    private void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    private void waitFor(String expression)throws Exception{for(int i=0;i<150;i++){if(Boolean.TRUE.equals(js("return "+expression)))return;SystemClock.sleep(200);}throw new AssertionError("Timed out: "+expression);}
    private JSONObject state()throws Exception{return(JSONObject)js("return JSON.parse(CalculatorHost.getUpdateState());");}
    private void shot(String name)throws Exception{SystemClock.sleep(200);Bitmap b=getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    private void clickText(String selector,String text)throws Exception{js("[...document.querySelectorAll("+JSONObject.quote(selector)+")].find(n=>n.textContent==="+JSONObject.quote(text)+").click();return true;");}
    @Override public void onStart(){Bundle report=new Bundle();try{
        Activity activity=startActivitySync(new Intent().setClassName(getTargetContext(),"com.kongye.calculator.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        for(int i=0;i<100&&web==null;i++){runOnMainSync(()->web=find(activity.getWindow().getDecorView()));SystemClock.sleep(100);}
        check(web!=null,"WebView missing");waitFor("document.documentElement.dataset.ready==='true'");
        js("document.getElementById('skip-brand-splash')?.click();openPage('关于');return true;");
        check(((Number)js("return document.querySelectorAll('.about-row').length")).intValue()==2,"About must have only two entries");
        check("关于".equals(js("return document.getElementById('drawer-about').ariaLabel")),"Old About name remains");
        check(Boolean.TRUE.equals(js("return document.querySelector('.about-version').textContent===JSON.parse(CalculatorHost.getUpdateState()).currentVersion")),"Version is not beside Check Updates");
        js("window.updateTestEvents=[];const receive=onAppUpdateEvent;window.onAppUpdateEvent=e=>{if(e.kind==='update')updateTestEvents.push(JSON.parse(JSON.stringify(e.state)));receive(e);};openPage('检查更新');return true;");
        waitFor("!['idle','checking'].includes(JSON.parse(CalculatorHost.getUpdateState()).phase)");
        JSONObject network=state();check(!"error".equals(network.optString("phase")),"GitHub check failed: "+network);
        check("关于".equals(js("return document.getElementById('utility-title').textContent")),"Check Updates navigated away from About");
        check(Boolean.TRUE.equals(js("return !!document.querySelector('.app-update-dialog[open]')")),"Check Updates is not a dialog");
        if(download){
            check("available".equals(network.optString("phase")),"Expected a newer release: "+network);
            waitFor("document.querySelector('.app-update-dialog .update-title')?.textContent==='发现新版本'");shot("qa-update-available");
            clickText(".update-actions button","立即下载");
            waitFor("['ready','download-error','cancelled'].includes(JSON.parse(CalculatorHost.getUpdateState()).phase)");
            JSONObject completed=state();check("ready".equals(completed.optString("phase")),"Download failed: "+completed);
            check(completed.getLong("downloaded")==completed.getLong("total")&&completed.getLong("total")>1000000,"Wrong byte counts");
            check(Boolean.TRUE.equals(js("return updateTestEvents.some(s=>s.phase==='downloading'&&s.downloaded>0&&s.downloaded<s.total)")),"No intermediate download progress");
            check(((Number)js("return document.querySelector('.update-progress').value")).intValue()==100,"Progress not complete");
            js("androidBack();showCheckUpdate();return true;");
            check("ready".equals(state().getString("phase")),"Download state lost on navigation");
            try(android.os.ParcelFileDescriptor fd=getTargetContext().getContentResolver().openFileDescriptor(Uri.parse("content://com.kongye.calculator.updates/update.apk"),"r")){check(fd!=null,"Installer cannot read verified APK");}
            boolean blocked=false;try{getTargetContext().getContentResolver().openFileDescriptor(Uri.parse("content://com.kongye.calculator.updates/other.apk"),"r");}catch(java.io.FileNotFoundException expected){blocked=true;}check(blocked,"Provider exposed another path");
            shot("qa-update-downloaded");report.putString("stream","PASS real GitHub update prompt, APK download with intermediate progress, checksum/package/signature/newer-version verification, persistent state and narrow install URI "+completed+"\n");
        }else{
            js("androidBack();return true;");
            js("openPage('更新历史');return true;");waitFor("document.querySelectorAll('.release-entry').length>0");
            check(Boolean.TRUE.equals(js("return !!document.querySelector('#utility-actions .release-github svg')&&document.querySelector('.release-history-header').children.length===1")),"GitHub icon is not at top right");
            check(Boolean.TRUE.equals(js("return [...document.querySelectorAll('.release-entry')].every(n=>!/# 空叶计算器|build \\d+|版本名称|版本代码/.test(n.textContent))")),"Release Markdown metadata remains visible");
            shot("qa-update-history-live");
            Field field=MainActivity.class.getDeclaredField("updates");field.setAccessible(true);AppUpdates updates=(AppUpdates)field.get(activity);
            Field releases=AppUpdates.class.getDeclaredField("releases"),at=AppUpdates.class.getDeclaredField("releasesAt");releases.setAccessible(true);at.setAccessible(true);
            Object old=releases.get(updates);long oldAt=at.getLong(updates);
            List<JSONObject> fixtures=new ArrayList<>();for(int i=0;i<25;i++)fixtures.add(AppUpdates.object("name","1.0."+(25-i),"tag_name","v1.0."+(25-i),"published_at","2026-11-24T08:00:00Z","body",i%2==0?"优化绘图":"公式对齐","html_url",AppUpdates.REPOSITORY+"/releases/tag/v1.0."+(25-i)));
            releases.set(updates,fixtures);at.setLong(updates,System.currentTimeMillis());
            js("openPage('更新历史');return true;");waitFor("document.querySelector('.release-summary').textContent==='共 25 个版本'");
            clickText(".release-pager button","下一页");waitFor("document.querySelector('.release-pager span').textContent==='2 / 3'");
            js("const n=document.querySelector('.release-search');n.value='绘图';n.dispatchEvent(new Event('input'));return true;");waitFor("document.querySelector('.release-summary').textContent==='共 13 个版本'");
            clickText(".release-pager button","下一页");waitFor("document.querySelectorAll('.release-entry').length===3");
            shot("qa-update-history-search");
            js("const n=document.querySelector('.release-search');n.value='无匹配记录';n.dispatchEvent(new Event('input'));return true;");waitFor("!!document.querySelector('.release-empty')");
            releases.set(updates,old);at.setLong(updates,oldAt);
            check(AppUpdates.isNewer(AppUpdates.object("name","1.0.2 · build 36"),"1.0.1",35),"New build comparison");
            check(!AppUpdates.isNewer(AppUpdates.object("name","1.0.0 · build 34"),"1.0.1",35),"Old build comparison");
            check(AppUpdates.isNewer(AppUpdates.object("tag_name","v1.2.0"),"1.1.9",35),"Semantic version comparison");
            js("openPage('关于');return true;");shot("qa-about-two-items");
            report.putString("stream","PASS real GitHub check/history, two-entry About, header GitHub icon, native search and pagination over 25 fixtures, empty results and version comparisons "+network+"\n");
        }
        finish(Activity.RESULT_OK,report);
    }catch(Throwable error){report.putString("stream","FAIL "+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,report);}}
}
