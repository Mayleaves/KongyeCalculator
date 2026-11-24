package com.kongye.calculator;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.icu.util.ChineseCalendar;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextClock;
import android.widget.TextView;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

final class ToolsPages {
    private final Activity activity;
    private final int bg,ink,muted,card,board,border,accent,function,selected;
    private final boolean dark;

    ToolsPages(Activity activity,int bg,int ink,int muted,int card,int board,int border,int accent,int function,int selected,boolean dark){
        this.activity=activity;this.bg=bg;this.ink=ink;this.muted=muted;this.card=card;this.board=board;
        this.border=border;this.accent=accent;this.function=function;this.selected=selected;this.dark=dark;
    }

    interface FractionHistory { void add(String raw,String expression,Calculator.Value value); }

    void fractions(LinearLayout pane,FractionHistory history){new FractionPage(pane,history);}

    private final class FractionPage {
        final StringBuilder[] numbers={new StringBuilder(),new StringBuilder(),new StringBuilder()};
        final FractionHistory history;
        final LinearLayout answers;
        String prefix="",plainPrefix="";
        boolean negative,finished;

        FractionPage(LinearLayout pane,FractionHistory history){
            this.history=history;
            ScrollView viewport=new ScrollView(activity);viewport.setFillViewport(true);
            pane.addView(viewport,new LinearLayout.LayoutParams(-1,0,1));
            answers=column();answers.setGravity(Gravity.BOTTOM);answers.setPadding(dp(12),dp(5),dp(12),dp(9));viewport.addView(answers);
            LinearLayout inputs=row();pane.addView(inputs,new LinearLayout.LayoutParams(-1,0,2));
            LinearLayout whole=column();inputs.addView(whole,new LinearLayout.LayoutParams(0,-1,38));
            addFractionRow(whole,new String[]{"C","+/−"},0);
            for(String[] keys:new String[][]{{"1","2"},{"3","4"},{"5","6"},{"7","8"},{"9","0"},{"⌫"}})addFractionRow(whole,keys,0);
            LinearLayout right=column();inputs.addView(right,new LinearLayout.LayoutParams(0,-1,62));
            for(int field=1;field<=2;field++){
                LinearLayout part=column();right.addView(part,new LinearLayout.LayoutParams(-1,0,1));
                TextView fieldLabel=text(field==1?"分子":"分母",11,muted);fieldLabel.setGravity(Gravity.CENTER);fieldLabel.setBackgroundColor(function);
                part.addView(fieldLabel,new LinearLayout.LayoutParams(-1,dp(19)));
                for(String[] keys:new String[][]{{"1","2","3"},{"4","5","6"},{"7","8","9"},{"0","⌫"}})addFractionRow(part,keys,field);
            }
            LinearLayout ops=row();pane.addView(ops,new LinearLayout.LayoutParams(-1,dp(53)));
            for(String op:new String[]{"+","−","×","÷","="}){
                TextView button=button(op,24,op.equals("=")?selected:function,()->operate(op));
                addCell(ops,button);
            }
            draw();
        }

