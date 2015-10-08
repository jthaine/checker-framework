import org.checkerframework.framework.qual.*;
import org.checkerframework.checker.lock.qual.GuardSatisfied;
import java.io.*;

// Tests related to resource variables in try-with-resources statements.

class ResourceVariables {
  void foo(InputStream arg) {
    try (@GuardSatisfied InputStream in = arg) {
    } catch (IOException e) {
    }
  }
}

