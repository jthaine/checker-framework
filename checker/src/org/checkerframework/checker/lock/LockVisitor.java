package org.checkerframework.checker.lock;

/*>>>
import org.checkerframework.checker.compilermsgs.qual.CompilerMessageKey;
*/

import org.checkerframework.checker.lock.LockAnnotatedTypeFactory.SideEffectAnnotation;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.lock.qual.GuardedByBottom;
import org.checkerframework.checker.lock.qual.GuardedByInaccessible;
import org.checkerframework.checker.lock.qual.GuardSatisfied;
import org.checkerframework.checker.lock.qual.LockHeld;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.dataflow.analysis.FlowExpressions;
import org.checkerframework.dataflow.analysis.FlowExpressions.Receiver;
import org.checkerframework.dataflow.cfg.node.ArrayAccessNode;
import org.checkerframework.dataflow.cfg.node.ExplicitThisLiteralNode;
import org.checkerframework.dataflow.cfg.node.FieldAccessNode;
import org.checkerframework.dataflow.cfg.node.ImplicitThisLiteralNode;
import org.checkerframework.dataflow.cfg.node.LocalVariableNode;
import org.checkerframework.dataflow.cfg.node.MethodInvocationNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.cfg.node.ThisLiteralNode;
import org.checkerframework.framework.flow.CFAbstractStore;
import org.checkerframework.framework.flow.CFAbstractValue;
import org.checkerframework.framework.source.Result;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.util.AnnotatedTypes;
import org.checkerframework.framework.util.FlowExpressionParseUtil;
import org.checkerframework.framework.util.FlowExpressionParseUtil.FlowExpressionContext;
import org.checkerframework.framework.util.FlowExpressionParseUtil.FlowExpressionParseException;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.InternalUtils;
import org.checkerframework.javacutil.Pair;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.util.TreePath;

/**
 * The LockVisitor enforces the special type-checking rules described in the Lock Checker manual chapter.
 *
 * @checker_framework.manual #lock-checker Lock Checker
 */

public class LockVisitor extends BaseTypeVisitor<LockAnnotatedTypeFactory> {
    private final Class<? extends Annotation> checkerGuardedByClass = GuardedBy.class;
    private final Class<? extends Annotation> checkerGuardSatisfiedClass = GuardSatisfied.class;

    // Note that Javax and JCIP @GuardedBy is used on both methods and objects. For methods they are
    // equivalent to the Checker Framework @Holding annotation.
    private final Class<? extends Annotation> javaxGuardedByClass = javax.annotation.concurrent.GuardedBy.class;
    private final Class<? extends Annotation> jcipGuardedByClass = net.jcip.annotations.GuardedBy.class;

    /** Annotation constants */
    protected final AnnotationMirror GUARDEDBY, GUARDEDBYINACCESSIBLE, GUARDSATISFIED, GUARDEDBYBOTTOM;

    public LockVisitor(BaseTypeChecker checker) {
        super(checker);

        GUARDEDBYINACCESSIBLE = AnnotationUtils.fromClass(elements, GuardedByInaccessible.class);
        GUARDEDBY = AnnotationUtils.fromClass(elements, GuardedBy.class);
        GUARDSATISFIED = AnnotationUtils.fromClass(elements, GuardSatisfied.class);
        GUARDEDBYBOTTOM = AnnotationUtils.fromClass(elements, GuardedByBottom.class);

        checkForAnnotatedJdk();
    }

    @Override
    public Void visitVariable(VariableTree node, Void p) { // visit a variable declaration
        // A user may not annotate a primitive type with any qualifier from the @GuardedBy hierarchy.
        if (node.getType().getKind() == Kind.PRIMITIVE_TYPE &&
            (node.toString().contains("GuardSatisfied") ||
             node.toString().contains("GuardedBy"))){ // HACK!!! TODO: Fix once there is a way to reliably retrieve user-written qualifiers.
            checker.report(Result.failure("primitive.type.guardedby"), node);
        }
        return super.visitVariable(node, p);
    }

    @Override
    public LockAnnotatedTypeFactory createTypeFactory() {
        return new LockAnnotatedTypeFactory(checker);
    }

