package org.checkerframework.checker.lock;

/*>>>
import org.checkerframework.checker.interning.qual.*;
*/

import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.flow.CFAbstractAnalysis;
import org.checkerframework.framework.flow.CFValue;
import org.checkerframework.checker.lock.qual.GuardSatisfied;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.lock.qual.Holding;
import org.checkerframework.checker.lock.qual.LockHeld;
import org.checkerframework.checker.lock.qual.LockPossiblyHeld;
import org.checkerframework.checker.lock.qual.LockingFree;
import org.checkerframework.checker.lock.qual.MayReleaseLocks;
import org.checkerframework.checker.lock.qual.ReleasesNoLocks;
import org.checkerframework.dataflow.qual.Pure;
import org.checkerframework.dataflow.qual.SideEffectFree;
import org.checkerframework.framework.type.*;
import org.checkerframework.framework.util.MultiGraphQualifierHierarchy;
import org.checkerframework.framework.util.MultiGraphQualifierHierarchy.MultiGraphFactory;
import org.checkerframework.framework.util.defaults.QualifierDefaults;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.Pair;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;

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
    protected final AnnotationMirror LOCKHELD, LOCKPOSSIBLYHELD, SIDEEFFECTFREE, GUARDEDBY, JCIPGUARDEDBY, JAVAXGUARDEDBY, GUARDSATISFIED;

    public LockAnnotatedTypeFactory(BaseTypeChecker checker) {
        super(checker, true);

        LOCKHELD = AnnotationUtils.fromClass(elements, LockHeld.class);
        LOCKPOSSIBLYHELD = AnnotationUtils.fromClass(elements, LockPossiblyHeld.class);
        SIDEEFFECTFREE = AnnotationUtils.fromClass(elements, SideEffectFree.class);
        GUARDEDBY = AnnotationUtils.fromClass(elements, GuardedBy.class);
        JCIPGUARDEDBY = AnnotationUtils.fromClass(elements, net.jcip.annotations.GuardedBy.class);
        JAVAXGUARDEDBY = AnnotationUtils.fromClass(elements, javax.annotation.concurrent.GuardedBy.class);
        GUARDSATISFIED = AnnotationUtils.fromClass(elements, GuardSatisfied.class);

        addAliasedAnnotation(javax.annotation.concurrent.GuardedBy.class, GUARDEDBY);
        addAliasedAnnotation(net.jcip.annotations.GuardedBy.class, GUARDEDBY);

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
    public QualifierHierarchy createQualifierHierarchy(MultiGraphFactory factory) {
        return new LockQualifierHierarchy(factory);
    }

    @Override
    protected LockAnalysis createFlowAnalysis(List<Pair<VariableElement, CFValue>> fieldValues) {
        return new LockAnalysis(checker, this, fieldValues);
    }

    @Override
    public LockTransfer createFlowTransferFunction(CFAbstractAnalysis<CFValue, LockStore, LockTransfer> analysis) {
        return new LockTransfer((LockAnalysis) analysis,(LockChecker)this.checker);
    }

    protected AnnotationMirror getDeclAnnotationNoAliases(Element elt,
            Class<? extends Annotation> anno) {
        String annoName = anno.getCanonicalName().intern();
        return getDeclAnnotation(elt, annoName, false);
    }

    class LockQualifierHierarchy extends MultiGraphQualifierHierarchy {

        public LockQualifierHierarchy(MultiGraphFactory f) {
            super(f, LOCKHELD);
        }

        private boolean isGuardedBy(AnnotationMirror am) {
            return AnnotationUtils.areSameIgnoringValues(am, GUARDEDBY) ||
                   AnnotationUtils.areSameIgnoringValues(am, JAVAXGUARDEDBY) ||
                   AnnotationUtils.areSameIgnoringValues(am, JCIPGUARDEDBY);
        }
        
        // TODO: it would be better to override AnnotationMirror.equals instead of needing
        // this method, but that is not convenient given the current structure of the code.
        @Override
        protected boolean annotationMirrorsAreEqual(AnnotationMirror a1, AnnotationMirror a2) {
            return (isGuardedBy(a1) && isGuardedBy(a2)) || super.annotationMirrorsAreEqual(a1, a2);
        }

        @Override
        public boolean isSubtype(AnnotationMirror rhs, AnnotationMirror lhs) {

            boolean lhsIsGuardedBy = isGuardedBy(lhs);
            boolean rhsIsGuardedBy = isGuardedBy(rhs);

            if (lhsIsGuardedBy && rhsIsGuardedBy) {
                // Two @GuardedBy annotations are considered subtypes of each other if and only if their values match exactly.

                List<String> lhsValues =
                    AnnotationUtils.getElementValueArray(lhs, "value", String.class, true);
                List<String> rhsValues =
                    AnnotationUtils.getElementValueArray(rhs, "value", String.class, true);

                return rhsValues.containsAll(lhsValues) && lhsValues.containsAll(rhsValues);
            }

            // Remove values from @GuardedBy annotations (and use the Checker Framework's GuardedBy annotation, not JCIP's or Javax's)
            // for further subtype checking.

            return super.isSubtype(rhsIsGuardedBy ? GUARDEDBY : rhs, lhsIsGuardedBy ? GUARDEDBY : lhs);
        }
    }

    // The side effect annotations processed by the Lock Checker.
    enum SideEffectAnnotation {
        MAYRELEASELOCKS,
        RELEASESNOLOCKS,
        LOCKINGFREE,
        SIDEEFFECTFREE,
        PURE
    }
    
    /**
     * Given side effect annotations a and b, returns true if a
     * is a strictly weaker side effect annotation than b.
     */
    boolean isWeaker(SideEffectAnnotation a, SideEffectAnnotation b) {
        boolean weaker = false;
        
        switch(b) {
            case MAYRELEASELOCKS:
                break;
            case RELEASESNOLOCKS:
                if (a == SideEffectAnnotation.MAYRELEASELOCKS) {
                    weaker = true;
                }
                break;
            case LOCKINGFREE:
                switch(a) {
                    case MAYRELEASELOCKS:
                    case RELEASESNOLOCKS:
                        weaker = true;
                    default:
                }
                break;
            case SIDEEFFECTFREE:
                switch(a) {
                    case MAYRELEASELOCKS:
                    case RELEASESNOLOCKS:
                    case LOCKINGFREE:
                        weaker = true;
                    default:
                }
                break;
            case PURE:
                switch(a) {
                    case MAYRELEASELOCKS:
                    case RELEASESNOLOCKS:
                    case LOCKINGFREE:
                    case SIDEEFFECTFREE:
                        weaker = true;
                    default:
                }
                break;
        }
        
        return weaker;
    }

    // Indicates which side effect annotation is present on the given method.
    // If more than one annotation is present, this method issues an error (if issueErrorIfMoreThanOnePresent is true)
    // and returns the annotation providing the weakest guarantee.
    // If no annotation is present, return RELEASESNOLOCKS as the default, and MAYRELEASELOCKS
    // as the default for unannotated code.
    SideEffectAnnotation methodSideEffectAnnotation(Element element, boolean issueErrorIfMoreThanOnePresent) {
        if (element != null) {
            final int countSideEffectAnnotations = SideEffectAnnotation.values().length;

            boolean[] sideEffectAnnotationPresent = new boolean[countSideEffectAnnotations];

            // It is important that these are ordered from weaker to stronger. A for loop below relies on this.
            sideEffectAnnotationPresent[0] = getDeclAnnotationNoAliases(element, MayReleaseLocks.class) != null;
            sideEffectAnnotationPresent[1] = getDeclAnnotationNoAliases(element, ReleasesNoLocks.class) != null;
            sideEffectAnnotationPresent[2] = getDeclAnnotationNoAliases(element, LockingFree.class) != null;
            sideEffectAnnotationPresent[3] = getDeclAnnotationNoAliases(element, SideEffectFree.class) != null;
            sideEffectAnnotationPresent[4] = getDeclAnnotationNoAliases(element, Pure.class) != null;
            assert(countSideEffectAnnotations == 5); // If this assertion fails, the assignments above need to be updated.

            int count = 0;

            for(int i = 0; i < countSideEffectAnnotations; i++) {
                if (sideEffectAnnotationPresent[i]) {
                    count++;
                }
            }

            if (count == 0) {
                return defaults.applyUnannotatedDefaults(element) ?
                    SideEffectAnnotation.MAYRELEASELOCKS :
                    SideEffectAnnotation.RELEASESNOLOCKS;
            }

            if (count > 1 && issueErrorIfMoreThanOnePresent) {
                // TODO: Turn on after figuring out how this interacts with inherited annotations.
                // checker.report(Result.failure("multiple.sideeffect.annotations"), element);
            }

            // If at least one side effect annotation was found, return the weakest.
            for(int i = 0; i < countSideEffectAnnotations; i++) {
                if (sideEffectAnnotationPresent[i]) {
                    return SideEffectAnnotation.values()[i];
                }
            }
        }

        // When there is not enough information to determine the correct side effect annotation,
        // return the weakest one.
        return SideEffectAnnotation.MAYRELEASELOCKS;
    }

}
