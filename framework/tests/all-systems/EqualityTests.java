import org.checkerframework.checker.lock.qual.GuardedBy;

public class EqualityTests {
    @SuppressWarnings({"Interning", "lock:cast.unsafe"}) // the Interning checker correctly issues an error below, but we would like to keep this test in all-systems.
    public boolean compareLongs(Long v1, Long v2) {
        // This expression used to cause an assertion
        // failure in GLB computation.
        return (@GuardedBy({}) boolean) !(((v1 == 0) || (v2 == 0)) && (v1 != v2));
    }

    public int charEquals(boolean cond) {
        char result = 'F';
        if (cond) {
            result = 'T';
        }

        if (result == 'T') {
            return 1;
        } else {
            assert result == '?';
        }
        return 10;
    }
}


