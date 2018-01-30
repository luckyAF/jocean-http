package org.jocean.netty.util;

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.util.CharsetUtil;

/**
 * An {@link OutputStream} which writes data to a {@link ByteBuf}.
 * <p>
 * A write operation against this stream will occur at the {@code writerIndex}
 * of its underlying buffer and the {@code writerIndex} will increase during
 * the write operation.
 * <p>
 * This stream implements {@link DataOutput} for your convenience.
 * The endianness of the stream is not always big endian but depends on
 * the endianness of the underlying buffer.
 *
 * @see ByteBufInputStream
 */
public class ByteBufArrayOutputStream extends OutputStream implements DataOutput {

    private final int _pageSize;
    private final ByteBufAllocator _allocator;
    private final List<ByteBuf> _bufs = new ArrayList<>();
//    private final int startIndex;
    private final DataOutputStream utf8out = new DataOutputStream(this);

    /**
     * Creates a new stream which writes data to the specified {@code buffer}.
     */
    public ByteBufArrayOutputStream(final ByteBufAllocator allocator, final int pageSize) {
        if (allocator == null) {
            throw new NullPointerException("allocator");
        }
        this._allocator = allocator;
        this._pageSize = pageSize;
//        startIndex = buffer.writerIndex();
    }

    /**
     * Returns the number of written bytes by this stream so far.
     */
//    public int writtenBytes() {
//        return buffer.writerIndex() - startIndex;
//    }

    private ByteBuf lastBuf() {
        if (_bufs.isEmpty()) {
            return addBuf();
        }
        final ByteBuf lastbuf = _bufs.get(_bufs.size()-1);
        return lastbuf.isWritable() ? lastbuf : addBuf();
    }
    
    private ByteBuf addBuf() {
        final ByteBuf newbuf = _allocator.buffer(_pageSize, _pageSize);
        _bufs.add(newbuf);
        return newbuf;
    }

    @Override
    public void write(final byte[] b, int off, int len) throws IOException {
        while (len > 0) {
            final ByteBuf buffer = lastBuf();
            final int size = Math.min(buffer.writableBytes(), len);
            buffer.writeBytes(b, off, size);
            off += size;
            len -= size;
        }
    }

    @Override
    public void write(final byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(final int b) throws IOException {
        lastBuf().writeByte(b);
    }

    @Override
    public void writeBoolean(boolean v) throws IOException {
        lastBuf().writeBoolean(v);
    }

    @Override
    public void writeByte(int v) throws IOException {
        lastBuf().writeByte(v);
    }

    @Override
    public void writeBytes(final String s) throws IOException {
        write(s.getBytes(CharsetUtil.US_ASCII));
    }

    @Override
    public void writeChar(final int v) throws IOException {
        writeShort(v);
    }

    @Override
    public void writeChars(final String s) throws IOException {
        final int len = s.length();
        for (int i = 0 ; i < len ; i ++) {
            writeChar(s.charAt(i));
        }
    }

    @Override
    public void writeDouble(double v) throws IOException {
        writeLong(Double.doubleToRawLongBits(v));
    }

    @Override
    public void writeFloat(float v) throws IOException {
        writeInt(Float.floatToRawIntBits(v));
    }

    @Override
    public void writeInt(int v) throws IOException {
        writeShort((short) (v >>> 16));
        writeShort((short) v);
    }

    @Override
    public void writeLong(long v) throws IOException {
        writeInt((int) (v >>> 32));
        writeInt((int) v);
    }

    @Override
    public void writeShort(int v) throws IOException {
        writeByte((byte) (v >>> 8));
        writeByte((byte) v);
    }

    @Override
    public void writeUTF(String s) throws IOException {
        utf8out.writeUTF(s);
    }

    /**
     * Returns the buffer where this stream is writing data.
     */
    public ByteBuf[] buffers() {
        return _bufs.toArray(new ByteBuf[0]);
    }
}