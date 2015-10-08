import org.checkerframework.checker.lock.qual.GuardSatisfied;

interface Function<T, R> {
    R apply(@GuardSatisfied T t);
}
@SuppressWarnings("javari")
class FromByteCode {
    Function<String, String> f1 = String::toString;
}
