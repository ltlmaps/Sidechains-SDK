package com.horizen.utils;

import com.google.common.primitives.Ints;
import org.bouncycastle.util.Strings;
import org.junit.Test;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;
import scorex.core.serialization.BytesSerializable;
import scorex.core.serialization.Serializer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;

class ListSerializerTestObjectA implements BytesSerializable {

    public String _testData;

    public ListSerializerTestObjectA(String testData) {
        _testData = testData;
    }
    @Override
    public byte[] bytes() {
        return serializer().toBytes(this);
    }

    @Override
    public Serializer serializer() {
        return new ListSerializerTestObjectASerializer();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (!(obj instanceof ListSerializerTestObjectA))
            return false;
        if (obj == this)
            return true;
        return this._testData.equals(((ListSerializerTestObjectA) obj)._testData);
    }
}

class ListSerializerTestObjectASerializer implements Serializer<ListSerializerTestObjectA> {

    @Override
    public byte[] toBytes(ListSerializerTestObjectA obj) {
        return (obj._testData).getBytes();
    }

    @Override
    public Try<ListSerializerTestObjectA> parseBytes(byte[] bytes) {
        try {
            return new Success<>(new ListSerializerTestObjectA(Strings.fromByteArray(bytes)));
        } catch (Exception e) {
            return new Failure<>(e);
        }
    }
}

class ListSerializerTestObjectB implements BytesSerializable {

    public int _testData;

    public ListSerializerTestObjectB(int testData) {
        _testData = testData;
    }
    @Override
    public byte[] bytes() {
        return serializer().toBytes(this);
    }

    @Override
    public Serializer serializer() {
        return new ListSerializerTestObjectBSerializer();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (!(obj instanceof ListSerializerTestObjectB))
            return false;
        if (obj == this)
            return true;
        return this._testData == ((ListSerializerTestObjectB) obj)._testData;
    }
}

class ListSerializerTestObjectBSerializer implements Serializer<ListSerializerTestObjectB> {

    @Override
    public byte[] toBytes(ListSerializerTestObjectB obj) {
        return Ints.toByteArray(obj._testData);
    }

    @Override
    public Try<ListSerializerTestObjectB> parseBytes(byte[] bytes) {
        try {
            return new Success<>(new ListSerializerTestObjectB(Ints.fromByteArray(bytes)));
        } catch (Exception e) {
            return new Failure<>(e);
        }
    }
}

public class ListSerializerTest {