        private void addFractionRow(LinearLayout panel,String[] keys,int field){
            LinearLayout line=row();panel.addView(line,new LinearLayout.LayoutParams(-1,0,1));
            for(String key:keys)addCell(line,button(key,21,card,()->type(key,field)));
        }
        private void type(String key,int field){
            if(key.equals("C") || finished){prefix="";plainPrefix="";clearInput();finished=false;}
            if(key.equals("+/−"))negative=!negative;
            else if(key.equals("⌫")){StringBuilder value=numbers[field];if(value.length()>0)value.deleteCharAt(value.length()-1);}
            else if(!key.equals("C") && numbers[field].length()<12)numbers[field].append(key);
            draw();
        }
        private boolean typed(){return numbers[0].length()+numbers[1].length()+numbers[2].length()>0;}
        private void clearInput(){for(StringBuilder input:numbers)input.setLength(0);negative=false;}
        private String operand(boolean math){
            String whole=numbers[0].toString();
            if(numbers[1].length()+numbers[2].length()==0)return (negative?"−":"")+(whole.isEmpty()?"0":whole);
            String top=numbers[1].length()==0?"0":numbers[1].toString();
            String bottom=numbers[2].toString();
            String fraction=math?FormulaCatalog.frac(top,bottom):top+"/"+(bottom.isEmpty()?"□":bottom);
            if(whole.isEmpty())return (negative?"−":"")+(math?fraction:"("+fraction+")");
            return (negative?"−":"")+"("+whole+"+"+fraction+")";
        }
        private void operate(String operator){
            if(operator.equals("=")){
                if(finished || !typed() && !prefix.isEmpty())return;
                try{
                    String raw=prefix+operand(true), input=plainPrefix+operand(false);
                    MathDocument document=new MathDocument();document.restore(raw,0);
                    Calculator.Value value=Calculator.evaluateDetailed(document.expressions().get(0),true,Collections.emptyMap());
                    history.add(raw,input,value);finished=true;draw();
                    if(value.exact!=null && value.exact.fitsDisplay()){
                        MathDocument exact=new MathDocument();exact.restore("="+FormulaCatalog.frac(value.exact.top.toString(),value.exact.bottom.toString()),0);
                        answers.addView(new MathEditorView(activity,exact,accent,accent,""),new LinearLayout.LayoutParams(-1,-2));
                    }
                    TextView decimal=text("= "+Calculator.format(value.number),22,accent);decimal.setPadding(dp(10),0,0,0);answers.addView(decimal);
                }catch(IllegalArgumentException error){draw();answers.addView(text(error.getMessage(),15,accent));}
            }else if(typed() || prefix.isEmpty()){
                if((numbers[1].length()!=0 || numbers[2].length()!=0) && numbers[2].length()==0){
                    draw();answers.addView(text("请填写分母",15,accent));return;
                }
                prefix+=operand(true)+operator;plainPrefix+=operand(false)+operator;clearInput();finished=false;draw();
            }else{
                prefix=prefix.substring(0,prefix.length()-1)+operator;
                plainPrefix=plainPrefix.substring(0,plainPrefix.length()-1)+operator;draw();
            }
        }
        private void draw(){
            answers.removeAllViews();MathDocument document=new MathDocument();
            document.restore(prefix+(typed()||prefix.isEmpty()?operand(true):""),0);
            MathEditorView view=new MathEditorView(activity,document,ink,muted,"");
            if(typed())view.setContentDescription(operand(false));
            answers.addView(view,new LinearLayout.LayoutParams(-1,-2));
        }
    }

    private static final String[] UNIT_TYPES={"长度","面积","体积","质量","温度","压强"};
    private static final String[][] UNIT_NAMES={
        {"千米","米","分米","厘米","毫米","微米","纳米","英寸","英尺","码","英里"},
        {"平方千米","平方米","公顷","亩","平方厘米","平方英尺","英亩"},
        {"立方米","升","毫升","立方厘米","美制加仑","英制加仑"},
        {"千克","克","毫克","吨","斤","磅","盎司"},
        {"摄氏度","华氏度","开尔文"},
        {"帕","千帕","兆帕","巴","标准大气压","毫米汞柱","磅/平方英寸"}
    };
    private static final double[][] SCALES={
        {1000,1,.1,.01,.001,1e-6,1e-9,.0254,.3048,.9144,1609.344},
        {1e6,1,1e4,2000d/3,.0001,.09290304,4046.8564224},
        {1,.001,.000001,.000001,.003785411784,.00454609},
        {1,.001,1e-6,1000,.5,.45359237,.028349523125},
        {1,1,1},
        {1,1000,1e6,1e5,101325,133.322387415,6894.757293168}
    };

