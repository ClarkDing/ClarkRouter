package top.clarkding.router.plugin.test

import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.utils.FileUtils
import com.mobu.router.plugin.util.LogUtil
import javassist.ClassPool
import java.io.File

class JavaTestTransform: Transform() {

    private val mClassPool = ClassPool.getDefault()

    override fun getName(): String {
        return "JavaTest"
    }

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
        return TransformManager.CONTENT_CLASS
    }

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    override fun isIncremental(): Boolean {
        return false
    }

    override fun transform(transformInvocation: TransformInvocation?) {
        super.transform(transformInvocation)

        transformInvocation?.let { invocation ->

            val outProvider = invocation.outputProvider
            invocation.inputs.forEach { input ->

                // copy the files of jar to next transform
                input.jarInputs.forEach { jarInput ->
                    val dest = outProvider.getContentLocation(jarInput.name,
                        jarInput.contentTypes, jarInput.scopes, Format.JAR)
                    FileUtils.copyFile(jarInput.file, dest)
                }

                // copy the files of class to next transform
                input.directoryInputs.forEach { clazzInput ->
                    val dirName = clazzInput.name
                    val dest = outProvider.getContentLocation(dirName,
                        clazzInput.contentTypes, clazzInput.scopes, Format.DIRECTORY)
                    val preFileName = clazzInput.file.absolutePath
                    try {
                        mClassPool.insertClassPath(preFileName)
                    } catch (err: Exception) {
                        err.printStackTrace()
                    }
                    findTarget(clazzInput.file, preFileName)
                    FileUtils.copyDirectory(clazzInput.file, dest)
                }
            }
        }
    }

    private fun findTarget(clazz: File, fileName: String) {
        LogUtil.d("findTarget: $fileName, ${clazz.absolutePath}")
        if (clazz.isDirectory) {
            clazz.listFiles()?.forEach {
                findTarget(it, fileName)
            }
        } else {
            val curPath = clazz.absolutePath
            if (!curPath.endsWith(".class", true)) {
                return
            }
            if (curPath.contains("R$") || curPath.contains("R.class")
                    || curPath.contains("BuildConfig.class")) {
                return
            }
            if (curPath.contains("Router")) {
                modify(clazz, fileName)
            }
        }
    }

    private fun modify(clazz: File, fileName: String) {
        LogUtil.d("modify: $fileName, ${clazz.absolutePath}")
        val curPath = clazz.absolutePath
        val clsName = curPath
            .replace(fileName, "")
            .replace("\\", ".")
            .replace("/", ".")
            .replace(".class", "")
            .substring(1)
        LogUtil.d("clazz: $clsName")
        try {
            // modify the source code
            val ctClass = mClassPool[clsName]
            val ctMethod = ctClass.getDeclaredMethod("init")
            val extraBody = "com.example.transform_demo.ActivityUtil.putActivity();"
            LogUtil.d("insertBefore: $clsName")
            ctMethod.insertBefore(extraBody)
            LogUtil.d("insertAfter: $clsName")
            ctClass.writeFile(fileName)
            ctClass.detach()
        } catch (err: Exception) {
            err.printStackTrace()
            LogUtil.e("err: ${err.message}")
        }

    }
}