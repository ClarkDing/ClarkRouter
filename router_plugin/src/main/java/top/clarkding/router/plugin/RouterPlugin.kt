package top.clarkding.router.plugin

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import top.clarkding.router.plugin.core.RegisterTransform
import top.clarkding.router.plugin.util.LogUtil

/**
 * 插件入口
 */
class RouterPlugin: Plugin<Project> {

    // 注解处理器生成类的目录
    private val pkgName = "top/clarking/android/router"

    // 插件的main函数
    // main函数         插件  apply函数
    override fun apply(project: Project) {
        // 判断是否是主工程: 只需要在主工程中注册Activity
        println("Router Start ")
        val isApp = project.plugins.hasPlugin(AppPlugin::class.java)
        println("Router Start isApp: $isApp")
        if (isApp) {
            LogUtil.d("Router Build Start")
            // 注册transform，BaseExtension：基础扩展对象
            project.extensions
                .getByType(BaseExtension::class.java)
                .registerTransform(RegisterTransform(pkgName))
        }
    }
}
