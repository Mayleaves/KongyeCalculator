package com.kongye.calculator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.DragEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.math.MathContext;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class ClassicActivity extends Activity {
    private static final String[] CATEGORIES={"基础","科学","变量","函数"};
    private static final String[] CATEGORY_KEYS={"+−\n×÷","ƒ","a–z","f(x)"};
    private static final String[][][] DEFAULT_KEYS={
        {{"logₐ","xʸ","ⁿ√","a/b","÷"},{"x","7","8","9","×"},{"y","4","5","6","−"},{"%","1","2","3","+"},{"(",")","0",".","="},
            {"z","{","}","[","]"}},
        {{"sin","cos","tan","A","C"},{"arcsin","arccos","arctan","公倍","公约"},{"°","′","″","π","e"},
            {"i","∞","∠","x!","x!!"},{"abs","log₁₀","log₂","mod","ln"},
            {"∫ₐᵇ","∫","f′",">","≥"},{"<","≤",":","Σ","Π"},{"[⋮]","·","叉积","统计","lim"}},
        {{"a","b","c","k","m"},{"n","ρ","θ","", ""}}
    };
    private static final String VARIABLE_KEYS="xyzabckmnρθηhpr";
    private String[][][] keyboardLayout;
    private JSONObject customFunctions=new JSONObject();
    private final MathDocument document=new MathDocument();
    private SharedPreferences prefs;
    private JSONArray history=new JSONArray();
    private boolean dark,justEvaluated;
    private double ans,x,y,previousAns;
    private String temporaryValue="",temporaryLabel="",visibleAnswer="",visibleDecimal="";
    private int mode;
    private int ink,muted,bg,card,board,border,number,function,operator,accent,selected,ghost,slotFill;
    private FrameLayout host,drawer;
    private LinearLayout drawerPanel;
    private LinearLayout screen,historyScreen,keys,tabs,exactResult;
    private View utilityScreen;
    private ScrollView keyScroll;
    private final LinearLayout[] keyboardSections=new LinearLayout[CATEGORIES.length];
    private final TextView[] categoryTabs=new TextView[CATEGORIES.length];
    private TextView result,undoButton,redoButton,saveTemporary,temporaryChip;
    private MathEditorView editor;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("calculator",0);
        dark=prefs.getBoolean("dark",false);
        justEvaluated=prefs.getBoolean("confirmed",false);
        previousAns=readNumber("previousAns");
        mode=Math.max(0,Math.min(CATEGORIES.length-1,prefs.getInt("keyboard",0)));
        loadKeyboard();
        try{customFunctions=new JSONObject(prefs.getString("customFunctions","{}"));}catch(JSONException ignored){}
        x=readNumber("x");y=readNumber("y");ans=readNumber("ans");
        try{history=new JSONArray(prefs.getString("history","[]"));}catch(JSONException ignored){}
        String raw=state==null?prefs.getString("expression",""):state.getString("expression","");
        document.restore(raw,state==null?prefs.getInt("cursor",raw.length()):state.getInt("cursor",raw.length()));
        build();
        if(state!=null){
            temporaryValue=state.getString("temporaryValue","");temporaryLabel=state.getString("temporaryLabel","");
            updateTemporaryChip();
            if(state.getBoolean("showHistory"))showHistory();
        }
    }

    @Override public void onConfigurationChanged(Configuration configuration){
        super.onConfigurationChanged(configuration);
        boolean keepHistory=historyScreen!=null;
        build();
        if(keepHistory)showHistory();
    }
    @Override protected void onSaveInstanceState(Bundle state){
        state.putString("expression",document.value);state.putInt("cursor",document.cursor);
        state.putString("temporaryValue",temporaryValue);state.putString("temporaryLabel",temporaryLabel);
        state.putBoolean("showHistory",historyScreen!=null);
        super.onSaveInstanceState(state);
    }
    @Override protected void onPause(){super.onPause();save();}
    @Override public void onBackPressed(){
        if(historyScreen!=null)closeHistory();else if(utilityScreen!=null)closeUtility();else if(drawer!=null)closeDrawer();else super.onBackPressed();
    }
    private double readNumber(String key){return Double.longBitsToDouble(prefs.getLong(key,0));}
    private void save(){
        prefs.edit().putBoolean("dark",dark).putBoolean("confirmed",justEvaluated)
            .putInt("keyboard",mode).putInt("cursor",document.cursor).putString("expression",document.value)
            .putString("history",history.toString()).putLong("x",Double.doubleToLongBits(x))
            .putLong("y",Double.doubleToLongBits(y)).putLong("ans",Double.doubleToLongBits(ans))
            .putLong("previousAns",Double.doubleToLongBits(previousAns)).apply();
    }

    private void palette(){
        bg=color("#E5E5E8","#191A1E");ink=color("#4C4D51","#E4E4E7");muted=color("#85868A","#A6A6AE");
        board=color("#E5E5E8","#202126");card=color("#FFFFFF","#292A30");border=color("#DEDEE2","#38393F");
        number=color("#FFFFFF","#292A30");function=color("#E9E9EC","#36373D");
        operator=number;accent=color("#5F6065","#D2D2D7");slotFill=color("#E9E9EC","#414248");
        selected=color("#64656B","#D0D0D6");ghost=color("#A0A0A6","#777880");
    }

    private void build(){
        palette();drawer=null;drawerPanel=null;historyScreen=null;utilityScreen=null;
        getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(board);
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        host=new FrameLayout(this);host.setBackgroundColor(bg);
        screen=column();screen.setBackground(shape(card,0,22));screen.setClipToOutline(true);
        FrameLayout.LayoutParams screenSize=new FrameLayout.LayoutParams(-1,-1);
        screenSize.setMargins(dp(6),dp(6),dp(6),dp(6));host.addView(screen,screenSize);setContentView(host);
        host.setOnApplyWindowInsetsListener((v,i)->{
            v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());
            return i.consumeSystemWindowInsets();
        });
        host.requestApplyInsets();
        buildHeader();
        buildDisplay();
        buildKeyboard();
        refresh();
    }

    private void buildHeader(){
        LinearLayout header=row();header.setPadding(dp(10),0,dp(10),0);
        screen.addView(header,new LinearLayout.LayoutParams(-1,dp(52)));
        header.addView(icon("menu","打开侧栏",this::openDrawer),new LinearLayout.LayoutParams(dp(46),-1));
        TextView name=label("空叶计算器",18,ink);name.setTypeface(null,Typeface.BOLD);name.setSingleLine(true);
        header.addView(name,new LinearLayout.LayoutParams(0,-1,1));
        header.addView(icon("history","计算历史",this::showHistory),new LinearLayout.LayoutParams(dp(46),-1));
        undoButton=icon("undo","撤销",()->{document.undo();justEvaluated=false;refresh();});
        redoButton=icon("redo","重做",()->{document.redo();justEvaluated=false;refresh();});
        header.addView(undoButton,new LinearLayout.LayoutParams(dp(46),-1));
        header.addView(redoButton,new LinearLayout.LayoutParams(dp(46),-1));
        View divider=new View(this);divider.setBackgroundColor(border);screen.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
    }

    private void buildDisplay(){
        LinearLayout space=column();space.setPadding(dp(16),dp(9),dp(16),dp(6));
        screen.addView(space,new LinearLayout.LayoutParams(-1,0,1));
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);
        space.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content=column();scroll.addView(content);
        editor=new MathEditorView(this,document,()->{justEvaluated=false;refresh();},this::press,ink,muted,ghost,accent,slotFill);
        content.addView(editor,new LinearLayout.LayoutParams(-1,-2));
        exactResult=column();content.addView(exactResult,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout decimalRow=row();decimalRow.setPadding(dp(10),0,0,0);
        content.addView(decimalRow,new LinearLayout.LayoutParams(-1,-2));
        result=label("",22,muted);result.setTypeface(Typeface.SERIF);
        result.setOnLongClickListener(v->copyResult());
        decimalRow.addView(result,new LinearLayout.LayoutParams(-2,-2));
        saveTemporary=button("+","暂存当前结果",this::storeTemporaryValue);
        saveTemporary.setTextSize(20);saveTemporary.setTextColor(accent);saveTemporary.setBackground(touch(function,border,8));
        LinearLayout.LayoutParams controls=new LinearLayout.LayoutParams(dp(34),dp(34));controls.setMargins(dp(12),0,0,0);
        decimalRow.addView(saveTemporary,controls);
        editor.requestFocus();
    }

    private void buildKeyboard(){
        LinearLayout keyboard=column();keyboard.setBackground(shape(board,0,0));
        boolean landscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        screen.addView(keyboard,new LinearLayout.LayoutParams(-1,dp(keyboardHeight())));

        LinearLayout tools=row();keyboard.addView(tools,new LinearLayout.LayoutParams(-1,dp(landscape?36:44)));
        tools.addView(icon("trash","清空表达式",()->{document.replace("");justEvaluated=false;refresh();}),new LinearLayout.LayoutParams(0,-1,1));
        temporaryChip=button("","插入暂存结果",this::insertTemporaryValue);
        temporaryChip.setTextSize(12);temporaryChip.setTextColor(accent);temporaryChip.setSingleLine();
        temporaryChip.setEllipsize(TextUtils.TruncateAt.END);temporaryChip.setPadding(dp(5),0,dp(5),0);
        temporaryChip.setBackground(touch(function,border,8));
        LinearLayout.LayoutParams chipParams=new LinearLayout.LayoutParams(0,dp(30),1.25f);
        chipParams.setMargins(dp(2),0,dp(2),0);tools.addView(temporaryChip,chipParams);
        temporaryChip.setOnLongClickListener(v->{temporaryValue="";temporaryLabel="";updateTemporaryChip();return true;});
        updateTemporaryChip();
        addTool(tools,"group","插入大括号分组或续加一行",()->{document.group();justEvaluated=false;refresh();});
        addTool(tools,"back","光标左移",()->press("←"));
        addTool(tools,"forward","光标右移",()->press("→"));
        addTool(tools,"delete","退格",()->press("退格"));
        tools.getChildAt(tools.getChildCount()-1).setOnLongClickListener(v->{document.replace("");justEvaluated=false;refresh();return true;});

        LinearLayout body=row();keyboard.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        tabs=column();tabs.setBackgroundColor(board);body.addView(tabs,new LinearLayout.LayoutParams(dp(52),-1));
        keys=column();keys.setBackgroundColor(border);body.addView(keys,new LinearLayout.LayoutParams(0,-1,1));
        renderKeys();
    }

    private void addTool(LinearLayout tools,String glyph,String name,Runnable action){
        tools.addView(icon(glyph,name,action),new LinearLayout.LayoutParams(0,-1,1));
    }
    private void selectMode(int requested){
        int next=Math.max(0,Math.min(requested,CATEGORIES.length-1));
        if(keyScroll!=null && keyboardSections[next]!=null){
            keyScroll.scrollTo(0,keyboardSections[next].getTop());updateKeyboardMode();
        }
    }
    private void renderKeys(){
        final int initialMode=mode;
        tabs.removeAllViews();keys.removeAllViews();
        for(int i=0;i<CATEGORIES.length;i++){
            final int page=i;
            FrameLayout slot=new FrameLayout(this);tabs.addView(slot,new LinearLayout.LayoutParams(-1,0,1));
            TextView tab=button(CATEGORY_KEYS[i],CATEGORIES[i]+"键盘",()->selectMode(page));
            categoryTabs[i]=tab;tab.setTextSize(i==0?15:i==1?25:17);
            tab.setLineSpacing(0,.85f);tab.setIncludeFontPadding(false);
            int side=Math.min(40,Math.max(24,(keyboardHeight()-36)/4-8));
            FrameLayout.LayoutParams tabSize=new FrameLayout.LayoutParams(dp(side),dp(side),Gravity.CENTER);
            slot.addView(tab,tabSize);
        }
        keyScroll=new ScrollView(this);keyScroll.setFillViewport(true);keyScroll.setVerticalScrollBarEnabled(false);
        keys.addView(keyScroll,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout sheet=column();keyScroll.addView(sheet);
        for(int page=0;page<CATEGORIES.length;page++){
            LinearLayout section=column();section.setMinimumHeight(dp(keyHeight()*5));
            keyboardSections[page]=section;sheet.addView(section,new LinearLayout.LayoutParams(-1,-2));
            if(page==3)addFormulaRows(section,false);else addKeyboardSection(section,page);
        }
        updateCategoryTabs();
        keyScroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->updateKeyboardMode());
        keyScroll.post(()->{keyScroll.scrollTo(0,keyboardSections[initialMode].getTop());updateKeyboardMode();});
    }
    private void updateKeyboardMode(){
        int next=0;
        for(int i=1;i<keyboardSections.length;i++){
            if(keyScroll.getScrollY()+dp(2)>=keyboardSections[i].getTop())next=i;
        }
        if(next!=mode){mode=next;updateCategoryTabs();}
    }
    private void updateCategoryTabs(){
        for(int i=0;i<categoryTabs.length;i++){
            TextView tab=categoryTabs[i];if(tab==null)continue;
            tab.setSelected(i==mode);tab.setTextColor(i==mode?color("#FFFFFF","#25262B"):ink);
            tab.setBackground(touch(i==mode?selected:function,0,10));
        }
    }
    private void addKeyboardSection(LinearLayout sheet,int page){
        for(int r=0;r<keyboardLayout[page].length;r++){
            LinearLayout row=row();sheet.addView(row,new LinearLayout.LayoutParams(-1,dp(keyHeight())));
            for(int c=0;c<5;c++){
                String key=keyboardLayout[page][r][c];
                boolean equal=key.equals("="), ordinary=key.length()==1 && "0123456789.".contains(key);
                boolean template=key.equals("logₐ")||key.equals("xʸ")||key.equals("ⁿ√")||key.equals("a/b");
                TextView view=template?icon(key,keyName(key),()->press(key)):button(keyFace(key),keyName(key),()->press(key));
                view.setTextSize(ordinary?24:page==0?23:key.length()>4?14:key.length()>2?16:21);
                if(page!=0 || !ordinary && c!=4)view.setTypeface(Typeface.SERIF);
                view.setTextColor(equal?color("#FFFFFF","#25262B"):ink);
                view.setBackground(touch(equal?selected:number,0,0));
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);
                p.setMargins(1,1,0,0);row.addView(view,p);
                if(key.isEmpty())view.setEnabled(false);
                if(key.length()==1 && VARIABLE_KEYS.contains(key))view.setOnLongClickListener(v->{editVariable(key);return true;});
            }
        }
        if(page==2)addFormulaRows(sheet,true);
    }

    private int keyboardHeight(){
        int deviceHeight=getResources().getConfiguration().screenHeightDp;
        boolean landscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        return landscape?Math.max(140,Math.min(224,deviceHeight*57/100)):Math.max(264,Math.min(384,deviceHeight*43/100));
    }
    private int keyHeight(){
        boolean landscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        return (keyboardHeight()-(landscape?36:44))/5;
    }
    private String keyFace(String name){
        switch(name){case "abs":return "|▫|";case "x!":return "▫!";case "x!!":return "▫!!";
            case "A":return "A▫";case "C":return "C▫";default:return name;}
    }

    private void addFormulaRows(LinearLayout list,boolean limited){
        for(int i=0;i<(limited?3:FormulaCatalog.SECTIONS.length);i++){
            String[] section=FormulaCatalog.SECTIONS[i];
            LinearLayout sectionRow=row();list.addView(sectionRow,new LinearLayout.LayoutParams(-1,-2));
            TextView title=label(section[0],14,ink);title.setGravity(Gravity.CENTER);title.setBackgroundColor(number);
            LinearLayout.LayoutParams labelSize=new LinearLayout.LayoutParams(0,-1,2);labelSize.setMargins(1,1,0,0);
            sectionRow.addView(title,labelSize);
            LinearLayout entries=column();sectionRow.addView(entries,new LinearLayout.LayoutParams(0,-2,3));
            for(int j=1;j<section.length;j+=2){
                String ui=section[j], raw=section[j+1];
                TextView entry=button(ui,"插入 "+ui,()->{document.replace(raw);justEvaluated=false;refresh();});
                entry.setTypeface(Typeface.SERIF);entry.setTextSize(ui.length()>16?14:18);entry.setTextColor(accent);
                entry.setBackground(touch(number,0,0));
                LinearLayout.LayoutParams entrySize=new LinearLayout.LayoutParams(-1,dp(Math.max(38,keyHeight()*4/5)));
                entrySize.setMargins(1,1,0,0);entries.addView(entry,entrySize);
            }
        }
        if(!limited && customFunctions.length()>0){
            java.util.Iterator<String> names=customFunctions.keys();
            while(names.hasNext()){
                String name=names.next();
                TextView entry=button(name+"(x) = "+customFunctions.optString(name),"插入 "+name,()->{
                    if(justEvaluated)document.replace("");document.bracket(true,name);justEvaluated=false;refresh();
                });
                entry.setTextSize(17);entry.setTypeface(Typeface.SERIF);list.addView(entry,new LinearLayout.LayoutParams(-1,dp(46)));
            }
        }
    }

    private void loadKeyboard(){
        keyboardLayout=new String[DEFAULT_KEYS.length][][];
        for(int p=0;p<DEFAULT_KEYS.length;p++){
            keyboardLayout[p]=new String[DEFAULT_KEYS[p].length][];
            for(int r=0;r<DEFAULT_KEYS[p].length;r++)keyboardLayout[p][r]=DEFAULT_KEYS[p][r].clone();
        }
        try{
            JSONArray saved=new JSONArray(prefs.getString("keyLayout","[]"));
            for(int p=0;p<keyboardLayout.length && p<saved.length();p++){
                JSONArray page=saved.getJSONArray(p);
                for(int r=0;r<keyboardLayout[p].length && r<page.length();r++){
                    JSONArray row=page.getJSONArray(r);
                    for(int c=0;c<5 && c<row.length();c++)keyboardLayout[p][r][c]=row.getString(c);
                }
            }
        }catch(JSONException ignored){}
    }

    private void saveKeyboard(){
        JSONArray state=new JSONArray();
        for(String[][] page:keyboardLayout){
            JSONArray rows=new JSONArray();
            for(String[] row:page){JSONArray cells=new JSONArray();for(String key:row)cells.put(key);rows.put(cells);}
            state.put(rows);
        }
        prefs.edit().putString("keyLayout",state.toString()).apply();renderKeys();
    }

    private String keyName(String key){
        switch(key){case "=":return "等于";case "logₐ":return "自定义底数对数";case "xʸ":return "幂";
            case "ⁿ√":return "n 次方根";case "a/b":return "分数";default:return key;}
    }

    private void press(String key){
        if(key.isEmpty())return;
        if(java.util.Arrays.asList("∫ₐᵇ","∫","f′","Σ","Π","叉积","统计","lim","[⋮]","·").contains(key)){
            openNumericTool(key);return;
        }
        if(key.equals("=")){calculate();return;}
        if(key.equals("←") || key.equals("→")){
            document.moveTo(document.cursor+(key.equals("←")?-1:1));
        }else if(key.equals("退格")){
            document.backspace();
        }else{
            if(justEvaluated){
                boolean chained="+−-×*÷/%^!".contains(key) || key.equals("xʸ") || key.equals("a/b") || key.equals("x!") || key.equals("x!!");
                document.replace(chained?visibleAnswer:"");
            }
            switch(key){
                case "logₐ":document.insertTemplate(MathDocument.LOG);break;
                case "xʸ":document.insertTemplate(MathDocument.POWER);break;
                case "ⁿ√":document.insertTemplate(MathDocument.ROOT);break;
                case "a/b":document.insertTemplate(MathDocument.FRACTION);break;
                case "(":document.bracket(true,"");break;
                case ")":document.bracket(false,"");break;
                case "√":document.bracket(true,"sqrt");break;
                case "1/x":document.bracket(true,"1÷");break;
                case "sin":case "cos":case "tan":case "ln":case "log":case "abs":document.bracket(true,key);break;
                case "x!":document.insert("!");break;
                case "x!!":document.insert("!!");break;
                case "arcsin":case "arccos":case "arctan":document.bracket(true,key);break;
                case "A":document.insertCall("perm");break;
                case "C":document.insertCall("comb");break;
                case "公倍":document.insertCall("lcm");break;
                case "公约":document.insertCall("gcd");break;
                case "mod":document.insertCall("mod");break;
                case "log₁₀":document.bracket(true,"log");break;
                case "log₂":document.bracket(true,"log2");break;
                case "{":document.group();break;
                case "}":document.moveTo(document.value.length());break;
                case "[":document.bracket(true,"");break;
                case "]":document.bracket(false,"");break;
                case "x²":document.insert("x^2");break;
                case "y²":document.insert("y^2");break;
                default:document.insert(key);
            }
        }
        justEvaluated=false;
        refresh();
    }

    private Map<String,Double> variables(){
        return variables(ans);
    }
    private Map<String,Double> variables(double answer){
        Map<String,Double> values=new HashMap<>();values.put("x",x);values.put("y",y);values.put("Ans",answer);
        for(int i=2;i<VARIABLE_KEYS.length();i++){
            String name=VARIABLE_KEYS.substring(i,i+1);values.put(name,readNumber("var_"+name));
        }
        return values;
    }
    private List<Calculator.Value> evaluate(List<String> expressions,double answer){
        List<Calculator.Value> values=new ArrayList<>();Map<String,Double> inputs=variables(answer);
        for(int i=0;i<expressions.size();i++){
            String expression=expressions.get(i);
            if(expression.trim().isEmpty())throw new IllegalArgumentException("请填写第 "+(i+1)+" 行算式");
            values.add(evaluateLine(expression,inputs));
        }
        return values;
    }
    private Calculator.Value evaluateLine(String expression,Map<String,Double> inputs){
        if(expression.contains("∫")||expression.contains("Σ")||expression.contains("Π")||expression.contains("lim")
                ||expression.contains("叉积")||expression.contains("f′")||expression.contains("[⋮]"))
            throw new IllegalArgumentException("这个符号当前可输入，暂不计算");
        for(char comparator:new char[]{'>','≥','<','≤'}){
            int position=expression.indexOf(comparator);
            if(position<0)continue;
            double left=evaluateWithFunctions(expression.substring(0,position),inputs,0).number;
            double right=evaluateWithFunctions(expression.substring(position+1),inputs,0).number;
            boolean pass=comparator=='>'?left>right:comparator=='≥'?left>=right:comparator=='<'?left<right:left<=right;
            return new Calculator.Value(pass?1:0,null);
        }
        expression=expression.replace(':','÷');
        int equals=expression.indexOf('=');
        if(equals<0)return evaluateWithFunctions(expression,inputs,0);
        String left=expression.substring(0,equals).trim(),right=expression.substring(equals+1);
        if(left.equals("y"))return evaluateWithFunctions(right,inputs,0);
        return evaluateWithFunctions("("+left+")−("+right+")",inputs,0);
    }
    private Calculator.Value evaluateWithFunctions(String expression,Map<String,Double> inputs,int depth){
        if(depth>=20)throw new IllegalArgumentException("自定义函数嵌套过深. 请检查是否递归调用");
        return Calculator.evaluateDetailed(expression,true,inputs,(name,argument)->{
            if(!customFunctions.has(name))throw new IllegalArgumentException("未知函数："+name);
            Map<String,Double> local=new HashMap<>(inputs);local.put("x",argument.number);
            return evaluateWithFunctions(customFunctions.optString(name),local,depth+1);
        });
    }
    private void openNumericTool(String key){
        LinearLayout fields=column();fields.setPadding(dp(22),0,dp(22),0);
        boolean vector=key.equals("叉积")||key.equals("·")||key.equals("[⋮]");
        boolean list=key.equals("统计")||vector;
        boolean range=key.equals("∫ₐᵇ")||key.equals("Σ")||key.equals("Π");
        EditText first=numericToolInput(fields,list?"用逗号分隔，例如 1,2,3":"关于 x 的算式，例如 x^2");
        EditText second=(key.equals("统计")||key.equals("[⋮]"))?null:numericToolInput(fields,vector?"第二个向量，例如 3,2,1":range?"起点":"x 的值");
        EditText third=range?numericToolInput(fields,"终点"):null;
        String title=key.equals("∫")?"从 0 起的数值积分":key.equals("∫ₐᵇ")?"数值定积分":key.equals("f′")?"数值导数":key;
        AlertDialog window=dialog().setTitle(title).setView(fields).setPositiveButton("计算",null).setNegativeButton("取消",null).create();
        window.setOnShowListener(d->window.getButton(-1).setOnClickListener(v->{
            try{
                String input=first.getText().toString();
                String other=second==null?"":second.getText().toString();
                String output;
                if(list)output=listOperation(key,numericList(input),other);
                else{
                    double a=evaluateWithFunctions(other,variables(),0).number;
                    double b=third==null?0:evaluateWithFunctions(third.getText().toString(),variables(),0).number;
                    output=Calculator.format(numericOperation(key,input,a,b));
                    if(!key.equals("Σ")&&!key.equals("Π"))output="约等于 "+output;
                }
                window.dismiss();dialog().setTitle(title).setMessage(output).setPositiveButton("关闭",null).show();
            }catch(IllegalArgumentException error){first.setError(error.getMessage()==null?"请检查输入":error.getMessage());}
        }));window.show();
    }
    private EditText numericToolInput(LinearLayout fields,String hint){
        EditText input=new EditText(this);input.setTextColor(ink);input.setHintTextColor(muted);input.setSingleLine(true);input.setHint(hint);
        fields.addView(input,new LinearLayout.LayoutParams(-1,dp(48)));return input;
    }
    private double[] numericList(String values){
        String[] items=values.trim().split("[,，]",-1);
        if(items.length>1000)throw new IllegalArgumentException("最多可输入 1000 个数值");
        double[] result=new double[items.length];
        for(int i=0;i<items.length;i++)result[i]=evaluateWithFunctions(items[i],variables(),0).number;
        return result;
    }
    private String listOperation(String key,double[] values,String other){
        if(key.equals("[⋮]"))return java.util.Arrays.toString(values);
        if(key.equals("统计")){
            double sum=0,min=values[0],max=values[0];
            for(double value:values){sum+=value;min=Math.min(min,value);max=Math.max(max,value);}
            return "个数  "+values.length+"\n合计  "+Calculator.format(sum)+"\n平均值  "+Calculator.format(sum/values.length)
                +"\n最小值  "+Calculator.format(min)+"\n最大值  "+Calculator.format(max);
        }
        double[] right=numericList(other);
        if(key.equals("·")){
            if(values.length!=right.length)throw new IllegalArgumentException("两个向量的维数应相同");
            double dot=0;for(int i=0;i<values.length;i++)dot+=values[i]*right[i];return Calculator.format(dot);
        }
        if(values.length!=3||right.length!=3)throw new IllegalArgumentException("叉积需要两个三维向量");
        return "("+Calculator.format(values[1]*right[2]-values[2]*right[1])+", "+Calculator.format(values[2]*right[0]-values[0]*right[2])
            +", "+Calculator.format(values[0]*right[1]-values[1]*right[0])+")";
    }
    private double numericOperation(String key,String expression,double a,double b){
        Map<String,Double> inputs=variables();
        java.util.function.DoubleUnaryOperator sample=x->{inputs.put("x",x);return evaluateWithFunctions(expression,inputs,0).number;};
        if(key.equals("Σ")||key.equals("Π")){
            if(a!=Math.rint(a)||b!=Math.rint(b)||a>b||b-a>999||Math.abs(a)>1e9||Math.abs(b)>1e9)
                throw new IllegalArgumentException("请使用整数上下限，最多计算 1000 项");
            double result=key.equals("Σ")?0:1;
            for(int i=(int)a;i<=(int)b;i++)result=key.equals("Σ")?result+sample.applyAsDouble(i):result*sample.applyAsDouble(i);
            return result;
        }
        double h=1e-5*Math.max(1,Math.abs(a));
        if(key.equals("f′"))return (sample.applyAsDouble(a+h)-sample.applyAsDouble(a-h))/(2*h);
        if(key.equals("lim")){
            double left=sample.applyAsDouble(a-h),right=sample.applyAsDouble(a+h);
            double scale=Math.max(1,Math.max(Math.abs(left),Math.abs(right)));
            if(Math.abs(left-right)>scale*.001)throw new IllegalArgumentException("两侧结果不一致，无法确认极限");
            return (left+right)/2;
        }
        if(key.equals("∫")){b=a;a=0;}
        double spacing=(b-a)/256;
        double total=sample.applyAsDouble(a)+sample.applyAsDouble(b);
        for(int i=1;i<256;i++)total+=(i%2==0?2:4)*sample.applyAsDouble(a+i*spacing);
        return total*spacing/3;
    }
    private String formatted(List<Calculator.Value> values,String delimiter){
        List<String> formatted=new ArrayList<>();
        for(Calculator.Value value:values)formatted.add(Calculator.format(value.number));
        return String.join(delimiter,formatted);
    }
    private void refresh(){
        editor.refresh();
        updateEnabled(undoButton,document.canUndo());updateEnabled(redoButton,document.canRedo());
        clearResult();
        if(document.value.isEmpty())return;
        try{
            List<String> expressions=document.expressions();
            displayResult(expressions,evaluate(expressions,justEvaluated?previousAns:ans));
        }
        catch(IllegalArgumentException ignored){}
    }
    private void clearResult(){
        exactResult.removeAllViews();result.setTextColor(muted);result.setTextSize(24);result.setText("");
        saveTemporary.setVisibility(View.GONE);visibleAnswer="";visibleDecimal="";
    }
    private Calculator.Fraction displayedFraction(List<String> expressions,List<Calculator.Value> values){
        Calculator.Fraction exact=values.size()==1?values.get(0).exact:null;
        String input=expressions.get(0);
        return exact!=null && exact.fitsDisplay() && (input.contains("/") || input.contains("÷"))?exact:null;
    }
    private void displayResult(List<String> expressions,List<Calculator.Value> values){
        Calculator.Fraction fraction=displayedFraction(expressions,values);
        if(fraction!=null){
            MathDocument line=new MathDocument();line.restore("="+fractionTemplate(fraction),0);
            MathEditorView stacked=new MathEditorView(this,line,muted,muted,"");
            stacked.setContentDescription("等于 "+fraction.label());exactResult.addView(stacked,new LinearLayout.LayoutParams(-1,-2));
        }
        boolean equation=expressions.size()==1 && expressions.get(0).contains("=") && !expressions.get(0).startsWith("y=");
        result.setText((equation?"左式 − 右式 = ":"= ")+(fraction==null?formatted(values,"\n= "):roundedFraction(fraction)));
        if(values.size()==1){
            visibleDecimal=fraction==null?Calculator.format(values.get(0).number):roundedFraction(fraction);
            visibleAnswer=fraction==null?visibleDecimal:fractionTemplate(fraction);
            saveTemporary.setVisibility(View.VISIBLE);
        }
    }
    private String fractionTemplate(Calculator.Fraction fraction){
        return ""+MathDocument.FRACTION+fraction.top.toString()+MathDocument.NEXT+fraction.bottom+MathDocument.END;
    }
    private String roundedFraction(Calculator.Fraction fraction){return fraction.decimal(new MathContext(10)).stripTrailingZeros().toString();}
    private void storeTemporaryValue(){temporaryValue=visibleAnswer;temporaryLabel=visibleDecimal;updateTemporaryChip();}
    private void updateTemporaryChip(){
        temporaryChip.setText(temporaryLabel);temporaryChip.setVisibility(temporaryValue.isEmpty()?View.GONE:View.VISIBLE);
        temporaryChip.setContentDescription("插入暂存结果 "+temporaryLabel);
    }
    private void insertTemporaryValue(){
        if(temporaryValue.isEmpty())return;
        if(justEvaluated)document.replace(temporaryValue);else document.insert(temporaryValue);
        justEvaluated=false;refresh();
    }
    private void updateEnabled(View view,boolean enabled){view.setEnabled(enabled);view.setAlpha(enabled?1f:.40f);}
    private void calculate(){
        if(document.value.isEmpty())return;
        try{
            List<String> expressions=document.expressions();
            double inputAnswer=justEvaluated?previousAns:ans;
            List<Calculator.Value> values=evaluate(expressions,inputAnswer);
            JSONObject entry=new JSONObject();entry.put("editor",document.value);
            Calculator.Fraction fraction=displayedFraction(expressions,values);
            entry.put("expression",String.join("  /  ",expressions));
            entry.put("result",fraction==null?formatted(values,"  /  "):roundedFraction(fraction));
            if(fraction!=null){entry.put("fraction",fraction.label());entry.put("fractionTemplate",fractionTemplate(fraction));}
            entry.put("createdAt",System.currentTimeMillis());
            JSONArray next=new JSONArray();next.put(entry);
            for(int i=0;i<Math.min(99,history.length());i++)next.put(history.get(i));history=next;
            previousAns=inputAnswer;ans=values.get(values.size()-1).number;
            justEvaluated=values.size()==1;
            editor.refresh();clearResult();displayResult(expressions,values);
            save();
        }catch(IllegalArgumentException|JSONException error){
            clearResult();
            result.setTextColor(color("#A63E32","#FFAD9D"));result.setTextSize(16);
            result.setText(error.getMessage()==null?"请检查算式":error.getMessage());
        }
    }

    private AlertDialog.Builder dialog(){
        return new AlertDialog.Builder(this,dark?R.style.AppDialogDark:R.style.AppDialogLight);
    }
    private void showHistory(){
        if(historyScreen!=null)host.removeView(historyScreen);
        screen.setVisibility(View.GONE);
        historyScreen=column();historyScreen.setBackgroundColor(card);
        host.addView(historyScreen,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout bar=row();bar.setBackgroundColor(bg);bar.setPadding(dp(4),0,dp(10),0);
        historyScreen.addView(bar,new LinearLayout.LayoutParams(-1,dp(58)));
        bar.addView(icon("back","返回计算",this::closeHistory),new LinearLayout.LayoutParams(dp(48),-1));
        TextView title=label("历史记录",20,ink);title.setTypeface(null,Typeface.BOLD);
        bar.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        TextView clear=button("清空","清空历史记录",this::confirmClearHistory);
        TextView share=button("分享","分享历史记录",this::shareHistory);
        for(TextView action:new TextView[]{clear,share}){
            action.setTextSize(14);action.setTextColor(accent);updateEnabled(action,history.length()>0);
            bar.addView(action,new LinearLayout.LayoutParams(dp(53),-1));
        }
        if(history.length()==0){
            TextView empty=label("暂无历史记录",16,muted);empty.setGravity(Gravity.CENTER);
            historyScreen.addView(empty,new LinearLayout.LayoutParams(-1,0,1));return;
        }
        ScrollView scroll=new ScrollView(this);historyScreen.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout entries=column();scroll.addView(entries);
        String lastDate="";
        for(int i=0;i<history.length();i++){
            JSONObject entry=history.optJSONObject(i);
            if(entry==null)continue;
            String date=historyDate(entry);
            if(!date.equals(lastDate)){
                TextView section=label(date,13,muted);section.setBackgroundColor(board);section.setPadding(dp(20),0,dp(20),0);
                entries.addView(section,new LinearLayout.LayoutParams(-1,dp(32)));lastDate=date;
            }
            addHistoryRow(entries,entry);
        }
    }
    private String historyDate(JSONObject entry){
        long time=entry.optLong("createdAt",0);
        return time<=0?"日期未记录":Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy年M月d日",Locale.SIMPLIFIED_CHINESE));
    }
    private String editorValue(JSONObject entry){return entry.optString("editor",entry.optString("expression"));}
    private void addHistoryRow(LinearLayout entries,JSONObject entry){
        HorizontalScrollView horizontal=new HorizontalScrollView(this);
        horizontal.setFillViewport(true);horizontal.setHorizontalScrollBarEnabled(false);
        entries.addView(horizontal,new LinearLayout.LayoutParams(-1,-2));
        String raw=editorValue(entry), fraction=entry.optString("fractionTemplate");
        MathDocument formula=new MathDocument();formula.restore(raw+(fraction.isEmpty()?"":"="+fraction),0);
        MathEditorView item=new MathEditorView(this,formula,ink,muted,"  =  "+entry.optString("result"));
        item.setMinimumHeight(dp(60));item.setContentDescription(historyText(entry));
        activateButton(item,()->{
            document.replace(raw);justEvaluated=false;closeUtility();closeHistory();refresh();save();
        });
        horizontal.addView(item,new FrameLayout.LayoutParams(-2,-2));
        View divider=new View(this);divider.setBackgroundColor(board);entries.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
    }
    private String historyText(JSONObject entry){
        String fraction=entry.optString("fraction");
        return entry.optString("expression")+(fraction.isEmpty()?"":" = "+fraction)+" = "+entry.optString("result");
    }
    private void closeHistory(){
        if(historyScreen==null)return;
        host.removeView(historyScreen);historyScreen=null;
        if(utilityScreen!=null)utilityScreen.setVisibility(View.VISIBLE);
        else{screen.setVisibility(View.VISIBLE);editor.requestFocus();}
    }
    private void shareHistory(){
        StringBuilder content=new StringBuilder("空叶计算器 · 历史记录");
        String lastDate="";
        for(int i=0;i<history.length();i++){
            JSONObject entry=history.optJSONObject(i);
            if(entry==null)continue;
            String date=historyDate(entry);
            if(!date.equals(lastDate)){content.append("\n\n").append(date);lastDate=date;}
            content.append('\n').append(historyText(entry));
        }
        Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,content.toString());
        startActivity(Intent.createChooser(send,"分享历史记录"));
    }
    private void confirmClearHistory(){
        dialog().setTitle("清空全部计算历史？").setPositiveButton("清空",(d,i)->{history=new JSONArray();save();showHistory();})
            .setNegativeButton("取消",null).show();
    }
    private void openUtility(String page){
        closeUtility();screen.setVisibility(View.GONE);
        LinearLayout pane=column();pane.setBackgroundColor(bg);utilityScreen=pane;
        host.addView(pane,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout bar=row();pane.addView(bar,new LinearLayout.LayoutParams(-1,dp(58)));
        bar.addView(icon("back","返回计算",this::closeUtility),new LinearLayout.LayoutParams(dp(52),-1));
        TextView title=label(page,19,ink);title.setTypeface(null,Typeface.BOLD);bar.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        ToolsPages tools=new ToolsPages(this,bg,ink,muted,card,board,border,accent,function,selected,dark);
        switch(page){
            case "分数":
                tools.fractions(pane,this::recordFraction);break;
            case "单位转换":tools.units(pane);break;
            case "日期计算":tools.dates(pane);break;
            case "亲戚称呼":tools.relatives(pane);break;
            case "算法管理":
                TextView reset=button("恢复默认布局","恢复默认布局",()->{prefs.edit().remove("keyLayout").apply();loadKeyboard();renderKeys();openUtility(page);});
                reset.setTextSize(13);bar.addView(reset,new LinearLayout.LayoutParams(dp(98),-1));addKeyboardManager(pane);break;
            case "自定义函数管理":
                bar.addView(button("+","添加自定义函数",()->editFunction("")),new LinearLayout.LayoutParams(dp(48),-1));
                addFunctionManager(pane);break;
        }
    }
    private void closeUtility(){
        if(utilityScreen==null)return;
        host.removeView(utilityScreen);utilityScreen=null;screen.setVisibility(View.VISIBLE);editor.requestFocus();
    }
    private void recordFraction(String raw,String expression,Calculator.Value value){
        try{
            JSONObject entry=new JSONObject();entry.put("editor",raw);entry.put("expression",expression);
            Calculator.Fraction fraction=value.exact;
            boolean show=fraction!=null && fraction.fitsDisplay();
            entry.put("result",show?roundedFraction(fraction):Calculator.format(value.number));
            if(show){entry.put("fraction",fraction.label());entry.put("fractionTemplate",fractionTemplate(fraction));}
            entry.put("createdAt",System.currentTimeMillis());
            JSONArray next=new JSONArray();next.put(entry);
            for(int i=0;i<Math.min(99,history.length());i++)next.put(history.get(i));
            history=next;save();
        }catch(JSONException ignored){}
    }
    private void addKeyboardManager(LinearLayout pane){
        ScrollView scroll=new ScrollView(this);pane.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout list=column();list.setPadding(dp(10),0,dp(10),dp(10));scroll.addView(list);
        int[] selectedKey={-1,-1,-1};
        for(int p=0;p<keyboardLayout.length;p++){
            TextView heading=label(CATEGORIES[p]+" · 拖动或依次点击两个键交换位置",13,muted);
            list.addView(heading,new LinearLayout.LayoutParams(-1,dp(42)));
            for(int r=0;r<keyboardLayout[p].length;r++){
                LinearLayout row=row();list.addView(row,new LinearLayout.LayoutParams(-1,dp(44)));
                for(int c=0;c<5;c++){
                    final int p1=p,r1=r,c1=c;
                    TextView tile=button(keyFace(keyboardLayout[p][r][c]),"选择键位 "+keyboardLayout[p][r][c],()->{
                        if(selectedKey[0]<0){selectedKey[0]=p1;selectedKey[1]=r1;selectedKey[2]=c1;Toast.makeText(this,"再点一个键交换",Toast.LENGTH_SHORT).show();}
                        else{swapKeys(selectedKey,p1,r1,c1);selectedKey[0]=-1;}
                    });
                    tile.setTextSize(16);tile.setBackground(touch(card,border,7));
                    tile.setOnLongClickListener(v->{v.startDragAndDrop(ClipData.newPlainText("键位",""),new View.DragShadowBuilder(v),new int[]{p1,r1,c1},0);return true;});
                    tile.setOnDragListener((v,event)->{
                        if(!(event.getLocalState() instanceof int[]))return false;
                        if(event.getAction()==DragEvent.ACTION_DROP){swapKeys((int[])event.getLocalState(),p1,r1,c1);return true;}
                        return true;
                    });
                    LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,-1,1);size.setMargins(dp(2),dp(2),dp(2),dp(2));row.addView(tile,size);
                }
            }
        }
    }
    private void swapKeys(int[] source,int page,int row,int col){
        String key=keyboardLayout[source[0]][source[1]][source[2]];
        keyboardLayout[source[0]][source[1]][source[2]]=keyboardLayout[page][row][col];
        keyboardLayout[page][row][col]=key;saveKeyboard();openUtility("算法管理");
    }
    private void addFunctionManager(LinearLayout pane){
        if(customFunctions.length()==0){TextView empty=label("点击右上角 + 添加函数",15,muted);empty.setGravity(Gravity.CENTER);
            pane.addView(empty,new LinearLayout.LayoutParams(-1,0,1));return;}
        ScrollView scroll=new ScrollView(this);pane.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout list=column();scroll.addView(list);
        java.util.Iterator<String> names=customFunctions.keys();
        while(names.hasNext()){
            String name=names.next();
            TextView entry=button(name+"(x) = "+customFunctions.optString(name),"编辑 "+name,()->editFunction(name));
            entry.setTextSize(18);list.addView(entry,new LinearLayout.LayoutParams(-1,dp(54)));
        }
    }
    private void editFunction(String current){
        LinearLayout fields=column();fields.setPadding(dp(23),0,dp(23),0);
        EditText name=numericToolInput(fields,"函数名，例如 f");name.setText(current);
        EditText body=numericToolInput(fields,"表达式，例如 x^2+1");body.setText(customFunctions.optString(current));
        AlertDialog.Builder setup=dialog().setTitle("自定义函数").setView(fields).setPositiveButton("保存",null).setNegativeButton("取消",null);
        if(!current.isEmpty())setup.setNeutralButton("删除",(d,i)->{customFunctions.remove(current);updateFunctions();});
        AlertDialog alert=setup.create();alert.setOnShowListener(d->alert.getButton(-1).setOnClickListener(v->{
            String next=name.getText().toString().trim(), expression=body.getText().toString().trim();
            if(!next.matches("[a-z]{1,12}") || variables().containsKey(next) || (" sin cos tan arcsin arccos arctan ln log root sqrt abs perm comb lcm gcd mod e i ".contains(" "+next+" "))){
                name.setError("请输入未被占用的小写字母名称");return;
            }
            if(!current.equals(next) && customFunctions.has(next)){name.setError("已有同名函数");return;}
            if(expression.isEmpty() || expression.length()>500){body.setError("请输入 1–500 字符的表达式");return;}
            try{customFunctions.put(next,expression);if(!current.isEmpty() && !current.equals(next))customFunctions.remove(current);}
            catch(JSONException error){body.setError(error.getMessage());return;}
            alert.dismiss();updateFunctions();
        }));alert.show();
    }
    private void updateFunctions(){prefs.edit().putString("customFunctions",customFunctions.toString()).apply();renderKeys();openUtility("自定义函数管理");}
    private void editVariable(String name){
        EditText input=new EditText(new android.view.ContextThemeWrapper(this,dark?android.R.style.Theme_Material:android.R.style.Theme_Material_Light));
        input.setSingleLine(true);input.setText(Calculator.format(variables().get(name)));input.selectAll();
        AlertDialog d=dialog().setTitle("设置变量 "+name).setMessage("可输入数字或算式，例如 3/2。")
            .setView(input).setPositiveButton("保存",null).setNegativeButton("取消",null).create();
        d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{
            try{double value=Calculator.evaluate(input.getText().toString(),true,variables());
                if(name.equals("x"))x=value;else if(name.equals("y"))y=value;
                else prefs.edit().putLong("var_"+name,Double.doubleToLongBits(value)).apply();
                justEvaluated=false;refresh();save();d.dismiss();}
            catch(IllegalArgumentException error){input.setError(error.getMessage());}
        }));d.show();
    }
    private void settings(){
        String[] options={"外观 · "+(dark?"黑色模式":"白色模式")};
        dialog().setTitle("设置").setItems(options,(d,i)->{
            if(i==0)appearance();
        }).setNegativeButton("关闭",null).show();
    }
    private void appearance(){
        dialog().setTitle("外观").setSingleChoiceItems(new String[]{"白色模式","黑色模式"},dark?1:0,(d,i)->{
            d.dismiss();boolean next=i==1;
            if(next!=dark){dark=next;build();save();}
        }).setNegativeButton("取消",null).show();
    }
    private boolean copyResult(){
        String value=result.getText().toString();
        if(!value.startsWith("= "))return false;
        ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("计算结果",value.replace("= ","")));
        Toast.makeText(this,"已复制结果",Toast.LENGTH_SHORT).show();
        return true;
    }
    private void guide(){
        dialog().setTitle("使用说明").setMessage("基础、科学、变量和函数在同一个键盘里连续排列。上下滑动滚动键盘，左侧工具随当前位置高亮；点击工具可直接定位到对应功能区。\n\n点击对数、幂、根式或分数键插入公式，再点击带底色的虚线框或用左右箭头逐项填写。\n\n浅色括号会在补输入或把光标移出时确认。↵ 插入多行大括号，再按一次追加一行。各行独立算值。\n\n长按变量键可赋值。计算历史位于主页右上角，黑白模式位于侧栏底部的设置。Ans 取上一结果。% 表示除以 100。")
            .setPositiveButton("关闭",null).show();
    }

    private void openDrawer(){
        if(drawer!=null)return;
        drawer=new FrameLayout(this);host.addView(drawer,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this);shade.setBackgroundColor(dark?0xA0000000:0x62000000);shade.setContentDescription("关闭侧栏");
        shade.setOnClickListener(v->closeDrawer());drawer.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout panel=row();panel.setGravity(Gravity.TOP);panel.setClickable(true);panel.setBackgroundColor(board);
        int width=Math.min(dp(336),Math.round(host.getWidth()*.82f));
        drawerPanel=panel;
        drawer.addView(panel,new FrameLayout.LayoutParams(width,-1,Gravity.START));
        panel.setTranslationX(-width);panel.animate().translationX(0).setDuration(200).start();
        screen.animate().translationX(width).setDuration(200).start();
        LinearLayout rail=column();rail.setGravity(Gravity.CENTER_HORIZONTAL);rail.setPadding(dp(4),dp(10),dp(4),dp(10));
        panel.addView(rail,new LinearLayout.LayoutParams(dp(48),-1));
        rail.addView(button("×","关闭侧栏",this::closeDrawer),new LinearLayout.LayoutParams(-1,dp(43)));
        rail.addView(new View(this),new LinearLayout.LayoutParams(-1,0,1));
        rail.addView(button("⚙","设置",()->leaveDrawer(this::settings)),new LinearLayout.LayoutParams(-1,dp(43)));
        rail.addView(button("ⓘ","关于",()->leaveDrawer(()->dialog().setTitle("空叶计算器").setMessage("1.0.0 · Android 8.0 及以上").setPositiveButton("关闭",null).show())),new LinearLayout.LayoutParams(-1,dp(43)));
        ScrollView cardScroll=new ScrollView(this);cardScroll.setFillViewport(true);cardScroll.setBackground(shape(card,0,22));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-1,1);cp.setMargins(0,dp(8),dp(8),dp(8));panel.addView(cardScroll,cp);
        LinearLayout entries=column();entries.setPadding(dp(10),dp(15),dp(10),dp(12));cardScroll.addView(entries);
        LinearLayout title=row();
        TextView name=label("空叶计算器",18,ink);name.setTypeface(null,Typeface.BOLD);name.setSingleLine(true);name.setPadding(dp(12),0,0,0);
        title.addView(name,new LinearLayout.LayoutParams(0,dp(52),1));entries.addView(title);
        menuRow(entries,"▦","计算",true,this::closeDrawer);
        menuRow(entries,"⅟","分数",false,()->leaveDrawer(()->openUtility("分数")));
        menuRow(entries,"⇄","单位转换",false,()->leaveDrawer(()->openUtility("单位转换")));
        menuRow(entries,"▤","日期计算",false,()->leaveDrawer(()->openUtility("日期计算")));
        menuRow(entries,"◎","亲戚称呼",false,()->leaveDrawer(()->openUtility("亲戚称呼")));
        menuRow(entries,"▦","算法管理",false,()->leaveDrawer(()->openUtility("算法管理")));
        menuRow(entries,"ƒ","自定义函数管理",false,()->leaveDrawer(()->openUtility("自定义函数管理")));
        menuRow(entries,"?","使用说明",false,()->leaveDrawer(this::guide));
    }
    private void menuRow(LinearLayout entries,String symbol,String name,boolean active,Runnable action){
        LinearLayout view=row();view.setPadding(dp(12),0,dp(8),0);view.setContentDescription(name);
        activateButton(view,action);
        TextView glyph=label(symbol,18,active?accent:muted);glyph.setGravity(Gravity.CENTER);
        glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        view.addView(glyph,new LinearLayout.LayoutParams(dp(24),-1));
        TextView text=label(name,15,active?accent:ink);text.setSingleLine(true);text.setEllipsize(TextUtils.TruncateAt.END);
        text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);text.setPadding(dp(12),0,0,0);
        view.addView(text,new LinearLayout.LayoutParams(0,-1,1));
        if(active)view.setBackground(touch(function,0,10));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(46));p.setMargins(0,dp(2),0,0);entries.addView(view,p);
    }
    private void closeDrawer(){
        if(drawer==null)return;
        FrameLayout closing=drawer;drawer=null;
        if(drawerPanel!=null){
            drawerPanel.animate().translationX(-drawerPanel.getWidth()).setDuration(180).start();drawerPanel=null;
        }
        closing.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        closing.animate().alpha(0).setDuration(180).withEndAction(()->host.removeView(closing)).start();
        screen.animate().translationX(0).setDuration(180).start();
    }
    private void leaveDrawer(Runnable action){closeDrawer();action.run();}

    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private int color(String light,String darkColor){return Color.parseColor(dark?darkColor:light);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView label(String s,float size,int color){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);v.setFontFeatureSettings("tnum");return v;
    }
    private TextView button(String s,String description,Runnable action){
        TextView v=label(s,24,ink);v.setGravity(Gravity.CENTER);v.setContentDescription(description);
        activateButton(v,action);return v;
    }
    private void activateButton(View v,Runnable action){
        v.setBackground(touch(Color.TRANSPARENT,Color.TRANSPARENT,10));
        v.setOnClickListener(w->action.run());
    }
    private TextView icon(String symbol,String description,Runnable action){
        TextView view=new TextView(this){
            private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c){
                super.onDraw(c);c.save();
                boolean math=symbol.equals("logₐ")||symbol.equals("xʸ")||symbol.equals("ⁿ√")||symbol.equals("a/b");
                float scale=Math.min(dp(math?32:22),Math.min(getWidth(),getHeight())*.80f)/24f;
                c.translate((getWidth()-24*scale)/2,(getHeight()-24*scale)/2);c.scale(scale,scale);
                pen.setColor(ink);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(1.65f);
                pen.setStrokeCap(Paint.Cap.ROUND);pen.setStrokeJoin(Paint.Join.ROUND);
                Path path=new Path();
                switch(symbol){
                    case "menu":
                        c.drawLine(3,6,21,6,pen);c.drawLine(3,12,17,12,pen);c.drawLine(3,18,21,18,pen);break;
                    case "history":
                        c.drawArc(3,3,21,21,8,315,false,pen);
                        path.moveTo(12,7);path.lineTo(12,12);path.lineTo(16,12);c.drawPath(path,pen);break;
                    case "undo":case "redo":
                        if(symbol.equals("redo")){c.translate(24,0);c.scale(-1,1);}
                        path.moveTo(9,4);path.lineTo(3,10);path.lineTo(9,16);
                        path.moveTo(4,10);path.lineTo(15,10);path.cubicTo(23,10,23,21,15,21);path.lineTo(8,21);
                        c.drawPath(path,pen);break;
                    case "trash":
                        path.moveTo(3,7);path.lineTo(3,5);path.quadTo(3,4,5,4);path.lineTo(8,4);path.lineTo(9,2.5f);
                        path.lineTo(15,2.5f);path.lineTo(16,4);path.lineTo(19,4);path.quadTo(21,4,21,5);
                        path.lineTo(21,7);path.lineTo(3,7);path.moveTo(5,10);path.lineTo(7,20);
                        path.quadTo(7.4f,21,9,21);path.lineTo(15,21);path.quadTo(16.6f,21,17,20);path.lineTo(19,10);c.drawPath(path,pen);
                        c.drawLine(10,10,10,17,pen);c.drawLine(14,10,14,17,pen);break;
                    case "group":
                        path.moveTo(21,3);path.lineTo(21,11);path.quadTo(21,17,15,17);path.lineTo(4,17);
                        path.moveTo(8,12.5f);path.lineTo(3,17);path.lineTo(8,21.5f);c.drawPath(path,pen);break;
                    case "back":case "forward":
                        if(symbol.equals("forward")){c.translate(24,0);c.scale(-1,1);}
                        path.moveTo(16,4);path.lineTo(7,11);path.quadTo(6,12,7,13);path.lineTo(16,20);
                        path.quadTo(18,21.5f,18,19);path.lineTo(18,5);path.quadTo(18,2.5f,16,4);c.drawPath(path,pen);break;
                    case "delete":
                        path.moveTo(9,5);path.lineTo(21,5);path.quadTo(22.5f,5,22.5f,6.5f);path.lineTo(22.5f,17.5f);
                        path.quadTo(22.5f,19,21,19);path.lineTo(9,19);path.lineTo(2,12);path.close();c.drawPath(path,pen);
                        c.drawLine(12,9.5f,17,14.5f,pen);c.drawLine(17,9.5f,12,14.5f,pen);break;
                    case "logₐ":
                        pen.setColor(accent);pen.setStyle(Paint.Style.FILL);pen.setTypeface(Typeface.SERIF);pen.setTextSize(11);
                        c.drawText("log",1,15,pen);placeholder(c,14,14,3,4,pen);placeholder(c,18,7,5,9,pen);break;
                    case "xʸ":
                        placeholder(c,5,9,9,11,pen);placeholder(c,15,5,5,5,pen);break;
                    case "ⁿ√":
                        pen.setColor(accent);pen.setStrokeWidth(1.2f);path.moveTo(3,14);path.lineTo(5,12.5f);path.lineTo(8,18);
                        path.lineTo(12,6);path.lineTo(21,6);c.drawPath(path,pen);
                        placeholder(c,5,5,3.5f,4,pen);placeholder(c,13,8.5f,6,9,pen);break;
                    case "a/b":
                        pen.setColor(accent);pen.setStrokeWidth(1.2f);c.drawLine(5,12,19,12,pen);
                        placeholder(c,9,3,6,7,pen);placeholder(c,9,14,6,7,pen);break;
                }
                c.restore();
            }
        };
        view.setContentDescription(description);activateButton(view,action);return view;
    }
    private void placeholder(Canvas canvas,float x,float y,float w,float h,Paint pen){
        pen.setStyle(Paint.Style.FILL);pen.setColor(slotFill);canvas.drawRoundRect(x,y,x+w,y+h,1.5f,1.5f,pen);
        pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(.5f);pen.setColor(accent);
        pen.setPathEffect(new DashPathEffect(new float[]{.9f,.7f},0));
        canvas.drawRoundRect(x,y,x+w,y+h,1.5f,1.5f,pen);pen.setPathEffect(null);
    }
    private GradientDrawable shape(int color,int stroke,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));
        if(stroke!=Color.TRANSPARENT)d.setStroke(dp(1),stroke);return d;
    }
    private RippleDrawable touch(int fill,int stroke,int radius){
        return new RippleDrawable(ColorStateList.valueOf(dark?0x30FFFFFF:0x20606068),shape(fill,stroke,radius),shape(Color.WHITE,0,radius));
    }

}
