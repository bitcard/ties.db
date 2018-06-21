/**
 * Copyright © 2017 Ties BV
 *
 * This file is part of Ties.DB project.
 *
 * Ties.DB project is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Ties.DB project is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with Ties.DB project. If not, see <https://www.gnu.org/licenses/lgpl-3.0>.
 */
package com.tiesdb.protocol.v0r0.test;

import static com.tiesdb.protocol.v0r0.ebml.TiesDBType.*;
import static com.tiesdb.protocol.v0r0.test.util.TestUtil.*;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.xml.bind.DatatypeConverter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.tiesdb.lib.crypto.digest.DigestManager;
import com.tiesdb.lib.crypto.digest.api.Digest;
import com.tiesdb.lib.crypto.ecc.signature.ECKey;
import com.tiesdb.protocol.v0r0.ebml.TiesDBType;
import com.tiesdb.protocol.v0r0.ebml.TiesDBType.Context;
import com.tiesdb.protocol.v0r0.ebml.format.UUIDFormat;

import one.utopic.sparse.api.Event.CommonEventType;
import one.utopic.sparse.ebml.EBMLEvent;
import one.utopic.sparse.ebml.EBMLType;
import one.utopic.sparse.ebml.EBMLReader.EBMLReadFormat;
import one.utopic.sparse.ebml.format.ASCIIStringFormat;
import one.utopic.sparse.ebml.format.BigDecimalFormat;
import one.utopic.sparse.ebml.format.BigIntegerFormat;
import one.utopic.sparse.ebml.format.BytesFormat;
import one.utopic.sparse.ebml.format.DateFormat;
import one.utopic.sparse.ebml.format.IntegerFormat;
import one.utopic.sparse.ebml.format.LongFormat;
import one.utopic.sparse.ebml.format.UTF8StringFormat;

@EnabledIfSystemProperty(named = "test-generate", matches = "true")
public class TiesDBProtocolV0R0Generate {

    @Test
    public void generateSampleDate() {
        Date date = new Date(1522661357000L);
        System.out.println(date);
        byte[] entryDate = encode(//
                part(ENTRY_TIMESTAMP, DateFormat.INSTANCE, date) //
        );
        System.out.println("Date " + DatatypeConverter.printHexBinary(entryDate));
        decode(entryDate, ENTRY_HEADER.getContext(), r -> {
            r.forEachRemaining(e -> {
                System.out.println(DateFormat.INSTANCE.read(r));
            });
        });
    }

    @DisplayName("generateSampleBigNumberTest")
    @ParameterizedTest(name = "{index}. {arguments}")
    @ValueSource(strings = { //
            "0", //
            "-1", //
            "57896044618658097711785492504343953926634992332820282019728792003956564819967", //
            "-57896044618658097711785492504343953926634992332820282019728792003956564819968", //
    })
    public void generateSampleBigNumber(String value) {
        BigInteger amount = new BigInteger(value);
        byte[] entryDate = encode(//
                part(CHEQUE_AMOUNT, BigIntegerFormat.INSTANCE, amount) //
        );
        System.out.println("BigNumber  DEC " + value + "\nBigNumber EBML " + DatatypeConverter.printHexBinary(entryDate));
    }

