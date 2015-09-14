package org.checkerframework.checker.lock;

import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.flow.CFAbstractAnalysis;
import org.checkerframework.framework.flow.CFValue;
import org.checkerframework.checker.lock.qual.GuardSatisfied;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.lock.qual.LockHeld;
import org.checkerframework.checker.lock.qual.LockPossiblyHeld;
import org.checkerframework.checker.lock.qual.LockingFree;
import org.checkerframework.checker.lock.qual.MayReleaseLocks;
import org.checkerframework.checker.lock.qual.ReleasesNoLocks;
import org.checkerframework.dataflow.qual.SideEffectFree;
import org.checkerframework.framework.type.*;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.treeannotator.ListTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.TreeAnnotator;
import org.checkerframework.framework.type.typeannotator.ListTypeAnnotator;
import org.checkerframework.framework.type.typeannotator.TypeAnnotator;
import org.checkerframework.framework.util.MultiGraphQualifierHierarchy;
import org.checkerframework.framework.util.MultiGraphQualifierHierarchy.MultiGraphFactory;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.Pair;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.VariableElement;

import com.sun.source.tree.Tree;

/**
 * LockAnnotatedTypeFactory builds types with LockHeld and LockPossiblyHeld annotations.
 * LockHeld identifies that an object is being used as a lock and is being held when a
 * given tree is executed. LockPossiblyHeld is the default type qualifier for this
 * hierarchy and applies to all fields, local variables and parameters - hence it does
 * not convey any information other than that it is not LockHeld.
 *
 * However, there are a number of other annotations used in conjunction with these annotations
 * to enforce proper locking.
 * @checker_framework.manual #lock-checker Lock Checker
 */