    void units(LinearLayout pane){
        LinearLayout body=column();body.setPadding(dp(12),0,dp(12),dp(8));pane.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        int[] state={0,0,1};StringBuilder value=new StringBuilder("1");
        Runnable[] draw={null};
        draw[0]=()->{
            body.removeAllViews();LinearLayout tabs=row();body.addView(tabs,new LinearLayout.LayoutParams(-1,dp(44)));
            for(int i=0;i<UNIT_TYPES.length;i++){
                final int index=i;addCell(tabs,button(UNIT_TYPES[i],13,index==state[0]?function:bg,()->{state[0]=index;state[1]=0;state[2]=1;draw[0].run();}));
            }
            int category=state[0];String[] names=UNIT_NAMES[category];
            LinearLayout core=row();core.setBackgroundColor(board);body.addView(core,new LinearLayout.LayoutParams(-1,dp(86)));
            LinearLayout from=column(),to=column();core.addView(from,new LinearLayout.LayoutParams(0,-1,1));
            TextView input=text("",24,ink);input.setGravity(Gravity.CENTER);from.addView(input,new LinearLayout.LayoutParams(-1,0,1));
            from.addView(button(names[state[1]]+" ▾",15,function,()->selectUnit(names,state,1,draw[0])),new LinearLayout.LayoutParams(-1,dp(34)));
            core.addView(button("⇄",24,board,()->{int swap=state[1];state[1]=state[2];state[2]=swap;draw[0].run();}),new LinearLayout.LayoutParams(dp(46),-1));
            core.addView(to,new LinearLayout.LayoutParams(0,-1,1));
            TextView output=text("",24,accent);output.setGravity(Gravity.CENTER);to.addView(output,new LinearLayout.LayoutParams(-1,0,1));
            to.addView(button(names[state[2]]+" ▾",15,function,()->selectUnit(names,state,2,draw[0])),new LinearLayout.LayoutParams(-1,dp(34)));
            ScrollView listScroller=new ScrollView(activity);body.addView(listScroller,new LinearLayout.LayoutParams(-1,0,1));
            LinearLayout list=column();listScroller.addView(list);
            Runnable refresh=()->{
                input.setText(value.length()==0?"0":value.toString());double number;
                try{number=Double.parseDouble(value.toString());}catch(NumberFormatException ignored){number=0;}
                output.setText(formatUnit(convert(category,state[1],state[2],number)));list.removeAllViews();
                for(int i=0;i<names.length;i++)if(i!=state[1])addPair(list,names[i],formatUnit(convert(category,state[1],i,number)));
            };
            for(String[] keys:new String[][]{{"7","8","9","C"},{"4","5","6","⌫"},{"1","2","3","+/−"},{"0","00","."}}){
                LinearLayout row=row();body.addView(row,new LinearLayout.LayoutParams(-1,dp(42)));
                for(String key:keys)addCell(row,button(key,19,card,()->{
                    if(key.equals("C"))value.setLength(0);
                    else if(key.equals("⌫")){if(value.length()>0)value.deleteCharAt(value.length()-1);}
                    else if(key.equals("+/−")){if(value.indexOf("-")==0)value.deleteCharAt(0);else value.insert(0,"-");}
                    else if(value.length()<19 && (!key.equals(".") || value.indexOf(".")<0)){
                        if(value.toString().equals("0")&&!key.equals("."))value.setLength(0);value.append(key);
                    }
                    refresh.run();
                }));
            }
            refresh.run();
        };
        draw[0].run();
    }
    private void selectUnit(String[] units,int[] state,int field,Runnable draw){
        dialog().setTitle("选择单位").setSingleChoiceItems(units,state[field],(d,i)->{state[field]=i;d.dismiss();draw.run();}).setNegativeButton("取消",null).show();
    }
    static double convert(int category,int from,int to,double value){
        if(category!=4)return value*SCALES[category][from]/SCALES[category][to];
        double celsius=from==1?(value-32)*5/9:from==2?value-273.15:value;
        return to==1?celsius*9/5+32:to==2?celsius+273.15:celsius;
    }
    private String formatUnit(double value){return Double.isFinite(value)?Calculator.format(value):"超出范围";}

