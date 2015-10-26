import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;
import org.checkerframework.checker.lock.qual.*;
import org.checkerframework.dataflow.qual.SideEffectFree;

public class TestOperations {

  class MyClass {
     Object field = new Object();
     @LockingFree
     Object method(@GuardSatisfied MyClass this){return new Object();}
     void method2(){}
  }

  Object lock1, lock2;

  void method() {
     @GuardedBy("lock1") String s1 = "a";
     @GuardedBy("lock2") String s2 = "b";

     //:: error: (contracts.precondition.not.satisfied.field)
     @GuardedBy({}) String s3 = s1 + s2;
     //:: error: (contracts.precondition.not.satisfied.field)
     @GuardedBy({}) String s4 = s1 + s2 + s2;
     synchronized(lock1) {
         //:: error: (contracts.precondition.not.satisfied.field)
         @GuardedBy({}) String s5 = s1 + s2;
         //:: error: (contracts.precondition.not.satisfied.field)
         @GuardedBy({}) String s6 = s1 + s2 + s2;
         synchronized(lock2) {
             @GuardedBy({}) String s7 = s1 + s2;
             @GuardedBy({}) String s8 = s1 + s2 + s2;
         }
     }

  }
}
