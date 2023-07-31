package top.clarkding.router.plugin.util

import com.sun.org.apache.bcel.internal.generic.*
import org.apache.commons.io.IOUtils
import org.objectweb.asm.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

/**
 * 代码插入工具
 */
class RegisterCodeUtil {

    companion object {

        val instance by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            RegisterCodeUtil()
        }
    }

    /*
     * 插入ARouter init
     * destName: "com/freespace/android/router/ARouter.class"
     */
    fun interInitCode(registerList: List<String>,
                      jarFile: File,
                      pkgName: String) {
        // 临时文件
        val optJar = File(jarFile.parent, jarFile.name + ".opt")
        LogUtil.d("optJar: $pkgName, ${registerList.size}, ${optJar.absolutePath}")
        if (optJar.exists()) {
            optJar.delete()
        }
        val file = JarFile(jarFile)
        val jarOutputStream = JarOutputStream(FileOutputStream(optJar))
        file.entries().asIterator().forEach { jarEntry ->
            val entryName = jarEntry.name
            val zipEntry = ZipEntry(entryName)
            val inputStream = file.getInputStream(jarEntry)
            jarOutputStream.putNextEntry(zipEntry)
            LogUtil.d("interInitCode entryName: $entryName")
            if (entryName == "$pkgName/ARouter.class") {
                // 写入内容
                referHackWhenInit(registerList, inputStream, pkgName)?.let { bytes ->
                    jarOutputStream.write(bytes)
                }
            } else {
                jarOutputStream.write(IOUtils.toByteArray(inputStream))
            }
            inputStream.close()
            jarOutputStream.closeEntry()
        }
        jarOutputStream.close()
        file.close()
        // 注：将修改好的jar包替换掉就的jar包
        if (jarFile.exists()) {
            jarFile.delete()
        }

        optJar.renameTo(jarFile)
    }

    @Throws(IOException::class)
    private fun referHackWhenInit(registerList: List<String>,
                                  inputStream: InputStream,
                                  pkgName: String): ByteArray? {
        // 准备内容：在已有的基础上修改
        LogUtil.d("referHackWhenInit start")
        val clazzReader = ClassReader(inputStream)
        val clazzWriter = ClassWriter(clazzReader, ClassWriter.COMPUTE_FRAMES)
        // 中间修改代码
        val clazzVisitor = RegClassVisitor(Opcodes.ASM5, clazzWriter, registerList,
            pkgName)
        clazzReader.accept(clazzVisitor, ClassReader.EXPAND_FRAMES)
        LogUtil.d("referHackWhenInit end")
        return clazzWriter.toByteArray()
    }

    /**
     * 方法检查器
     */
    inner class RegClassVisitor(api: Int, cv: ClassVisitor,
                                private val mRegList: List<String?>,
                                private val pkgName: String): ClassVisitor(api, cv) {

        override fun visitMethod(
            access: Int,
            name: String?,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor {

            var mv =  super.visitMethod(access, name, descriptor, signature, exceptions)
            LogUtil.d("RegMethodVisitor visitMethod $name")
            if (name == "init") {
                mv = RegMethodVisitor(
                   api,
                   mv,
                   mRegList,
                   pkgName)
            }
            return mv
        }
    }

    /**
     * @param clsName: "com/maniu/arouter/ARouter"
     * @param fieldName: "map"
     * @param fieldDes: "Ljava/util/Map;"
     * @param methodName: "putActivity"
     * @param methodDes: "(Ljava/util/Map;)V"
     */
    inner class RegMethodVisitor(api: Int,  mv: MethodVisitor,
                                 private val mRegList: List<String?>,
                                 private val pkgName: String): MethodVisitor(api,  mv) {

        override fun visitInsn(opcode: Int) {
            // 生成新init方法
            LogUtil.d("visitInsn: $opcode")
            // Opcodes.IRETURN：方法开始执行
            // Opcodes.RETURN：方法结束执行
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                mRegList.forEach { name ->
                    LogUtil.d("visitInsn registerList $name, $pkgName/ARouter")

                    // New IRouter实现类的对象
                    mv.visitTypeInsn(Opcodes.NEW, name)
                    // 复制栈顶 因为INVOKESPECIAL会消耗一个
                    mv.visitInsn(Opcodes.DUP)
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, name, "<init>", "()V", false)
                    // 调用构造方法需要传入隐式参数this
                    mv.visitVarInsn(Opcodes.ALOAD, 0)

                    // 访问ARouter的mActRouters对象
                    mv.visitFieldInsn(
                        Opcodes.GETFIELD,
                        "$pkgName/ARouter",
                        "mActRouters",
                        "Ljava/util/Map;")

                    // 插入IRouter实现类的putActivity方法
                    mv.visitMethodInsn(
                        Opcodes.INVOKEVIRTUAL,
                        name,
                        "putActivity",
                        "(Ljava/util/Map;)V",
                        false)
                }

            }
            super.visitInsn(opcode)
        }
    }
}