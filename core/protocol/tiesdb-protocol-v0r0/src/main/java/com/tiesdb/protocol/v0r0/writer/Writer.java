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
package com.tiesdb.protocol.v0r0.writer;

import java.math.BigInteger;

import com.tiesdb.protocol.exception.TiesDBProtocolException;
import com.tiesdb.protocol.v0r0.TiesDBProtocolV0R0.Conversation;
import com.tiesdb.protocol.v0r0.writer.ModificationResponseWriter.ModificationResponse;
import com.tiesdb.protocol.v0r0.writer.RecollectionResponseWriter.RecollectionResponse;

@FunctionalInterface
public interface Writer<T> {

    interface Response {

        interface Visitor<T> {

            T on(ModificationResponse response) throws TiesDBProtocolException;

            T on(RecollectionResponse response) throws TiesDBProtocolException;

        }

        <T> T accept(Visitor<T> v) throws TiesDBProtocolException;

        BigInteger getMessageId();

    }

    void accept(Conversation session, T t) throws TiesDBProtocolException;

}
