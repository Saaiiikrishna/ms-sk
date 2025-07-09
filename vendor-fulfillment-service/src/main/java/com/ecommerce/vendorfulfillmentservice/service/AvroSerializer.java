package com.ecommerce.vendorfulfillmentservice.service;

import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificRecordBase;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class AvroSerializer {

    // Generic method to serialize Avro SpecificRecordBase objects
    public <T extends SpecificRecordBase> byte[] serialize(T record) throws IOException {
        DatumWriter<T> datumWriter = new GenericDatumWriter<>(record.getSchema());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
        datumWriter.write(record, encoder);
        encoder.flush();
        outputStream.close();
        return outputStream.toByteArray();
    }

    // Overload for GenericRecord if needed, though SpecificRecordBase is preferred with generated classes
    public byte[] serializeGenericRecord(GenericRecord record) throws IOException {
        DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(record.getSchema());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
        datumWriter.write(record, encoder);
        encoder.flush();
        outputStream.close();
        return outputStream.toByteArray();
    }
}
