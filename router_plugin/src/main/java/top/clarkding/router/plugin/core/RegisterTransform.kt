package top.clarkding.router.plugin.core

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.utils.FileUtils
import com.mobu.router.plugin.util.LogUtil
import com.mobu.router.plugin.util.REGISTER_NAME
import com.mobu.router.plugin.util.RegisterCodeUtil
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.jar.JarFile

/**
 * Transform：Google提供的插件方法，允许第三方打包dex前操作.class文件
 * 注册步骤：
 * 1. 找到各个Module生成的IRouter的实现类
 * 2. 找到ARouter
 * 3. 将IRouter的实现类全部注册到ARouter的init中
 */
class RegisterTransform(private val pkgName: String): Transform() {

    private var mDstFile: File? = null
    private val mActList = mutableListOf<String>()
    private val mBindingList = mutableListOf<String>()

    private val mARouterName = "$pkgName/ARouter.class"
    private val mIRouterName = "$pkgName/IRouterAct"

    /**
     * Transform名称
     */
    override fun getName(): String {
        return REGISTER_NAME
    }

    /**
     * 需要处理的数据类型，有两种枚举类型:
     * 1. CLASSES: 代表处理的 java的class文件，返回TransformManager.CONTENT_CLASS
     * 2. RESOURCES: 代表要处理 java的资源，返回TransformManager.CONTENT_RESOURCES
     */
    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
        return TransformManager.CONTENT_CLASS
    }

    /**
     * 指Transform要操作内容的范围:
     * 1. EXTERNAL_LIBRARIES ： 只有外部库
     * 2. PROJECT ： 只有项目内容
     * 3. PROJECT_LOCAL_DEPS ： 只有项目的本地依赖(本地jar)
     * 4. PROVIDED_ONLY ： 只提供本地或远程依赖项
     * 5. SUB_PROJECTS ： 只有子项目
     * 6. SUB_PROJECTS_LOCAL_DEPS： 只有子项目的本地依赖项(本地jar)
     * 7. TESTED_CODE ：由当前变量(包括依赖项)测试的代码
     * 如果要处理所有的class字节码，返回TransformManager.SCOPE_FULL_PROJECT
     */
    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    /**
     * 增量编译开关：当我们开启增量编译的时候，相当input包含了changed/removed/added三种状态
     * 1. NOTCHANGED: 当前文件不需处理，甚至复制操作都不用
     * 2. ADDED、CHANGED: 正常处理，输出给下一个任务
     * 3. REMOVED: 移除outputProvider获取路径对应的文件
     */
    override fun isIncremental(): Boolean {
        return false
    }

    /**
     * 处理中转的函数
     * 如果拿取了getInputs()的输入进行消费，则transform后必须再输出给下一级
     * 如果拿取了getReferencedInputs()的输入，则不应该被transform
     * 是否增量编译要以transformInvocation.isIncremental()为准
     */
    override fun transform(transformInvocation: TransformInvocation?) {
        super.transform(transformInvocation)

        // 输入地
        transformInvocation?.inputs?.forEach {
            // Jar包集合(如AppCompatActivity所在aar)，一般module中的文件都会被编译在jar里
            it.jarInputs.forEach { jar ->
                LogUtil.d("jar in directory: ${jar.file.absolutePath}")

                // 文件输出路径
                // getContentLocation查询下一个输出路径，注意Format为JAR
                transformInvocation.outputProvider?.let { provider ->
                    val destPath = provider.getContentLocation(
                        jar.name,
                        jar.contentTypes,
                        jar.scopes,
                        Format.JAR)
                    scanJar(jar.file, destPath)
                    // 将jar文件复制给下一个transform
                    FileUtils.copyFile(jar.file, destPath)
                }
            }

            // class文件集合：只有com.android.application模块才走class文件集合，注意Format为DIRECTORY
            it.directoryInputs.forEach { directory ->
                LogUtil.d("class in directory: ${directory.file.absolutePath}")

                transformInvocation.outputProvider?.let { provider ->

                    // 文件输出路径
                    // getContentLocation查询下一个输出路径
                    val destPath = provider.getContentLocation(
                        directory.name,
                        directory.contentTypes,
                        directory.scopes,
                        Format.DIRECTORY
                    )
                    findTarget(directory.file, directory.file.absolutePath)
                    // 将class文件复制给下一个transform，注意：此处是文件夹，所以要用copyDirectory
                    FileUtils.copyDirectory(directory.file, destPath)
                }
            }
        }

        mDstFile?.let {
            RegisterCodeUtil.instance.interInitCode(mActList, it, pkgName)
        }
    }

    /*
     * 遍历处理Jar包(系统Jar + 各module编译成的jar)，找出ARouter以及各个module生成的IRouter实现类
     */
    @Throws(IOException::class)
    private fun scanJar(src: File, dest: File) {
        val file = JarFile(src)
        val enumeration = file.entries()
        while (enumeration.hasMoreElements()) {
            val jarEntry = enumeration.nextElement()
            val entryName = jarEntry.name
            // 类名
            LogUtil.d("scanJar filePath: $entryName")
            if (entryName == mARouterName) {
                LogUtil.d("scanJar ARouter entryName: $entryName")
                mDstFile = dest
                break
            } else if (shouldProcessClass(entryName)) {
                LogUtil.d("scanJar shouldProcessClass entryName: $entryName")
                val inputStream = file.getInputStream(jarEntry)
                scanClass(inputStream)
            }
        }
    }

    /*
     * 通过ClassVisitor过滤出继承了IRouter的类
     */
    @Throws(IOException::class)
    private fun scanClass(inputStream: InputStream) {
        val cr = ClassReader(inputStream)
        val cv = ScanClassVisitor(Opcodes.ASM5)
        cr.accept(cv, ClassReader.EXPAND_FRAMES)
        inputStream.close()
    }

    /*
     * 判断是否是要处理的文件
     */
    private fun shouldProcessClass(entryName: String?): Boolean {
        return entryName != null && entryName.startsWith(pkgName)
    }

    /*
     * 遍历主module生成的IRouter实现类
     */
    private fun findTarget(clazz: File, fileName: String) {
        if (clazz.isDirectory) {
            clazz.listFiles()?.forEach { subFile ->
                findTarget(subFile, fileName)
            }
        } else {
            val filePath = clazz.absolutePath
            if (!filePath.endsWith(".class")) {
                return
            }
            if (filePath.contains("R$")
                    || filePath.contains("R.class")
                    || filePath.contains("BuildConfig.class")) {
                return
            }
            // 注意检查replace后的格式是否与pkgName相对应
            val curPath = filePath.replace(fileName, "")
                .replace("\\\\", "/")
                .replace("\\", "/")
                .removeRange(0, 1)
            if (shouldProcessClass(curPath)) {
                try {
                    // 获得IRouterLoad接口实现类，并记录
                    scanClass(FileInputStream(filePath))
                } catch (err: IOException) {
                    err.printStackTrace()
                }
            }
        }
    }

    inner class BindingClassVisitor(api: Int): ClassVisitor(api) {
        override fun visit(
            version: Int,
            access: Int,
            name: String?,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?
        ) {
            super.visit(version, access, name, signature, superName, interfaces)

            LogUtil.d("ScanClassVisitor: $mIRouterName")
            /*
             * 过滤出IRouter的实现类，并放入mRegisterList，方便后面插入ARouter的init
             */
            interfaces?.forEach { interfaceName ->
                LogUtil.d("ScanClassVisitor $name, interfaceName: $interfaceName")
                if (interfaceName == "androidx.databinding.DataBinderMapper") {
                    // 记录需要注册的apt生成类(所有实现了IRouter的类)
                    LogUtil.d("visit name: $name")
                    name?.let { clsName ->
                        if (!mBindingList.contains(clsName)) {
                            mBindingList.add(clsName)
                        }
                    }
                }
            }
        }
    }

    /**
     * ClassVisitor就是每个类对应的Asm检查器，用来判断当前类是否符合某个特征
     */
    inner class ScanClassVisitor(api: Int): ClassVisitor(api) {

        override fun visit(
            version: Int,
            access: Int,
            name: String?,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?
        ) {
            super.visit(version, access, name, signature, superName, interfaces)

            LogUtil.d("ScanClassVisitor: $mIRouterName")
            /*
             * 过滤出IRouter的实现类，并放入mRegisterList，方便后面插入ARouter的init
             */
            interfaces?.forEach { interfaceName ->
                LogUtil.d("ScanClassVisitor $name, interfaceName: $interfaceName")
                if (interfaceName == mIRouterName) {
                    // 记录需要注册的apt生成类(所有实现了IRouter的类)
                    LogUtil.d("visit name: $name")
                    name?.let { clsName ->
                        if (!mActList.contains(clsName)) {
                            mActList.add(clsName)
                        }
                    }
                }
            }
        }
    }
}