    // Issue an error if a method (explicitly or implicitly) annotated with @MayReleaseLocks has a formal parameter
    // or receiver (explicitly or implicitly) annotated with @GuardSatisfied.
    @Override
    public Void visitMethod(MethodTree node, Void p) {

        SideEffectAnnotation sea = atypeFactory.methodSideEffectAnnotation(TreeUtils.elementFromDeclaration(node), true);

        if (sea == SideEffectAnnotation.MAYRELEASELOCKS) {
            boolean issueGSwithMRLWarning = false;

            VariableTree receiver = node.getReceiverParameter();
            if (receiver != null) {
                if (atypeFactory.getAnnotatedType(receiver).hasAnnotation(checkerGuardSatisfiedClass)) {
                    issueGSwithMRLWarning = true;
                }
            }

            if (!issueGSwithMRLWarning) { // Skip this loop if it is already known that the warning must be issued.
                for(VariableTree vt : node.getParameters()) {
                    if (atypeFactory.getAnnotatedType(vt).hasAnnotation(checkerGuardSatisfiedClass)) {
                        issueGSwithMRLWarning = true;
                        break;
                    }
                }
            }

            if (issueGSwithMRLWarning) {
                checker.report(Result.failure("guardsatisfied.with.mayreleaselocks"), node);
            }
        }

        return super.visitMethod(node, p);
    }

    // When visiting a method call, if the receiver formal parameter
    // has type @GuardSatisfied, skip the receiver subtype check and instead
    // verify that the guard is satisfied.
    @Override
    protected boolean skipReceiverSubtypeCheck(MethodInvocationTree node,
            AnnotatedTypeMirror methodDefinitionReceiver,
            AnnotatedTypeMirror methodCallReceiver) {
        Set<AnnotationMirror> annos = methodDefinitionReceiver.getAnnotations();
        for(AnnotationMirror anno : annos) {
            if (AnnotationUtils.areSameByClass(anno, checkerGuardSatisfiedClass)) {
                Element invokedElement = TreeUtils.elementFromUse(node);

                if (invokedElement != null) {
                    checkPreconditions(node,
                            invokedElement,
                            true,
                            generatePreconditionsBasedOnGuards(methodCallReceiver, false));
                }

                return true;
            }
        }

        return false;
    }

    @Override
    protected Set<? extends AnnotationMirror> getExceptionParameterLowerBoundAnnotations() {
        Set<? extends AnnotationMirror> tops = atypeFactory.getQualifierHierarchy().getTopAnnotations();
        Set<AnnotationMirror> annotationSet = AnnotationUtils.createAnnotationSet();
        for (AnnotationMirror anno : tops) {
            if (anno.equals(GUARDEDBYINACCESSIBLE)) {
                annotationSet.add(GUARDEDBY);
            }
            else {
                annotationSet.add(anno);
            }
        }
        return annotationSet;
    }

    private Set<Pair<String, String>> generatePreconditionsBasedOnGuards(AnnotatedTypeMirror atm, boolean translateItselfToThis) {
        return generatePreconditionsBasedOnGuards(atm.getAnnotations(), translateItselfToThis);
    }

    // Given a set of AnnotationMirrors, returns the list of lock expression preconditions
    // specified in all the @GuardedBy annotations in the set.
    // Returns an empty set if no such expressions are found.
    private Set<Pair<String, String>> generatePreconditionsBasedOnGuards(Set<AnnotationMirror> amList, boolean translateItselfToThis) {
        Set<Pair<String, String>> preconditions = new HashSet<>();

        if (amList != null) {
            for (AnnotationMirror annotationMirror : amList) {

                if (AnnotationUtils.areSameByClass( annotationMirror, checkerGuardedByClass) ||
                    AnnotationUtils.areSameByClass( annotationMirror, javaxGuardedByClass) ||
                    AnnotationUtils.areSameByClass( annotationMirror, jcipGuardedByClass)) {
                    if (AnnotationUtils.hasElementValue(annotationMirror, "value")) {
                        List<String> guardedByValue = AnnotationUtils.getElementValueArray(annotationMirror, "value", String.class, false);

                        for(String lockExpression : guardedByValue) {
                            if (translateItselfToThis && lockExpression.equals("itself")) {
                                lockExpression = "this";
                            }
                            preconditions.add(Pair.of(lockExpression, LockHeld.class.toString().substring(10 /* "interface " */)));
                        }
                    }
                }
            }
        }

        return preconditions;
    }

