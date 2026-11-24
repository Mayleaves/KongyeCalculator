package com.kongye.calculator;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Native networking only. The calculator WebView keeps network access blocked. */
final class AppUpdates {
    static final String REPOSITORY="https://github.com/Mayleaves/KongyeCalculator";
    private static final String API="https://api.github.com/repos/Mayleaves/KongyeCalculator/releases";
    private static final long MAX_APK=150L*1024*1024;
    private final Activity activity;
    private final Consumer<JSONObject> events;
    private final ExecutorService jobs=Executors.newFixedThreadPool(2);
    private final File folder,apk;
    private final String version;
    private final long code;
    private JSONObject state;
    private JSONObject latest;
    private volatile boolean cancelled,closed;
    private volatile List<JSONObject> releases;
    private volatile long releasesAt;
    private final Object historyLock=new Object();
    private boolean awaitingInstall;

    AppUpdates(Activity activity, Consumer<JSONObject> events) {
        this.activity=activity;this.events=events;
        folder=new File(activity.getFilesDir(),"updates");apk=new File(folder,"update.apk");
        try {
            PackageInfo info=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);
            version=info.versionName;code=versionCode(info);
        } catch(PackageManager.NameNotFoundException e){throw new IllegalStateException(e);}
        state=object("phase","idle","currentVersion",version,"currentCode",code,"downloaded",0,"total",0);
    }
    static JSONObject object(Object... values) {
        JSONObject o=new JSONObject();try{for(int i=0;i<values.length;i+=2)o.put((String)values[i],values[i+1]);}catch(Exception e){throw new IllegalArgumentException(e);}return o;
    }
    private static long versionCode(PackageInfo info){return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}
    synchronized String snapshot(){return state.toString();}
    private synchronized String phase(){return state.optString("phase");}
    private synchronized void set(String phase,Object... values){
        if(closed)return;
        try {state.put("phase",phase);for(int i=0;i<values.length;i+=2)state.put((String)values[i],values[i+1]);}
        catch(Exception e){throw new IllegalArgumentException(e);}
        events.accept(object("kind","update","state",state));
    }
    synchronized void check(){
        if(closed||Arrays.asList("checking","downloading","ready").contains(phase()))return;
        set("checking","message","正在检查更新…");
        jobs.execute(()->{
            try {
                JSONObject release=new JSONObject(read(API+"/latest",2*1024*1024));
                JSONObject asset=apkAsset(release);
                synchronized(this){latest=release;}
                boolean newer=isNewer(release,version,code);
                if(newer&&asset==null)throw new IOException("新版本尚未提供安装包，请稍后重试");
                set(newer?"available":"current","version",release.optString("tag_name"),"name",release.optString("name"),
                    "notes",release.optString("body"),"total",asset==null?0:asset.optLong("size"),"downloaded",0,
                    "message",newer?"发现新版本":"已是最新版本");
            }catch(Exception e){set("error","message",message(e));}
        });
    }
    static boolean isNewer(JSONObject release,String current,long currentCode){
        Matcher build=Pattern.compile("(?:build\\s*|版本代码[：: ]*)(\\d+)",Pattern.CASE_INSENSITIVE).matcher(release.optString("name")+"\n"+release.optString("body"));
        if(build.find())return Long.parseLong(build.group(1))>currentCode;
        String tag=release.optString("tag_name").replaceFirst("^[vV]","");
        String[] a=tag.split("[.\\-+]"),b=current.split("[.\\-+]");
        for(int i=0;i<Math.max(a.length,b.length);i++){
            long left=i<a.length&&a[i].matches("\\d+")?Long.parseLong(a[i]):0;
            long right=i<b.length&&b[i].matches("\\d+")?Long.parseLong(b[i]):0;
            if(left!=right)return left>right;
        }return false;
    }
    static JSONObject apkAsset(JSONObject release){
        JSONArray assets=release.optJSONArray("assets");if(assets==null)return null;
        for(int i=0;i<assets.length();i++){JSONObject a=assets.optJSONObject(i);if(a!=null&&a.optString("name").toLowerCase(Locale.ROOT).endsWith(".apk"))return a;}
        return null;
    }
    synchronized void download(){
        if(closed||latest==null||!Arrays.asList("available","cancelled","download-error").contains(phase()))return;
        final JSONObject release=latest,asset=apkAsset(release);if(asset==null)return;
        cancelled=false;set("downloading","downloaded",0,"message","正在下载…");
        jobs.execute(()->download(release,asset));
    }
    void cancel(){cancelled=true;}
    private void download(JSONObject release,JSONObject asset){
        File part=new File(folder,"update.part");HttpURLConnection connection=null;
        try {
            if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("无法创建下载目录");
            String checksum=asset.optString("digest").replaceFirst("^sha256:","");
            if(!checksum.matches("[a-fA-F0-9]{64}")){
                checksum="";JSONArray assets=release.getJSONArray("assets");
                for(int i=0;i<assets.length();i++){
                    JSONObject a=assets.getJSONObject(i);
                    if(!a.optString("name").equals("SHA256SUMS.txt"))continue;
                    for(String line:read(a.getString("browser_download_url"),128*1024).split("\\R")){
                        String[] fields=line.trim().split("\\s+",2);
                        if(fields.length==2&&fields[1].replaceFirst("^\\*","").equals(asset.getString("name")))checksum=fields[0];
                    }
                }
            }
            if(!checksum.matches("[a-fA-F0-9]{64}"))throw new IOException("未找到安装包校验信息");
            long expected=asset.optLong("size");if(expected<=0||expected>MAX_APK)throw new IOException("安装包大小异常");
            connection=connect(asset.getString("browser_download_url"));
            MessageDigest digest=MessageDigest.getInstance("SHA-256");long downloaded=0,last=0;
            try(InputStream input=connection.getInputStream();FileOutputStream output=new FileOutputStream(part)){
                byte[] buffer=new byte[32768];
                for(int n;(n=input.read(buffer))!=-1;){
                    if(cancelled||closed||Thread.currentThread().isInterrupted())throw new IOException("下载已取消");
                    downloaded+=n;if(downloaded>expected||downloaded>MAX_APK)throw new IOException("安装包大小不符");
                    output.write(buffer,0,n);digest.update(buffer,0,n);
                    long now=android.os.SystemClock.elapsedRealtime();
                    if(now-last>150){set("downloading","downloaded",downloaded,"total",expected);last=now;}
                }
                output.getFD().sync();
            }
            if(cancelled||closed)throw new IOException("下载已取消");
            if(downloaded!=expected||!hex(digest.digest()).equalsIgnoreCase(checksum))throw new IOException("安装包校验失败，请重新下载");
            verifyApk(part,true);
            if(cancelled||closed)throw new IOException("下载已取消");
            if(apk.exists()&&!apk.delete())throw new IOException("无法替换旧安装包");
            if(!part.renameTo(apk))throw new IOException("无法保存安装包");
            set("ready","downloaded",downloaded,"total",expected,"message","下载完成，可以安装");
        }catch(Exception e){part.delete();set(cancelled?"cancelled":"download-error","message",cancelled?"下载已取消":message(e));}
        finally{if(connection!=null)connection.disconnect();}
    }
    void verifyApk(File file,boolean newer) throws Exception {
        PackageManager manager=activity.getPackageManager();
        int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo candidate=manager.getPackageArchiveInfo(file.getAbsolutePath(),flags);
        PackageInfo own=manager.getPackageInfo(activity.getPackageName(),flags);
        if(candidate==null||!own.packageName.equals(candidate.packageName))throw new IOException("安装包不属于空叶计算器");
        if(newer&&versionCode(candidate)<=versionCode(own))throw new IOException("安装包版本不高于当前版本");
        Signature[] a=Build.VERSION.SDK_INT>=28?candidate.signingInfo.getApkContentsSigners():candidate.signatures;
        Signature[] b=Build.VERSION.SDK_INT>=28?own.signingInfo.getApkContentsSigners():own.signatures;
        if(a==null||b==null||a.length!=b.length||!Arrays.asList(a).containsAll(Arrays.asList(b)))throw new IOException("安装包签名不匹配");
    }
    void install(){
        activity.runOnUiThread(()->{
            try{
                if(!"ready".equals(phase())||!apk.isFile())throw new IOException("请先下载安装包");
                verifyApk(apk,true);
                if(!activity.getPackageManager().canRequestPackageInstalls()){
                    awaitingInstall=true;
                    activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));
                    return;
                }
                awaitingInstall=false;
                Uri uri=Uri.parse("content://"+activity.getPackageName()+".updates/update.apk");
                Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setClipData(ClipData.newRawUri("update",uri));
                activity.startActivity(intent);
            }catch(Exception e){set("download-error","message",message(e));}
        });
    }
    void resume(){if(awaitingInstall){awaitingInstall=false;if(activity.getPackageManager().canRequestPackageInstalls())install();}}
    void history(int id,int page,String query){
        if(closed)return;
        final String search=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        if(search.length()>100){events.accept(object("kind","history","id",id,"error","搜索内容过长"));return;}
        jobs.execute(()->{
            try{
                List<JSONObject> all=releaseIndex();List<JSONObject> found=new ArrayList<>();
                for(JSONObject release:all)if((release.optString("name")+" "+release.optString("tag_name")+" "+release.optString("body")).toLowerCase(Locale.ROOT).contains(search))found.add(release);
                int pages=Math.max(1,(found.size()+9)/10),current=Math.max(1,Math.min(pages,page));JSONArray rows=new JSONArray();
                for(int i=(current-1)*10;i<Math.min(current*10,found.size());i++){
                    JSONObject r=found.get(i);rows.put(object("name",r.optString("name"),"tag",r.optString("tag_name"),"date",r.optString("published_at"),"body",r.optString("body"),"url",r.optString("html_url")));
                }
                if(!closed)events.accept(object("kind","history","id",id,"page",current,"pages",pages,"total",found.size(),"items",rows));
            }catch(Exception e){if(!closed)events.accept(object("kind","history","id",id,"error",message(e)));}
        });
    }
    private List<JSONObject> releaseIndex() throws Exception { synchronized(historyLock) {
        if(releases!=null&&System.currentTimeMillis()-releasesAt<5*60*1000)return releases;
        List<JSONObject> index=new ArrayList<>();
        for(int page=1;page<=100;page++){
            JSONArray batch=new JSONArray(read(API+"?per_page=100&page="+page,8*1024*1024));
            for(int i=0;i<batch.length();i++){JSONObject r=batch.getJSONObject(i);if(!r.optBoolean("draft"))index.add(r);}
            if(batch.length()<100){releases=index;releasesAt=System.currentTimeMillis();return index;}
        }throw new IOException("更新记录较多，请在 GitHub 查看完整历史");
    } }
    void openGitHub(String url){
        if(!url.equals(REPOSITORY)&&!url.equals(REPOSITORY+"/releases")&&!url.startsWith(REPOSITORY+"/releases/tag/"))return;
        activity.runOnUiThread(()->{try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception ignored){android.widget.Toast.makeText(activity,"没有可打开链接的浏览器",0).show();}});
    }
    private static HttpURLConnection connect(String address) throws IOException {
        for(int redirect=0;redirect<8;redirect++){
            URL url=new URL(address);String host=url.getHost();
            if(!url.getProtocol().equals("https")||!(host.equals("api.github.com")||host.equals("github.com")||host.equals("release-assets.githubusercontent.com")||host.equals("objects.githubusercontent.com")))throw new IOException("更新地址无效");
            HttpURLConnection connection=(HttpURLConnection)url.openConnection();
            connection.setConnectTimeout(15000);connection.setReadTimeout(20000);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent","KongyeCalculator");connection.setRequestProperty("Accept-Encoding","identity");
            if(host.equals("api.github.com"))connection.setRequestProperty("Accept","application/vnd.github+json");
            int status=connection.getResponseCode();
            if(status>=300&&status<400){String next=connection.getHeaderField("Location");connection.disconnect();if(next==null)throw new IOException("更新地址重定向失败");address=new URL(url,next).toString();continue;}
            if(status!=200){connection.disconnect();throw new IOException(status==403||status==429?"请求过于频繁，请稍后重试":status==404?"暂无可用更新":"连接 GitHub 失败（"+status+"）");}
            return connection;
        }throw new IOException("更新地址重定向次数过多");
    }
    private static String read(String url,int limit) throws IOException {
        HttpURLConnection connection=connect(url);
        try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];for(int n;(n=input.read(buffer))!=-1;){if(output.size()+n>limit)throw new IOException("服务器返回内容过大");output.write(buffer,0,n);}return output.toString("UTF-8");
        }finally{connection.disconnect();}
    }
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    private static String message(Exception e){String m=e.getMessage();return m!=null&&m.matches(".*[\\u3400-\\u9fff].*")?m:"网络连接失败，请稍后重试";}
    void close(){closed=true;cancelled=true;jobs.shutdownNow();}
}
