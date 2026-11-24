package com.kongye.calculator;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

final class MathEditorView extends View {
    interface KeyHandler { void press(String key); }
    private final MathDocument document;
    private final Runnable moved;
    private final KeyHandler keys;
    private final int ink, muted, ghost, accent, slotColor;
    private final boolean readOnly;
    private final String suffix;
    private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Caret> carets=new ArrayList<>();
    private Tile layout;
    private float offset;
    private final float unit;

    MathEditorView(Context context, MathDocument document, Runnable moved, KeyHandler keys,
                   int ink,int muted,int ghost,int accent,int slotColor){
        this(context,document,moved,keys,ink,muted,ghost,accent,slotColor,null);
    }

    MathEditorView(Context context,MathDocument document,int ink,int muted,String suffix){
        this(context,document,null,null,ink,muted,ink,ink,0,suffix);
    }

    private MathEditorView(Context context,MathDocument document,Runnable moved,KeyHandler keys,
                           int ink,int muted,int ghost,int accent,int slotColor,String suffix){
        super(context);
        this.document=document;this.moved=moved;this.keys=keys;
        this.ink=ink;this.muted=muted;this.ghost=ghost;this.accent=accent;this.slotColor=slotColor;
        unit=getResources().getDisplayMetrics().density;
        readOnly=suffix!=null;this.suffix=suffix;
        if(!readOnly){setFocusableInTouchMode(true);setClickable(true);setContentDescription("计算表达式");}
        pen.setTypeface(Typeface.create("serif",Typeface.NORMAL));
        refresh();
    }

    void refresh(){
        carets.clear();
        layout=sequence(document.parse(),0,document.value.length(),(readOnly?21:28)*unit,false);
        requestLayout();
        invalidate();
    }

