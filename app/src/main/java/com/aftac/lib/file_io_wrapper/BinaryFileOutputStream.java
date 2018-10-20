package com.aftac.lib.file_io_wrapper;

import java.io.IOException;
import java.io.RandomAccessFile;


public class BinaryFileOutputStream {
	public enum Endianness { BIG_ENDIAN, LITTLE_ENDIAN	}

	private RandomAccessFile stream;
	private long markPos = 0;
	private boolean bigEndian = true;
	private int bufferLength = 4096;
	private byte[] buffer = new byte[bufferLength];
	
	public BinaryFileOutputStream(String fileName) throws IOException {
		this(fileName, Endianness.BIG_ENDIAN);
	}
	
	public BinaryFileOutputStream(String fileName, Endianness endianness) throws IOException {
		this.stream = new RandomAccessFile(fileName, "rw");
		this.bigEndian = (endianness == Endianness.BIG_ENDIAN);
		markPos=0;
	}
	
	public void setEndianness(Endianness endianness) {
	    this.bigEndian = (endianness == Endianness.BIG_ENDIAN);
	}
	
	public long getLength() throws IOException{
		if (stream == null) throw new IOException();
		return stream.length()-markPos;
	}
	
	public void setLength(long length) throws IOException {
		if (stream == null) throw new IOException();
		stream.setLength(length);
	}
	
	public long getPosition() throws IOException {
		if (stream == null) throw new IOException();
		return stream.getFilePointer() - markPos;
	}
	
	public void seek(long offset) throws IOException {
		if (stream == null) throw new IOException();
		stream.seek(offset + markPos);
	}
	
	public void seekRelative(long offset) throws IOException {
		if (stream == null) throw new IOException();
		stream.seek(stream.getFilePointer() + offset);
	}

	public void write(byte[] byteArray, int offset, int length) throws IOException {
		if (stream == null) throw new IOException();
		stream.write(byteArray, offset, length);
	}
    public void write(byte[] byteArray) throws IOException {
        if (stream == null) throw new IOException();
        stream.write(byteArray);
    }

	public void write(short[] shortArray, int offset, int length) throws IOException {
		if (stream == null) throw new IOException();
		int i = offset, stop = i + length;
        if (bigEndian) { while (i < stop) stream.write(shortArray[i]); }
        else { while (i < stop) stream.write(Short.reverseBytes(shortArray[i])); }
	}
    public void write(short[] shortArray) throws IOException {
        write(shortArray, 0, shortArray.length);
    }

    public void write(int[] intArray, int offset, int length) throws IOException {
	    if (stream == null) throw new IOException();
	    int i = offset, stop = i + length;
	    if (bigEndian) { while (i < stop) stream.write(intArray[i]); }
	    else { while (i < stop) stream.write(Integer.reverseBytes(intArray[i])); }
    }
    public void write(int[] intArray) throws IOException {
        write(intArray, 0, intArray.length);
    }

    public void writeBytes(String str) throws IOException {
        if (stream == null) throw new IOException();
        byte[] arr = str.getBytes("UTF-8");
        int i = 0, len = arr.length;
        if (bigEndian) { while (i < len) { stream.write((byte) arr[i]); } };
    }

	public void writeShort(int value) throws IOException {
		if (stream == null) throw new IOException();
		if (bigEndian) stream.writeShort(value);
		else stream.writeShort(Short.reverseBytes((short)value));
	}
	
	public void writeInt(int value) throws IOException {
		if (stream == null) throw new IOException();
		if (bigEndian) stream.writeInt(value);
		else stream.writeInt(Integer.reverseBytes(value));
	}
	
	public void mark() throws IOException {
		if (stream == null) throw new IOException();
		markPos = stream.getFilePointer();
	}
	
	public void unmark() {
		markPos = 0;
	}
	
	public void close() throws IOException {
		stream.close();
		stream = null;
	}
}