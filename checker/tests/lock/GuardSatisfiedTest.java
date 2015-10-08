import org.checkerframework.checker.lock.qual.LockingFree;
import org.checkerframework.checker.lock.qual.GuardSatisfied;
import org.checkerframework.checker.lock.qual.Holding;
import org.checkerframework.checker.lock.qual.GuardedBy;

public class GuardSatisfiedTest {
   void testMethodCall(@GuardSatisfied GuardSatisfiedTest this, @GuardedBy("lock1") Object o, @GuardedBy("lock2") Object p, @GuardSatisfied Object q) {
       // Test matching parameters

       //:: error: (contracts.precondition.not.satisfied.field)
       methodToCall1(o, o);
       //:: error: (contracts.precondition.not.satisfied.field) :: error: (guardsatisfied.parameters.must.match)
       methodToCall1(o, p);
       //:: error: (contracts.precondition.not.satisfied.field)
       methodToCall1(p, p);
       synchronized(lock2) {
           //:: error: (contracts.precondition.not.satisfied.field)
           methodToCall1(o, o);
           //:: error: (guardsatisfied.parameters.must.match) :: error: (contracts.precondition.not.satisfied.field)
           methodToCall1(o, p);
           methodToCall1(p, p);
           synchronized(lock1) {
               methodToCall1(o, o);
               //:: error: (guardsatisfied.parameters.must.match)
               methodToCall1(o, p);
               methodToCall1(p, p);
           }
       }

       // Test a return type matching a parameter. The return value does not need to be captured.

       /* Temporarily disable
          methodToCall2(o);
       // TODO: we also expect contracts.precondition.not.satisfied.field
       // error guardsatisfied.parameters.must.match
       methodToCall2(p);
       synchronized(lock2) {
           methodToCall2(o);
           // error guardsatisfied.parameters.must.match
           methodToCall2(p);
       }
       */

       // Test the receiver type matching a parameter

       // TODO: these argument type incompatible errors are due to a limitation in the code - fix them.
       // TODO: we also expect contracts.precondition.not.satisfied.field
       //:: error: (argument.type.incompatible)
       methodToCall3(q);
       //  TODO expect an error contracts.precondition.not.satisfied.field - it is probably getting swallowed
       //:: error: (guardsatisfied.parameters.must.match)
       methodToCall3(p);
       synchronized(lock1) {
           //:: error: (argument.type.incompatible)
           methodToCall3(q);
           //  TODO expect an error contracts.precondition.not.satisfied.field - it is probably getting swallowed
           //:: error: (guardsatisfied.parameters.must.match)
           methodToCall3(p);
           synchronized(lock2) {
               //:: error: (argument.type.incompatible)
               methodToCall3(q);
               //:: error: (guardsatisfied.parameters.must.match)
               methodToCall3(p);
           }
       }

       // Test the return type matching the receiver type

       // Temporarily disable
       // methodToCall4();
   }

   // Test the return type NOT matching the receiver type
   /* Temporarily disable
     void testMethodCall(@GuardedBy("lock1") GuardSatisfiedTest this) {
       // TODO we also expect contracts.precondition.not.satisfied but it is getting swallowed when guardsatisfied.parameters.must.match is issued
       // error guardsatisfied.parameters.must.match
       methodToCall4();
       synchronized(lock1) {
           // error guardsatisfied.parameters.must.match
           methodToCall4();
       }
   }*/

   @GuardSatisfied Object testReturnTypesMustMatch1(@GuardSatisfied Object o) {
       return o;
   }

   @GuardSatisfied(1) Object testReturnTypesMustMatch2(@GuardSatisfied(1) Object o) {
       return o;
   }

   @GuardSatisfied(0) Object testReturnTypesMustMatch3(@GuardSatisfied(0) Object o) {
       return o;
   }

   // @GuardSatisfied is equivalent to @GuardSatisfied(0)
   @GuardSatisfied Object testReturnTypesMustMatch4(@GuardSatisfied(0) Object o) {
       return o;
   }

   @GuardSatisfied(1) Object testReturnTypesMustMatch5(@GuardSatisfied(2) Object o) {
       //:: error: (return.type.incompatible)
       return o;
   }

   @GuardSatisfied Object testReturnTypesMustMatch6(@GuardSatisfied(2) Object o) {
       //:: error: (return.type.incompatible)
       return o;
   }

   void testParamsMustMatch(@GuardSatisfied(1) Object o, @GuardSatisfied(2) Object p) {
       //:: error: (assignment.type.incompatible)
       o = p;
   }

   void methodToCall1(@GuardSatisfied(1) Object o, @GuardSatisfied(1) Object p) {
   }

   @GuardSatisfied(1) Object methodToCall2(@GuardSatisfied(1) Object o) {
       return o;
   }

   void methodToCall3(@GuardSatisfied(1) GuardSatisfiedTest this, @GuardSatisfied(1) Object o) {
   }

   @GuardSatisfied(1) Object methodToCall4(@GuardSatisfied(1) GuardSatisfiedTest this) {
       return this;
   }

   Object lock1, lock2;
}