    private static final DateTimeFormatter DATE_TIME=DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm");
    void dates(LinearLayout pane){
        String[] modes={"日期间隔","日期互转","日期推算","世界时间"};
        LinearLayout tabs=row();pane.addView(tabs,new LinearLayout.LayoutParams(-1,dp(45)));
        ScrollView scroll=new ScrollView(activity);pane.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout body=column();body.setPadding(dp(14),dp(10),dp(14),dp(12));scroll.addView(body);
        int[] active={0};Runnable[] render={null};
        render[0]=()->{
            body.removeAllViews();tabs.removeAllViews();
            for(int i=0;i<modes.length;i++){
                final int tab=i;addCell(tabs,button(modes[i],13,i==active[0]?function:bg,()->{active[0]=tab;render[0].run();}));
            }
            switch(active[0]){case 0:interval(body);break;case 1:dateConvert(body);break;case 2:dateOffset(body);break;default:clocks(body);}
        };
        render[0].run();
    }
    private void interval(LinearLayout body){
        LocalDateTime now=LocalDateTime.now().withSecond(0).withNano(0);
        LocalDateTime[] time={now,now};
        TextView start=button("",15,card,()->{}),end=button("",15,card,()->{});
        body.addView(start,new LinearLayout.LayoutParams(-1,dp(48)));body.addView(end,new LinearLayout.LayoutParams(-1,dp(48)));
        Switch include=new Switch(activity);include.setText("包括结束日期");include.setTextColor(ink);
        body.addView(include,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout list=column();body.addView(list);
        Runnable refresh=()->{
            start.setText("开始  "+DATE_TIME.format(time[0])+"  ›");end.setText("结束  "+DATE_TIME.format(time[1])+"  ›");list.removeAllViews();
            LocalDateTime a=time[0].isAfter(time[1])?time[1]:time[0], b=time[0].isAfter(time[1])?time[0]:time[1];
            long minutes=Duration.between(a,b).toMinutes()+(include.isChecked()?1440:0);
            LocalDate dateA=a.toLocalDate(), dateB=b.toLocalDate().plusDays(include.isChecked()?1:0);
            long days=ChronoUnit.DAYS.between(dateA,dateB);
            Period period=Period.between(dateA,dateB);
            addPair(list,"天",days+"天");addPair(list,"工作日（周一至周五）",weekdays(dateA,days)+"天");
            addPair(list,"周",days/7+"周"+days%7+"天");addPair(list,"月",period.toTotalMonths()+"月"+period.getDays()+"天");
            addPair(list,"年",period.getYears()+"年"+period.getMonths()+"月"+period.getDays()+"天");
            addPair(list,"小时",minutes/60+"小时"+minutes%60+"分钟");addPair(list,"分钟",minutes+"分钟");
        };
        start.setOnClickListener(v->pickTime(time[0],picked->{time[0]=picked;refresh.run();}));
        end.setOnClickListener(v->pickTime(time[1],picked->{time[1]=picked;refresh.run();}));
        include.setOnCheckedChangeListener((v,value)->refresh.run());refresh.run();
    }
    static long weekdays(LocalDate start,long days){
        long result=(days/7)*5;
        for(int i=0;i<days%7;i++)if(start.plusDays(i).getDayOfWeek().getValue()<6)result++;
        return result;
    }
    private void dateConvert(LinearLayout body){
        LocalDateTime[] date={LocalDateTime.now().withSecond(0).withNano(0)};
        LinearLayout list=column();TextView choose=button("选择日期",15,function,()->pickTime(date[0],value->{date[0]=value;showConversions(list,value);}));
        body.addView(choose,new LinearLayout.LayoutParams(-1,dp(48)));body.addView(list);
        EditText timestamp=numberInput("Unix 时间戳（秒）");body.addView(timestamp,new LinearLayout.LayoutParams(-1,dp(48)));
        body.addView(button("时间戳转日期",15,card,()->{
            try{date[0]=Instant.ofEpochSecond(Long.parseLong(timestamp.getText().toString())).atZone(ZoneId.systemDefault()).toLocalDateTime();showConversions(list,date[0]);}
            catch(RuntimeException error){timestamp.setError("请输入有效的 Unix 时间戳");}
        }),new LinearLayout.LayoutParams(-1,dp(44)));
        showConversions(list,date[0]);
    }
    private void showConversions(LinearLayout list,LocalDateTime value){
        list.removeAllViews();long seconds=value.atZone(ZoneId.systemDefault()).toEpochSecond();
        ChineseCalendar lunar=new ChineseCalendar();lunar.setTimeInMillis(seconds*1000);
        String lunarDate=(lunar.get(ChineseCalendar.IS_LEAP_MONTH)==1?"闰":"")+(lunar.get(ChineseCalendar.MONTH)+1)+"月"+lunar.get(ChineseCalendar.DATE)+"日";
        addPair(list,"公历",DATE_TIME.format(value));addPair(list,"农历",lunarDate);
        addPair(list,"星期",value.format(DateTimeFormatter.ofPattern("EEEE",java.util.Locale.SIMPLIFIED_CHINESE)));
        addPair(list,"Unix 时间戳",Long.toString(seconds));
    }
    private void dateOffset(LinearLayout body){
        LocalDateTime[] base={LocalDateTime.now().withSecond(0).withNano(0)};
        String[] units={"天","周","月","年"};int[] unit={0};
        TextView output=text("",21,accent);EditText input=numberInput("偏移量，可输入负数");input.setText("0");
        TextView start=button(DATE_TIME.format(base[0])+"  ›",15,card,()->{});
        TextView select=button("单位  天 ▾",15,function,()->{});
        body.addView(start,new LinearLayout.LayoutParams(-1,dp(49)));body.addView(input,new LinearLayout.LayoutParams(-1,dp(49)));
        body.addView(select,new LinearLayout.LayoutParams(-1,dp(49)));body.addView(output,new LinearLayout.LayoutParams(-1,dp(60)));
        Runnable refresh=()->{
            try{int step=Integer.parseInt(input.getText().toString());LocalDateTime date=base[0];
                switch(unit[0]){case 0:date=date.plusDays(step);break;case 1:date=date.plusWeeks(step);break;case 2:date=date.plusMonths(step);break;default:date=date.plusYears(step);}
                output.setText(DATE_TIME.format(date));
            }catch(RuntimeException error){output.setText("请输入有效的偏移量");}
        };
        start.setOnClickListener(v->pickTime(base[0],value->{base[0]=value;start.setText(DATE_TIME.format(value)+"  ›");refresh.run();}));
        select.setOnClickListener(v->dialog().setTitle("选择单位").setItems(units,(d,i)->{unit[0]=i;select.setText("单位  "+units[i]+" ▾");refresh.run();}).show());
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){refresh.run();}public void afterTextChanged(Editable e){} });
        refresh.run();
    }
    private void clocks(LinearLayout body){
        String[][] zones={{"北京","Asia/Shanghai"},{"东京","Asia/Tokyo"},{"新加坡","Asia/Singapore"},
            {"伦敦","Europe/London"},{"巴黎","Europe/Paris"},{"纽约","America/New_York"},{"洛杉矶","America/Los_Angeles"},{"悉尼","Australia/Sydney"}};
        for(String[] zone:zones){
            LinearLayout row=pairRow(body,zone[0]);TextClock clock=new TextClock(activity);clock.setTimeZone(zone[1]);clock.setTextColor(ink);
            clock.setFormat12Hour("M/d HH:mm");clock.setFormat24Hour("M/d HH:mm");clock.setTextSize(17);row.addView(clock);
        }
    }
    private void pickTime(LocalDateTime old,Consumer<LocalDateTime> update){
        new DatePickerDialog(activity,(v,year,month,day)->new TimePickerDialog(activity,(t,h,m)->
            update.accept(LocalDateTime.of(year,month+1,day,h,m)),old.getHour(),old.getMinute(),true).show(),
            old.getYear(),old.getMonthValue()-1,old.getDayOfMonth()).show();
    }

    void relatives(LinearLayout pane){
        ScrollView scroll=new ScrollView(activity);pane.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout list=column();list.setPadding(dp(16),dp(6),dp(16),dp(16));scroll.addView(list);
        List<Integer> chain=new ArrayList<>();boolean[] male={true};
        TextView path=text("我",18,muted), title=text("自己",26,accent);
        path.setMinHeight(dp(52));title.setMinHeight(dp(68));list.addView(path);list.addView(title);
        Runnable refresh=()->{
            int[] values=chain.stream().mapToInt(Integer::intValue).toArray();
            path.setText(Kinship.path(values));title.setText(Kinship.title(values,male[0]));
        };
        LinearLayout choices=row();list.addView(choices,new LinearLayout.LayoutParams(-1,dp(45)));
        TextView man=button("我是男性",15,function,()->{}),woman=button("我是女性",15,card,()->{});
        addCell(choices,man);addCell(choices,woman);
        man.setOnClickListener(v->{male[0]=true;man.setBackground(shape(function,8));woman.setBackground(shape(card,8));refresh.run();});
        woman.setOnClickListener(v->{male[0]=false;man.setBackground(shape(card,8));woman.setBackground(shape(function,8));refresh.run();});
        for(int i=0;i<Kinship.LABELS.length;i+=2){
            LinearLayout line=row();list.addView(line,new LinearLayout.LayoutParams(-1,dp(47)));
            for(int j=i;j<i+2;j++){
                final int element=j;addCell(line,button(Kinship.LABELS[j],17,card,()->{if(chain.size()<12){chain.add(element);refresh.run();}}));
            }
        }
        LinearLayout controls=row();list.addView(controls,new LinearLayout.LayoutParams(-1,dp(47)));
        addCell(controls,button("撤回一层",15,function,()->{if(!chain.isEmpty()){chain.remove(chain.size()-1);refresh.run();}}));
        addCell(controls,button("清空",15,function,()->{chain.clear();refresh.run();}));
    }

    private LinearLayout pairRow(LinearLayout parent,String name){
        LinearLayout row=row();row.setPadding(dp(12),0,dp(12),0);row.setBackgroundColor(card);
        TextView caption=text(name,14,muted); row.addView(caption,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(43));params.setMargins(0,1,0,0);parent.addView(row,params);return row;
    }
    private void addPair(LinearLayout parent,String name,String value){LinearLayout row=pairRow(parent,name);row.addView(text(value,16,ink));}
    private int dp(float v){return Math.round(v*activity.getResources().getDisplayMetrics().density);}
    private LinearLayout row(){LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);return row;}
    private LinearLayout column(){LinearLayout column=new LinearLayout(activity);column.setOrientation(LinearLayout.VERTICAL);return column;}
    private TextView text(String text,int size,int color){TextView view=new TextView(activity);view.setText(text);view.setTextSize(size);view.setTextColor(color);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private TextView button(String label,int size,int color,Runnable action){
        TextView view=text(label,size,color==selected?(dark?0xff14303e:0xffffffff):ink);view.setGravity(Gravity.CENTER);
        view.setBackground(shape(color,6));view.setOnClickListener(v->action.run());return view;
    }
    private GradientDrawable shape(int color,int radius){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));return shape;}
    private void addCell(LinearLayout row,View cell){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(1,1,1,1);row.addView(cell,p);}
    private AlertDialog.Builder dialog(){return new AlertDialog.Builder(activity,dark?android.R.style.Theme_Material_Dialog_Alert:android.R.style.Theme_Material_Light_Dialog_Alert);}
    private EditText numberInput(String hint){EditText value=new EditText(activity);value.setTextColor(ink);value.setHintTextColor(muted);value.setHint(hint);
        value.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);value.setSingleLine(true);return value;}
}
