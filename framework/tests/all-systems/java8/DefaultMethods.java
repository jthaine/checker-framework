import org.checkerframework.checker.lock.qual.GuardedBy;

interface DefaultMethods {

    // Test that abstract methods are still ignored.
    void abstractMethod();

    default String method(@GuardedBy({}) String s) {
        return s.toString();
    }
}

interface DefaultMethods2 extends DefaultMethods {

    @Override
    default String method(@GuardedBy({}) String s) {
        return s;
    }
}