public class LockAnnotatedTypeFactory
    extends GenericAnnotatedTypeFactory<CFValue, LockStore, LockTransfer, LockAnalysis> {

    /** Annotation constants */
    protected final AnnotationMirror LOCKHELD, LOCKPOSSIBLYHELD, SIDEEFFECTFREE, GUARDEDBY, GUARDSATISFIED;

    // Cache for the lock annotations
    protected final Set<Class<? extends Annotation>> lockAnnos;

    public LockAnnotatedTypeFactory(BaseTypeChecker checker, boolean useFlow) {
        super(checker, useFlow);

        LOCKHELD = AnnotationUtils.fromClass(elements, LockHeld.class);
        LOCKPOSSIBLYHELD = AnnotationUtils.fromClass(elements, LockPossiblyHeld.class);
        SIDEEFFECTFREE = AnnotationUtils.fromClass(elements, SideEffectFree.class);
        GUARDEDBY = AnnotationUtils.fromClass(elements, GuardedBy.class);
        GUARDSATISFIED = AnnotationUtils.fromClass(elements, GuardSatisfied.class);

        Set<Class<? extends Annotation>> tempLockAnnos = new HashSet<>();
        tempLockAnnos.add(LockHeld.class);
        tempLockAnnos.add(LockPossiblyHeld.class);
        lockAnnos = Collections.unmodifiableSet(tempLockAnnos);

        // This alias is only true for the Lock Checker. All other checkers must
        // ignore the @LockingFree annotation.
        addAliasedDeclAnnotation(LockingFree.class,
                SideEffectFree.class,
                AnnotationUtils.fromClass(elements, SideEffectFree.class));

        // This alias is only true for the Lock Checker. All other checkers must
        // ignore the @ReleasesNoLocks annotation.  Note that ReleasesNoLocks is
        // not truly side-effect-free even as far as the Lock Checker is concerned,
        // so there is additional handling of this annotation in the Lock Checker.
        addAliasedDeclAnnotation(ReleasesNoLocks.class,
                SideEffectFree.class,
                AnnotationUtils.fromClass(elements, SideEffectFree.class));

        postInit();
    }
    
    @Override
    protected void postInit() {
    	super.postInit();

    	addInheritedAnnotation(AnnotationUtils.fromClass(elements,
                MayReleaseLocks.class));
    	addInheritedAnnotation(AnnotationUtils.fromClass(elements,
    			ReleasesNoLocks.class));
    	addInheritedAnnotation(AnnotationUtils.fromClass(elements,
                LockingFree.class));
    }

    @Override
    public QualifierHierarchy createQualifierHierarchy(MultiGraphFactory factory) {
        return new LockQualifierHierarchy(factory);
    }

    @Override
    public TreeAnnotator createTreeAnnotator() {
        return new ListTreeAnnotator(
                super.createTreeAnnotator(),
                new LockTreeAnnotator(this)
        );
    }

    private class LockTreeAnnotator extends TreeAnnotator {
        public LockTreeAnnotator(AnnotatedTypeFactory atypeFactory) {
            super(atypeFactory);
        }
    }

    @Override
    protected LockAnalysis createFlowAnalysis(List<Pair<VariableElement, CFValue>> fieldValues) {
        return new LockAnalysis(checker, this, fieldValues);
    }

    @Override
    public LockTransfer createFlowTransferFunction(CFAbstractAnalysis<CFValue, LockStore, LockTransfer> analysis) {
        return new LockTransfer((LockAnalysis) analysis,(LockChecker)this.checker);
    }
    
    @Override
    protected TypeAnnotator createTypeAnnotator() {
        return new ListTypeAnnotator(
                new LockTypeAnnotator(this),
                super.createTypeAnnotator()
        );
    }


    private class LockTypeAnnotator extends TypeAnnotator {

    	LockTypeAnnotator(LockAnnotatedTypeFactory atypeFactory) {
            super(atypeFactory);
        }
    	
    	@Override
    	public Void visitExecutable(AnnotatedExecutableType t, Void p) {
            /*AnnotatedDeclaredType type = t.getReceiverType();
            if (type != null) {
            	if (type.getAnnotationInHierarchy(GUARDEDBY) == null) {
        			type.replaceAnnotation(GUARDSATISFIED);
            	}
            }*/

    		return super.visitExecutable(t, p);
    	}
    }

    protected AnnotatedTypeMirror getDeclaredAndDefaultedAnnotatedType(Tree tree) {
        shouldCache = false;

        AnnotatedTypeMirror type = getAnnotatedType(tree);

        shouldCache = true; // TODO: What does this do?

        return type;
    }

    /**
     * @return The list of annotations of the lock type system.
     */
    public Set<Class<? extends Annotation>> getLockAnnotations() {
        return lockAnnos;
    }

    class LockQualifierHierarchy extends MultiGraphQualifierHierarchy {

        public LockQualifierHierarchy(MultiGraphFactory f) {
            super(f, LOCKHELD);
        }

        @Override
        public boolean isSubtype(AnnotationMirror rhs, AnnotationMirror lhs) {

            boolean lhsIsGuardedBy = AnnotationUtils.areSameIgnoringValues(lhs, GUARDEDBY);
            boolean rhsIsGuardedBy = AnnotationUtils.areSameIgnoringValues(rhs, GUARDEDBY);

            if (lhsIsGuardedBy && rhsIsGuardedBy) {
                // Two @GuardedBy annotations are considered subtypes of each other if and only if their values match exactly.

                List<String> lhsValues =
                    AnnotationUtils.getElementValueArray(lhs, "value", String.class, true);
                List<String> rhsValues =
                    AnnotationUtils.getElementValueArray(rhs, "value", String.class, true);

                return rhsValues.containsAll(lhsValues) && lhsValues.containsAll(rhsValues);
            }

            // Remove values from @GuardedBy annotations for further subtype checking.

            return super.isSubtype(rhsIsGuardedBy ? GUARDEDBY : rhs, lhsIsGuardedBy ? GUARDEDBY : lhs);
        }
    }
}