    @Test
    public void generateSampleInsertRequest() {
        Date date = new Date(1522661357000L);
        ECKey key = ECKey.fromPrivate(hs2ba("b84f0b9766fb4b7e88f11f124f98170cb437cd09515caf970da886e4ef4c5fa3"));

        LinkedList<Supplier<byte[]>> fieldHashes = new LinkedList<>();

        byte[] fieldsData = encodeTies(//
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "string"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "description"), //
                                part(FIELD_VALUE, UTF8StringFormat.INSTANCE, "Initial description") //
                        ) //
                ), //
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "uuid"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "uuid"), //
                                part(FIELD_VALUE, UUIDFormat.INSTANCE, UUID.fromString("75e6b139-de73-4e31-9600-e216e4239030")) //
                        ) //
                ) //
        );

        byte[] fldsHash = getHash(d -> fieldHashes.forEach(s -> {
            byte[] hash = s.get();
            System.out.println("FieldHash: " + DatatypeConverter.printHexBinary(hash));
            d.update(hash);
        }));

        System.out.println("EntryFieldsHash: " + DatatypeConverter.printHexBinary(fldsHash));

        byte[] encData = encodeTies(//
                part(MODIFICATION_REQUEST, //
                        part(CONSISTENCY, IntegerFormat.INSTANCE, 0x64), // ALL
                        part(MODIFICATION_ENTRY, //
                                part(ENTRY_HEADER, //
                                        tiesPartSign(key, SIGNATURE, //
                                                part(ENTRY_TABLESPACE_NAME, UTF8StringFormat.INSTANCE, "filatov-dev.node.dev.tablespace"), //
                                                part(ENTRY_TABLE_NAME, UTF8StringFormat.INSTANCE, "dev-test"), //
                                                // part(ENTRY_TYPE, IntegerFormat.INSTANCE, 0x01), // INSERT
                                                part(ENTRY_TIMESTAMP, DateFormat.INSTANCE, date), //
                                                part(ENTRY_VERSION, IntegerFormat.INSTANCE, 0x01), // creating version 1
                                                part(ENTRY_FLD_HASH, BytesFormat.INSTANCE, fldsHash), //
                                                part(ENTRY_NETWORK, IntegerFormat.INSTANCE, 60), //
                                                part(SIGNER, BytesFormat.INSTANCE, key.getAddress()) //
                                        ) //
                                ), //
                                part(FIELD_LIST, BytesFormat.INSTANCE, fieldsData), //
                                part(CHEQUE_LIST, //
                                        part(CHEQUE, //
                                                tiesPartSign(key, SIGNATURE, //
                                                        part(CHEQUE_RANGE, UUIDFormat.INSTANCE,
                                                                UUID.fromString("38007241-b550-4fa5-87d6-8ee7587d4073")), //
                                                        part(CHEQUE_NUMBER, LongFormat.INSTANCE, 1L), //
                                                        part(CHEQUE_TIMESTAMP, DateFormat.INSTANCE, date), //
                                                        part(CHEQUE_AMOUNT, BigIntegerFormat.INSTANCE, BigInteger.ONE), //
                                                        part(ADDRESS_LIST, //
                                                                part(ADDRESS, BytesFormat.INSTANCE,
                                                                        hs2ba("64ed31c6187765D40271EE4F9b4C29A5a125DE23")) //
                                                        ), //
                                                        part(SIGNER, BytesFormat.INSTANCE, key.getAddress()) //
                                                ) //
                                        ) //
                                ) //
                        ) //
                ) //
        );
        System.out.println("Insert " + DatatypeConverter.printHexBinary(encData));
    }

    @Test
    public void generateSampleUpdateRequest() {
        Date date = new Date(1522661357000L);
        ECKey key = ECKey.fromPrivate(hs2ba("b84f0b9766fb4b7e88f11f124f98170cb437cd09515caf970da886e4ef4c5fa3"));

        LinkedList<Supplier<byte[]>> fieldHashes = new LinkedList<>();

        byte[] fieldsData = encodeTies(//
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "string"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "description"), //
                                part(FIELD_VALUE, UTF8StringFormat.INSTANCE, "Updated description") //
                        ) //
                ), //
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "uuid"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "uuid"), //
                                part(FIELD_VALUE, UUIDFormat.INSTANCE, UUID.fromString("75e6b139-de73-4e31-9600-e216e4239030")) //
                        ) //
                ) //
        );

        byte[] fldsHash = getHash(d -> fieldHashes.forEach(s -> {
            byte[] hash = s.get();
            System.out.println("FieldHash: " + DatatypeConverter.printHexBinary(hash));
            d.update(hash);
        }));

        System.out.println("EntryFieldsHash: " + DatatypeConverter.printHexBinary(fldsHash));

        byte[] encData = encodeTies(//
                part(MODIFICATION_REQUEST, //
                        part(CONSISTENCY, IntegerFormat.INSTANCE, 0x64), // ALL
                        part(MODIFICATION_ENTRY, //
                                part(ENTRY_HEADER, //
                                        tiesPartSign(key, SIGNATURE, //
                                                part(ENTRY_TABLESPACE_NAME, UTF8StringFormat.INSTANCE, "filatov-dev.node.dev.tablespace"), //
                                                part(ENTRY_TABLE_NAME, UTF8StringFormat.INSTANCE, "dev-test"), //
                                                // part(ENTRY_TYPE, IntegerFormat.INSTANCE, 0x02), // UPDATE
                                                part(ENTRY_TIMESTAMP, DateFormat.INSTANCE, date), //
                                                part(ENTRY_VERSION, IntegerFormat.INSTANCE, 0x02), // from 1 to 2
                                                part(ENTRY_OLD_HASH, BytesFormat.INSTANCE,
                                                        hs2ba("9c22ff5f21f0b81b113e63f7db6da94fedef11b2119b4088b89664fb9a3cb658")), //
                                                part(ENTRY_FLD_HASH, BytesFormat.INSTANCE, fldsHash), //
                                                part(ENTRY_NETWORK, IntegerFormat.INSTANCE, 60), //
                                                part(SIGNER, BytesFormat.INSTANCE, key.getAddress()) //
                                        ) //
                                ), //
                                part(FIELD_LIST, BytesFormat.INSTANCE, fieldsData), //
                                part(CHEQUE_LIST, //
                                        part(CHEQUE, //
                                                tiesPartSign(key, SIGNATURE, //
                                                        part(CHEQUE_RANGE, UUIDFormat.INSTANCE,
                                                                UUID.fromString("38007241-b550-4fa5-87d6-8ee7587d4073")), //
                                                        part(CHEQUE_NUMBER, LongFormat.INSTANCE, 2L), //
                                                        part(CHEQUE_TIMESTAMP, DateFormat.INSTANCE, date), //
                                                        part(CHEQUE_AMOUNT, BigIntegerFormat.INSTANCE, BigInteger.ONE), //
                                                        part(ADDRESS_LIST, //
                                                                part(ADDRESS, BytesFormat.INSTANCE,
                                                                        hs2ba("64ed31c6187765D40271EE4F9b4C29A5a125DE23")) //
                                                        ), //
                                                        part(SIGNER, BytesFormat.INSTANCE, key.getAddress()) //
                                                ) //
                                        ) //
                                ) //
                        ) //
                ) //
        );
        System.out.println("Update " + DatatypeConverter.printHexBinary(encData));
    }

    public byte[] generateModificationRequest(byte[] entryData) {
        byte[] encData = encodeTies(//
                part(MODIFICATION_REQUEST, //
                        part(CONSISTENCY, IntegerFormat.INSTANCE, 0x64), // ALL
                        part(MESSAGE_ID, LongFormat.INSTANCE, 777L), //
                        part(MODIFICATION_ENTRY, BytesFormat.INSTANCE, entryData) //
                ) //
        );
        return encData;
    }

    @Test
    public void generateMultivalueSampleInsertRequest() {
        Date date = new Date(1522661357000L);
        ECKey key = ECKey.fromPrivate(hs2ba("b84f0b9766fb4b7e88f11f124f98170cb437cd09515caf970da886e4ef4c5fa3"));
        UUID uuid = UUID.fromString("7606fc02-8c19-44ee-99be-a24fc1449008");

        LinkedList<Supplier<byte[]>> fieldHashes = new LinkedList<>();

        Random rg = new SecureRandom();

        byte[] randomBytes = new byte[rg.nextInt(32) + 32];
        rg.nextBytes(randomBytes);
        System.out.println("randomBytes " + DatatypeConverter.printHexBinary(randomBytes));
        byte[] fieldsData = encodeTies(//
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "uuid"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "Id"), //
                                part(FIELD_VALUE, UUIDFormat.INSTANCE, uuid) //
                        ) //
                ), //
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "binary"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "fBinary"), //
                                part(FIELD_VALUE, BytesFormat.INSTANCE, randomBytes) //
                        ) //
                ), //
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "boolean"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "fBoolean"), //
                                part(FIELD_VALUE, IntegerFormat.INSTANCE, rg.nextInt(2)) //
                        ) //
                ), //
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "decimal"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "fDecimal"), //
                                part(FIELD_VALUE, BigDecimalFormat.INSTANCE, new BigDecimal(rg.nextDouble())) //
                        ) //
                ), //
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "double"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "fDouble"), //
                                part(FIELD_VALUE, BigDecimalFormat.INSTANCE, new BigDecimal(rg.nextDouble())) //
                        ) //
                ), //
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "duration"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "fDuration"), //
                                part(FIELD_VALUE, BigDecimalFormat.INSTANCE, new BigDecimal("267512643.210")) //
                        ) //
                ), //
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "float"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "fFloat"), //
                                part(FIELD_VALUE, BigDecimalFormat.INSTANCE, new BigDecimal(rg.nextDouble())) //
                        ) //
                ), //
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "integer"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "fInteger"), //
                                part(FIELD_VALUE, IntegerFormat.INSTANCE, rg.nextInt()) //
                        ) //
                ), //
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "long"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "fLong"), //
                                part(FIELD_VALUE, LongFormat.INSTANCE, rg.nextLong()) //
                        ) //
                ), //
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "string"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "fString"), //
                                part(FIELD_VALUE, UTF8StringFormat.INSTANCE, this.toString()) //
                        ) //
                ), //
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "time"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "fTime"), //
                                part(FIELD_VALUE, DateFormat.INSTANCE, new Date()) //
                        ) //
                ) //
        );

        byte[] fldsHash = getHash(d -> fieldHashes.forEach(s -> {
            byte[] hash = s.get();
            // System.out.println("FieldHash: " + DatatypeConverter.printHexBinary(hash));
            d.update(hash);
        }));

        // System.out.println("EntryFieldsHash: " +
        // DatatypeConverter.printHexBinary(fldsHash));

        byte[] encData = encodeTies(//
                part(MODIFICATION_REQUEST, //
                        part(CONSISTENCY, IntegerFormat.INSTANCE, 0x64), // ALL
                        part(MESSAGE_ID, LongFormat.INSTANCE, 777L), // ALL
                        part(MODIFICATION_ENTRY, //
                                part(ENTRY_HEADER, //
                                        tiesPartSign(key, SIGNATURE, //
                                                part(ENTRY_TABLESPACE_NAME, UTF8StringFormat.INSTANCE, "client-dev.test"), //
                                                part(ENTRY_TABLE_NAME, UTF8StringFormat.INSTANCE, "all_types"), //
                                                // part(ENTRY_TYPE, IntegerFormat.INSTANCE, 0x01), // INSERT
                                                part(ENTRY_TIMESTAMP, DateFormat.INSTANCE, date), //
                                                part(ENTRY_VERSION, IntegerFormat.INSTANCE, 0x01), // INSERT
                                                part(ENTRY_FLD_HASH, BytesFormat.INSTANCE, fldsHash), //
                                                part(ENTRY_NETWORK, IntegerFormat.INSTANCE, 60), //
                                                part(SIGNER, BytesFormat.INSTANCE, key.getAddress()) //
                                        ) //
                                ), //
                                part(FIELD_LIST, BytesFormat.INSTANCE, fieldsData), //
                                part(CHEQUE_LIST, //
                                        part(CHEQUE, //
                                                tiesPartSign(key, SIGNATURE, //
                                                        part(CHEQUE_RANGE, UUIDFormat.INSTANCE,
                                                                UUID.fromString("38007241-b550-4fa5-87d6-8ee7587d4073")), //
                                                        part(CHEQUE_NUMBER, LongFormat.INSTANCE, 1L), //
                                                        part(CHEQUE_TIMESTAMP, DateFormat.INSTANCE, date), //
                                                        part(CHEQUE_AMOUNT, BigIntegerFormat.INSTANCE, BigInteger.ONE), //
                                                        part(ADDRESS_LIST, //
                                                                part(ADDRESS, BytesFormat.INSTANCE,
                                                                        hs2ba("64ed31c6187765D40271EE4F9b4C29A5a125DE23")) //
                                                        ), //
                                                        part(SIGNER, BytesFormat.INSTANCE, key.getAddress()) //
                                                ) //
                                        ) //
                                ) //
                        ) //
                ) //
        );
        System.out.println("MultivalueInsert " + DatatypeConverter.printHexBinary(encData));
        packetDecode(encData);
    }

    private byte[] getHash(Consumer<Digest> d) {
        Digest digest = DigestManager.getDigest(DEFAULT_DIGEST_NAME);
        d.accept(digest);
        byte[] out = new byte[digest.getDigestSize()];
        digest.doFinal(out, 0);
        return out;
    }

    private Consumer<Byte> newDigestConsumer(LinkedList<Supplier<byte[]>> fieldHashes) {
        Digest digest = DigestManager.getDigest(DEFAULT_DIGEST_NAME);
        fieldHashes.addLast(() -> {
            byte[] buf = new byte[digest.getDigestSize()];
            digest.doFinal(buf, 0);
            return buf;
        });
        return digest::update;
    }

    private byte[] hs2ba(String hexString) {
        return DatatypeConverter.parseHexBinary(hexString);
    }

    @Test
    public void generateMultivalueSampleUpdateRequest() {
        Date date = new Date(1522661357000L);
        ECKey key = ECKey.fromPrivate(hs2ba("b84f0b9766fb4b7e88f11f124f98170cb437cd09515caf970da886e4ef4c5fa3"));
        UUID uuid = UUID.fromString("7606fc02-8c19-44ee-99be-a24fc1449008");

        LinkedList<Supplier<byte[]>> fieldHashes = new LinkedList<>();

        Random rg = new SecureRandom();

        byte[] randomBytes = new byte[rg.nextInt(32) + 32];
        rg.nextBytes(randomBytes);
        System.out.println("randomBytes " + DatatypeConverter.printHexBinary(randomBytes));
        byte[] fieldsData = encodeTies(//
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "uuid"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "Id"), //
                                part(FIELD_VALUE, UUIDFormat.INSTANCE, uuid) //
                        ) //
                ), //
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "binary"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "fBinary"), //
                                part(FIELD_VALUE, BytesFormat.INSTANCE, randomBytes) //
                        ) //
                ), //
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "boolean"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "fBoolean"), //
                                part(FIELD_VALUE, IntegerFormat.INSTANCE, rg.nextInt(2)) //
                        ) //
                ), //
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "decimal"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "fDecimal"), //
                                part(FIELD_VALUE, BigDecimalFormat.INSTANCE, new BigDecimal(rg.nextDouble())) //
                        ) //
                ), //
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "double"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "fDouble"), //
                                part(FIELD_VALUE, BigDecimalFormat.INSTANCE, new BigDecimal(rg.nextDouble())) //
                        ) //
                ), //
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "duration"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "fDuration"), //
                                part(FIELD_VALUE, BigDecimalFormat.INSTANCE, new BigDecimal("267512643.210")) //
                        ) //
                ), //
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "float"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "fFloat"), //
                                part(FIELD_VALUE, BigDecimalFormat.INSTANCE, new BigDecimal(rg.nextDouble())) //
                        ) //
                ), //
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "integer"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "fInteger"), //
                                part(FIELD_VALUE, IntegerFormat.INSTANCE, rg.nextInt()) //
                        ) //
                ), //
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "long"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "fLong"), //
                                part(FIELD_VALUE, LongFormat.INSTANCE, rg.nextLong()) //
                        ) //
                ), //
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "string"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "fString"), //
                                part(FIELD_VALUE, UTF8StringFormat.INSTANCE, this.toString()) //
                        ) //
                ), //
                part(FIELD, //
                        part(FIELD_TYPE, ASCIIStringFormat.INSTANCE, "time"), //
                        ties(newDigestConsumer(fieldHashes), //
                                part(FIELD_NAME, UTF8StringFormat.INSTANCE, "fTime"), //
                                part(FIELD_VALUE, DateFormat.INSTANCE, new Date()) //
                        ) //
                ) //
        );

        byte[] fldsHash = getHash(d -> fieldHashes.forEach(s -> {
            byte[] hash = s.get();
            // System.out.println("FieldHash: " + DatatypeConverter.printHexBinary(hash));
            d.update(hash);
        }));

        // System.out.println("EntryFieldsHash: " +
        // DatatypeConverter.printHexBinary(fldsHash));

        byte[] encData = encodeTies(//
                part(MODIFICATION_REQUEST, //
                        part(CONSISTENCY, IntegerFormat.INSTANCE, 0x64), // ALL
                        part(MESSAGE_ID, LongFormat.INSTANCE, 777L), // ALL
                        part(MODIFICATION_ENTRY, //
                                part(ENTRY_HEADER, //
                                        tiesPartSign(key, SIGNATURE, //
                                                part(ENTRY_TABLESPACE_NAME, UTF8StringFormat.INSTANCE, "client-dev.test"), //
                                                part(ENTRY_TABLE_NAME, UTF8StringFormat.INSTANCE, "all_types"), //
                                                // part(ENTRY_TYPE, IntegerFormat.INSTANCE, 0x02), // UPDATE
                                                part(ENTRY_TIMESTAMP, DateFormat.INSTANCE, date), //
                                                part(ENTRY_VERSION, IntegerFormat.INSTANCE, 0x02), // UPDATE
                                                part(ENTRY_OLD_HASH, BytesFormat.INSTANCE,
                                                        DatatypeConverter.parseHexBinary(
                                                                "0917769875F57980143A2127F0B2AB7A32B9956E5E6F881D9685882A144ED103")), // UPDATE
                                                part(ENTRY_FLD_HASH, BytesFormat.INSTANCE, fldsHash), //
                                                part(ENTRY_NETWORK, IntegerFormat.INSTANCE, 60), //
                                                part(SIGNER, BytesFormat.INSTANCE, key.getAddress()) //
                                        ) //
                                ), //
                                part(FIELD_LIST, BytesFormat.INSTANCE, fieldsData), //
                                part(CHEQUE_LIST, //
                                        part(CHEQUE, //
                                                tiesPartSign(key, SIGNATURE, //
                                                        part(CHEQUE_RANGE, UUIDFormat.INSTANCE,
                                                                UUID.fromString("38007241-b550-4fa5-87d6-8ee7587d4073")), //
                                                        part(CHEQUE_NUMBER, LongFormat.INSTANCE, 1L), //
                                                        part(CHEQUE_TIMESTAMP, DateFormat.INSTANCE, date), //
                                                        part(CHEQUE_AMOUNT, BigIntegerFormat.INSTANCE, BigInteger.ONE), //
                                                        part(ADDRESS_LIST, //
                                                                part(ADDRESS, BytesFormat.INSTANCE,
                                                                        hs2ba("64ed31c6187765D40271EE4F9b4C29A5a125DE23")) //
                                                        ), //
                                                        part(SIGNER, BytesFormat.INSTANCE, key.getAddress()) //
                                                ) //
                                        ) //
                                ) //
                        ) //
                ) //
        );
        System.out.println("MultivalueInsert " + DatatypeConverter.printHexBinary(encData));
        packetDecode(encData);
    }

    @Test
    public void generateSampleSelectRequest() {

        UUID uuid = UUID.fromString("7606fc02-8c19-44ee-99be-a24fc1449008");

        byte[] encData = encodeTies(//
                part(RECOLLECTION_REQUEST, //
                        part(CONSISTENCY, IntegerFormat.INSTANCE, 0x64), // ALL
                        part(MESSAGE_ID, LongFormat.INSTANCE, 777L), // ALL
                        part(RECOLLECTION_TABLESPACE_NAME, UTF8StringFormat.INSTANCE, "client-dev.test"), //
                        part(RECOLLECTION_TABLE_NAME, UTF8StringFormat.INSTANCE, "all_types"), //
                        part(RETRIEVE_LIST, //
                                part(RET_FIELD, UTF8StringFormat.INSTANCE, "Id"), //
                                part(RET_FIELD, UTF8StringFormat.INSTANCE, "fString"), //
                                part(RET_COMPUTE, //
                                        part(RET_COMPUTE_ALIAS, UTF8StringFormat.INSTANCE, "WriteTime"), //
                                        part(RET_COMPUTE_TYPE, UTF8StringFormat.INSTANCE, "binary"), //
                                        part(FUNCTION_NAME, ASCIIStringFormat.INSTANCE, "bigIntAsBlob"), //
                                        part(FUN_ARGUMENT_FUNCTION, //
                                                part(FUNCTION_NAME, ASCIIStringFormat.INSTANCE, "toUnixTimestamp"), //
                                                part(FUN_ARGUMENT_FUNCTION, //
                                                        part(FUNCTION_NAME, ASCIIStringFormat.INSTANCE, "cast"), //
                                                        part(FUN_ARGUMENT_FUNCTION, //
                                                                part(FUNCTION_NAME, ASCIIStringFormat.INSTANCE, "writeTime"), //
                                                                part(FUN_ARGUMENT_REFERENCE, UTF8StringFormat.INSTANCE, "fTime")//
                                                        ), //
                                                        part(FUN_ARGUMENT_STATIC, //
                                                                part(ARG_STATIC_TYPE, ASCIIStringFormat.INSTANCE, "string"), //
                                                                part(ARG_STATIC_VALUE, ASCIIStringFormat.INSTANCE, "date")) //
                                                )//
                                        )//
                                ), //
                                part(RET_COMPUTE, //
                                        part(RET_COMPUTE_ALIAS, UTF8StringFormat.INSTANCE, "TestValue"), //
                                        part(RET_COMPUTE_TYPE, UTF8StringFormat.INSTANCE, "binary"), //
                                        part(FUNCTION_NAME, ASCIIStringFormat.INSTANCE, "intAsBlob"), //
                                        part(FUN_ARGUMENT_STATIC, //
                                                part(ARG_STATIC_TYPE, ASCIIStringFormat.INSTANCE, "integer"), //
                                                part(ARG_STATIC_VALUE, IntegerFormat.INSTANCE, 777))//
                                )//
                        ), part(FILTER_LIST, //
                                part(FILTER, //
                                        part(FILTER_FIELD, UTF8StringFormat.INSTANCE, "Id"), //
                                        part(FUNCTION_NAME, ASCIIStringFormat.INSTANCE, "IN"), //
                                        part(FUN_ARGUMENT_STATIC, //
                                                part(ARG_STATIC_TYPE, ASCIIStringFormat.INSTANCE, "uuid"), //
                                                part(ARG_STATIC_VALUE, UUIDFormat.INSTANCE, uuid)//
                                        )//
                                )//
                        )//
                )//
        );

        packetDecode(encData);
    }

    public static void main(String[] args) {
        TreeSet<String> values = new TreeSet<>();
        for (int i = 0; i < args.length; i++) {
            values.add(args[i]);
        }
        values.forEach(System.out::println);
    }

    @Test
    public void packetDecodeTest() {
        String[] dataStrings = new String[] {
                "1E5449454316EE8164EC820309E1430CE140A4808F636C69656E742D6465762E746573748289616C6C5F747970657386857EBE0941C88881018CA09A2FC5F82D9A2ACE7A1F07BA0D3D243DCC67E8DFBFB3C4412E2178D15E59A6528E813CFC94FAFE9C9E7845F446D091C12C74D44C61A0923C00FEC1958AEB999D5550B8AA47AE3348B482F2E92C18D17A957B79A8ECBECE8F70726C7443523A9E20B0E952F1D51FB721871B1F48FEF145D2940E59CF39339739EB5D26D141CCD19C8284757569648082496486907606FC028C1944EE99BEA24FC1449008D1D1828662696E61727980876642696E61727986BEE0DCB1C405014B072475985E971BDCE006C7F55E50ADF3EEF5174BBFC6415D10F03080BFCC4AF554961E249FE4B527802C9F5F2F5BA603D0FC6CB5D4A730D1968287626F6F6C65616E808866426F6F6C65616E868101D1AB8287646563696D616C808866446563696D616C8696E20464F9AF575D4083AD82A9523EFA464D0F4DE28A85D1AA8286646F75626C65808766446F75626C658697EA15B12CA0FDACF3E5DE7C0363C10F43E975B8FDB4F96BD19D82886475726174696F6E8089664475726174696F6E8686863E48FEFE8AD1A88285666C6F6174808666466C6F61748697EA14BC4602E04501DFF99F59B289440C90B12BDFACA449D1998287696E7465676572808866496E74656765728684E8CCFE15D19782846C6F6E678085664C6F6E678688BF08AB5B8EF9E48DD1D48286737472696E67808766537472696E6786C1636F6D2E7469657364622E70726F746F636F6C2E763072302E746573742E54696573444250726F746F636F6C5630523047656E6572617465403363343139363331D195828474696D6580856654696D65868600805A38C324C14093C14090809038007241B5504FA587D68EE7587D407382810184857EBE0941C8868101A196A09464ED31C6187765D40271EE4F9B4C29A5A125DE23FC94FAFE9C9E7845F446D091C12C74D44C61A0923C00FEC12C06F9333BE122E2F32D8D9D7A9216D27CEE6C4219F72AA56E04D21FA4A0DBC33AB1129244908432DC54A9AEED4E60396178CB27695886CB7D23CA539EBAB2A326",
                "1E5449454322EE8164EC820309E14318E140C6808F636C69656E742D6465762E746573748289616C6C5F747970657386857EBE0941C88881028AA00917769875F57980143A2127F0B2AB7A32B9956E5E6F881D9685882A144ED1038CA03F9530E87F4F1CD292595A902B800871B9B32075647BC377A57479CB6DF9AC6E8E813CFC94FAFE9C9E7845F446D091C12C74D44C61A0923C00FEC15D3F13EDEBC666EEEF5FC43CCA54E018938D9C6EE8E88B892463E249FD7801A528A98F073BC4EEAF0D75C3ABA46FEB3A6A7C989CABE22E54EC59AB68164A844225D141B6D19C8284757569648082496486907606FC028C1944EE99BEA24FC1449008D1B9828662696E61727980876642696E61727986A6C57AF2EA4ED466156227CE83F89C9EE8AF8D0EA7763A648EEEE3F49ADF57DEC1C074E7A091DFD1968287626F6F6C65616E808866426F6F6C65616E868100D1AC8287646563696D616C808866446563696D616C8697EA7F5A60237720A2D735B02CB808103E0B8EDEBD0156EBD1AB8286646F75626C65808766446F75626C658698EA008BDA51BB1C2C2C53519DD94BEF10395BBC8622C31105D19D82886475726174696F6E8089664475726174696F6E8686863E48FEFE8AD1A88285666C6F6174808666466C6F61748697E8042328CBF8CDDC933CA00E5D08234389F82AB1FBAF83D1998287696E7465676572808866496E74656765728684623842BED19782846C6F6E678085664C6F6E678688696C7E02800F0D64D1D48286737472696E67808766537472696E6786C1636F6D2E7469657364622E70726F746F636F6C2E763072302E746573742E54696573444250726F746F636F6C5630523047656E6572617465403363343139363331D195828474696D6580856654696D65868600805A3BE189C14093C14090809038007241B5504FA587D68EE7587D407382810184857EBE0941C8868101A196A09464ED31C6187765D40271EE4F9B4C29A5A125DE23FC94FAFE9C9E7845F446D091C12C74D44C61A0923C00FEC12C06F9333BE122E2F32D8D9D7A9216D27CEE6C4219F72AA56E04D21FA4A0DBC33AB1129244908432DC54A9AEED4E60396178CB27695886CB7D23CA539EBAB2A326",
                "1E5449454325EE8164EC820309E1431BE140C6808F636C69656E742D6465762E746573748289616C6C5F747970657386857EBE0941C88881038AA04F5E0569913F87FF7E25017F825AD5A366380D05D3DB5D8EFFC05B51086E66A98CA0CBE9E96873DDEBB169F414F0C164FDDC53E72D56A36D4A3C26D5138AF1426A538E813CFC94FAFE9C9E7845F446D091C12C74D44C61A0923C00FEC133D552F102CD9D592726736EC35295B3863D3EC3AE95CE644503685DE14E76B440E784586175FCD5FD755FE172C0623AF3FDB0047C22AECAF5B074FEA7B87C9B26D141B9D19C8284757569648082496486907606FC028C1944EE99BEA24FC1449008D1BC828662696E61727980876642696E61727986A97EBD582F5CEA0B0D1CA7D75DDC2F29A8098742E97CDF6E493CC7796BDD5668003F114C87F9F0D7FBBBD1968287626F6F6C65616E808866426F6F6C65616E868101D1AC8287646563696D616C808866446563696D616C8697EA091CA513DD172A5E44CA137CE71D1B5D178461928433D1AA8286646F75626C65808766446F75626C658697EA7D5ADEA44E63419EBF0CD39FC601FADCD781243020BBD19D82886475726174696F6E8089664475726174696F6E8686863E48FEFE8AD1A98285666C6F6174808666466C6F61748698EA00B4888A69925AC849D72D4C6A01C098E4E06C6E47BADFD1998287696E7465676572808866496E746567657286840C23928DD19782846C6F6E678085664C6F6E6786884689A3296A4D4D3FD1D48286737472696E67808766537472696E6786C1636F6D2E7469657364622E70726F746F636F6C2E763072302E746573742E54696573444250726F746F636F6C5630523047656E6572617465403363343139363331D195828474696D6580856654696D65868600805A3F9A93C14093C14090809038007241B5504FA587D68EE7587D407382810184857EBE0941C8868101A196A09464ED31C6187765D40271EE4F9B4C29A5A125DE23FC94FAFE9C9E7845F446D091C12C74D44C61A0923C00FEC12C06F9333BE122E2F32D8D9D7A9216D27CEE6C4219F72AA56E04D21FA4A0DBC33AB1129244908432DC54A9AEED4E60396178CB27695886CB7D23CA539EBAB2A326",
                "1154494540EAEE8164EC820309808F636C69656E742D6465762E746573748289616C6C5F747970657383409ED0824964D08766537472696E67C1E0A089577269746554696D65A28662696E617279F08C626967496E744173426C6F62F3BDF08F746F556E697854696D657374616D70F3AAF08463617374F392F089777269746554696D65F2856654696D65F18E8086737472696E67828464617465C1ADA0895465737456616C7565A28662696E617279F089696E744173426C6F62F18D8087696E746567657282820309A3A4F1A2E0824964F082494EF19880847575696482907606FC028C1944EE99BEA24FC1449008",
                //
        };

        for (String data : dataStrings) {
            packetDecode(DatatypeConverter.parseHexBinary(data.replaceFirst("C001BA5E1225EFFF0000000000000001", "")));
        }
    }

    @Test
    public void packetDecodeTest2() {
        String[] dataStrings = new String[] { //
                "125449454341EC820309A1433AE142FBE140A4808F636C69656E742D6465762E746573748289616C6C5F747970657386857EBE0941C88881018CA09A2FC5F82D9A2ACE7A1F07BA0D3D243DCC67E8DFBFB3C4412E2178D15E59A6528E813CFC94FAFE9C9E7845F446D091C12C74D44C61A0923C00FEC1958AEB999D5550B8AA47AE3348B482F2E92C18D17A957B79A8ECBECE8F70726C7443523A9E20B0E952F1D51FB721871B1F48FEF145D2940E59CF39339739EB5D26D14251D19C8082496482847575696486907606FC028C1944EE99BEA24FC1449008D1B380876642696E617279828662696E61727984A0A319749EF77B897C33D86FD09842962E9807A246010711AC9EEF1B206A792691D1B5808866426F6F6C65616E8287626F6F6C65616E84A0CAE62207D2E809E3F65D101DA7DC21C406244B20B91FBD496A83402F02E55CCBD1B5808866446563696D616C8287646563696D616C84A0EBF17D9D8F5491FFC07B1331CB8B008C37AB85A1DC3F0309C8D96A974AC7FF25D1B3808766446F75626C658286646F75626C6584A02B2444E1F794887B7F0B8E9EA4C3CBB4DCC101715BE3CACAC59065518436DD75D1B78089664475726174696F6E82886475726174696F6E84A047CA4AD678B5580200643E94596163E7AF5546E39C0A191A5B040924E388D4CDD1B1808666466C6F61748285666C6F617484A00C810B124089360422E34183F5802A8B69A90BF3AC7406385C450648BA74515CD1B5808866496E74656765728287696E746567657284A0E77E340AC5AEE1359801CFD340DB9C387E797284AB72E1C7A1C84883B1CA06D3D1AF8085664C6F6E6782846C6F6E6784A014EB7C8CBC42818EF3F2D88599BC18B8FEC56C7CA6A8053FDAA7B9B5BD1348ABD1D4808766537472696E678286737472696E6786C1636F6D2E7469657364622E70726F746F636F6C2E763072302E746573742E54696573444250726F746F636F6C5630523047656E6572617465403363343139363331D1AF80856654696D65828474696D6584A05BDD4DBA601177373C27ED4EF4D47D86F9E5B421961A53EBB386185E002C5C79C1BAC19D8089577269746554696D65828662696E617279868800056F524B1AC400C19980895465737456616C7565828662696E617279868400000309", //
                "",
                //
        };

        for (String data : dataStrings) {
            packetDecode(DatatypeConverter.parseHexBinary(data.toUpperCase().replaceFirst("C001BA5E1225EFFF0000000000000001", "")));
        }
    }

    @Test
    public void generateSampleModificationRequest() {
        String[] entryDataStrings = new String[] { //
                "E140C6808F636C69656E742D6465762E746573748289616C6C5F747970657386857EBE0941C88881038AA04F5E0569913F87FF7E25017F825AD5A366380D05D3DB5D8EFFC05B51086E66A98CA0CBE9E96873DDEBB169F414F0C164FDDC53E72D56A36D4A3C26D5138AF1426A538E813CFC94FAFE9C9E7845F446D091C12C74D44C61A0923C00FEC133D552F102CD9D592726736EC35295B3863D3EC3AE95CE644503685DE14E76B440E784586175FCD5FD755FE172C0623AF3FDB0047C22AECAF5B074FEA7B87C9B26D14251D19C8082496482847575696486907606FC028C1944EE99BEA24FC1449008D1B380876642696E617279828662696E61727984A06A49D4FC8AFD1846672644E5BA70B518B5F106759D12FC26619C4081A1CC1A37D1B5808866426F6F6C65616E8287626F6F6C65616E84A0CAE62207D2E809E3F65D101DA7DC21C406244B20B91FBD496A83402F02E55CCBD1B5808866446563696D616C8287646563696D616C84A035B77925583BFD5EFC86BED4F55CDE85505B894E1E7360F21A854090D39C8A4ED1B3808766446F75626C658286646F75626C6584A033E2DB659C905B16F3244EE6E04EE9921EB510148917700C82B6EF26BFD3C053D1B78089664475726174696F6E82886475726174696F6E84A047CA4AD678B5580200643E94596163E7AF5546E39C0A191A5B040924E388D4CDD1B1808666466C6F61748285666C6F617484A0014904BE5EEFDC3C9A063232F5C027F0F50877135A40391F28752FE06F1A6D95D1B5808866496E74656765728287696E746567657284A0C51D952132E4C06C79087585ACB28BB609BC24B7B5DD4C9989425809715ED197D1AF8085664C6F6E6782846C6F6E6784A0F75986856290C317BD5F817BC080D2898751254159ADC3302806EB5C0012DD4ED1D4808766537472696E678286737472696E6786C1636F6D2E7469657364622E70726F746F636F6C2E763072302E746573742E54696573444250726F746F636F6C5630523047656E6572617465403363343139363331D1AF80856654696D65828474696D6584A0A63CCA12CE3544C712745F7506C3D2F845A7AB69F5EDBDD094754FFCA7F9C49D", //
                "E140A4808F636C69656E742D6465762E746573748289616C6C5F747970657386857EBE0941C88881018CA09A2FC5F82D9A2ACE7A1F07BA0D3D243DCC67E8DFBFB3C4412E2178D15E59A6528E813CFC94FAFE9C9E7845F446D091C12C74D44C61A0923C00FEC1958AEB999D5550B8AA47AE3348B482F2E92C18D17A957B79A8ECBECE8F70726C7443523A9E20B0E952F1D51FB721871B1F48FEF145D2940E59CF39339739EB5D26D14251D19C8082496482847575696486907606FC028C1944EE99BEA24FC1449008D1B380876642696E617279828662696E61727984A0A319749EF77B897C33D86FD09842962E9807A246010711AC9EEF1B206A792691D1B5808866426F6F6C65616E8287626F6F6C65616E84A0CAE62207D2E809E3F65D101DA7DC21C406244B20B91FBD496A83402F02E55CCBD1B5808866446563696D616C8287646563696D616C84A0EBF17D9D8F5491FFC07B1331CB8B008C37AB85A1DC3F0309C8D96A974AC7FF25D1B3808766446F75626C658286646F75626C6584A02B2444E1F794887B7F0B8E9EA4C3CBB4DCC101715BE3CACAC59065518436DD75D1B78089664475726174696F6E82886475726174696F6E84A047CA4AD678B5580200643E94596163E7AF5546E39C0A191A5B040924E388D4CDD1B1808666466C6F61748285666C6F617484A00C810B124089360422E34183F5802A8B69A90BF3AC7406385C450648BA74515CD1B5808866496E74656765728287696E746567657284A0E77E340AC5AEE1359801CFD340DB9C387E797284AB72E1C7A1C84883B1CA06D3D1AF8085664C6F6E6782846C6F6E6784A014EB7C8CBC42818EF3F2D88599BC18B8FEC56C7CA6A8053FDAA7B9B5BD1348ABD1D4808766537472696E678286737472696E6786C1636F6D2E7469657364622E70726F746F636F6C2E763072302E746573742E54696573444250726F746F636F6C5630523047656E6572617465403363343139363331D1AF80856654696D65828474696D6584A05BDD4DBA601177373C27ED4EF4D47D86F9E5B421961A53EBB386185E002C5C79",
                //
        };

        for (String data : entryDataStrings) {
            packetDecode(generateModificationRequest(DatatypeConverter.parseHexBinary(data.toUpperCase().replaceFirst("C001BA5E1225EFFF0000000000000001", ""))));
        }
    }

    HashMap<TiesDBType, EBMLReadFormat<?>> formatMap = new HashMap<>();
    {
        formatMap.put(MESSAGE_ID, BigIntegerFormat.INSTANCE);
        formatMap.put(ERROR_MESSAGE, UTF8StringFormat.INSTANCE);
        formatMap.put(ENTRY_TABLESPACE_NAME, UTF8StringFormat.INSTANCE);
        formatMap.put(ENTRY_TABLE_NAME, UTF8StringFormat.INSTANCE);
        formatMap.put(FIELD_NAME, UTF8StringFormat.INSTANCE);
        formatMap.put(FIELD_TYPE, ASCIIStringFormat.INSTANCE);
        // formatMap.put(ENTRY_TYPE, IntegerFormat.INSTANCE);
        formatMap.put(ENTRY_VERSION, LongFormat.INSTANCE);
        formatMap.put(ENTRY_NETWORK, IntegerFormat.INSTANCE);
        formatMap.put(ENTRY_TIMESTAMP, DateFormat.INSTANCE);
        formatMap.put(CHEQUE_TIMESTAMP, DateFormat.INSTANCE);
        formatMap.put(CHEQUE_RANGE, UUIDFormat.INSTANCE);
        formatMap.put(CHEQUE_NUMBER, LongFormat.INSTANCE);
        formatMap.put(CHEQUE_AMOUNT, BigIntegerFormat.INSTANCE);

        formatMap.put(RECOLLECTION_TABLESPACE_NAME, UTF8StringFormat.INSTANCE);
        formatMap.put(RECOLLECTION_TABLE_NAME, UTF8StringFormat.INSTANCE);
        formatMap.put(RET_COMPUTE_ALIAS, UTF8StringFormat.INSTANCE);
        formatMap.put(RET_COMPUTE_TYPE, ASCIIStringFormat.INSTANCE);
        formatMap.put(RET_FIELD, UTF8StringFormat.INSTANCE);
        formatMap.put(FUNCTION_NAME, UTF8StringFormat.INSTANCE);
        formatMap.put(FUN_ARGUMENT_REFERENCE, UTF8StringFormat.INSTANCE);
        formatMap.put(ARG_STATIC_TYPE, ASCIIStringFormat.INSTANCE);
        formatMap.put(FILTER_FIELD, UTF8StringFormat.INSTANCE);
    }

    public void packetDecode(byte[] encData) {
        // TiesDBType x = TiesDBType.MODIFICATION_RESPONSE;
        decode(encData, Context.ROOT, r -> {
            int i = 0;
            while (r.hasNext()) {
                EBMLEvent e = r.next();
                EBMLType type = e.get();
                if (e.getType().equals(CommonEventType.BEGIN)) {

                    char[] tab = new char[i];
                    Arrays.fill(tab, '\t');
                    System.out.print(tab);
                    i++;

                    System.out.print("<" + type + " code=\"" + type.getEBMLCode().toHexString() + "\"");
                    if (type.getContext().equals(Context.VALUE)) {
                        EBMLReadFormat<?> format = formatMap.get(type);
                        if (null == format) {
                            System.out.print(" format=\"Hex\">" + DatatypeConverter.printHexBinary(BytesFormat.INSTANCE.read(r)));
                        } else {
                            System.out.print(
                                    " format=\"" + format.getClass().getSimpleName().replaceAll("Format$", "") + "\">" + format.read(r));
                        }
                    } else {
                        System.out.println(">");
                    }
                } else if (e.getType().equals(CommonEventType.END)) {

                    i--;
                    if (!type.getContext().equals(Context.VALUE)) {
                        char[] tab = new char[i];
                        Arrays.fill(tab, '\t');
                        System.out.print(tab);
                    }

                    System.out.println("</" + type + ">");
                } else {
                    fail("Wrong event " + e);
                }
            }
            System.out.println("<!--\n" + DatatypeConverter.printHexBinary(encData) + "\n-->");
            System.out.println();
        });
    }

}
