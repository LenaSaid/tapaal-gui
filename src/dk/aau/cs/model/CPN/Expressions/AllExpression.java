package dk.aau.cs.model.CPN.Expressions;

import dk.aau.cs.model.CPN.Color;
import dk.aau.cs.model.CPN.ColorType;
import dk.aau.cs.model.CPN.ExpressionSupport.ExprValues;
import dk.aau.cs.model.CPN.ExpressionSupport.ExprStringPosition;
import dk.aau.cs.model.CPN.Variable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Vector;

public class AllExpression extends ColorExpression {

    private ColorType sort;

    public ColorType getColorType(List<ColorType> colorTypes){
        return getColorType();
    }

    public ColorType getColorType(){
        return this.sort;
    }

    public ColorType setColorType(ColorType newType){
        return this.sort = newType;
    }

    public AllExpression(ColorType sort) {
        this.sort = sort;
    }

    //TODO unsure about this
    public List<Color> eval(ExpressionContext context) {
        return sort.getColors();
    }

    @Override
    public boolean hasColor(Color color) {
        //This should always be false because we replace all arc expressions
        return false;
    }

    @Override
    public ColorExpression updateColor(Color color, ColorType newColorType) {
        return this;
    }

    @Override
    public boolean hasVariable(List<Variable> variables) {
        return false;
    }

    public Integer size() {
        return sort.size();
    }
    @Override
    public ColorExpression replace(Expression object1, Expression object2){
        return replace(object1,object2,false);
    }
    @Override
    public ColorExpression replace(Expression object1, Expression object2, boolean replaceAllInstances) {
        if (this == object1 && object2 instanceof ColorExpression) {
            ColorExpression obj2 = (ColorExpression) object2;
            obj2.setParent(parent);
            return obj2;
        } else {
            return this;
        }
    }

    public ColorType getSort() {return sort;}

    @Override
    public AllExpression copy() {
        return new AllExpression(sort);
    }

    @Override
    public AllExpression deepCopy() {
        return new AllExpression(sort.copy());
    }

    @Override
    public boolean containsPlaceHolder() {
        return false;
    }

    @Override
    public AllExpression findFirstPlaceHolder() {
        return null;
    }

    @Override
    public void getValues(ExprValues exprValues) {
        exprValues.addColorType(sort);
    }

    @Override
    public void getVariables(Set<Variable> variables) {

    }

    public String toString() {
        return sort.getName() + ".all";
    }

    @Override
    public boolean isSimpleProperty() {return false; }

    @Override
    public ExprStringPosition[] getChildren() {
        ExprStringPosition[] children = new ExprStringPosition[0];
        return children;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof AllExpression) {
            AllExpression expr = (AllExpression) o;
            return expr.sort.equals(this.sort);
        }
        return false;
    }

    @Override
    public boolean isComparable(ColorExpression otherExpr){
        return false;
    }

    @Override
    public ColorExpression getButtomColorExpression(){
        return this;
    }

    @Override
    public Vector<ColorType> getColorTypes(){
        return new Vector<>(Arrays.asList(sort));
    }
}