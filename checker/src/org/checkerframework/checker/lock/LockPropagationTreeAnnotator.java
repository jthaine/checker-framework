package org.checkerframework.checker.lock;

import java.util.ArrayList;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.util.Elements;

import org.checkerframework.checker.lock.LockAnnotatedTypeFactory.LockQualifierHierarchy;
import org.checkerframework.checker.lock.qual.GuardSatisfied;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.lock.qual.GuardedByInaccessible;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.treeannotator.PropagationTreeAnnotator;
import org.checkerframework.javacutil.AnnotationUtils;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.Tree.Kind;

public class LockPropagationTreeAnnotator extends PropagationTreeAnnotator {

    /** Annotation constants */
    protected final AnnotationMirror GUARDEDBYINACCESSIBLE, GUARDSATISFIED, GUARDEDBY;

    public LockPropagationTreeAnnotator(AnnotatedTypeFactory atypeFactory) {
        super(atypeFactory);

        Elements elements = atypeFactory.getElementUtils();
        GUARDEDBYINACCESSIBLE = AnnotationUtils.fromClass(elements, GuardedByInaccessible.class);
        GUARDSATISFIED = AnnotationUtils.fromClass(elements, GuardSatisfied.class);
        GUARDEDBY = AnnotationUtils.fromClass(elements, GuardedBy.class);
    }
    
    @Override
    public Void visitBinary(BinaryTree node, AnnotatedTypeMirror type) {
        // TODO: Document that this must match the other method
        System.out.println(node.toString());
        if (((LockAnnotatedTypeFactory)atypeFactory).isGenerativeBinaryOperator(node.getKind())) {
            AnnotationMirror a = atypeFactory.getAnnotatedType(node.getLeftOperand()).getAnnotationInHierarchy(GUARDEDBYINACCESSIBLE);
            AnnotationMirror b = atypeFactory.getAnnotatedType(node.getRightOperand()).getAnnotationInHierarchy(GUARDEDBYINACCESSIBLE);

            LockQualifierHierarchy lockQualifierHierarchy = (LockQualifierHierarchy) ((LockAnnotatedTypeFactory) atypeFactory).getQualifierHierarchy();

            if ((AnnotationUtils.areSameIgnoringValues(a, GUARDSATISFIED) || lockQualifierHierarchy.isGuardedBy(a)) &&
                (AnnotationUtils.areSameIgnoringValues(b, GUARDSATISFIED) || lockQualifierHierarchy.isGuardedBy(b))) {
                ArrayList<AnnotationMirror> annotations = new ArrayList<AnnotationMirror>();
                // Checking for preconditions on the left and right operands annotated with @GuardedBy(...) is not done here - it is done in the visitor.
                // TODO: Document in manual.
                // TODO: IMPORTANT - MUST BE SMARTER ABOUT THIS AND ONLY CHANGE THE PRIMARY ANNOTATION, BUT NOT WIPE AWAY THE OTHER ANNOTATIONS OF THE LUB.
                annotations.add(GUARDEDBY);
                type.addMissingAnnotations(annotations);
            }
        }
        
        // TODO: Generalize this and move it to the super class:
        if (node.getKind() == Kind.EQUAL_TO || node.getKind() == Kind.NOT_EQUAL_TO) {
            // TODO: Do regular defaulting. Or does it happen already?
            return null;
        }

        return super.visitBinary(node, type);
    }

    @Override
    public Void visitCompoundAssignment(CompoundAssignmentTree node,
            AnnotatedTypeMirror type) {
        // TODO Auto-generated method stub
        return super.visitCompoundAssignment(node, type);
    }

    @Override
    public Void visitConditionalExpression(ConditionalExpressionTree node,
            AnnotatedTypeMirror p) {
        // TODO Auto-generated method stub
        AnnotatedTypeMirror a = atypeFactory.getAnnotatedType(node.getFalseExpression());
        AnnotatedTypeMirror b = atypeFactory.getAnnotatedType(node.getTrueExpression());
        
        return super.visitConditionalExpression(node, p);
    }
}
