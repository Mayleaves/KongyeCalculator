package com.kongye.calculator;

import java.util.HashMap;
import java.util.Map;

final class Kinship {
    static final String[] LABELS={"爸爸","妈妈","哥哥","弟弟","姐姐","妹妹","丈夫","妻子","儿子","女儿"};
    private static final String CODES="fmbBzZhw sd".replace(" ","");
    private static final Map<String,String> NAMES=new HashMap<>();
    static {
        put("f=爸爸 / 父亲;m=妈妈 / 母亲;b=哥哥;B=弟弟;z=姐姐;Z=妹妹;h=丈夫;w=妻子;s=儿子;d=女儿");
        put("ff=爷爷 / 祖父;fm=奶奶 / 祖母;mf=外公 / 外祖父;mm=外婆 / 外祖母;fff=曾祖父;ffm=曾祖母;fmf=曾外祖父;fmm=曾外祖母");
        put("mff=外曾祖父;mfm=外曾祖母;mmf=外曾外祖父;mmm=外曾外祖母");
        put("fb=伯伯 / 伯父;fB=叔叔 / 叔父;fz=姑妈 / 姑姑;fZ=姑姑;mb=舅舅;mB=舅舅;mz=姨妈;mZ=小姨 / 姨妈");
        put("fbw=伯母;fBw=婶婶;fzh=姑父;fZh=姑父;mbw=舅妈;mBw=舅妈;mzh=姨父;mZh=姨父");
        put("bw=嫂子 / 嫂嫂;Bw=弟媳;zh=姐夫;Zh=妹夫;ss=孙子;sd=孙女;ds=外孙;dd=外孙女;sw=儿媳;dh=女婿");
        put("hf=公公;hm=婆婆;wf=岳父;wm=岳母;hb=大伯子;hB=小叔子;hz=大姑子;hZ=小姑子;wb=大舅子;wB=小舅子;wz=大姨子;wZ=小姨子");
        put("hff=丈夫的爷爷;hfm=丈夫的奶奶;wff=妻子的爷爷;wfm=妻子的奶奶;swf=亲家公;swm=亲家母;dhf=亲家公;dhm=亲家母");
        put("ssf=儿子;ssm=儿媳;sdf=儿子;sdm=儿媳;dsf=女婿;dsm=女儿;ddf=女婿;ddm=女儿");
        for(char relation:new char[]{'b','B','z','Z'}){
            boolean brother=relation=='b'||relation=='B';
            NAMES.put(relation+"s",brother?"侄子":"外甥");NAMES.put(relation+"d",brother?"侄女":"外甥女");
            NAMES.put("f"+relation+"s",brother?"堂兄 / 堂弟":"表兄 / 表弟");
            NAMES.put("f"+relation+"d",brother?"堂姐 / 堂妹":"表姐 / 表妹");
            NAMES.put("m"+relation+"s","表兄 / 表弟");NAMES.put("m"+relation+"d","表姐 / 表妹");
        }
        put("sss=曾孙;ssd=曾孙女;sds=曾外孙;sdd=曾外孙女;dss=外曾孙;dsd=外曾孙女");
    }
    private static void put(String pairs){for(String pair:pairs.split(";")){int split=pair.indexOf('=');NAMES.put(pair.substring(0,split),pair.substring(split+1));}}
    static String path(int[] relations){
        StringBuilder text=new StringBuilder("我");for(int relation:relations)text.append("的").append(LABELS[relation]);return text.toString();
    }
    static String title(int[] relations,boolean male){
        if(relations.length==0)return "自己";
        StringBuilder codes=new StringBuilder();for(int index:relations)codes.append(CODES.charAt(index));
        String chain=codes.toString();
        for(int pass=0;pass<chain.length()+4;pass++){
            String changed=chain.replace("fw","m").replace("mh","f").replace("hw","").replace("wh","");
            changed=changed.replace("bf","f").replace("Bm","m").replace("zf","f").replace("Zm","m")
                .replace("bm","m").replace("Bf","f").replace("zm","m").replace("Zf","f");
            if(changed.equals(chain))break;
            chain=changed;
        }
        if(chain.isEmpty())return "自己";
        if(chain.equals("fs") || chain.equals("ms"))return male?"自己 / 兄弟":"哥哥 / 弟弟";
        if(chain.equals("fd") || chain.equals("md"))return male?"姐姐 / 妹妹":"自己 / 姐妹";
        if(chain.equals("sf") || chain.equals("df"))return male?"自己":"丈夫";
        if(chain.equals("sm") || chain.equals("dm"))return male?"妻子":"自己";
        return NAMES.getOrDefault(chain,"没有统一称呼，可说："+path(relations));
    }
}
