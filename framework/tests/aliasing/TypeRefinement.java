import org.checkerframework.common.aliasing.qual.*;
class TypeRefinement {
    class MyClass {}

/**
 * Type refinement is treated in the usual way, except that at
 * (pseudo-)assignments the RHS may lose its type refinement, before the LHS is
 * type-refined.
 *
 * The RHS always loses its type refinement (it is widened to @MaybeAliased, and
 * its declared type must have been @MaybeAliased) except in the following
 * cases:
 * 1.The RHS is a fresh expression.
 * 2.The LHS is a @NonLeaked formal parameter and the RHS is an
 * argument in a method call or constructor invocation.
 * 3.The LHS is a @LeakedToResult formal parameter, the RHS is an
 * argument in a method call or constructor invocation, and the method's return
 * value is discarded.
 */

    // Test cases for the Aliasing type refinement cases below.
    // One method for each exception case. The usual case is tested in every method too.
    // As annotated in stubfile.astub, MyClass() has type @Unique @NonLeaked.

    void rule1() {
        MyClass unique = new MyClass();
        // unique is refined to @Unique here, according to the definition.
        isUnique(unique);

        MyClass notUnique = unique; //unique loses its refinement.

        //:: error: (argument.type.incompatible)
        isUnique(unique);
        //:: error: (argument.type.incompatible)
        isUnique(notUnique);

    }

    void rule2() {
        @Unique MyClass unique = new MyClass();

        isUnique(unique);
        nonLeaked(unique);
        isUnique(unique);

        leaked(unique);
        //:: error: (argument.type.incompatible)
        isUnique(unique);
    }

    void rule3() {
        @Unique MyClass unique = new MyClass();
        isUnique(unique);
        leakedToResult(unique);
        isUnique(unique);

        MyClass notUnique = leakedToResult(unique);
        //:: error: (argument.type.incompatible)
        isUnique(unique);
    }

    void nonLeaked(@NonLeaked MyClass s) {}
    void leaked(MyClass s) {}
    MyClass leakedToResult(@LeakedToResult MyClass s) {
        return s;
    }

    void isUnique(@NonLeaked @Unique MyClass s) {} //@NonLeaked so it doesn't refine the type of the argument.

}
