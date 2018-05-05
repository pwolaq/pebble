package com.mitchellbosecke.pebble.attributes;

import com.mitchellbosecke.pebble.error.ClassAccessException;
import com.mitchellbosecke.pebble.template.EvaluationContextImpl;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

public class MemberCacheUtils {
  private final ConcurrentHashMap<MemberCacheKey, Member> memberCache = new ConcurrentHashMap<>(100, 0.9f, 1);

  Member getMember(Object instance, String attributeName) {
    return this.memberCache.get(new MemberCacheKey(instance.getClass(), attributeName));
  }

  Member cacheMember(Object instance,
                     String attributeName,
                     Object[] argumentValues,
                     EvaluationContextImpl context,
                     String filename,
                     int lineNumber) {
    if (argumentValues == null) {
      argumentValues = new Object[0];
    }
    Class<?>[] argumentTypes = new Class<?>[argumentValues.length];

    for (int i = 0; i < argumentValues.length; i++) {
      Object o = argumentValues[i];
      if (o == null) {
        argumentTypes[i] = null;
      } else {
        argumentTypes[i] = o.getClass();
      }
    }

    Member member = this.reflect(instance, attributeName, argumentTypes, filename, lineNumber, context.isAllowGetClass());
    if (member != null) {
      this.memberCache.put(new MemberCacheKey(instance.getClass(), attributeName), member);
    }
    return member;
  }

  /**
   * Performs the actual reflection to obtain a "Member" from a class.
   */
  private Member reflect(Object object, String attributeName, Class<?>[] parameterTypes, String filename, int lineNumber, boolean allowGetClass) {

    Class<?> clazz = object.getClass();

    // capitalize first letter of attribute for the following attempts
    String attributeCapitalized = Character.toUpperCase(attributeName.charAt(0)) + attributeName.substring(1);

    // check get method
    Member result = this.findMethod(clazz, "get" + attributeCapitalized, parameterTypes, filename, lineNumber, allowGetClass);

    // check is method
    if (result == null) {
      result = this.findMethod(clazz, "is" + attributeCapitalized, parameterTypes, filename, lineNumber, allowGetClass);
    }

    // check has method
    if (result == null) {
      result = this.findMethod(clazz, "has" + attributeCapitalized, parameterTypes, filename, lineNumber, allowGetClass);
    }

    // check if attribute is a public method
    if (result == null) {
      result = this.findMethod(clazz, attributeName, parameterTypes, filename, lineNumber, allowGetClass);
    }

    // public field
    if (result == null) {
      try {
        result = clazz.getField(attributeName);
      } catch (NoSuchFieldException | SecurityException e) {
      }
    }

    if (result != null) {
      ((AccessibleObject) result).setAccessible(true);
    }

    return result;
  }

  /**
   * Finds an appropriate method by comparing if parameter types are
   * compatible. This is more relaxed than class.getMethod.
   */
  private Method findMethod(Class<?> clazz, String name, Class<?>[] requiredTypes, String filename, int lineNumber, boolean allowGetClass) {
    if (!allowGetClass && name.equals("getClass")) {
      throw new ClassAccessException(lineNumber, filename);
    }

    Method result = null;

    Method[] candidates = clazz.getMethods();

    for (Method candidate : candidates) {
      if (!candidate.getName().equalsIgnoreCase(name)) {
        continue;
      }

      Class<?>[] types = candidate.getParameterTypes();

      if (types.length != requiredTypes.length) {
        continue;
      }

      boolean compatibleTypes = true;
      for (int i = 0; i < types.length; i++) {
        if (requiredTypes[i] != null && !this.widen(types[i]).isAssignableFrom(requiredTypes[i])) {
          compatibleTypes = false;
          break;
        }
      }

      if (compatibleTypes) {
        result = candidate;
        break;
      }
    }
    return result;
  }

  /**
   * Performs a widening conversion (primitive to boxed type)
   */
  private Class<?> widen(Class<?> clazz) {
    Class<?> result = clazz;
    if (clazz == int.class) {
      result = Integer.class;
    } else if (clazz == long.class) {
      result = Long.class;
    } else if (clazz == double.class) {
      result = Double.class;
    } else if (clazz == float.class) {
      result = Float.class;
    } else if (clazz == short.class) {
      result = Short.class;
    } else if (clazz == byte.class) {
      result = Byte.class;
    } else if (clazz == boolean.class) {
      result = Boolean.class;
    }
    return result;
  }

  private class MemberCacheKey {
    private final Class<?> clazz;
    private final String attributeName;

    private MemberCacheKey(Class<?> clazz, String attributeName) {
      this.clazz = clazz;
      this.attributeName = attributeName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || this.getClass() != o.getClass()) return false;

      MemberCacheKey that = (MemberCacheKey) o;

      if (!this.clazz.equals(that.clazz)) return false;
      return this.attributeName.equals(that.attributeName);

    }

    @Override
    public int hashCode() {
      int result = this.clazz.hashCode();
      result = 31 * result + this.attributeName.hashCode();
      return result;
    }
  }
}
