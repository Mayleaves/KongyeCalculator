import com.kongye.calculator.Calculator;
import java.util.Map;
public class CalculatorTest {
    static int count;
    static void check(String s,double expected){double actual=Calculator.evaluate(s,true,Map.of("x",3d,"y",4d,"Ans",12d));if(Math.abs(actual-expected)>1e-9*Math.max(1,Math.abs(expected)))throw new AssertionError(s+" -> "+actual);count++;}
    static void fail(String s){try{Calculator.evaluate(s,true,Map.of());}catch(IllegalArgumentException e){count++;return;}throw new AssertionError("should reject: "+s);}
    public static void main(String[] args){
        check("2+3×4",14);check("(2+3)×4",20);check("2^3^2",512);check("-2^2",-4);check("2^-2",.25);check("0.1+0.2",.3);check("50%",.5);check("200×10%",20);check("sqrt(81)",9);check("√9",3);check("log(100)",2);check("log(2,8)",3);check("root(3,27)",3);check("sin(30)",.5);check("cos(60)",.5);check("5!",120);check("2π",2*Math.PI);check("2(3+4)",14);check("x^2+y^2",25);check("Ans+1",13);check("1E-5×10",.0001);
        fail("1/0");fail("sqrt(-1)");fail("(1+2");fail("1..2");fail("171!");fail("tan(90)");fail("1+");fail("z");fail("log(1,3)");
        if(!Calculator.format(.1+.2).equals("0.3"))throw new AssertionError("format");
        System.out.println("PASS: "+count+" arithmetic, precedence, domain and error checks + display precision");
    }
}
