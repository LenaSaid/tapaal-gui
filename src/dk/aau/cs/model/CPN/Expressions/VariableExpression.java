package dk.aau.cs.model.CPN.Expressions;

import dk.aau.cs.model.CPN.Color;
import dk.aau.cs.model.CPN.ExpressionSupport.ExprStringPosition;
import dk.aau.cs.model.CPN.ExpressionSupport.ExprValues;
import dk.aau.cs.model.CPN.Variable;

import java.util.Set;

public class VariableExpression extends ColorExpression {

    private Variable variable;

    public Variable getVariable() {
        return this.variable;
    }

    public VariableExpression(Variable variable) {
        this.variable = variable;
    }

    public Color eval(ExpressionContext context) {
        return context.binding.get(variable.getName());
    }

    @Override
    public boolean hasColor(Color color) {
        //This should also have been fixed beforehand
        return false;
    }

    @Override
    public ColorExpression replace(Expression object1, Expression object2) {
        if (this.equals(object1) && object2 instanceof ColorExpression) {
            ColorExpression obj2 = (ColorExpression)object2;
            obj2.setParent(parent);
            return obj2;
        }
        else {
            return this;
        }
    }

    @Override
    public ColorExpression copy() {
        return new VariableExpression(this.variable);
    }

    @Override
    public boolean containsPlaceHolder() {
        return false;
    }

    @Override
    public ColorExpression findFirstPlaceHolder() {
        return null;
    }

    @Override
    public ExprValues getValues(ExprValues exprValues) {
        exprValues.addVariable(variable);
        return exprValues;
    }

    public void getVariables(Set<Variable> variables) {
        variables.add(variable);
    }

    @Override
    public String toString() {
        return variable.getName();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof VariableExpression) {
            VariableExpression expr = (VariableExpression) o;
            return variable.getName().equals(expr.variable.getName());
        }

        return false;
    }

    @Override
    public ExprStringPosition[] getChildren() {
        ExprStringPosition[] children = new ExprStringPosition[0];
        return children;
    }

    @Override
    public boolean isSimpleProperty() {
        return false;
    }


    public int hashCode() {
        int result = 17;
        result = 31 * result + variable.hashCode();
        return result;
    }
}