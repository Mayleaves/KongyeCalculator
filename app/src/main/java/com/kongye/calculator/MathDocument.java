package com.kongye.calculator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The editor stores template boundaries and suggested brackets separately from typed glyphs. */
final class MathDocument {
    static final char LOG='\uE000', POWER='\uE001', ROOT='\uE002', FRACTION='\uE003', GROUP='\uE004';
    static final char NEXT='\uE005', END='\uE006', GHOST_LEFT='\uE007', GHOST_RIGHT='\uE008';
    private static final int LIMIT=1000;
    private final ArrayDeque<State> undo=new ArrayDeque<>(), redo=new ArrayDeque<>();
    String value="";
    int cursor;

    static boolean template(char c){return c>=LOG && c<=GROUP;}
    static boolean ghost(char c){return c==GHOST_LEFT || c==GHOST_RIGHT;}
    static char visible(char c){return c==GHOST_LEFT?'(':c==GHOST_RIGHT?')':c;}

    void restore(String raw,int point){
        value=raw;
        cursor=Math.max(0,Math.min(point,raw.length()));
    }
    boolean canUndo(){return !undo.isEmpty();}
    boolean canRedo(){return !redo.isEmpty();}
    void undo(){travel(undo,redo);}
    void redo(){travel(redo,undo);}
    private void travel(ArrayDeque<State> from,ArrayDeque<State> to){
        if(from.isEmpty())return;
        to.push(new State(value,cursor));
        State target=from.pop();
        restore(target.text,target.cursor);
    }
    private void change(String raw,int point){
        if(raw.length()>LIMIT)return;
        if(!raw.equals(value)){
            undo.push(new State(value,cursor));
            if(undo.size()>100)undo.removeLast();
            redo.clear();
        }
        restore(raw,point);
    }
    void replace(String raw){change(raw,raw.length());}
    void insert(String chars){splice(cursor,cursor,chars,chars.length());}
    void insertCall(String name){splice(cursor,cursor,name+"(,"+GHOST_RIGHT,name.length()+1);}
    private void splice(int start,int end,String text,int point){
        change(value.substring(0,start)+text+value.substring(end),start+point);
    }

    void moveTo(int point){
        point=Math.max(0,Math.min(point,value.length()));
        StringBuilder raw=new StringBuilder(value);
        for(int i=Math.min(cursor,point);i<Math.max(cursor,point);i++){
            if(ghost(raw.charAt(i)))raw.setCharAt(i,visible(raw.charAt(i)));
        }
        change(raw.toString(),point);
    }

    void bracket(boolean left,String prefix){
        if(prefix.isEmpty() && left && cursor>0 && value.charAt(cursor-1)==GHOST_LEFT){
            moveTo(cursor-1);
        }else if(!left && cursor<value.length() && value.charAt(cursor)==GHOST_RIGHT){
            moveTo(cursor+1);
        }else if(left){
            splice(cursor,cursor,prefix+'('+GHOST_RIGHT,prefix.length()+1);
        }else{
            splice(cursor,cursor,""+GHOST_LEFT+')',1);
        }
    }

    void insertTemplate(char kind){
        int start=cursor;
        if(kind==POWER || kind==FRACTION)start=operandStart();
        String operand=value.substring(start,cursor);
        splice(start,cursor,""+kind+operand+NEXT+END,operand.isEmpty()?1:operand.length()+2);
    }

    private int operandStart(){
        int i=cursor;
        if(i==0)return i;
        if(value.charAt(i-1)==END){
            int depth=0;
            while(i>0){char c=value.charAt(--i);if(c==END)depth++;else if(template(c) && --depth==0)return c==GROUP?cursor:i;}
            return cursor;
        }
        if(visible(value.charAt(i-1))==')'){
            int depth=0;
            while(i>0){
                char c=visible(value.charAt(--i));
                if(c==')')depth++;
                if(c=='(' && --depth==0){
                    while(i>0 && Character.isLetter(value.charAt(i-1)))i--;
                    return i;
                }
            }
            return cursor;
        }
        while(i>0 && (value.charAt(i-1)=='%' || value.charAt(i-1)=='!'))i--;
        if(i>0 && (Character.isDigit(value.charAt(i-1)) || value.charAt(i-1)=='.')){
            while(i>0 && (Character.isDigit(value.charAt(i-1)) || value.charAt(i-1)=='.'))i--;
            int mantissa=i;
            if(i>0 && (value.charAt(i-1)=='-' || value.charAt(i-1)=='+'))i--;
            if(i>1 && value.charAt(i-1)=='E' && Character.isDigit(value.charAt(i-2))){
                i--;
                while(i>0 && (Character.isDigit(value.charAt(i-1)) || value.charAt(i-1)=='.'))i--;
            }else i=mantissa;
            return i;
        }
        while(i>0 && Character.isLetter(value.charAt(i-1)))i--;
        return i==cursor || i>0 && (value.charAt(i-1)=='!' || value.charAt(i-1)=='%')?cursor:i;
    }

