package top.clarkding.router

import android.content.Context

/**
 * Core自动初始化步骤：
 * 1. 收集全部的IIniter实现类，并存入List(class -> dex)
 * 3. 收集完毕后，将list存入ARouter的init方法(通过javasist或asm)
 */
class AIniter {

    fun init(context: Context) {

    }
}