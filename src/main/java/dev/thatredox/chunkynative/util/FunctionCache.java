package dev.thatredox.chunkynative.util;

import java.lang.ref.WeakReference;
import java.util.function.Consumer;
import java.util.function.Function;

public class FunctionCache<T, R> {
    protected WeakReference<T> input;
    protected R output;

    protected final Function<T, R> function;
    protected final Consumer<R> oldValueConsumer;

    public FunctionCache(Function<T, R> function, Consumer<R> oldValueConsumer, T initial) {
        this.function = function;
        this.oldValueConsumer = oldValueConsumer;
        this.input = new WeakReference<>(initial, null);
        if (initial != null) {
            this.output = function.apply(initial);
        } else {
            this.output = null;
        }
    }

    public R apply(T input) {
        if (this.input.get() != input) {
            if (this.output != null) {
                this.oldValueConsumer.accept(this.output);
            }
            this.output = function.apply(input);
            this.input = new WeakReference<>(input, null);
        }
        return this.output;
    }
}
