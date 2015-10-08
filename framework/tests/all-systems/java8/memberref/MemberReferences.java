interface Supplier<R> {
    R supply();
}
interface Function<T, R> {
    R apply(T t);
}
interface Consumer<T> {
    void consume(T t);
}
interface BiFunction<T, U, R> {
    R apply(T t, U u);
}


/** super # instMethod */
//SUPER(ReferenceMode.INVOKE, false),
//@SuppressWarnings("javari")
class Super {

    @SuppressWarnings("lock")
    Object func1 (Object o) { return o; }
    <T> T func2 (T o) { return o; }

    class Sub extends Super {
        void context() {
            @SuppressWarnings("lock")
            Function<Object, Object> f1 = super::func1;
            // TODO: type argument inference
            //:: warning: (methodref.inference.unimplemented)
            Function f2 = super::func2;
            // Top level wildcards are ignored when type checking
            @SuppressWarnings("javari")
            Function<? extends String, ? extends String> f3 = super::<String>func2;
        }
    }
}
@SuppressWarnings("javari")
class SuperWithArg<U> {

    void func1 (U o) { }

    class Sub extends SuperWithArg<Number> {
        void context() {
            Consumer<Integer> f1 = super::func1;
        }
    }
}

/** Type # instMethod */
// UNBOUND(ReferenceMode.INVOKE, true),
@SuppressWarnings("javari")
class Unbound {
    <T> T func1 (T o) { return o; }

    void context() {
        @SuppressWarnings("lock")
        Function<String, String> f1 = String::toString;
        // TODO: type argument inference
        BiFunction<Unbound, String, String> f2 = Unbound::func1;
        @SuppressWarnings({"nullness:type.argument.type.incompatible", "lock"})
        BiFunction<? extends Unbound, ? super Integer, ? extends Integer> f3 = Unbound::<Integer>func1;
    }
}
@SuppressWarnings({"oigj", "javari"})
abstract class UnboundWithArg<U> {
    abstract U func1();

    void context() {
        // TODO: type argument inference
        Function<UnboundWithArg<String>, String> f1 = UnboundWithArg::func1;
        @SuppressWarnings("lock")
        Function<UnboundWithArg<String>, String> f2 = UnboundWithArg<String>::func1;
        // TODO: type argument inference
        Function<? extends UnboundWithArg<String>, String> f3 = UnboundWithArg::func1;
        @SuppressWarnings("lock")
        Function<? extends UnboundWithArg<String>, String> f4 = UnboundWithArg<String>::func1;
    }
}

/** Type # staticMethod */
// STATIC(ReferenceMode.INVOKE, false),
@SuppressWarnings("javari")
class Static {
    static <T> T func1 (T o) { return o; }
    void context() {
        // TODO: type argument inference
        Function<String, String> f1 = Static::func1;
        Function<String, String> f2 = Static::<String>func1;
    }
}

///** Expr # instMethod */
// BOUND(ReferenceMode.INVOKE, false),
@SuppressWarnings("javari")
class Bound {
    <T> T func1 (T o) { return o; }
    void context(Bound bound) {
        // TODO: type argument inference
        Function<String, String> f1 = bound::func1;
        // TODO: type argument inference
        Function<String, String> f2 = this::func1;
        Function<String, String> f3 = this::<String>func1;
        Function<? extends String, ? extends String> f4 = this::<String>func1;
    }
}
@SuppressWarnings("javari")
class BoundWithArg<U> {
    void func1 (U param) { }
    void context(BoundWithArg<Number> bound) {
        Consumer<Number> f1 = bound::func1;
        Consumer<Integer> f2 = bound::func1;
    }
}

/** Inner # new */
// IMPLICIT_INNER(ReferenceMode.NEW, false),
@SuppressWarnings("javari")
class Outer {
    void context(Outer other) {
        @SuppressWarnings("lock")
        Supplier<Inner> f1 = Inner::new;
    }
    class Inner extends Outer {

    }
}
@SuppressWarnings({"oigj", "javari"})
class OuterWithArg {
    void context() {
        // TODO: type argument inference
        Supplier<Inner<String>> f1 = Inner::new;
        @SuppressWarnings("lock")
        Supplier<? extends Inner<Number>> f2 = Inner<Number>::new;
        @SuppressWarnings("lock")
        Supplier<? extends Inner<? extends Number>> f3 = Inner<Integer>::new;

    }

    class Inner<T> extends OuterWithArg { }
}

/** Toplevel # new */
// TOPLEVEL(ReferenceMode.NEW, false),
@SuppressWarnings("javari")
class TopLevel {
    TopLevel() {}
    <T> TopLevel(T s) {}
    void context() {
        Supplier<TopLevel> f1 = TopLevel::new;
        // TODO: type argument inference
        Function<String, TopLevel> f2 = TopLevel::new;
        Function<String, TopLevel> f3 = TopLevel::<String>new;
    }
}
@SuppressWarnings("javari")
class TopLevelWithArg<T> {
    TopLevelWithArg() {}
    <U> TopLevelWithArg(U s) {}
    void context() {
        // TODO: type argument inference
        Supplier<TopLevelWithArg<String>> f1 = TopLevelWithArg::new;
        Supplier<TopLevelWithArg<String>> f2 = TopLevelWithArg<String>::new;
        Function<String, TopLevelWithArg<String>> f3 = TopLevelWithArg<String>::<String>new;
    }
}

/** ArrayType # new */
// ARRAY_CTOR(ReferenceMode.NEW, false);
@SuppressWarnings({"oigj", "javari"})
class ArrayType {
    void context() {
        @SuppressWarnings("lock")
        Function<Integer, String[]> string = String[]::new;
        Function<String[], String[]> clone = String[]::clone;
        Function<String[], String> toString = String[]::toString;

    }
}
