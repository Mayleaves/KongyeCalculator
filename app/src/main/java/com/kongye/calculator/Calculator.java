package com.kongye.calculator;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Map;

public final class Calculator {
    public static double evaluate(String source, boolean degrees, Map<String, Double> variables) {
        return evaluateDetailed(source,degrees,variables).number;
    }

    static Value evaluateDetailed(String source,boolean degrees,Map<String,Double> variables){
        return evaluateDetailed(source,degrees,variables,null);
    }
    interface FunctionResolver {Value resolve(String name,Value argument);}
    static Value evaluateDetailed(String source,boolean degrees,Map<String,Double> variables,FunctionResolver resolver){
        Parser parser=new Parser(source,degrees,variables,resolver);
        Value value=parser.sum();
        parser.space();
        if(parser.pos!=parser.source.length())throw new IllegalArgumentException("请检查符号或括号");
        if(!Double.isFinite(value.number))throw new IllegalArgumentException("结果超出范围或无定义");
        return value;
    }

    public static String format(double value){
        if(value==0)value=0;
        return BigDecimal.valueOf(value).round(new MathContext(12)).stripTrailingZeros().toString();
    }

    static final class Value {
        final double number;
        final Fraction exact;

        Value(double number,Fraction exact){this.number=number;this.exact=exact;}

        Value combine(char op,Value other){
            if(op=='/' && (other.exact==null?other.number==0:other.exact.top.signum()==0))
                throw new IllegalArgumentException("不能除以零");
            Fraction fraction=exact!=null && other.exact!=null?exact.combine(op,other.exact):null;
            if(fraction!=null)return new Value(fraction.decimal(MathContext.DECIMAL64).doubleValue(),fraction);
            double right=other.number;
            switch(op){
                case '+':return new Value(number+right,null);
                case '-':return new Value(number-right,null);
                case '*':return new Value(number*right,null);
                case '/':return new Value(number/right,null);
                default:return new Value(Math.pow(number,right),null);
            }
        }

        Value negate(){return new Value(-number,exact==null?null:new Fraction(exact.top.negate(),exact.bottom));}
    }

    static final class Fraction {
        private static final int MAX_BITS=2048;
        final BigInteger top,bottom;

        Fraction(BigInteger top,BigInteger bottom){
            BigInteger divisor=top.gcd(bottom);
            if(bottom.signum()<0)divisor=divisor.negate();
            this.top=top.divide(divisor);this.bottom=bottom.divide(divisor);
        }

        static Fraction from(BigDecimal value){
            int scale=value.scale();
            if(scale>400 || scale< -400)return null;
            BigInteger unscaled=value.unscaledValue();
            return scale<0?limited(unscaled.multiply(BigInteger.TEN.pow(-scale)),BigInteger.ONE)
                :limited(unscaled,BigInteger.TEN.pow(scale));
        }

        private static Fraction limited(BigInteger top,BigInteger bottom){
            if(bottom.signum()==0)return null;
            Fraction result=new Fraction(top,bottom);
            return result.top.bitLength()<=MAX_BITS && result.bottom.bitLength()<=MAX_BITS?result:null;
        }

        Fraction combine(char op,Fraction other){
            switch(op){
                case '+':return limited(top.multiply(other.bottom).add(other.top.multiply(bottom)),bottom.multiply(other.bottom));
                case '-':return limited(top.multiply(other.bottom).subtract(other.top.multiply(bottom)),bottom.multiply(other.bottom));
                case '*':return limited(top.multiply(other.top),bottom.multiply(other.bottom));
                case '/':return limited(top.multiply(other.bottom),bottom.multiply(other.top));
                default:
                    if(!other.bottom.equals(BigInteger.ONE) || other.top.abs().compareTo(BigInteger.valueOf(128))>0)return null;
                    int power=other.top.intValue();
                    if(Math.abs(power)*Math.max(top.abs().bitLength(),bottom.bitLength())>MAX_BITS)return null;
                    return power<0?limited(bottom.pow(-power),top.pow(-power)):limited(top.pow(power),bottom.pow(power));
            }
        }

        BigDecimal decimal(MathContext precision){return new BigDecimal(top).divide(new BigDecimal(bottom),precision);}
        String label(){return top+"/"+bottom;}
        boolean fitsDisplay(){return !bottom.equals(BigInteger.ONE) && top.abs().toString().length()<=12 && bottom.toString().length()<=12;}
    }