    @Override protected void onMeasure(int width,int height){
        int wanted=(int)Math.ceil(layout.width+20*unit+(readOnly?measure(suffix,21*unit):0));
        float padding=readOnly?14:24;
        setMeasuredDimension(resolveSize(wanted,width),resolveSize((int)Math.ceil(Math.max((readOnly?43:66)*unit,layout.height+padding*unit)),height));
    }

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        carets.clear();
        float y=readOnly?(getHeight()-layout.height)/2:10*unit;
        layout.draw(canvas,10*unit-offset,y);
        if(readOnly){
            glyph(canvas,suffix,21*unit,muted,10*unit+layout.width,getHeight()/2f);
            return;
        }
        Caret active=null;
        for(Caret c:carets)if(c.index==document.cursor){active=c;break;}
        if(active!=null){
            float edge=12*unit;
            float target=offset;
            if(active.x>getWidth()-edge)target+=active.x-getWidth()+edge;
            if(active.x<edge)target-=edge-active.x;
            target=Math.max(0,target);
            if(Math.abs(offset-target)>.5f && getWidth()>0){offset=target;invalidate();}
            line(canvas,active.x,active.y-active.height/2,active.x,active.y+active.height/2,accent,1.65f*unit);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event){
        if(readOnly)return super.onTouchEvent(event);
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN)return true;
        if(event.getActionMasked()==MotionEvent.ACTION_UP){
            requestFocus();
            Caret best=null;float distance=Float.MAX_VALUE;
            for(Caret c:carets){
                float dx=event.getX()-c.x, dy=event.getY()-c.y;
                float score=dx*dx+2.7f*dy*dy;
                if(c.slot && Math.abs(dx)<c.height && Math.abs(dy)<c.height*.65f)score*=.15f;
                if(score<distance){distance=score;best=c;}
            }
            if(best!=null){document.moveTo(best.index);moved.run();}
            performClick();
        }
        return true;
    }

    @Override public boolean performClick(){super.performClick();return true;}

    @Override public boolean onKeyDown(int code,KeyEvent event){
        if(readOnly)return super.onKeyDown(code,event);
        switch(code){
            case KeyEvent.KEYCODE_DPAD_LEFT:keys.press("←");return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:keys.press("→");return true;
            case KeyEvent.KEYCODE_DEL:keys.press("退格");return true;
            case KeyEvent.KEYCODE_ENTER:keys.press("=");return true;
            case KeyEvent.KEYCODE_TAB:keys.press(event.isShiftPressed()?"←":"→");return true;
        }
        int character=event.getUnicodeChar();
        if(character>0 && character<128 && "0123456789.+-*/^!%(),xyze".indexOf(character)>=0){
            keys.press(String.valueOf((char)character));return true;
        }
        return super.onKeyDown(code,event);
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
        super.onInitializeAccessibilityNodeInfo(info);
        if(readOnly)return;
        info.setClassName("android.widget.EditText");
        try{info.setText(android.text.TextUtils.join("，",document.expressions()));}
        catch(IllegalArgumentException e){info.setText("待填写的公式");}
    }

    private Tile sequence(List<MathDocument.Box> boxes,int begin,int end,float size,boolean slot){
        if(boxes.isEmpty()){
            float width=slot?.62f*size:0;
            return new Tile(width,size,(c,x,y)->{
                if(slot){
                    float left=x+unit,top=y+size*.05f,right=x+width-unit,bottom=y+size*.95f;
                    float radius=Math.min(3*unit,size*.12f);
                    pen.setStyle(Paint.Style.FILL);pen.setColor(slotColor);
                    c.drawRoundRect(left,top,right,bottom,radius,radius,pen);
                    pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(.8f*unit);pen.setColor(accent);
                    pen.setPathEffect(new DashPathEffect(new float[]{2*unit,1.6f*unit},0));
                    c.drawRoundRect(left,top,right,bottom,radius,radius,pen);
                    pen.setPathEffect(null);pen.setStyle(Paint.Style.FILL);
                }
                if(!readOnly)carets.add(new Caret(begin,x,y+size/2,size*.9f,slot));
            });
        }
        List<Tile> children=new ArrayList<>();
        float width=0,height=size;
        for(MathDocument.Box item:boxes){Tile tile=box(item,size);children.add(tile);width+=tile.width;height=Math.max(height,tile.height);}
        final float h=height;
        return new Tile(width,height,(c,x,y)->{
            if(!readOnly)carets.add(new Caret(begin,x,y+h/2,size*.93f,false));
            for(int i=0;i<children.size();i++){
                Tile child=children.get(i);
                child.draw(c,x,y+(h-child.height)/2);
                x+=child.width;
                if(!readOnly)carets.add(new Caret(boxes.get(i).end,x,y+h/2,size*.93f,false));
            }
        });
    }

    private Tile field(MathDocument.Field field,float size){
        return sequence(field.boxes,field.start,field.end,Math.max(11*unit,size),true);
    }

    private Tile box(MathDocument.Box node,float size){
        if(!MathDocument.template(node.symbol)){
            String s=String.valueOf(MathDocument.visible(node.symbol));
            float gap="+−×÷=".contains(s)?size*.15f:0;
            float width=measure(s,size)+2*gap;
            return new Tile(width,size,(c,x,y)->glyph(c,s,size,MathDocument.ghost(node.symbol)?ghost:ink,x+gap,y+size/2));
        }
        if(node.symbol==MathDocument.GROUP)return group(node,size);
        Tile a=field(node.fields.get(0),node.symbol==MathDocument.POWER?size:size*.70f);
        Tile b=field(node.fields.get(1),node.symbol==MathDocument.ROOT||node.symbol==MathDocument.LOG?size:size*.74f);
        switch(node.symbol){
            case MathDocument.FRACTION:{
                float width=Math.max(a.width,b.width)+size*.35f, gap=size*.17f;
                return new Tile(width,a.height+b.height+2*gap,(c,x,y)->{
                    a.draw(c,x+(width-a.width)/2,y);
                    line(c,x+size*.09f,y+a.height+gap,x+width-size*.09f,y+a.height+gap,ink,1.25f*unit);
                    b.draw(c,x+(width-b.width)/2,y+a.height+2*gap);
                });
            }
            case MathDocument.POWER:{
                float rise=b.height*.58f;
                return new Tile(a.width+b.width+size*.17f,Math.max(a.height+rise,b.height),(c,x,y)->{
                    a.draw(c,x+size*.06f,y+rise);
                    b.draw(c,x+a.width+size*.10f,y);
                });
            }
            case MathDocument.ROOT:{
                float lead=Math.max(a.width+size*.31f,size*.69f), top=size*.22f;
                float height=Math.max(b.height+top,a.height+size*.14f);
                return new Tile(lead+b.width+size*.24f,height,(c,x,y)->{
                    a.draw(c,x+size*.04f,y);
                    Path p=new Path();p.moveTo(x+lead-size*.39f,y+height*.62f);
                    p.lineTo(x+lead-size*.24f,y+height*.62f);p.lineTo(x+lead-size*.12f,y+height*.89f);
                    p.lineTo(x+lead+size*.04f,y+top*.48f);p.lineTo(x+lead+b.width+size*.17f,y+top*.48f);
                    stroke(c,p,ink,1.35f*unit);
                    b.draw(c,x+lead+size*.10f,y+height-b.height);
                });
            }
            default:{
                float logWidth=measure("log",size), pad=size*.16f;
                float par=measure("(",size), h=Math.max(size*1.24f,b.height);
                return new Tile(logWidth+a.width+b.width+2*par+2*pad,h,(c,x,y)->{
                    glyph(c,"log",size,ink,x+pad,y+h*.43f);
                    a.draw(c,x+logWidth+pad,y+h-a.height);
                    float argument=x+logWidth+a.width+pad;
                    glyph(c,"(",size,ink,argument,y+h/2);
                    b.draw(c,argument+par,y+(h-b.height)/2);
                    glyph(c,")",size,ink,argument+par+b.width,y+h/2);
                });
            }
        }
    }

    private Tile group(MathDocument.Box node,float size){
        List<Tile> rows=new ArrayList<>();float width=0,height=0,gap=size*.23f;
        for(MathDocument.Field field:node.fields){Tile row=field(field,size);rows.add(row);width=Math.max(width,row.width);height+=row.height+gap;}
        final float h=height-gap, lead=size*.8f;
        return new Tile(lead+width+size*.25f,h,(c,x,y)->{
            float start=x+size*.12f, stem=x+size*.30f, tip=x+size*.59f, mid=y+h/2;
            Path p=new Path();p.moveTo(tip,y);p.quadTo(stem,y,stem,y+size*.20f);
            p.lineTo(stem,mid-size*.20f);p.quadTo(stem,mid,start,mid);
            p.quadTo(stem,mid,stem,mid+size*.20f);p.lineTo(stem,y+h-size*.20f);p.quadTo(stem,y+h,tip,y+h);
            stroke(c,p,ink,1.6f*unit);
            for(Tile line:rows){line.draw(c,x+lead,y);y+=line.height+gap;}
        });
    }

    private float measure(String s,float size){pen.setTextSize(size);return pen.measureText(s);}
    private void glyph(Canvas c,String s,float size,int color,float x,float center){
        pen.setStyle(Paint.Style.FILL);pen.setTextSize(size);pen.setColor(color);
        c.drawText(s,x,center-(pen.ascent()+pen.descent())/2,pen);
    }
    private void line(Canvas c,float x,float y,float x2,float y2,int color,float width){
        pen.setColor(color);pen.setStrokeWidth(width);pen.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x,y,x2,y2,pen);
    }
    private void stroke(Canvas c,Path p,int color,float width){
        pen.setColor(color);pen.setStrokeWidth(width);pen.setStrokeCap(Paint.Cap.ROUND);pen.setStyle(Paint.Style.STROKE);
        c.drawPath(p,pen);pen.setStyle(Paint.Style.FILL);
    }
    private interface Drawing {void draw(Canvas c,float x,float y);}
    private static final class Tile {
        final float width,height;
        final Drawing drawing;
        Tile(float width,float height,Drawing drawing){this.width=width;this.height=height;this.drawing=drawing;}
        void draw(Canvas c,float x,float y){drawing.draw(c,x,y);}
    }
    private static final class Caret {
        final int index;
        final float x,y,height;
        final boolean slot;
        Caret(int index,float x,float y,float height,boolean slot){this.index=index;this.x=x;this.y=y;this.height=height;this.slot=slot;}
    }
}
