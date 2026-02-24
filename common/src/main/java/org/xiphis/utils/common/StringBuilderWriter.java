package org.xiphis.utils.common;

import java.io.Writer;

public class StringBuilderWriter extends Writer {
    private final StringBuilder stringBuilder = new StringBuilder();

    public StringBuilder stringBuilder() {
        return stringBuilder;
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
        stringBuilder.append(cbuf, off, len);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
}
