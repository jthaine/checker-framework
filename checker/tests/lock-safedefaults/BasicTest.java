import org.checkerframework.checker.lock.qual.*;
import org.checkerframework.framework.qual.*;
import java.util.concurrent.locks.*;

public class BasicTest {
    class MyClass {
        public Object field;
    }

    Object myUnannotatedMethod(Object param) {
        return param;
    }

    void myUnannotatedMethod2() {
    }

    @AnnotatedFor("lock")
    @GuardSatisfied Object myAnnotatedMethod(Object param) {
        return param;
    }

    @AnnotatedFor("lock")
    void myAnnotatedMethod2() {
    }

    ReentrantLock lockField = new ReentrantLock();
    @GuardedBy("lockField") MyClass m;

    Object o1 = new Object(), p1;

    @AnnotatedFor("lock")
    @MayReleaseLocks
    void testFields() {
        //:: error: (assignment.type.incompatible) :: error: (argument.type.incompatible)
        p1 = myUnannotatedMethod(o1);
        // Ignore the following error as it is expected.
        //:: error: (guardsatisfied.parameters.must.match)
        myAnnotatedMethod(o1);

        // Now test that an unannotated method behaves as if it's annotated with @MayReleaseLocks
        lockField.lock();
        myAnnotatedMethod2();
        m.field.toString();
        myUnannotatedMethod2();
        //:: error: (contracts.precondition.not.satisfied.field)
        m.field.toString();
    }

    @AnnotatedFor("lock")
    @MayReleaseLocks
    void testLocalVariables() {
        Object o2 = new Object(), p2;
        //:: error: (assignment.type.incompatible) :: error: (argument.type.incompatible)
        p2 = myUnannotatedMethod(o2);
        // Ignore the following error as it is expected.
        //:: error: (guardsatisfied.parameters.must.match)
        myAnnotatedMethod(o2);

        // Now test that an unannotated method behaves as if it's annotated with @MayReleaseLocks
        ReentrantLock lock = new ReentrantLock();
        @GuardedBy("lock") MyClass q = new MyClass();
        lock.lock();
        myAnnotatedMethod2();
        q.field.toString();
        myUnannotatedMethod2();
        //:: error: (contracts.precondition.not.satisfied.field)
        q.field.toString();
    }
}
