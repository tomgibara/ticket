/*
 * Copyright 2015 Tom Gibara
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.tomgibara.ticket;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.tomgibara.bits.BitStreamException;
import com.tomgibara.coding.CodedReader;
import com.tomgibara.coding.CodedStreams;
import com.tomgibara.coding.CodedWriter;

//TODO consider value of weakly caching these?
final class TicketAdapter<T> implements Serializable {

	// private statics

	private static final long serialVersionUID = 2192751899582213550L;

	private static final Field[] NO_FIELDS = new Field[0];
	private static final Object[] NO_VALUES = new Object[0];
	private static final Object[] DEFAULTS = {
		"",             (byte)0,        0.0f,           '\0',           (short)0,       null,           0L,             0,
		null,           null,           null,           0.0,            false,          null,           null,           null,
		null,           new byte[0],    new float[0],   new char[0],    new short[0],   null,           new long[0],    new int[0],
		null,           null,           null,           new double[0],  new boolean[0], null,           null,           null
	};

	private static int hash(Class<?> clss) {
		return (clss.getName().hashCode() >> 8) & 0xf;
	}

	private static boolean hasTicketFields(Class<?> i) {
		Method[] methods = i.getDeclaredMethods();
		for (Method method : methods) {
			if (method.isAnnotationPresent(TicketField.class)) return true;
		}
		return false;
	}

	private static String fieldName(String name) {
		// trim prefix
		if (name.startsWith("get")) {
			name = name.substring(3);
		} else if (name.startsWith("is")) {
			name = name.substring(2);
		}

		// count upper case characters
		int i = 0;
		int length = name.length();
		for (; i < length; i++) {
			if (Character.isLowerCase(name.charAt(i))) break;
		}

		// handle cases
		// whole string is lower case: eg. size -> size
		if (i == 0) return name;
		// starts with a single upper case character eg. Length -> length
		if (i == 1) return name.substring(0, 1).toLowerCase() + name.substring(1);
		// whole string is upper case (actually ambiguous): eg. HTML -> html
		if (i == length) return name.toLowerCase();
		// compound upper case first segment: eg. JVMName -> jvmName
		return name.substring(0, i - 1).toLowerCase() + name.substring(i - 1);
	}

	private static Field[] deriveFields(Class<?> iface) {
		if (iface == null) return NO_FIELDS;
		List<Field> list = new ArrayList<Field>();

		Method[] methods = iface.getDeclaredMethods();
		Map<String, Method> lookup = new HashMap<String, Method>();
		for (Method method : methods) {
			lookup.put(method.getName(), method);
		}

		for (Method method : methods) {
			TicketField ann = method.getAnnotation(TicketField.class);
			if (ann == null) continue;
			String name = method.getName();
			if (method.getParameterTypes().length != 0) {
				throw new IllegalArgumentException("Ticket field getter method " + name + " has parameters.");
			}
			Class<?> clss = method.getReturnType();
			if (clss == void.class) {
				throw new IllegalArgumentException("Ticket field getter method " + name + " is void.");
			}
			if (clss.isArray()) {
				Class<?> ct = clss.getComponentType();
				if (!ct.isEnum() && !ct.isPrimitive()) {
					throw new IllegalArgumentException("Ticket method " + name + " has non-primitive array type: " + ct.getName());
				}
			} else if (clss.isEnum()) {
				Object[] enums = clss.getEnumConstants();
				if (enums.length == 0) throw new IllegalArgumentException("Ticket method " + name + " has enum type " + clss.getName() + " without constants.");
			} else if (clss != String.class && !clss.isPrimitive()) {
				throw new IllegalArgumentException("Ticket method " + name + " has invalid type: " + clss.getName());
			}
			list.add( new Field(ann.value(), method, ann.secret()) );
		}
		if (list.isEmpty()) throw new IllegalArgumentException("No fields");
		Collections.sort(list);
		Field[] fields = (Field[]) list.toArray(new Field[list.size()]);
		if (fields[0].index < 0) throw new IllegalArgumentException("Negative index");
		for (int i = 0; i < fields.length; i++) {
			Field field = fields[i];
			if (field.index > i) throw new IllegalArgumentException("Missing index " + i);
			if (field.index < i) throw new IllegalArgumentException("Duplicate index " + i);
		}

		return fields;
	}

	private static Map<Method, Integer> computeLookup(Field[] fields) {
		Map<Method, Integer> map = new HashMap<Method, Integer>();
		for (Field field : fields) {
			map.put(field.getter, field.index);
		}
		return Collections.unmodifiableMap(map);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static Map<Class<?>, Object> prepareEnumDefaults(Field[] fields) {
		Map<Class<?>, Object> map = Collections.emptyMap();
		for (Field field : fields) {
			if (!field.enumed) continue;
			Class<?> clss = field.objType;
			Object value;
			if (field.array) {
				value = Array.newInstance(clss.getComponentType(), 0);
			} else {
				value = field.objType.getEnumConstants()[0];
			}
			switch (map.size()) {
			case 0:
				map = (Map) Collections.singletonMap(clss, value);
				break;
			default:
				map = new HashMap<Class<?>, Object>(map);
				map.put(clss, value);
			}
		}
		return map;
	}

	private static Object[] generateDefaults(Field[] fields) {
		if (fields.length == 0) return NO_VALUES;
		Map<Class<?>, Object> enumDefaults = prepareEnumDefaults(fields);
		int length = fields.length;
		Object[] defaults = new Object[length];
		for (int i = 0; i < length; i++) {
			Field field = fields[i];
			defaults[i] = field.enumed ? enumDefaults.get(field.objType) : DEFAULTS[field.hash];
		}
		return defaults;
	}

	// package statics

	static <T> TicketAdapter<T> newData(Class<? extends T> type) {
		if (type == null) throw new IllegalArgumentException("null type");
		if (type == Void.class) return new TicketAdapter<T>(type, null);
		if (type.isInterface()) return new TicketAdapter<T>(type, type);

		Class<?> iface;
		Class<?>[] ifaces = type.getInterfaces();
		switch (ifaces.length) {
		case 0 : iface = null; break;
		case 1 : iface = ifaces[0]; break;
		default:
			iface = null;
			for (Class<?> i : ifaces) {
				if (hasTicketFields(i)) {
					if (iface != null) throw new IllegalArgumentException("Type " + type.getName() + " has two interfaces containing ticket fields: " + iface.getName() + " and " + i.getName());
					iface = i;
					break;
				}
			}
		}
		return new TicketAdapter<T>(type, iface);
	}

	// fields

	private final Class<? extends T> type;
	private final Class<?> iface;

	private final Class<?>[] ifaces;
	private final Class<?> proxyClass;
	private final Field[] fields;
	private final Object[] defaults;
	private final Map<Method, Integer> lookup;
	private final Field[] openFields;
	private final Field[] secretFields;

	// constructors

	private TicketAdapter(Class<? extends T> type, Class<?> iface) {
		this.type = type;
		this.iface = iface;

		if (iface == null) {
			ifaces = null;
			proxyClass = null;
		} else {
			ifaces = new Class[] {iface};
			proxyClass = Proxy.getProxyClass(iface.getClassLoader(), ifaces);
		}
		fields = deriveFields(iface);
		defaults = generateDefaults(fields);
		lookup = computeLookup(fields);

		// finally, do the work of creating separate open/secret field arrays
		// they will be needed if the adapter is called on to separate private fields
		int openCount = 0;
		int secretCount = 0;
		for (Field field : fields) {
			if (field.secret) {
				secretCount ++;
			} else {
				openCount ++;
			}
		}
		openFields = openCount == 0 ? NO_FIELDS : new Field[openCount];
		secretFields = secretCount == 0 ? NO_FIELDS : new Field[secretCount];
		int oi = 0;
		int si = 0;
		for (int i = 0; i < fields.length; i++) {
			Field field = fields[i];
			if (field.secret) {
				secretFields[si++] = field;
			} else {
				openFields[oi++] = field;
			}
		}
	}

	// package accessors

	Class<?> getInterface() {
		return iface;
	}

	Class<? extends T> getType() {
		return type;
	}

	boolean isSecretive() {
		return secretFields.length > 0;
	}

	// package methods

	Object[] defaultValues(Object... values) {
		if (values == null || values.length == 0) return defaults.clone();
		Object[] checked = new Object[fields.length];
		int firstNull = -1;
		if (values != null) {
			if (values.length > checked.length) throw new IllegalArgumentException("too many values");
			for (int i = 0; i < values.length; i++) {
				Object value = values[i];
				if (value != null) {
					if (firstNull == i - 1) firstNull ++;
					Class<?> expected = fields[i].objType;
					if (value.getClass() != expected) throw new IllegalArgumentException("Invalid type for parameter " + i + " expected " + expected + " but got " + value.getClass());
					checked[i] = value;
				}
			}
		}
		for (int i = firstNull + 1; i < defaults.length; i++) {
			checked[i] = defaults[i];
		}
		return checked;
	}

	// requires that values array is complete and null-free
	@SuppressWarnings("unchecked")
	T adapt(Object... values) {
		if (iface == null) return null;
		return (T) Proxy.newProxyInstance(iface.getClassLoader(), ifaces, new Handler(lookup, values));
	}

	Object[] unadapt(T value) {
		if (value == null) return defaults.clone();
		// pre-adapted case
		if (value.getClass() == proxyClass) {
			Handler handler = (Handler) Proxy.getInvocationHandler(value);
			return handler.values.clone();
		}
		// general case
		int length = fields.length;
		Object[] values = new Object[length];
		for (int i = 0; i < length; i++) {
			Field field = fields[i];
			Object obj;
			try {
				obj = field.getter.invoke(value);
			} catch (IllegalAccessException e) {
				throw new TicketException("Cannot access field " + field.name, e);
			} catch (InvocationTargetException e) {
				throw new TicketException("Failed to access field " + field.name, e);
			}
			values[i] = obj == null ? defaults[i] : obj;
		}
		return values;
	}

	int write(CodedWriter w, boolean secret, Object... values) {
		if (values.length != fields.length) throw new IllegalArgumentException();
		Field[] fields = secret ? secretFields : openFields;
		int length = fields.length;
		int count = w.writePositiveInt(length);
		for (int i = 0; i < length; i++) {
			Field field = fields[i];
			Object obj = values[field.index];
			//note: this is possible if client generates ticket from own data impl
			switch (field.hash) {
			case /*String*/   0: count += CodedStreams.writeString(w, (String) obj);    break;
			case /*boolean*/ 12: count += w.getWriter().writeBoolean((Boolean) obj);    break;
			case /*byte*/     1: count += w.writeInt((Byte) obj);                       break;
			case /*short*/    4: count += w.writeInt((Short) obj);                      break;
			case /*char*/     3: count += w.writePositiveInt((Character) obj);          break;
			case /*int*/      7: count += w.writeInt((Integer) obj);                    break;
			case /*float*/    2: count += w.writeFloat((Float) obj);                    break;
			case /*long*/     6: count += w.writeLong((Long) obj);                      break;
			case /*double*/  11: count += w.writeDouble((Double) obj);                  break;
			case /*enum*/     5: count += CodedStreams.writeEnum(w, (Enum<?>) obj);     break;
			case /*enum a.*/ 21: count += CodedStreams.writeEnumArray(w, (Enum[]) obj); break;
			default /*array*/  : count += CodedStreams.writePrimitiveArray(w, obj);     break;
			}
		}
		return count;
	}

	T read(CodedReader r, boolean secret) throws TicketException {
		Object[] values = unadapt(null);
		read(r, secret, values);
		return adapt(values);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	void read(CodedReader r, boolean secret, Object... values) throws TicketException {
		Field[] fields = secret ? secretFields : openFields;
		int count;
		try {
			count = r.readPositiveInt();
			if (count == 0) {
				if (iface == null) return;
			} else if (count > fields.length) {
				throw new TicketException("Too many data fields " + count + " " + fields.length);
			} else {
				for (int i = 0; i < count; i++) {
					Field field = fields[i];
					final Object value;
					// note that the casts below will not flag overflows
					// this is less than ideal, but matches the current behaviour
					// of the CodedStreams.readArray methods
					switch (field.hash) {
					case /*String*/   0: value = CodedStreams.readString(r);       break;
					case /*boolean*/ 12: value = r.getReader().readBoolean();      break;
					case /*byte*/     1: value = (byte)  r.readInt();              break;
					case /*short*/    4: value = (short) r.readInt();              break;
					case /*char*/     3: value = (char)  r.readPositiveInt();      break;
					case /*int*/      7: value = r.readInt();                      break;
					case /*float*/    2: value = r.readFloat();                    break;
					case /*long*/     6: value = r.readLong();                     break;
					case /*double*/  11: value = r.readDouble();                   break;
					case /*enum*/     5:
						value = CodedStreams.readEnum(r, (Class) field.objType);   break;

					/* arrays */
					case /*boolean*/ 28: value = CodedStreams.readBooleanArray(r); break;
					case /*byte*/    17: value = CodedStreams.readByteArray(r);    break;
					case /*short*/   20: value = CodedStreams.readShortArray(r);   break;
					case /*char*/    19: value = CodedStreams.readCharArray(r);    break;
					case /*int*/     23: value = CodedStreams.readIntArray(r);     break;
					case /*float*/   18: value = CodedStreams.readFloatArray(r);   break;
					case /*long*/    22: value = CodedStreams.readLongArray(r);    break;
					case /*double*/  27: value = CodedStreams.readDoubleArray(r);  break;
					case /*enum a.*/ 21:
						value = CodedStreams.readEnumArray(r, (Class) field.objType.getComponentType());
						break;

					default            : throw new IllegalStateException();
					}
					values[field.index] = value;
				}
			}
		} catch (BitStreamException e) {
			throw new TicketException("Invalid ticket bits", e);
		}
	}

	// object methods

	@Override
	public int hashCode() {
		return type.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (!(obj instanceof TicketAdapter)) return false;
		TicketAdapter<?> that = (TicketAdapter<?>) obj;
		return this.type.equals(that.type);
	}

	@Override
	public String toString() {
		return type.getName() + " adapter";
	}

	// serialization methods

	private Object writeReplace() {
		return new Serial(type);
	}

	// private utility methods

	// inner classes

	private final static class Field implements Comparable<Field> {

		final int index;
		final String name;
		final Method getter;
		final boolean secret;
		final Class<?> objType;
		final boolean string;
		final boolean array;
		final boolean enumed;
		final int hash;

		Field(int index, Method getter, boolean secret) {
			this.index = index;
			this.getter = getter;
			this.secret = secret;

			name = fieldName(getter.getName());
			Class<?> type = getter.getReturnType();
			string = type == String.class;
			array = type.isArray();
			if (array) {
				enumed = type.getComponentType().isEnum();
			} else {
				enumed = type.isEnum();
			}
			if (string) {
				hash = 0;
			} else if (enumed) {
				hash = array ? 5 + 16 : 5;
			} else if (array) {
				hash = hash(type.getComponentType()) + 16;
			} else {
				hash = hash(type);
			}

			switch (hash) {
			case /*boolean*/ 12: objType = Boolean.class;   break;
			case /*byte*/     1: objType = Byte.class;      break;
			case /*short*/    4: objType = Short.class;     break;
			case /*char*/     3: objType = Character.class; break;
			case /*int*/      7: objType = Integer.class;   break;
			case /*float*/    2: objType = Float.class;     break;
			case /*long*/     6: objType = Long.class;      break;
			case /*double*/  11: objType = Double.class;    break;
			default  /*objs*/  : objType = type;
			}
		}

		public int compareTo(Field that) {
			return this.index - that.index;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) return true;
			if (!(obj instanceof Field)) return false;
			Field that = (Field) obj;
			return
					this.index == that.index &&
					this.getter == that.getter &&
					this.secret == that.secret;
		}

		@Override
		public int hashCode() {
			return index ^ getter.hashCode() ^ Boolean.valueOf(secret).hashCode();
		}
	}

	private static class Handler implements InvocationHandler {

		static final Method TO_STRING;
		static final Method EQUALS;
		static final Method HASH_CODE;

		static {
			try {
				Class<Object> clss = Object.class;
				TO_STRING = clss.getMethod("toString");
				EQUALS = clss.getMethod("equals", clss);
				HASH_CODE = clss.getMethod("hashCode");
			} catch (NoSuchMethodException e) {
				throw new RuntimeException("Failed to obtain Object method");
			}
		}

		private final Map<Method, Integer> lookup;
		private final Object[] values;

		Handler(Map<Method, Integer> lookup, Object[] values) {
			this.lookup = lookup;
			this.values = values;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			if (method.equals(TO_STRING)) {
				Map<String, Object> map = new HashMap<String, Object>();
				for (Entry<Method,Integer> entry : lookup.entrySet()) {
					map.put(fieldName(entry.getKey().getName()), values[entry.getValue()]);
				}
				return map.toString();
			}
			if (method.equals(HASH_CODE)) return values.hashCode();
			if (method.equals(EQUALS)) {
				Object obj = args[0];
				if (obj == proxy) return true;
				if (obj.getClass() != proxy.getClass()) return false;
				Handler that = (Handler) Proxy.getInvocationHandler(proxy);
				return this.values.equals(that.values);
			}
			return values[lookup.get(method)];
		}

	}

	private static class Serial implements Serializable {

		private static final long serialVersionUID = -7288797260847592989L;

		private final Class<?> type;

		Serial(Class<?> type) {
			this.type = type;
		}

		private Object readResolve() {
			return TicketAdapter.newData(type);
		}

	}
}
