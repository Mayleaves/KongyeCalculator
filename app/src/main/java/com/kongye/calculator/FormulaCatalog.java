package com.kongye.calculator;

final class FormulaCatalog {
    static final String[][] SECTIONS={
        {"一次函数","y=kx+b","y=kx+b"},
        {"反比例函数","y=k/x","y="+frac("k","x")},
        {"二次函数","y=ax²+bx+c","y=a×"+pow("x")+"+bx+c","y=a(x−h)²+k","y=a×"+pow("(x−h)")+"+k"},
        {"指数函数","y=aˣ","y="+tile(MathDocument.POWER,"a","x")},
        {"对数函数","y=logₐ(x)","y="+tile(MathDocument.LOG,"a","x")},
        {"圆","(x−a)²+(y−b)²=r²",pow("(x−a)")+"+"+pow("(y−b)")+"="+pow("r"),"x²+y²+ax+by+c=0",pow("x")+"+"+pow("y")+"+ax+by+c=0"},
        {"椭圆","x²/a²+y²/b²=1",frac(pow("x"),pow("a"))+"+"+frac(pow("y"),pow("b"))+"=1",
            "(x−k)²/a²+(y−h)²/b²=1",frac(pow("(x−k)"),pow("a"))+"+"+frac(pow("(y−h)"),pow("b"))+"=1"},
        {"双曲线","x²/a²−y²/b²=1",frac(pow("x"),pow("a"))+"−"+frac(pow("y"),pow("b"))+"=1",
            "(x−k)²/a²−(y−h)²/b²=1",frac(pow("(x−k)"),pow("a"))+"−"+frac(pow("(y−h)"),pow("b"))+"=1"},
        {"抛物线","y²=2px",pow("y")+"=2px","x²=2py",pow("x")+"=2py"}
    };
    static String frac(String top,String bottom){return tile(MathDocument.FRACTION,top,bottom);}
    private static String pow(String base){return tile(MathDocument.POWER,base,"2");}
    private static String tile(char kind,String a,String b){return ""+kind+a+MathDocument.NEXT+b+MathDocument.END;}
}
