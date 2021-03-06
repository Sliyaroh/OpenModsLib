package openmods.reflection;

import java.lang.reflect.Field;

public class FieldAccessHelpers {

	public static int getIntField(final Class<?> klazz, Object target, String field) {
		return FieldAccessHelpers.getField(klazz, target, field, -1);
	}

	public static boolean getBooleanField(final Class<?> klazz, Object target, String field) {
		return FieldAccessHelpers.getField(klazz, target, field, false);
	}

	public static byte getByteField(final Class<?> klazz, Object target, String field) {
		return FieldAccessHelpers.getField(klazz, target, field, (byte)-1);
	}

	@SuppressWarnings("unchecked")
	public static <T> T getField(final Class<?> klazz, Object target, String field, T defaultValue) {
		try {
			Field f = ReflectionHelper.getField(klazz, field);
			Object o = f.get(target);
			if (o != null) return (T)o;
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
		return defaultValue;
	}

}
