package il.co.codeguru.corewars8086.jsadd;

public class AtomicReference<T> {
    public T ref;
    public AtomicReference(T r) {
        ref = r;
    }
    public T get() {
        return ref;
    }
    public void compareAndSet(T a, T b) {
        if (ref == a)
            ref = b;
    }
}