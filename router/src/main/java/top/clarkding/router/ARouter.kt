package top.clarkding.router

import android.app.Activity
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * 组件化步骤：
 * 1. 通过编译器注解RouterCompiler，生成每个module对应的IRouter的实现类，该实现类会将module中注解的全部Activity收集起来(java -> class)
 * 2. 收集全部的IRouter实现类，并存入List(class -> dex)
 * 3. 收集完毕后，将list存入ARouter的init方法(通过javasist或asm)
 */
class ARouter {

    private val mActRouters: Map<String, Class<out AppCompatActivity>> = mutableMapOf()
    private val mServiceRouters: Map<String, Class<out Service>> = mutableMapOf()

    companion object {

        val instance by lazy(LazyThreadSafetyMode.NONE) {
            ARouter()
        }
    }

    fun goTarget(context: Context, target: String, params: Bundle? = null) {
        mActRouters[target]?.let {
            val targetIntent = Intent(context, it)
            if (context !is Activity) {
                targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            params?.let {
                targetIntent.putExtras(params)
            }
            context.startActivity(targetIntent)
        } ?: kotlin.run {
            throw RuntimeException("Act: $target hadn't registered")
        }
    }

    fun buildActIntent(context: Context, target: String, params: Bundle? = null): Intent {
        mActRouters[target]?.let {
            val targetIntent = Intent(Intent.ACTION_VIEW, Uri.EMPTY, context, it)
            if (context !is Activity) {
                targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            params?.let {
                targetIntent.putExtras(params)
            }
            return targetIntent
        } ?: kotlin.run {
            throw RuntimeException("Act: $target hadn't registered")
        }
    }

    fun buildActPending(context: Context, pendCode: Int, target: String, param: Bundle? = null): PendingIntent {
        mActRouters[target]?.let {
            val targetIntent = Intent(context, it)
            targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK.or(Intent.FLAG_ACTIVITY_CLEAR_TASK))
            param?.let {
                targetIntent.putExtras(param)
            }

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getActivity(
                context,
                pendCode,
                targetIntent,
                flags
            )
        } ?: kotlin.run {
            throw RuntimeException("Act: $target hadn't registered")
        }
    }

    fun init() {
    }
}