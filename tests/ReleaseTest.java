package com.kongye.calculator;

import java.math.MathContext;
import java.util.Collections;
import java.util.Map;

public final class ReleaseTest {
    public static void main(String[] args){
        Calculator.Value value=Calculator.evaluateDetailed("5/12-1/8",true,Collections.emptyMap());
        require(value.exact.label().equals("7/24"),"exact fraction reduction");
        require(value.exact.decimal(new MathContext(10)).toString().equals("0.2916666667"),"decimal matches displayed precision");
        require(Calculator.evaluateDetailed("π/2",true,Collections.emptyMap()).exact==null,"do not invent an exact fraction for pi");
        require(Calculator.evaluateDetailed("2/(-6)",true,Collections.emptyMap()).exact.label().equals("-1/3"),"normalize negative denominator");
        require(Calculator.evaluateDetailed("1E-350/2E-350",true,Collections.emptyMap()).exact.label().equals("1/2"),"avoid underflow in rational division");
        eq("comb(6,2)+perm(5,2)",35);eq("gcd(18,24)+lcm(3,5)",21);eq("6!!",48);
        eq("log2(32)",5);eq("arcsin(0.5)",30);eq("30°15′30″",30.258333333333);
        require(Kinship.title(new int[]{0,2},true).contains("伯父"),"father's elder brother");
        require(Kinship.title(new int[]{1,0},true).contains("外祖父"),"mother's father");
        require(Kinship.title(new int[]{0,3,8},true).contains("堂弟"),"father's younger brother's son");
        require(Kinship.title(new int[]{5,8},false).equals("外甥"),"younger sister's son");
        require(Kinship.title(new int[]{8,0},false).equals("丈夫"),"female subject's son's father");
        require(Calculator.evaluateDetailed("twice(4)+1",true,Map.of(),(name,arg)->arg.combine('*',Calculator.evaluateDetailed("2",true,Map.of()))).number==9,"custom function resolver");
        MathDocument fraction=new MathDocument();fraction.restore(FormulaCatalog.frac("7","24"),0);
        require(Calculator.evaluateDetailed(fraction.expressions().get(0),true,Map.of()).exact.label().equals("7/24"),"stored fraction remains exact when reinserted");
        System.out.println("PASS: rational display, scientific functions, kinship, custom functions and reusable fractions");
    }
    private static void eq(String expression,double expected){
        require(Math.abs(Calculator.evaluate(expression,true,Map.of())-expected)<1e-10,expression);
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