    @Test
    public void ListSerializerTest_CreationTest() {

        // Test 1: try to create ListSerializer with valid parameters and no limits
        boolean exceptionOccurred = false;
        try {
            HashMap<Integer, Serializer<BytesSerializable>> serializers = new HashMap<>();
            serializers.put(1, (Serializer)new ListSerializerTestObjectASerializer());
            serializers.put(2, (Serializer)new ListSerializerTestObjectBSerializer());
            new ListSerializer<>(serializers);
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Unexpected exception occurred during creation without limits", false, exceptionOccurred);


        // Test 2: try to create ListSerializer with valid parameters and with limits
        exceptionOccurred = false;
        try {
            HashMap<Integer, Serializer<BytesSerializable>> serializers = new HashMap<>();
            serializers.put(1, (Serializer)new ListSerializerTestObjectASerializer());
            serializers.put(2, (Serializer)new ListSerializerTestObjectBSerializer());
            new ListSerializer<>(serializers, 10);
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Unexpected exception occurred during creation with limits", false, exceptionOccurred);


        // Test 3: try to create ListSerializer with invalid parameters (serializers duplications)
        exceptionOccurred = false;
        try {
            HashMap<Integer, Serializer<BytesSerializable>> serializers = new HashMap<>();
            serializers.put(1, (Serializer)new ListSerializerTestObjectASerializer());
            serializers.put(2, (Serializer)new ListSerializerTestObjectASerializer());
            new ListSerializer<>(serializers, 10);
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Exception expected during creation", true, exceptionOccurred);
    }

    @Test
    public void ListSerializerTest_SerializationTest() {
        HashMap<Integer, Serializer<BytesSerializable>> serializers = new HashMap<>();
        serializers.put(1, (Serializer)new ListSerializerTestObjectASerializer());
        serializers.put(2, (Serializer)new ListSerializerTestObjectBSerializer());
        ListSerializer<BytesSerializable> listSerializer = new ListSerializer<>(serializers);

        // Test 1: empty list serialization test
        byte[] bytes = listSerializer.toBytes(new ArrayList<>());
        List<BytesSerializable> res = listSerializer.parseBytes(bytes).get();
        assertEquals("Deserialized list should by empty", 0, res.size());

        // Test 2: not empty list with different types.
        ArrayList<BytesSerializable> data = new ArrayList<>();
        data.add(new ListSerializerTestObjectA("test1"));
        data.add(new ListSerializerTestObjectA("test2"));
        data.add(new ListSerializerTestObjectB(1));
        data.add(new ListSerializerTestObjectA("test3"));
        data.add(new ListSerializerTestObjectB(2));
        data.add(new ListSerializerTestObjectB(3));

        bytes = listSerializer.toBytes(data);
        res = listSerializer.parseBytes(bytes).get();

        assertEquals("Deserialized list has different size than original", data.size(), res.size());
        for(int i = 0; i < data.size(); i++)
            assertEquals("Deserialized list is different to original", data.get(i), res.get(i));
    }

    @Test
    public void ListSerializerTest_FailureSerializationTest() {
        HashMap<Integer, Serializer<BytesSerializable>> serializers = new HashMap<>();
        serializers.put(1, (Serializer)new ListSerializerTestObjectASerializer());
        serializers.put(2, (Serializer)new ListSerializerTestObjectBSerializer());

        ListSerializer<BytesSerializable> listSerializerWithLimits = new ListSerializer<>(serializers, 2);

        ArrayList<BytesSerializable> data = new ArrayList<>();
        data.add(new ListSerializerTestObjectA("test1"));
        data.add(new ListSerializerTestObjectB(1));

        // Test 1: bytes not broken, list size in NOT upper the limit
        boolean exceptionOccurred = false;
        byte[] bytes = listSerializerWithLimits.toBytes(data);
        try {
            listSerializerWithLimits.parseBytes(bytes).get();
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("List expected to be deserialized successful", false, exceptionOccurred);

        // Test 2: bytes not broken, list size in upper the limit
        exceptionOccurred = false;
        data.add(new ListSerializerTestObjectA("test2"));
        bytes = listSerializerWithLimits.toBytes(data);
        try {
            listSerializerWithLimits.parseBytes(bytes).get();
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Exception expected during deserialization, because of list size limits.", true, exceptionOccurred);


        // Test 3: broken bytes: contains some garbage in the end
        exceptionOccurred = false;
        bytes = new byte[]{ 0, 0, 0, 0, 1};
        try {
            listSerializerWithLimits.parseBytes(bytes).get();
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Exception expected during deserialization, because of garbage.", true, exceptionOccurred);


        // Test 4: broken bytes: some bytes in the end were cut
        ListSerializer<BytesSerializable> listSerializer = new ListSerializer<>(serializers);
        bytes = listSerializerWithLimits.toBytes(data);
        bytes = Arrays.copyOfRange(bytes, 0, bytes.length - 1);
        exceptionOccurred = false;
        try {
            listSerializerWithLimits.parseBytes(bytes).get();
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Exception expected during deserialization, because of cut end of bytes.", true, exceptionOccurred);


        // Test 5: broken bytes passed
        exceptionOccurred = false;
        bytes = new byte[]{ 0, 0, 0, 2, 1, 0, 3, 0, 1, 0, 4, 5, 6};
        try {
            listSerializerWithLimits.parseBytes(bytes).get();
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertEquals("Exception expected during deserialization, because of garbage.", true, exceptionOccurred);
    }
}