import org.checkerframework.checker.lock.qual.GuardSatisfied;

@SuppressWarnings("javari")
class InferAndIntersection {

    <T> void toInfer(Iterable<T> t) {}

    <U extends Object & Iterable<Object>> void context(@GuardSatisfied U u) {
        toInfer(u);
    }
}