    private static final class Parser {
        final String source;
        final boolean degrees;
        final Map<String,Double> variables;
        final FunctionResolver resolver;
        int pos,depth;

        Parser(String expression,boolean degrees,Map<String,Double> variables,FunctionResolver resolver){
            if(expression.length()>1000)throw new IllegalArgumentException("表达式过长");
            source=expression.replace('×','*').replace('÷','/').replace('−','-').replace('π','P');
            this.degrees=degrees;this.variables=variables;
            this.resolver=resolver;
        }

        void space(){while(pos<source.length() && Character.isWhitespace(source.charAt(pos)))pos++;}
        boolean take(char symbol){
            space();
            if(pos<source.length() && source.charAt(pos)==symbol){pos++;return true;}
            return false;
        }

        Value sum(){
            Value value=product();
            while(true){
                if(take('+'))value=value.combine('+',product());
                else if(take('-'))value=value.combine('-',product());
                else return value;
            }
        }

        Value product(){
            Value value=unary();
            while(true){
                if(take('*'))value=value.combine('*',unary());
                else if(take('/'))value=value.combine('/',unary());
                else if(pos<source.length() && (source.charAt(pos)=='(' || source.charAt(pos)=='√' || Character.isLetter(source.charAt(pos))))
                    value=value.combine('*',unary());
                else return value;
            }
        }

        Value unary(){
            if(++depth>128)throw new IllegalArgumentException("嵌套层数过多");
            try{
                if(take('+'))return unary();
                if(take('-'))return unary().negate();
                Value base=postfix();
                return take('^')?base.combine('^',unary()):base;
            }finally{depth--;}
        }

        Value postfix(){
            Value value=atom();
            while(true){
                if(take('%'))value=value.combine('/',new Value(100,new Fraction(BigInteger.valueOf(100),BigInteger.ONE)));
                else if(take('!')){
                    int n=integer(value.number,0,170,"阶乘仅支持 0～170 的整数");
                    int step=take('!')?2:1;
                    BigInteger factorial=BigInteger.ONE;
                    for(int i=n;i>1;i-=step)factorial=factorial.multiply(BigInteger.valueOf(i));
                    value=new Value(factorial.doubleValue(),value.exact==null?null:new Fraction(factorial,BigInteger.ONE));
                }else if(take('°'))value=value.combine('+',sexagesimal());
                else if(take('′'))value=value.combine('/',integerValue(60));
                else if(take('″'))value=value.combine('/',integerValue(3600));
                else return value;
            }
        }

        Value sexagesimal(){
            Value value=integerValue(0);
            for(int step=0;step<2;step++){
                space();int saved=pos;
                if(pos>=source.length() || !Character.isDigit(source.charAt(pos)))break;
                Value part=number();
                if(take('′'))value=value.combine('+',part.combine('/',integerValue(60)));
                else if(take('″')){value=value.combine('+',part.combine('/',integerValue(3600)));break;}
                else{pos=saved;break;}
            }
            return value;
        }

        private Value integerValue(long value){return new Value(value,new Fraction(BigInteger.valueOf(value),BigInteger.ONE));}

        private int integer(double value,int min,int max,String error){
            if(value<min || value>max || value!=Math.rint(value))throw new IllegalArgumentException(error);
            return (int)value;
        }

        private Value combinatorics(boolean combinations,double first,double second){
            int n=integer(first,0,170,"排列组合只支持 0～170 的整数");
            int r=integer(second,0,n,"所选个数应在 0 和总数之间");
            BigInteger result=BigInteger.ONE;
            for(int i=0;i<r;i++)result=result.multiply(BigInteger.valueOf(n-i));
            if(combinations)for(int i=2;i<=r;i++)result=result.divide(BigInteger.valueOf(i));
            return new Value(result.doubleValue(),new Fraction(result,BigInteger.ONE));
        }

        private Value common(boolean multiple,double first,double second){
            if(first!=Math.rint(first) || second!=Math.rint(second) || Math.abs(first)>1e12 || Math.abs(second)>1e12)
                throw new IllegalArgumentException("请输入绝对值不超过 10¹² 的整数");
            BigInteger a=BigInteger.valueOf((long)first).abs(), b=BigInteger.valueOf((long)second).abs();
            BigInteger gcd=a.gcd(b);
            BigInteger result=multiple && gcd.signum()!=0?a.divide(gcd).multiply(b):gcd;
            return new Value(result.doubleValue(),new Fraction(result,BigInteger.ONE));
        }