    void group(){
        if(!value.isEmpty() && value.charAt(0)==GROUP){
            Box box=parse().get(0);
            if(box.end==value.length()){
                int end=box.end-1;
                splice(end,end,String.valueOf(NEXT),1);
                return;
            }
        }
        String existing=value;
        change(GROUP+existing+NEXT+END,existing.isEmpty()?1:existing.length()+2);
    }

    void backspace(){
        if(cursor==0)return;
        char before=value.charAt(cursor-1);
        if(template(before)){
            Box box=readBox(cursor-1,new int[]{cursor-1});
            boolean empty=true;
            for(Field field:box.fields)empty &= field.start==field.end;
            if(empty)splice(box.start,box.end,"",0);else moveTo(cursor-1);
            return;
        }
        if(before==NEXT || before==END){moveTo(cursor-1);return;}
        if(visible(before)=='(' && cursor<value.length() && visible(value.charAt(cursor))==')'
                && (ghost(before) || ghost(value.charAt(cursor)))){
            splice(cursor-1,cursor+1,"",0);
            return;
        }
        if(visible(before)=='(' || visible(before)==')'){
            int opposite=matchingBracket(cursor-1);
            if(opposite>=0 && ghost(value.charAt(opposite))){
                StringBuilder raw=new StringBuilder(value);
                raw.deleteCharAt(Math.max(cursor-1,opposite));
                raw.deleteCharAt(Math.min(cursor-1,opposite));
                change(raw.toString(),cursor-(opposite<cursor-1?2:1));
                return;
            }
        }
        splice(cursor-1,cursor,"",0);
    }

    private int matchingBracket(int at){
        char first=visible(value.charAt(at));
        int step=first=='('?1:-1, depth=0;
        for(int i=at;i>=0 && i<value.length();i+=step){
            char c=visible(value.charAt(i));
            if(template(c) || c==NEXT || c==END)return -1;
            if(c==first)depth++;
            else if((c=='(' || c==')') && --depth==0)return i;
        }
        return -1;
    }

    List<Box> parse(){return readField(new int[]{0}).boxes;}
    private Field readField(int[] at){
        int start=at[0];
        List<Box> boxes=new ArrayList<>();
        while(at[0]<value.length()){
            char c=value.charAt(at[0]);
            if(c==NEXT || c==END)break;
            boxes.add(readBox(at[0],at));
        }
        return new Field(start,at[0],boxes);
    }

    private Box readBox(int start,int[] at){
        char c=value.charAt(at[0]++);
        if(!template(c))return new Box(c,start,at[0],Collections.emptyList());
        List<Field> fields=new ArrayList<>();
        fields.add(readField(at));
        while(at[0]<value.length() && value.charAt(at[0])==NEXT){at[0]++;fields.add(readField(at));}
        if(at[0]<value.length() && value.charAt(at[0])==END)at[0]++;
        return new Box(c,start,at[0],fields);
    }

    List<String> expressions(){
        List<Box> boxes=parse();
        List<String> rows=new ArrayList<>();
        if(boxes.size()==1 && boxes.get(0).symbol==GROUP){
            for(Field field:boxes.get(0).fields)rows.add(compile(field.boxes));
        }else rows.add(compile(boxes));
        return rows;
    }

    private String compile(List<Box> boxes){
        StringBuilder out=new StringBuilder();
        for(Box box:boxes){
            if(!template(box.symbol)){out.append(visible(box.symbol));continue;}
            if(box.symbol==GROUP)throw new IllegalArgumentException("大括号分组需要单独计算");
            String a=compile(box.fields.get(0).boxes), b=compile(box.fields.get(1).boxes);
            if(a.trim().isEmpty() || b.trim().isEmpty())throw new IllegalArgumentException("请填完公式中的空格");
            switch(box.symbol){
                case LOG:out.append("(log((").append(a).append("),(").append(b).append(")))");break;
                case ROOT:out.append("(root((").append(a).append("),(").append(b).append(")))");break;
                case POWER:out.append("((").append(a).append(")^(").append(b).append("))");break;
                case FRACTION:out.append("((").append(a).append(")/(").append(b).append("))");break;
            }
        }
        return out.toString();
    }

    static final class Field {
        final int start,end;
        final List<Box> boxes;
        Field(int start,int end,List<Box> boxes){this.start=start;this.end=end;this.boxes=boxes;}
    }
    static final class Box {
        final char symbol;
        final int start,end;
        final List<Field> fields;
        Box(char symbol,int start,int end,List<Field> fields){this.symbol=symbol;this.start=start;this.end=end;this.fields=fields;}
    }
    private static final class State {
        final String text;
        final int cursor;
        State(String text,int cursor){this.text=text;this.cursor=cursor;}
    }
}
