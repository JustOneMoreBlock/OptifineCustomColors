package com.github.optifinecustomcolors;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class DyeColorHooks {
    private static final String[] COLOR_PROPERTIES = {
        "mcpatcher/color.properties",
        "optifine/color.properties"
    };

    private static final Map<Class<?>, Field> COLOR_FIELDS =
        Collections.synchronizedMap(new HashMap<Class<?>, Field>());
    private static final Map<Object, Integer> ORIGINAL_COLORS =
        Collections.synchronizedMap(new IdentityHashMap<Object, Integer>());

    private static volatile IdentityHashMap<Object, Integer> bannerColors =
        new IdentityHashMap<Object, Integer>();

    private DyeColorHooks() {
    }

    public static void reload(Object resourceManager) {
        Properties properties = new Properties();
        boolean loaded = false;

        for (int i = 0; i < COLOR_PROPERTIES.length; i++) {
            loaded |= loadProperties(resourceManager, COLOR_PROPERTIES[i], properties);
        }

        if (!loaded) {
            bannerColors = new IdentityHashMap<Object, Integer>();
            return;
        }

        bannerColors = readDyeColors(properties);
    }

    public static int getBannerColor(Object mapColor) {
        if (mapColor == null) {
            return 0xFFFFFF;
        }

        Integer customColor = bannerColors.get(mapColor);
        if (customColor != null) {
            return customColor.intValue();
        }

        return getOriginalColor(mapColor);
    }

    private static boolean loadProperties(Object resourceManager, String path, Properties properties) {
        InputStream stream = null;
        try {
            Object resourceLocation = newResourceLocation(path);
            Object resource = invokeResource(resourceManager, resourceLocation);
            stream = invokeInputStream(resource);
            properties.load(stream);
            return true;
        } catch (IOException e) {
            return false;
        } catch (ReflectiveOperationException e) {
            return false;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static Object newResourceLocation(String path) throws ReflectiveOperationException {
        Class<?> resourceLocationClass = findClass("jy", "net.minecraft.util.ResourceLocation");
        Constructor<?> constructor = resourceLocationClass.getConstructor(String.class);
        return constructor.newInstance(path);
    }

    private static Object invokeResource(Object resourceManager, Object resourceLocation)
        throws ReflectiveOperationException, IOException {
        Class<?> locationClass = resourceLocation.getClass();
        Method method = findMethod(resourceManager.getClass(), "a", locationClass);

        if (method == null) {
            method = findMethod(resourceManager.getClass(), "getResource", locationClass);
        }

        if (method == null) {
            throw new NoSuchMethodException("getResource");
        }

        try {
            return method.invoke(resourceManager, resourceLocation);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw e;
        }
    }

    private static InputStream invokeInputStream(Object resource) throws ReflectiveOperationException {
        Method method = findNoArgMethod(resource.getClass(), "b");
        if (method == null) {
            method = findNoArgMethod(resource.getClass(), "getInputStream");
        }
        if (method == null) {
            throw new NoSuchMethodException("getInputStream");
        }
        return (InputStream) method.invoke(resource);
    }

    private static IdentityHashMap<Object, Integer> readDyeColors(Properties properties) {
        IdentityHashMap<Object, Integer> colors = new IdentityHashMap<Object, Integer>();
        Map<String, Object> dyeMap = getDyeMap();

        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("dye.")) {
                continue;
            }

            String dyeName = key.substring("dye.".length());
            Object mapColor = dyeMap.get(dyeName);
            Integer color = parseColor(properties.getProperty(key));

            if (mapColor != null && color != null) {
                colors.put(mapColor, color);
            }
        }

        return colors;
    }

    private static Map<String, Object> getDyeMap() {
        Map<String, Object> dyeMap = new HashMap<String, Object>();

        try {
            Class<?> dyeClass = findClass("zd", "net.minecraft.item.EnumDyeColor");
            Method valuesMethod = dyeClass.getMethod("values");
            Method mapColorMethod = findNoArgMethod(dyeClass, "e");
            Method serializedNameMethod = findNoArgMethod(dyeClass, "l");
            Method alternateNameMethod = findNoArgMethod(dyeClass, "d");

            Object[] dyes = (Object[]) valuesMethod.invoke(null);
            for (int i = 0; i < dyes.length; i++) {
                Object dye = dyes[i];
                Object mapColor = mapColorMethod.invoke(dye);

                addName(dyeMap, serializedNameMethod, dye, mapColor);
                addName(dyeMap, alternateNameMethod, dye, mapColor);
            }
        } catch (ReflectiveOperationException e) {
            return dyeMap;
        }

        Object lightBlue = dyeMap.get("light_blue");
        if (lightBlue != null) {
            dyeMap.put("lightBlue", lightBlue);
        }

        Object silver = dyeMap.get("silver");
        if (silver != null) {
            dyeMap.put("light_gray", silver);
        }

        Object lightGray = dyeMap.get("light_gray");
        if (lightGray != null) {
            dyeMap.put("silver", lightGray);
        }

        return dyeMap;
    }

    private static void addName(Map<String, Object> dyeMap, Method method, Object dye, Object mapColor)
        throws ReflectiveOperationException {
        if (method == null) {
            return;
        }

        Object value = method.invoke(dye);
        if (value instanceof String) {
            dyeMap.put((String) value, mapColor);
        }
    }

    private static Integer parseColor(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        if (normalized.length() != 6) {
            return null;
        }

        try {
            return Integer.valueOf(Integer.parseInt(normalized, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int getOriginalColor(Object mapColor) {
        Integer cached = ORIGINAL_COLORS.get(mapColor);
        if (cached != null) {
            return cached.intValue();
        }

        int color = readMapColor(mapColor);
        ORIGINAL_COLORS.put(mapColor, Integer.valueOf(color));
        return color;
    }

    private static int readMapColor(Object mapColor) {
        try {
            Field field = getColorField(mapColor.getClass());
            return field.getInt(mapColor) & 0xFFFFFF;
        } catch (ReflectiveOperationException e) {
            return 0xFFFFFF;
        }
    }

    private static Field getColorField(Class<?> mapColorClass) throws NoSuchFieldException {
        Field cached = COLOR_FIELDS.get(mapColorClass);
        if (cached != null) {
            return cached;
        }

        Field field = findField(mapColorClass, "L", "colorValue", "field_76291_p");
        field.setAccessible(true);
        COLOR_FIELDS.put(mapColorClass, field);
        return field;
    }

    private static Field findField(Class<?> type, String... names) throws NoSuchFieldException {
        for (int i = 0; i < names.length; i++) {
            try {
                Field field = type.getDeclaredField(names[i]);
                if (field.getType() == Integer.TYPE) {
                    return field;
                }
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(type.getName() + ".color");
    }

    private static Class<?> findClass(String... names) throws ClassNotFoundException {
        ClassNotFoundException last = null;
        for (int i = 0; i < names.length; i++) {
            try {
                return Class.forName(names[i]);
            } catch (ClassNotFoundException e) {
                last = e;
            }
        }
        throw last;
    }

    private static Method findMethod(Class<?> type, String name, Class<?> parameterType) {
        Method[] methods = type.getMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (method.getName().equals(name)
                && parameterTypes.length == 1
                && parameterTypes[0].isAssignableFrom(parameterType)
                && !isListReturn(method)) {
                return method;
            }
        }
        return null;
    }

    private static Method findNoArgMethod(Class<?> type, String name) {
        Method[] methods = type.getMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (method.getName().equals(name)
                && method.getParameterTypes().length == 0
                && !Modifier.isStatic(method.getModifiers())) {
                return method;
            }
        }
        return null;
    }

    private static boolean isListReturn(Method method) {
        return "java.util.List".equals(method.getReturnType().getName())
            || method.getReturnType().getName().toLowerCase(Locale.ROOT).contains("list");
    }
}