        Value atom(){
            if(take('(')){Value value=sum();closingBracket();return value;}
            if(take('√'))return approximate(Math.sqrt(unary().number));
            if(pos>=source.length())throw new IllegalArgumentException("请补全表达式");
            char symbol=source.charAt(pos);
            if(symbol=='∞')throw new IllegalArgumentException("无穷大不是有限数值");
            if(symbol=='∠')throw new IllegalArgumentException("当前仅支持实数计算");
            if(Character.isDigit(symbol)||symbol=='.')return number();
            if(Character.isLetter(symbol))return name();
            throw new IllegalArgumentException("请检查表达式");
        }

        private Value number(){
            int start=pos;
            while(pos<source.length() && (Character.isDigit(source.charAt(pos)) || source.charAt(pos)=='.'))pos++;
            if(pos<source.length() && source.charAt(pos)=='E'){
                pos++;
                if(pos<source.length() && (source.charAt(pos)=='+' || source.charAt(pos)=='-'))pos++;
                while(pos<source.length() && Character.isDigit(source.charAt(pos)))pos++;
            }
            try{
                String token=source.substring(start,pos);
                double number=Double.parseDouble(token);
                return new Value(number,Double.isFinite(number)?Fraction.from(new BigDecimal(token)):null);
            }catch(NumberFormatException error){throw new IllegalArgumentException("数字格式有误");}
        }

        private Value name(){
            int start=pos++;
            while(pos<source.length() && Character.isLetter(source.charAt(pos)))pos++;
            if(source.substring(start,pos).equals("log") && source.startsWith("2(",pos))pos++;
            String name=source.substring(start,pos);
            if(name.equals("P"))return approximate(Math.PI);
            if(name.equals("e"))return approximate(Math.E);
            if(variables.containsKey(name))return approximate(variables.get(name));
            if(name.equals("i"))throw new IllegalArgumentException("当前仅支持实数计算");
            if(name.length()>1){
                double product=1;
                boolean allVariables=true;
                for(int i=0;i<name.length();i++){
                    Double value=variables.get(name.substring(i,i+1));
                    if(value==null){allVariables=false;break;}product*=value;
                }
                if(allVariables)return approximate(product);
            }
            if(!take('('))throw new IllegalArgumentException("未定义变量："+name);
            Value argument=sum();
            double x=argument.number, y=take(',')?sum().number:Double.NaN;
            closingBracket();
            double angle=degrees?Math.toRadians(x):x;
            switch(name){
                case "sin":return approximate(Math.sin(angle));
                case "cos":return approximate(Math.cos(angle));
                case "arcsin":return inverseAngle(Math.asin(x));
                case "arccos":return inverseAngle(Math.acos(x));
                case "arctan":return inverseAngle(Math.atan(x));
                case "tan":
                    if(Math.abs(Math.cos(angle))<1e-14)throw new IllegalArgumentException("该角度的正切无定义");
                    return approximate(Math.tan(angle));
                case "ln":return approximate(Math.log(x));
                case "log":return approximate(Double.isNaN(y)?Math.log10(x):Math.log(y)/Math.log(x));
                case "log2":return approximate(Math.log(x)/Math.log(2));
                case "sqrt":return approximate(Math.sqrt(x));
                case "root":return approximate(x==0?Double.NaN:Math.pow(y,1/x));
                case "abs":return x<0?argument.negate():argument;
                case "perm":return combinatorics(false,x,y);
                case "comb":return combinatorics(true,x,y);
                case "gcd":return common(false,x,y);
                case "lcm":return common(true,x,y);
                case "mod":
                    if(y==0)throw new IllegalArgumentException("不能除以零");
                    return approximate(x%y);
                default:
                    if(resolver!=null && Double.isNaN(y))return resolver.resolve(name,argument);
                    throw new IllegalArgumentException("未知函数："+name);
            }
        }

        private void closingBracket(){if(!take(')'))throw new IllegalArgumentException("缺少右括号");}
        private Value inverseAngle(double value){return approximate(degrees?Math.toDegrees(value):value);}
        private Value approximate(double number){return new Value(number,null);}
    }
}
