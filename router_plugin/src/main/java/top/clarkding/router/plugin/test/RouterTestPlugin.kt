package top.clarkding.router.plugin.test

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import java.io.FileOutputStream

/**
 * Test for generate an object of User auto
 */
class RouterTestPlugin: Plugin<Project> {

    override fun apply(project: Project) {
        // 通过asm生成class字节码

        //1.生成Class字节码
        val genClassByte = genClass()
        try {
            // 输出Class字节码文件
            val fos = FileOutputStream("${System.getProperty("user.dir")}\\User.class")
            println("------------RouterTestPlugin-------------------")
            fos.write(genClassByte)
            fos.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 生成class文件
     */
    private fun genClass(): ByteArray? {
        // 此处代码可以通过ASM ByteCode Viewer生成
        val classWriter = ClassWriter(0)
        classWriter.visit(
            Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER, "file/space/clean/plugin/User",
            null, "java/lang/Object", null
        )

        // 写构造函数
        val methodVisitor = classWriter.visitMethod(
            Opcodes.ACC_PUBLIC,
            "<init>", "()V", null, null
        )
        methodVisitor.visitCode()

        // javac 的帮助 this  为了能够用    this
        val label0 = Label()
        methodVisitor.visitLabel(label0)
        methodVisitor.visitLineNumber(3, label0)
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
        methodVisitor.visitMethodInsn(
            Opcodes.INVOKESPECIAL, "java/lang/Object",
            "<init>", "()V", false
        )
        methodVisitor.visitInsn(Opcodes.RETURN)

        val label1 = Label()
        methodVisitor.visitLabel(label1)
        methodVisitor.visitLocalVariable(
            "this", "Lasm/User;", null, label0,
            label1, 0
        )
        methodVisitor.visitMaxs(1, 1)
        methodVisitor.visitEnd()

        classWriter.visitEnd()
        println("------------david 4 ")
        return classWriter.toByteArray()
    }
}