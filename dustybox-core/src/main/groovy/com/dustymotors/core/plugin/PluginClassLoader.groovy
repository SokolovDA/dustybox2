package com.dustymotors.core.plugin

import groovy.transform.CompileStatic
import java.net.URL
import java.net.URLClassLoader

@CompileStatic
class PluginClassLoader extends URLClassLoader {

    private static final List<String> DELEGATED_PACKAGE_PREFIXES = [
            'com.dustymotors.core.',
            'org.springframework.',
            'ch.qos.logback.',
            'org.slf4j.',
            'jakarta.',
            'java.',
            'javax.',
            'groovy.',
            'org.codehaus.groovy.',
            'org.apache.groovy.',
            'org.hibernate.',
            'org.postgresql.',
            'com.zaxxer.hikari.'
    ]

    PluginClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent)
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            // 1. Проверяем, уже загружен ли класс
            Class<?> c = findLoadedClass(name)
            if (c != null) return c

            // 2. Всегда делегируем системные классы родителю
            if (shouldDelegate(name)) {
                return super.loadClass(name, resolve)
            }

            // 3. Пробуем загрузить из своего JAR
            try {
                c = findClass(name)
                if (resolve) resolveClass(c)
                return c
            } catch (ClassNotFoundException e) {
                // 4. Если не нашли, делегируем родителю
                return super.loadClass(name, resolve)
            }
        }
    }

    private boolean shouldDelegate(String className) {
        DELEGATED_PACKAGE_PREFIXES.any { className.startsWith(it) }
    }
}