    @Override
    protected void checkAccess(IdentifierTree node, Void p) {
        AnnotatedTypeMirror atm = atypeFactory.getAnnotatedType(node);
        boolean hasJavaxOrJcipGuardedByAnnotation = atm == null ? false : (atm.hasAnnotation(javaxGuardedByClass) || atm.hasAnnotation(jcipGuardedByClass));
        // Don't check preconditions for annotations on javax or JCIP @GuardedBy because
        // those are meant for uses of those annotations when they are acting as the @Holding annotation,
        // not when they are acting as the Lock Checker's @GuardedBy type qualifier.
        checkAccess(node, p, !hasJavaxOrJcipGuardedByAnnotation);
    }

    @Override
    protected void commonAssignmentCheck(AnnotatedTypeMirror varType,
            AnnotatedTypeMirror valueType, Tree valueTree, /*@CompilerMessageKey*/ String errorKey,
            boolean isLocalVariableAssignement) {

        Kind valueTreeKind = valueTree.getKind();

        switch(valueTreeKind) {
            case NEW_CLASS:
            case NEW_ARRAY:
                // Avoid issuing warnings for: @GuardedBy(<something>) Object o = new Object();
                // Do NOT do this if the LHS is @GuardedByBottom.
                if (!varType.hasAnnotation(GuardedByBottom.class))
                    return;
                break;
            case INT_LITERAL:
            case LONG_LITERAL:
            case FLOAT_LITERAL:
            case DOUBLE_LITERAL:
            case BOOLEAN_LITERAL:
            case CHAR_LITERAL:
            case STRING_LITERAL:
                // Avoid issuing warnings for: @GuardedBy(<something>) Object o; o = <some literal>;
                // Do NOT do this if the LHS is @GuardedByBottom.
                if (!varType.hasAnnotation(GuardedByBottom.class))
                    return;
                break;
            case NULL_LITERAL: // Assigning null to any LHS is valid.
                return;
            default:
        }

        boolean skipSubtypeCheck = false;

        // Assigning a value with a @GuardedBy annotation to a variable with a @GuardSatisfied annotation is always
        // legal. However this is our last chance to check anything before the @GuardedBy information is lost in the
        // assignment to the variable annotated with @GuardSatisfied. See the Lock Checker manual chapter discussion
        // on the @GuardSatisfied annotation for more details.
        // TODO: Make the same behavior apply to @GuardedByInaccessible and document that in the manual.
        if (varType.hasAnnotation(GuardSatisfied.class)) {
            // TODO: Make sure the RHS can be @GuardSatisfied with a different index when matching method formal parameters to actual parameters.
            if (valueType.hasAnnotation(GuardedBy.class)) {
                ExpressionTree tree = (ExpressionTree) valueTree;

                checkPreconditions(tree,
                        TreeUtils.elementFromUse(tree),
                        tree.getKind() == Tree.Kind.METHOD_INVOCATION,
                          generatePreconditionsBasedOnGuards(valueType, false));

                skipSubtypeCheck = true;
            }
        }

        // If the LHS is a boxed primitive and the RHS is a primitive or vice-versa, skip the subtype check, since the conversion between
        // the two types is allowed as long as the appropriate locks are held on the boxed primitives, which is checked in visitIdentifier.
        if ((TypesUtils.isBoxedPrimitive(varType.getUnderlyingType()) && TypesUtils.isPrimitive(valueType.getUnderlyingType())) ||
            (TypesUtils.isPrimitive(varType.getUnderlyingType()) && TypesUtils.isBoxedPrimitive(valueType.getUnderlyingType()))) {
            skipSubtypeCheck = true;
        }

        if (!skipSubtypeCheck) {
            super.commonAssignmentCheck(varType, valueType, valueTree, errorKey, isLocalVariableAssignement);
        }
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree node, Void p) {
        checkAccessOfExpression(node);

        return super.visitMemberSelect(node, p);
    }

