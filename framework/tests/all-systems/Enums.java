import java.lang.annotation.ElementType;
import org.checkerframework.checker.lock.qual.GuardSatisfied;

class MyEnumSet<E extends Enum<E>> {}

class Enumeration {
  public enum VarFlags {IS_PARAM, NO_DUPS};
  public MyEnumSet<VarFlags> var_flags = new MyEnumSet<VarFlags>();

  VarFlags f1 = VarFlags.IS_PARAM;

  void foo1(MyEnumSet<VarFlags> p) {}
  void foo2(MyEnumSet<ElementType> p) {}

  <E extends Enum<E>> void mtv(Class<E> p) {}

  <T extends Object> @GuardSatisfied T checkNotNull(@GuardSatisfied T ref) { return ref; }

  <T extends Object, S extends Object> @GuardSatisfied T checkNotNull2(@GuardSatisfied T ref, @GuardSatisfied S ref2) { return ref; }

  class Test<T extends Enum<T>> {
    void m(Class<T> p) {
      checkNotNull(p);
    }

    public <SSS extends Object> @GuardSatisfied SSS firstNonNull(@GuardSatisfied SSS first, @GuardSatisfied SSS second) {
      @SuppressWarnings("nullness:known.nonnull")
      @GuardSatisfied SSS res = first != null ? first : checkNotNull(second);
      return res;
    }
  }

  class Unbound<X extends Object> {}
  class Test2<T extends Unbound<S>, S extends Unbound<T>> {
    void m(Class<T> p, Class<S> q) {
      checkNotNull2(p, q);
    }
  }
}
