import org.checkerframework.checker.lock.qual.GuardedBy;

@SuppressWarnings({"interning", "oigj"})
class InferAndWildcards {
    <UUU> Class<? extends UUU> b(@GuardedBy({}) Class<UUU> clazz) {
        return clazz;
    }

    <TTT> void a(@GuardedBy({}) Class<TTT> clazz) {
        Class<? extends TTT> v = b(clazz);
    }
}