    private void reportFailure(/*@CompilerMessageKey*/ String messageKey,
            MethodTree overriderTree,
            AnnotatedDeclaredType enclosingType,
            AnnotatedExecutableType overridden,
            AnnotatedDeclaredType overriddenType,
            List<String> overriderLocks,
            List<String> overriddenLocks
            ) {
        // Get the type of the overriding method.
        AnnotatedExecutableType overrider =
            atypeFactory.getAnnotatedType(overriderTree);

        if (overrider.getTypeVariables().isEmpty()
                && !overridden.getTypeVariables().isEmpty()) {
            overridden = overridden.getErased();
        }
        String overriderMeth = overrider.toString();
        String overriderTyp = enclosingType.getUnderlyingType().asElement().toString();
        String overriddenMeth = overridden.toString();
        String overriddenTyp = overriddenType.getUnderlyingType().asElement().toString();

        if (overriderLocks == null || overriddenLocks == null) {
            checker.report(Result.failure(messageKey,
                    overriderMeth, overriderTyp,
                    overriddenMeth, overriddenTyp), overriderTree);
        }
        else {
            checker.report(Result.failure(messageKey,
                    overriderMeth, overriderTyp,
                    overriddenMeth, overriddenTyp,
                    overriderLocks, overriddenLocks), overriderTree);
        }
    }

    /**
     *  Ensures that subclass methods are annotated with a stronger or equally strong side effect annotation
     *  than the parent class method.
     */
    @Override
    protected boolean checkOverride(MethodTree overriderTree,
            AnnotatedDeclaredType enclosingType,
            AnnotatedExecutableType overridden,
            AnnotatedDeclaredType overriddenType,
            Void p) {

        boolean isValid = true;

        SideEffectAnnotation seaOfOverriderMethod = atypeFactory.methodSideEffectAnnotation(TreeUtils.elementFromDeclaration(overriderTree), false);
        SideEffectAnnotation seaOfOverridenMethod = atypeFactory.methodSideEffectAnnotation(overridden.getElement(), false);

        if (atypeFactory.isWeaker(seaOfOverriderMethod, seaOfOverridenMethod)) {
            isValid = false;
            reportFailure("override.sideeffect.invalid", overriderTree, enclosingType, overridden, overriddenType, null, null);
        }

        return super.checkOverride(overriderTree, enclosingType, overridden, overriddenType, p) && isValid;
    }

    // Check the access of the expression of an ArrayAccessTree or
    // a MemberSelectTree, both of which happen to implement ExpressionTree.
    // The 'Expression' in checkAccessOfExpression is not the same as that in
    // 'Expression'Tree - the naming is a coincidence.
    protected void checkAccessOfExpression(ExpressionTree tree) {
        boolean isMethodCall = false;

        Kind treeKind = tree.getKind();
        assert(treeKind == Kind.ARRAY_ACCESS ||
               treeKind == Kind.MEMBER_SELECT ||
               treeKind == Kind.IDENTIFIER);

        if (treeKind == Kind.MEMBER_SELECT) {
            Element treeElement = TreeUtils.elementFromUse(tree);

            if (treeElement != null && treeElement.getKind() == ElementKind.METHOD) { // Method calls are not dereferences.
                isMethodCall = true;
            }
        }

        if (!isMethodCall) {
            ExpressionTree expr = null;

            switch(treeKind) {
                case ARRAY_ACCESS:
                    expr = ((ArrayAccessTree) tree).getExpression();
                    break;
                case MEMBER_SELECT:
                    expr = ((MemberSelectTree) tree).getExpression();
                    break;
                default:
                    expr = tree;
                    break;
            }

            Element invokedElement = TreeUtils.elementFromUse(expr);

            boolean boxedPrimitive = false;

            if (treeKind == Kind.IDENTIFIER) {
                TypeMirror type = InternalUtils.typeOf(expr);
                if (type != null && TypesUtils.isBoxedPrimitive(type)) {
                    boxedPrimitive = true;
                }
            }

            // Always check for accesses to identifiers representive a boxed primitive,
            // since that access may be syntactic sugar for a method call (such as boxedInt.intValue()).
            // In that case, use the ATM of the identifier directly instead of the receiver ATM.
            AnnotatedTypeMirror atmOfReceiverOrIdentifier = boxedPrimitive ?
                    atypeFactory.getAnnotatedType(tree) :
                    atypeFactory.getReceiverType(tree);

            Node node = atypeFactory.getNodeForTree(tree);

            // Is the receiver of the expression being accessed the same as the receiver of the enclosing method?
            boolean receiverIsThatOfEnclosingMethod = false;

            if (node instanceof FieldAccessNode) {
                Node receiverNode = ((FieldAccessNode) node).getReceiver();
                if (receiverNode instanceof ExplicitThisLiteralNode ||
                    receiverNode instanceof ImplicitThisLiteralNode ||
                    receiverNode instanceof ThisLiteralNode) {
                    receiverIsThatOfEnclosingMethod = true;
                }
            }

            if (expr != null && invokedElement != null && atmOfReceiverOrIdentifier != null) {
                AnnotationMirror gb = atmOfReceiverOrIdentifier.getAnnotationInHierarchy(GUARDEDBYINACCESSIBLE);
                if (gb != null) {
                    if (AnnotationUtils.areSameByClass( gb, checkerGuardedByClass) ||
                        AnnotationUtils.areSameByClass( gb, javaxGuardedByClass) ||
                        AnnotationUtils.areSameByClass( gb, jcipGuardedByClass)) {
                        // It is critical that if receiverIsThatOfEnclosingMethod is true,
                        // generatePreconditionsBasedOnGuards translate the expression
                        // "itself" to "this". That's because right now we know that, since
                        // we are dealing with the receiver of the method, "itself" corresponds
                        // to "this". However once checkPreconditions is called, that
                        // knowledge is lost and it will regards "itself" as referring to
                        // the variable the precondition we are about to add is attached to.

                        checkPreconditions(expr, invokedElement, expr.getKind() == Tree.Kind.METHOD_INVOCATION,
                            generatePreconditionsBasedOnGuards(atmOfReceiverOrIdentifier,
                                    receiverIsThatOfEnclosingMethod /* see comment above */));
                    } else if (AnnotationUtils.areSameByClass(gb, checkerGuardSatisfiedClass)){
                        // Can always dereference if type is @GuardSatisfied
                    } else {
                        // Can never dereference for any other types in the @GuardedBy hierarchy
                        String annotationName = gb.toString();
                        annotationName = annotationName.substring(annotationName.lastIndexOf('.') + 1 /* +1 to skip the last . as well */);
                        checker.report(Result.failure(
                                "cannot.dereference",
                                tree.toString(),
                                annotationName), tree);
                        return;
                    }
                }
            }
        }
    }

