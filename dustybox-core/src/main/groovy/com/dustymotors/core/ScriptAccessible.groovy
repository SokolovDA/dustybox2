package com.dustymotors.core

import java.lang.annotation.*

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@interface ScriptAccessible {
    // Маркерная аннотация для сервисов, доступных в скриптах
}