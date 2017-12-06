package com.tiesdb.protocol.v0.impl;

import static com.tiesdb.protocol.v0.impl.TiesEBMLType.*;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public enum TiesEBMLTag {

	x1E544945(Request), //
	xE0(RequestHeader), //
	xE1(RequestSignature), //
	;

	private static final Map<TiesEBMLType, TiesEBMLTag> idMap;
	static {
		TiesEBMLTag[] values = values();
		WeakHashMap<TiesEBMLType, TiesEBMLTag> map = new WeakHashMap<>();
		for (int i = 0; i < values.length; i++) {
			if (null != map.put(values[i].type, values[i])) {
				throw new InstantiationError("EBML duplicate ID 0" + values[i] + " for " + values[i].type);
			}
		}
		idMap = Collections.unmodifiableMap(map);
	}

	public static TiesEBMLTag getByType(TiesEBMLType type) {
		return idMap.get(type);
	}

	private final byte[] id;
	private final TiesEBMLType type;

	public TiesEBMLType getType() {
		return type;
	}

	private TiesEBMLTag(TiesEBMLType type) {
		Objects.requireNonNull(type);
		this.id = strToId(name().substring(1));
		type.protoType = new TiesEBMLType.ExtendedProtoType(type, id);
		this.type = type;
	}

	private byte[] strToId(String hexString) {
		int len = hexString.length();
		byte[] id = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			id[i / 2] = (byte) (//
			(Character.digit(hexString.charAt(i), 16) << 4) + Character.digit(hexString.charAt(i + 1), 16));
		}
		return checkEBMLCompatibility(id);
	}

	private byte[] checkEBMLCompatibility(byte[] id) {
		if (id.length == 0) {
			throw new InstantiationError("EBML ID could not be zero length");
		}
		int i = 0;
		byte snip = id[i++];
		int size = 1;
		while (snip == 0) {
			snip = id[i++];
			size += 7;
		}
		size += Integer.numberOfLeadingZeros(0xFF & snip) - 24;
		if (size != id.length) {
			throw new InstantiationError("EBML ID leading zero bits should conform the size in bytes");
		}
		return id;
	}

	static TiesEBMLType.ExtendedProtoType newProtoType(TiesEBMLType t) {
		return new TiesEBMLType.ExtendedProtoType(t, getByType(t).id);
	}

}