    @Override
    public Void visitArrayAccess(ArrayAccessTree node, Void p) {
        checkAccessOfExpression(node);

        return super.visitArrayAccess(node, p);
    }

    /**
     * Whether to skip a contract check based on whether the @GuardedBy
     * expression {@code expr} is valid for the tree {@code tree}
     * under the context {@code flowExprContext}
     * if the current path is within the expression
     * of a synchronized block (e.g. bar in
     * synchronized(bar) { ... }
     *
     *  @param tree The tree that is @GuardedBy.
     *  @param expr The expression of the @GuardedBy annotation.
     *  @param flowExprContext The current context.
     *
     *  @return Whether to skip the contract check.
     */
    @Override
    protected boolean skipContractCheck(Tree tree, FlowExpressions.Receiver expr, FlowExpressionContext flowExprContext) {
        String fieldName = null;

        try {

            Node nodeNode = atypeFactory.getNodeForTree(tree);

            if (nodeNode instanceof FieldAccessNode) {

                fieldName = ((FieldAccessNode) nodeNode).getFieldName();

                if (fieldName != null) {
                    FlowExpressions.Receiver fieldExpr = FlowExpressionParseUtil.parse(fieldName,
                            flowExprContext, getCurrentPath());

                    if (fieldExpr.equals(expr)) {
                        // Avoid issuing warnings when accessing the field that is guarding the receiver.
                        // e.g. avoid issuing a warning when accessing bar below:
                        // void foo(@GuardedBy("bar") myClass this) { synchronized(bar) { ... }}

                        // Cover only the most common case: synchronized(variableName).
                        // If the expression in the synchronized statement is more complex,
                        // we do want a warning to be issued so the user can take a closer look
                        // and see if the variable is safe to be used this way.

                        TreePath path = getCurrentPath().getParentPath();

                        if (path != null) {
                            path = path.getParentPath();

                            if (path != null && path.getLeaf().getKind() == Tree.Kind.SYNCHRONIZED) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (FlowExpressionParseException e) {
            checker.report(e.getResult(), tree);
        }

        return false;
    }

    @Override
    public boolean isValidUse(AnnotatedDeclaredType declarationType,
            AnnotatedDeclaredType useType, Tree tree) {
        declarationType.replaceAnnotation(GUARDEDBY);
        useType.replaceAnnotation(GUARDEDBY);

        return super.isValidUse(declarationType, useType, tree);
    }

    // When visiting a method invocation, issue an error if the side effect annotation
    // on the called method causes the side effect guarantee of the enclosing method
    // to be violated. For example, a method annotated with @ReleasesNoLocks may not
    // call a method annotated with @MayReleaseLocks.
    // Also check that matching @GuardSatisfied(index) on a method's formal return type/receiver/parameters matches
    // those in corresponding locations on the method call site.
    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void p) {

        SideEffectAnnotation seaOfInvokedMethod = atypeFactory.methodSideEffectAnnotation(TreeUtils.elementFromUse(node), false);

        MethodTree enclosingMethod = TreeUtils.enclosingMethod(atypeFactory.getPath(node));

        ExecutableElement methodElement = null;
        if (enclosingMethod != null) {
            methodElement = TreeUtils.elementFromDeclaration(enclosingMethod);
        }

        SideEffectAnnotation seaOfContainingMethod = atypeFactory.methodSideEffectAnnotation(methodElement, false);

        if (atypeFactory.isWeaker(seaOfInvokedMethod, seaOfContainingMethod)) {
            checker.report(Result.failure(
                    "method.guarantee.violated",
                    methodElement.toString(),
                    TreeUtils.elementFromUse(node).toString()), node);
        }

        // Check that matching @GuardSatisfied(index) on a method's formal return type/receiver/parameters matches
        // those in corresponding locations on the method call site.

        Pair<AnnotatedExecutableType, List<AnnotatedTypeMirror>> mfuPair = atypeFactory.methodFromUse(node);
        AnnotatedExecutableType invokedMethod = mfuPair.first;

        List<AnnotatedTypeMirror> requiredArgs =
            AnnotatedTypes.expandVarArgs(atypeFactory, invokedMethod, node.getArguments());

        // Index on @GuardSatisfied at each location. -1 when no @GuardSatisfied annotation was present.
        // The first two elements of the array are reserved for the return type and the receiver.
        int guardSatisfiedIndex[] = new int[requiredArgs.size() + 2]; // + 2 for the return type and receiver parameter type

        // Retrieve return types from method definition and method call

        guardSatisfiedIndex[0] = -1;

        AnnotatedTypeMirror methodDefinitionReturn = null;
        AnnotatedTypeMirror methodCallReturn = null;

        if (invokedMethod.getElement().getKind() != ElementKind.CONSTRUCTOR) {
            methodDefinitionReturn = invokedMethod.getReturnType().getErased();
            if (methodDefinitionReturn != null && methodDefinitionReturn.hasAnnotation(checkerGuardSatisfiedClass)) {
                guardSatisfiedIndex[0] = AnnotationUtils.
                        getElementValue(methodDefinitionReturn.getAnnotation(checkerGuardSatisfiedClass), "value", Integer.class, true);
                methodCallReturn = atypeFactory.getAnnotatedType(node);
            }
        }

        // Retrieve receiver types from method definition and method call

        guardSatisfiedIndex[1] = -1;

        AnnotatedTypeMirror methodDefinitionReceiver = null;
        AnnotatedTypeMirror methodCallReceiver = null;

        ExecutableElement invokedMethodElement = invokedMethod.getElement();
        if (!ElementUtils.isStatic(invokedMethodElement) && !TreeUtils.isSuperCall(node)) {
            if (invokedMethod.getElement().getKind() != ElementKind.CONSTRUCTOR) {
                methodDefinitionReceiver = invokedMethod.getReceiverType().getErased();
                if (methodDefinitionReceiver != null && methodDefinitionReceiver.hasAnnotation(checkerGuardSatisfiedClass)) {
                    guardSatisfiedIndex[1] = AnnotationUtils.
                            getElementValue(methodDefinitionReceiver.getAnnotation(checkerGuardSatisfiedClass), "value", Integer.class, true);
                    methodCallReceiver = atypeFactory.getReceiverType(node);
                }
            }
        }

        // Retrieve formal parameter types from the method definition

        for (int i = 0; i < requiredArgs.size(); i++) {
            guardSatisfiedIndex[i+2] = -1;

            AnnotatedTypeMirror arg = requiredArgs.get(i);

            if (arg.hasAnnotation(checkerGuardSatisfiedClass)) {
                guardSatisfiedIndex[i+2] = AnnotationUtils.getElementValue(arg.getAnnotation(checkerGuardSatisfiedClass), "value", Integer.class, true);
            }
        }

        // Combine all of the actual parameters into one list of AnnotationMirrors

        ArrayList<AnnotationMirror> passedArgAnnotations = new ArrayList<AnnotationMirror>(guardSatisfiedIndex.length); // Not necessary to pass guardSatisfiedIndex.length, but it is known, so why not.
        passedArgAnnotations.add(methodCallReturn == null ? null : methodCallReturn.getAnnotationInHierarchy(GUARDEDBYINACCESSIBLE));
        passedArgAnnotations.add(methodCallReceiver == null ? null : methodCallReceiver.getAnnotationInHierarchy(GUARDEDBYINACCESSIBLE));
        for(ExpressionTree tree : node.getArguments()) {
            passedArgAnnotations.add(atypeFactory.getAnnotatedType(tree).getAnnotationInHierarchy(GUARDEDBYINACCESSIBLE));
        }

        // Perform the validity check and issue an error if not valid.

        for (int i = 0; i < guardSatisfiedIndex.length; i++) {
            if (guardSatisfiedIndex[i] != -1) {
                for (int j = i + 1; j < guardSatisfiedIndex.length; j++) {
                    if (guardSatisfiedIndex[i] != -1 &&
                        guardSatisfiedIndex[i] == guardSatisfiedIndex[j]) {
                        // The @GuardedBy annotations must be identical on the corresponding actual parameters.
                        AnnotationMirror anno1 = passedArgAnnotations.get(i);
                        AnnotationMirror anno2 = passedArgAnnotations.get(j);
                        if (anno1 != null && anno2 != null) {
                            if (!atypeFactory.getQualifierHierarchy().isSubtype(anno1, anno2) ||
                                !atypeFactory.getQualifierHierarchy().isSubtype(anno2, anno1)) {
                                // TODO: allow these strings to be localized

                                String formalParam1 = null;

                                if (i == 0) {
                                    formalParam1 = "The return type";
                                } else if (i == 1){
                                    formalParam1 = "The receiver type";
                                } else {
                                    formalParam1 = "Parameter #" + (i-1); // -1, not -2, so the index is 1-based
                                }

                                String formalParam2 = null;

                                if (j == 1){
                                    formalParam2 = "the receiver type";
                                } else {
                                    formalParam2 = "parameter #" + (j-1); // -1, not -2, so the index is 1-based
                                }

                                checker.report(Result.failure(
                                        "guardsatisfied.parameters.must.match",
                                        formalParam1, formalParam2, invokedMethod.toString(), guardSatisfiedIndex[i], anno1, anno2), node);
                            }
                        }
                    }
                }
            }
        }

        return super.visitMethodInvocation(node, p);
    }

    // When visiting a synchronized block, issue an error if the expression
    // has a type that implements the java.util.concurrent.locks.Lock inteface.
    @Override
    public Void visitSynchronized(SynchronizedTree node, Void p) {
        ProcessingEnvironment processingEnvironment = checker.getProcessingEnvironment();

        javax.lang.model.util.Types types = processingEnvironment.getTypeUtils();

        TypeMirror lockInterfaceTypeMirror = TypesUtils.typeFromClass(types, processingEnvironment.getElementUtils(), Lock.class);

        TypeMirror expressionType = types.erasure(atypeFactory.getAnnotatedType(node.getExpression()).getUnderlyingType());

        if (types.isSubtype(expressionType, lockInterfaceTypeMirror)) {
            checker.report(Result.failure(
                    "explicit.lock.synchronized"), node);
        }

        return super.visitSynchronized(node, p);
    }

    @Override
    public Void visitIdentifier(IdentifierTree node, Void p) {

        checkAccessOfExpression(node);

        return super.visitIdentifier(node, p);
    }

    @Override // Same contents as super method except for the "if (nodeNode instanceof ExplicitThisLiteralNode ..." block.
    protected void checkPreconditions(Tree tree,
            Element invokedElement, boolean methodCall, Set<Pair<String, String>> additionalPreconditions) {
        Set<Pair<String, String>> preconditions = invokedElement == null ?
                new HashSet<Pair<String, String>>() :
                contractsUtils.getPreconditions(invokedElement);

        if (additionalPreconditions != null) {
            preconditions.addAll(additionalPreconditions);
        }

        FlowExpressionContext flowExprContext = null;

        for (Pair<String, String> p : preconditions) {
            String expression = p.first;
            AnnotationMirror anno = AnnotationUtils.fromName(elements, p.second);

            // Only check if the precondition concerns this checker
            if (!atypeFactory.isSupportedQualifier(anno)) {
                return;
            }

            Node nodeNode = atypeFactory.getNodeForTree(tree);

            if (flowExprContext == null) {
                if (methodCall) {
                    flowExprContext = FlowExpressionParseUtil
                            .buildFlowExprContextForUse(
                                    (MethodInvocationNode) nodeNode, checker.getContext());
                }
                else if (nodeNode instanceof FieldAccessNode) {
                    // Adapted from FlowExpressionParseUtil.buildFlowExprContextForUse

                    Receiver internalReceiver = FlowExpressions.internalReprOf(atypeFactory,
                        ((FieldAccessNode) nodeNode).getReceiver());

                    flowExprContext = new FlowExpressionContext(
                            internalReceiver, null, checker.getContext());
                }
                else if (nodeNode instanceof LocalVariableNode) {
                    // Adapted from org.checkerframework.dataflow.cfg.CFGBuilder.CFGTranslationPhaseOne.visitVariable

                    ClassTree enclosingClass = TreeUtils
                            .enclosingClass(getCurrentPath());
                    TypeElement classElem = TreeUtils
                            .elementFromDeclaration(enclosingClass);
                    Node receiver = new ImplicitThisLiteralNode(classElem.asType());

                    Receiver internalReceiver = FlowExpressions.internalReprOf(atypeFactory,
                            receiver);

                    flowExprContext = new FlowExpressionContext(
                            internalReceiver, null, checker.getContext());
                }
                else if (nodeNode instanceof ArrayAccessNode) {
                    // Adapted from FlowExpressionParseUtil.buildFlowExprContextForUse

                    Receiver internalReceiver = FlowExpressions.internalReprOfArrayAccess(atypeFactory,
                        (ArrayAccessNode) nodeNode);

                    flowExprContext = new FlowExpressionContext(
                            internalReceiver, null, checker.getContext());
                }
                else if (nodeNode instanceof ExplicitThisLiteralNode ||
                         nodeNode instanceof ImplicitThisLiteralNode ||
                         nodeNode instanceof ThisLiteralNode) {
                   Receiver internalReceiver = FlowExpressions.internalReprOf(atypeFactory, nodeNode, false);

                   flowExprContext = new FlowExpressionContext(
                           internalReceiver, null, checker.getContext());
                }
            }

            if (flowExprContext != null) {
                FlowExpressions.Receiver expr = null;
                try {
                    CFAbstractStore<?, ?> store = atypeFactory.getStoreBefore(tree);

                    String s = expression.trim();
                    Pattern selfPattern = Pattern.compile("^(this)$");
                    Matcher selfMatcher = selfPattern.matcher(s);
                    if (selfMatcher.matches()) {
                        s = flowExprContext.receiver.toString(); // it is possible that s == "this" after this call
                    }

                    // Try local variables first
                    CFAbstractValue<?> value = store.getValueOfLocalVariableByName(s);

                    if (value == null) { // Not a recognized local variable
                        expr = FlowExpressionParseUtil.parse(expression,
                                flowExprContext, getCurrentPath());

                        if (expr == null) {
                            // TODO: Wrap the following 'itself' handling logic into a method that calls FlowExpressionParseUtil.parse

                            /** Matches 'itself' - it refers to the variable that is annotated, which is different from 'this' */
                            Pattern itselfPattern = Pattern.compile("^itself$");
                            Matcher itselfMatcher = itselfPattern.matcher(expression.trim());

                            if (itselfMatcher.matches()) { // There is no variable, class, etc. named "itself"
                                expr = FlowExpressions.internalReprOf(atypeFactory,
                                        nodeNode);
                            }
                        }

                        value = store.getValue(expr);
                    }

                    AnnotationMirror inferredAnno = value == null ? null : value
                            .getType().getAnnotationInHierarchy(anno);
                    if (!skipContractCheck(tree, expr, flowExprContext) &&
                        !checkContract(expr, anno, inferredAnno, store)) {

                        checker.report(Result.failure(
                                methodCall ? "contracts.precondition.not.satisfied" : "contracts.precondition.not.satisfied.field",
                                tree.toString(),
                                expr == null ? expression : expr.toString()), tree);
                    }
                } catch (FlowExpressionParseException e) {
                    // errors are reported at declaration site
                }
            }
        }
    }
}
