package com.kongye.calculator;

import java.util.Collections;

public final class MathDocumentTest {
    public static void main(String[] args){
        MathDocument brackets=new MathDocument();
        brackets.bracket(true,"");brackets.insert("6");
        require(brackets.value.charAt(2)==MathDocument.GHOST_RIGHT && brackets.cursor==2,"opening suggests a closing bracket");
        brackets.bracket(false,"");
        require(brackets.value.equals("(6)") && brackets.cursor==3,"explicit close confirms without duplicating");
        brackets.undo();
        require(brackets.value.charAt(2)==MathDocument.GHOST_RIGHT && brackets.cursor==2,"undo restores the suggested bracket and the caret");
        brackets.redo();require(brackets.value.equals("(6)"),"redo confirms again");
        brackets.replace("");brackets.bracket(false,"");brackets.insert("3");
        require(brackets.value.charAt(0)==MathDocument.GHOST_LEFT,"closing suggests an opening bracket");
        brackets.moveTo(0);require(brackets.value.equals("(3)"),"leaving to the left confirms the opening bracket");
        brackets.moveTo(3);brackets.insert("+4");answer(brackets,0,7);
        brackets.replace("");brackets.bracket(true,"sin");brackets.insert("30");
        brackets.moveTo(brackets.cursor+1);require(brackets.value.equals("sin(30)"),"moving right confirms a function bracket");
        answer(brackets,0,.5);
        brackets.replace("");brackets.bracket(true,"");brackets.backspace();
        require(brackets.value.isEmpty(),"deleting an empty pair removes its suggestion");

        MathDocument log=new MathDocument();log.insertTemplate(MathDocument.LOG);
        log.insert("2");log.moveTo(log.cursor+1);log.insert("8");answer(log,0,3);
        MathDocument root=new MathDocument();root.insertTemplate(MathDocument.ROOT);
        root.insert("3");root.moveTo(root.cursor+1);root.insert("27");answer(root,0,3);
        MathDocument power=new MathDocument();power.insert("5+2");power.insertTemplate(MathDocument.POWER);
        power.insert("3");answer(power,0,13);
        MathDocument fraction=new MathDocument();fraction.insert("2");fraction.insertTemplate(MathDocument.FRACTION);
        fraction.insert("3");fraction.moveTo(fraction.cursor+1);fraction.insertTemplate(MathDocument.POWER);
        fraction.insert("2");answer(fraction,0,4d/9);
        MathDocument nested=new MathDocument();nested.insertTemplate(MathDocument.FRACTION);
        nested.insertTemplate(MathDocument.LOG);nested.insert("2");nested.moveTo(nested.cursor+1);nested.insert("16");
        nested.moveTo(nested.cursor+2);nested.insert("2");answer(nested,0,2);
        nested.undo();require(nested.canRedo(),"undo enables redo");nested.insert("4");
        require(!nested.canRedo(),"a fresh edit clears redo");answer(nested,0,1);

        MathDocument group=new MathDocument();group.group();group.insert("1+2");
        group.moveTo(group.cursor+1);group.insert("8/2");group.group();group.insert("9");
        require(group.expressions().size()==3,"the group supports another row");
        answer(group,0,3);answer(group,1,4);answer(group,2,9);
        MathDocument reloaded=new MathDocument();reloaded.restore(group.value,group.cursor);
        answer(reloaded,2,9);require(group.cursor==reloaded.cursor,"saved document retains the caret");

        MathDocument empty=new MathDocument();empty.insertTemplate(MathDocument.POWER);
        boolean rejected=false;
        try{empty.expressions();}catch(IllegalArgumentException expected){rejected=true;}
        require(rejected,"empty slots cannot produce a result");
        empty.backspace();require(empty.value.isEmpty(),"backspace removes an empty template");
        System.out.println("PASS: paired brackets, undo/redo, four templates, nesting, grouped rows and persisted edits");
    }

    private static void answer(MathDocument document,int row,double expected){
        double result=Calculator.evaluate(document.expressions().get(row),true,Collections.emptyMap());
        require(Math.abs(result-expected)<1e-10,"expected "+expected+" but got "+result+" for "+document.expressions());
    }
    private static void require(boolean test,String message){if(!test)throw new AssertionError(message);}
}
